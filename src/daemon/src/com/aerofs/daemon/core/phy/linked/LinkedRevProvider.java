package com.aerofs.daemon.core.phy.linked;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.aerofs.lib.Base64;
import com.aerofs.lib.os.OSUtil;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.lib.C;
import com.aerofs.lib.C.AuxFolder;
import com.aerofs.lib.ExternalSorter;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import javax.annotation.Nullable;

/**
 * Provider for backup revisions
 */
public class LinkedRevProvider implements IPhysicalRevProvider
{
    /*
     * - Keep revision contents as files in {AuxFolder.REVISION}/<path>/<name>-<kidx>_<date>
     *   - do we need an index of file paths to their OIDs, versions, etc?
     *   - how do we make this persistent?
     * - Keep track of amount of space used by old revisions
     *   - how do we make this persistent?
     * - Put a limit (high water mark) on space taken by old revisions
     *   - how do we pick this? e.g. 10% of AeroFS folder space + 100MB
     * - When space used exceeds limit, run garbage collection
     *   - collect until space used is below water mark, e.g. 50% of limit
     *
     */

    static final Logger l = Util.l(LinkedRevProvider.class);

    // public for use in DPUTMigrateRevisionSuffixToBase64
    public static class RevisionSuffix
    {
        // separates file name from encoded revision suffix
        public static final char SEPARATOR = '.';

        // KIndex + 2 timestamps
        // Stupid Java provide size in bits but not in bytes... On the other hand the spec does
        // ensure that long and int are 64bit and 32bit wide respectively
        public static int DECODED_LENGTH = 2 * (Long.SIZE / 8) + (Integer.SIZE / 8);

        public final KIndex _kidx;  // branch index
        public final long _mtime;   // mtime of file moved to revision tree
        public final long _rtime;   // time of movement to revision tree

        private @Nullable String encoded;  // cached url-safe base64 encoding

        /**
         * The mtime is used to offer a consistent timestamp in the UI for old and current versions.
         * The rtime is used to differentiate between revisions of a file with the same mtime (this
         * is necessary to avoid losing revision information on Unix devices and avoid no-sync on
         * Windows ones (renaming fails when the target exists)).
         */

        public RevisionSuffix(KIndex kidx, long mtime, long rtime)
        {
            _kidx = kidx;
            _mtime = mtime;
            _rtime = rtime;
        }

        /**
         * @param encoded url-safe base64 encoded suffix extracted from the name of a revision file
         * @return decoded suffix, null if the suffix was not valid
         */
        public static @Nullable RevisionSuffix fromEncodedNullable(String encoded)
        {
            byte[] decoded;
            try {
                decoded = Base64.decode(encoded, Base64.URL_SAFE);
            } catch (IOException e) {
                return null;
            }
            if (decoded.length != DECODED_LENGTH) return null;
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            KIndex kidx = new KIndex(buf.getInt());
            long mtime = buf.getLong();
            long rtime = buf.getLong();
            return new RevisionSuffix(kidx, mtime, rtime);
        }

        public String encoded()
        {
            if (encoded == null) {
                byte d[] = new byte[DECODED_LENGTH];
                ByteBuffer buf = ByteBuffer.wrap(d);
                buf.putInt(_kidx.getInt());
                buf.putLong(_mtime);
                buf.putLong(_rtime);
                try {
                    encoded = Base64.encodeBytes(d, Base64.URL_SAFE);
                } catch (IOException e) {
                    // the base64 encoder can only throw IOException when using the GZIP option...
                    throw Util.fatal(e);
                }
            }
            return encoded;
        }
    }

    class LinkedRevFile
    {
        private final InjectableFile _fRev, _fOrg;

        private LinkedRevFile(InjectableFile fRev, InjectableFile fOrg)
        {
            _fRev = fRev;
            _fOrg = fOrg;
        }

        void save_() throws IOException
        {
            InjectableFile fParent = _fRev.getParentFile();
            // ignore mkdirs() errors. it will be caught when moving the file
            // into the directory
            if (!fParent.exists()) fParent.mkdirs();
            _fOrg.moveInSameFileSystem(_fRev);
            changeSpace(_fRev.getLength());
        }

        void rollback_() throws IOException
        {
            _fRev.moveInSameFileSystem(_fOrg);
            changeSpace(-_fOrg.getLength());
        }
    }

    private final InjectableFile.Factory _factFile;
    private String _pathBase;
    boolean _startCleanerScheduler = true;
    CleanerScheduler _cleanerScheduler;
    long _spaceDelta;


    @Inject
    public LinkedRevProvider(InjectableFile.Factory factFile)
    {
        _factFile = factFile;
    }

    // called from LocalStorage
    void init_(String pathAuxRoot) throws IOException
    {
        _pathBase = Util.join(pathAuxRoot, AuxFolder.REVISION._name);
        InjectableFile fBase = _factFile.create(_pathBase);
        if (!fBase.exists()) fBase.mkdirs();
        if (_startCleanerScheduler) {
            _cleanerScheduler = new CleanerScheduler();
            _cleanerScheduler.start();
        }
    }

    // called from LocalStorage
    LinkedRevFile newLocalRevFile_(Path path, String absPath, KIndex kidx) throws IOException
    {
        InjectableFile f = _factFile.create(absPath);
        RevInfo rev = new RevInfo(Util.join(_pathBase, Util.join(path.removeLast().elements())),
                f.getName(), kidx, f.lastModified(), f.getLengthOrZeroIfNotFile());

        String pathRev = rev.getAbsolutePath();

        if (OSUtil.isWindows() && pathRev.length() > 260) {
            // TODO: shorten name, will break rev history but will keep sync working...
        }

        return new LinkedRevFile(_factFile.create(pathRev), f);
    }

    @Override
    public Collection<Child> listRevChildren_(Path path)
            throws Exception
    {
        String auxPath = Util.join(_pathBase, Util.join(path.elements()));
        Set<Child> children = Sets.newHashSet();

        InjectableFile parent = _factFile.create(auxPath);
        InjectableFile[] files = parent.listFiles();

        if (files != null) {
            for (InjectableFile file : files) {
                String name = file.getName();
                boolean dir = file.isDirectory();
                if (!dir) {
                    RevInfo info = RevInfo.fromRevisionFileNullable(file);
                    if (info == null) {
                        continue;
                    }
                    name = info._name;
                }
                children.add(new Child(name, dir));
            }
        }

        return children;
    }

    @Override
    public Collection<Revision> listRevHistory_(Path path)
            throws Exception
    {
        if (path.isEmpty())
            return Collections.emptyList();

        String parentPath = Util.join(path.removeLast().elements());
        String auxPath = Util.join(_pathBase, parentPath);

        InjectableFile parent = _factFile.create(auxPath);
        List<Revision> revisions = Lists.newArrayList();
        InjectableFile[] files = parent.listFiles();

        if (files != null) {
            for (InjectableFile file : files) {
                if (file.isDirectory()) {
                    continue;
                }
                RevInfo info = RevInfo.fromRevisionFileNullable(file);
                if (info == null) {
                    continue;
                }
                if (path.last().equals(info._name)) {
                    // TODO: use the rtime (revision creation time) as sorting key
                    revisions.add(new Revision(Util.string2utf(info.index()),
                                               info._suffix._mtime,
                                               info._length));
                }
            }
        }

        Collections.sort(revisions);
        return revisions;
    }

    @Override
    public RevInputStream getRevInputStream_(Path path, byte[] index)
            throws Exception
    {
        String auxPath = Util.join(_pathBase, Util.join(path.elements()))
                + RevisionSuffix.SEPARATOR + Util.utf2string(index);
        InjectableFile file = _factFile.create(auxPath);
        if (!file.exists() || file.isDirectory())
            throw new ExNotFound("Invalid revision index");
        return new RevInputStream(file.newInputStream(), file.getLength());
    }

    private synchronized void changeSpace(long delta)
    {
        _spaceDelta += delta;
        checkSpace();
    }

    private void checkSpace()
    {
        // TODO: start cleaner if there's not enough free space
    }

    class CleanerScheduler
    {
        private Thread _thread;
        private final Random _random = Util.rand();
        private final Cleaner _cleaner = new Cleaner();
        private boolean _trigger = false;

        public synchronized void start()
        {
            if (_thread != null) return;
            _thread = Util.startDaemonThread("rev-cleaner", new Runnable() {
                @Override
                public void run()
                {
                    loop();
                }
            });
            _thread.setPriority(3);
        }

        public synchronized void stop()
        {
            if (_thread != null) {
                _thread.interrupt();
                _thread = null;
            }
        }

        public synchronized void trigger()
        {
            _trigger = true;
            notify();
        }

        private void loop()
        {
            while (true) {
                try {
                    synchronized (this) {
                        if (Thread.interrupted()) return;
                        if (_thread == null) return;
                        if (!_trigger) {
                            long sleepMillis = getSleepMillis();
                            TimeUnit.MILLISECONDS.timedWait(this, sleepMillis);
                        }
                        if (Thread.interrupted()) return;
                        if (_thread == null) return;
                        _trigger = false;
                    }
                    _cleaner.run();
                } catch (InterruptedException e) {
                    l.warn("cleaner interrupted: " + Util.e(e));
                    return;
                } catch (Exception e) {
                    l.error("cleaner error: " + Util.e(e));
                }
            }
        }

        /** Run between 3 and 5 in the morning in the local time zone */
        private long getSleepMillis()
        {
            Calendar now = Calendar.getInstance();
            Calendar next = (Calendar)now.clone();
            next.set(Calendar.HOUR_OF_DAY, 3);
            next.set(Calendar.MINUTE, 0);
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            if (next.compareTo(now) < 0) next.add(Calendar.DAY_OF_YEAR, 1);
            long delayMillis = next.getTimeInMillis() - now.getTimeInMillis();
            delayMillis += _random.nextInt((int)TimeUnit.HOURS.toMillis(2));
            return delayMillis;
        }
    }


    class Cleaner {
        long _ageLimitMillis = TimeUnit.DAYS.toMillis(7);
        int _delayEvery = 10;
        long _delayMillis = 10;
        long _minSpaceLimit = 100 * C.MB;

        public void run() throws IOException, InterruptedException {
            RunData runData = new RunData();
            runData.run();
        }

        /** Delete old revs when using more than this much space */
        long getSpaceLimit() {
            InjectableFile root = _factFile.create(_pathBase);
            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            // allow at least 100 MB, at most min of 1/5 of total space and usable free space
            long spaceLimit = Math.max(_minSpaceLimit, Math.min(totalSpace / 5, usableSpace));
            return spaceLimit;
        }

        /** Delete old revs until space is under this limit */
        long getSpaceLowerLimit(long limit) {
            return limit * 3 / 4;
        }

        boolean tryDeleteOldRev(InjectableFile file) {
            l.debug("deleting " + file);
            try {
                // TODO: move to trash?
                file.delete();
                return true;
            } catch (IOException e) {
                l.error("failed to delete " + file + ": " + Util.e(e));
                return false;
            }
        }

        /**
         * The data for one sweep of the cleaner
         */
        class RunData {
            long _totalSize;
            long _fileCount;
            long _dirCount;
            Date _startTime = new Date();
            Date _maxTime = new Date(_startTime.getTime() - _ageLimitMillis);
            int _delayCount;

            ExternalSorter<RevInfo> _sorter =
                    new ExternalSorter<RevInfo>(RevInfo.CHRONOLOGICAL);
            {
                _sorter.setMaxSize(64 << 10);
            }

            // keep past 7 days
            // TODO: keep landmark versions?

            void run() throws InterruptedException, IOException {
                try {
                    walk();
                    delete();
                } finally {
                    close();
                }
            }

            private void close() throws IOException
            {
                _sorter.close();
            }

            void walk() throws InterruptedException, IOException {
                InjectableFile root = _factFile.create(_pathBase);
                walk(null, root);
            }

            void delete() throws InterruptedException, IOException {
                // add in overhead
                long space = _totalSize + (2 * C.KB * _fileCount);
                long spaceLimit = getSpaceLimit();
                l.debug("space: " + Util.formatSize(space));
                l.debug("limit: " + Util.formatSize(spaceLimit));
                if (space < spaceLimit) return;

                ExternalSorter.Input<RevInfo> it = _sorter.sort();
                try {
                    if (!it.hasNext()) return;

                    long needed = space - getSpaceLowerLimit(spaceLimit);
                    while (it.hasNext()) {
                        if (needed <= 0) return;
                        RevInfo revInfo = it.next();
                        long length = revInfo._length;
                        InjectableFile file = _factFile.create(revInfo.getAbsolutePath());
                        if (tryDeleteOldRev(file)) {
                            needed -= length;
                        }
                        delay();
                    }
                } finally {
                    it.close();
                }
            }

            private void walk(InjectableFile parent, InjectableFile file)
                    throws InterruptedException, IOException {
                checkInterrupted();
                if (file.isFile()) {
                    ++_fileCount;
                    RevInfo revInfo = RevInfo.fromRevisionFileNullable(file);
                    if (revInfo != null) {
                        _totalSize += revInfo._length;
                        if (_startTime.compareTo(new Date(revInfo._suffix._rtime)) > 0) {
                            l.debug("Old file: " + revInfo);
                            _sorter.add(revInfo);
                        }
                    }
                } else if (file.isDirectory()) {
                    ++_dirCount;
                    InjectableFile[] children = file.listFiles();
                    if (children != null) {
                        for (InjectableFile child : children) {
                            walk(file, child);
                        }
                    }
                    delay();
                }
            }

            private void checkInterrupted() throws InterruptedException {
                if (Thread.interrupted()) throw new InterruptedException();
            }

            private void delay() throws InterruptedException {
                checkInterrupted();
                if (_delayEvery > 0 && ++_delayCount >= _delayEvery) {
                    _delayCount = 0;
                    TimeUnit.MILLISECONDS.sleep(_delayMillis);
                }
            }
        }
    }

    static class RevInfo implements Serializable
    {
        private static final long serialVersionUID = 1L;

        static final Comparator<RevInfo> CHRONOLOGICAL = new Comparator<RevInfo>() {
            @Override
            public int compare(RevInfo o1, RevInfo o2)
            {
                int r = Util.compare(o1._suffix._rtime, o2._suffix._rtime);
                if (r == 0) r = Util.compare(o1._suffix._mtime, o2._suffix._mtime);
                return r;
            }
        };

        private final String _path;    // absolute path of directory in which revision file resides
        final String _name;            // name of file to which revision is associated
        final RevisionSuffix _suffix;  // decoded revision suffix
        final long _length;            // length in bytes of revision file

        @Override
        public String toString()
        {
            return getAbsolutePath();
        }

        /**
         * @return revision index (used to distinguish revisions and open a RevInputStream)
         */
        public String index()
        {
            return _suffix.encoded();
        }

        /**
         * @return absolute path to the actual location of the revision file
         */
        public String getAbsolutePath()
        {
            return Util.join(_path, _name) + RevisionSuffix.SEPARATOR + index();
        }

        public RevInfo(String revBase, String name, KIndex kidx, long mtime, long length)
        {
            this(revBase, name, new RevisionSuffix(kidx, mtime, new Date().getTime()), length);
        }

        public RevInfo(String revBase, String name, RevisionSuffix suffix, long length)
        {
            _path = revBase;
            _name = name;
            _suffix = suffix;
            _length = length;
        }

        /**
         * Extract revision information from a file in the revision tree.
         *
         * The date and the index are derived from the file name.
         * The length is queried from the file system.
         *
         * NOTE: the file MUST exist until the end of the method and its name MUST match the format
         * produced by {@link LinkedRevProvider#newLocalRevFile_}
         */
        public static @Nullable RevInfo fromRevisionFileNullable(InjectableFile file)
        {
            String revName = file.getName();
            int pos = revName.lastIndexOf(RevisionSuffix.SEPARATOR);
            if (pos <= 0) return null;
            String path = file.getParent();
            String name = revName.substring(0, pos);
            RevisionSuffix suffix = RevisionSuffix.fromEncodedNullable(revName.substring(pos + 1));
            if (suffix == null) return null;
            long length = file.getLengthOrZeroIfNotFile();
            // Check for file disappearance while we where building the RevInfo (support-143)
            return file.isFile() ? new RevInfo(path, name, suffix, length) : null;
        }
    }
}
