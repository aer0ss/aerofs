package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.Base64;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.lib.ExternalSorter;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

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

    static final Logger l = Loggers.getLogger(LinkedRevProvider.class);

    // public for use in DPUTMigrateRevisionSuffixToBase64
    public static class RevisionSuffix implements Serializable
    {
        private static final long serialVersionUID = 1L;

        // separates file name from encoded revision suffix
        public static final char SEPARATOR = '.';

        // KIndex + 2 timestamps
        public static int DECODED_LENGTH = 2 * C.LONG_SIZE + C.INTEGER_SIZE;

        public final int _kidx;     // branch index
        public final long _mtime;   // mtime of file moved to revision tree
        public final long _rtime;   // time of movement to revision tree

        private @Nullable String encoded;  // cached url-safe base64 encoding

        /**
         * The mtime is used to offer a consistent timestamp in the UI for old and current versions.
         * The rtime is used to differentiate between revisions of a file with the same mtime (this
         * is necessary to avoid losing revision information on Unix devices and avoid no-sync on
         * Windows ones (renaming fails when the target exists)).
         */

        public RevisionSuffix(int kidx, long mtime, long rtime)
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
            int kidx = buf.getInt();
            long mtime = buf.getLong();
            long rtime = buf.getLong();
            return new RevisionSuffix(kidx, mtime, rtime);
        }

        public String encoded()
        {
            if (encoded == null) {
                byte d[] = new byte[DECODED_LENGTH];
                ByteBuffer buf = ByteBuffer.wrap(d);
                buf.putInt(_kidx);
                buf.putLong(_mtime);
                buf.putLong(_rtime);
                try {
                    encoded = Base64.encodeBytes(d, Base64.URL_SAFE);
                } catch (IOException e) {
                    // the base64 encoder can only throw IOException when using the GZIP option...
                    throw SystemUtil.fatalWithReturn(e);
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

        void delete_() throws IOException
        {
            long sz = _fRev.getLength();
            _fRev.delete();
            changeSpace(-sz);
        }
    }

    private final LinkerRootMap _lrm;
    private final InjectableFile.Factory _factFile;

    CleanerScheduler _cleanerScheduler;
    long _spaceDelta;

    // sigh, can't use PowerMock in the daemon due to OSUtil so we have to use this
    // stupid wrapper to be able to test the cleaner...
    class TimeSource {
        long getTime() { return System.currentTimeMillis(); }
    }

    TimeSource _ts = new TimeSource();

    public LinkedRevProvider(LinkerRootMap lrm, InjectableFile.Factory factFile)
    {
        _lrm = lrm;
        _factFile = factFile;
    }

    void startCleaner_()
    {
        _cleanerScheduler = new CleanerScheduler();
        _cleanerScheduler.start();
    }

    String revPath(Path path)
    {
        return Util.join(_lrm.auxRoot_(path.sid()), AuxFolder.REVISION._name,
                Util.join(path.elements()));
    }

    // called from LocalStorage
    LinkedRevFile newLocalRevFile_(Path path, String absPath, KIndex kidx) throws IOException
    {
        InjectableFile f = _factFile.create(absPath);
        RevInfo rev = new RevInfo(revPath(path.removeLast()), f.getName(), kidx.getInt(),
                _ts.getTime(), f.lastModified(), f.getLengthOrZeroIfNotFile());

        // TODO use alternate folder for history of NROs

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
        String auxPath = revPath(path);
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

        String auxPath = revPath(path.removeLast());

        InjectableFile parent = _factFile.create(auxPath);
        SortedMap<Long, Revision> revisions = Maps.newTreeMap();
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
                    revisions.put(info._suffix._rtime,
                                  new Revision(BaseUtil.string2utf(info.index()),
                                               info._suffix._mtime,
                                               info._length));
                }
            }
        }

        return revisions.values();
    }

    private InjectableFile getRevFile_(Path path, byte[] index)
    {
        String auxPath = revPath(path) + RevisionSuffix.SEPARATOR + BaseUtil.utf2string(index);
        return _factFile.create(auxPath);
    }

    private InjectableFile getExistingRevFile_(Path path, byte[] index)
            throws InvalidRevisionIndexException
    {
        InjectableFile file = getRevFile_(path, index);
        if (!file.exists() || file.isDirectory()) throw new InvalidRevisionIndexException();
        return file;
    }

    @Override
    public RevInputStream getRevInputStream_(Path path, byte[] index)
            throws Exception
    {
        RevisionSuffix suffix = RevisionSuffix.fromEncodedNullable(BaseUtil.utf2string(index));
        if (suffix == null) throw new InvalidRevisionIndexException();
        InjectableFile file = getExistingRevFile_(path, index);
        return new RevInputStream(file.newInputStream(), file.getLength(), suffix._mtime);
    }

    @Override
    public void deleteRevision_(Path path, byte[] index) throws Exception
    {
        InjectableFile file = getExistingRevFile_(path, index);
        file.deleteOrThrowIfExist();
    }

    @Override
    public void deleteAllRevisionsUnder_(Path path) throws Exception
    {
        InjectableFile dir = _factFile.create(revPath(path));
        dir.deleteOrThrowIfExistRecursively();
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
            _thread = ThreadUtil.startDaemonThread("rev-cleaner", new Runnable()
            {
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
                for (LinkerRoot root : _lrm.getAllRoots_()) {
                    sweep(Util.join(_lrm.auxRoot_(root.sid()), AuxFolder.REVISION._name));
                }
            }
        }

        private void sweep(String absRevRoot)
        {
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
                _cleaner.run(absRevRoot);
            } catch (InterruptedException e) {
                l.warn("cleaner interrupted: " + Util.e(e));
                return;
            } catch (Exception e) {
                l.error("cleaner error: " + Util.e(e));
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

        public void run(String absRoot) throws IOException, InterruptedException {
            RunData runData = new RunData(absRoot);
            runData.run();
        }

        /** Delete old revs when using more than this much space */
        long getSpaceLimit(String absPath) {
            InjectableFile root = _factFile.create(absPath);
            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            // allow at least 100 MB, at most min of 1/5 of total space and usable free space
            return Math.max(_minSpaceLimit, Math.min(totalSpace / 5, usableSpace));
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
        class RunData
        {
            long _totalSize;
            long _fileCount;
            long _dirCount;
            long _startTime = _ts.getTime();
            int _delayCount;
            final String _absRevRoot;

            RunData(String absRoot)
            {
                _absRevRoot = absRoot;
            }

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
                InjectableFile root = _factFile.create(_absRevRoot);
                walk(root);
            }

            void delete() throws InterruptedException, IOException {
                // add in overhead
                long space = _totalSize + (2 * C.KB * _fileCount);
                long spaceLimit = getSpaceLimit(_absRevRoot);
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

            private void walk(InjectableFile file)
                    throws InterruptedException, IOException {
                checkInterrupted();
                if (file.isFile()) {
                    ++_fileCount;
                    RevInfo revInfo = RevInfo.fromRevisionFileNullable(file);
                    if (revInfo != null) {
                        _totalSize += revInfo._length;
                        if (_startTime > revInfo._suffix._rtime) {
                            l.debug("Old file: " + revInfo);
                            _sorter.add(revInfo);
                        }
                    }
                } else if (file.isDirectory()) {
                    ++_dirCount;
                    InjectableFile[] children = file.listFiles();
                    if (children != null) {
                        for (InjectableFile child : children) {
                            walk(child);
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
                int r = BaseUtil.compare(o1._suffix._rtime, o2._suffix._rtime);
                if (r == 0) r = BaseUtil.compare(o1._suffix._mtime, o2._suffix._mtime);
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

        public RevInfo(String revBase, String name, int kidx, long rtime, long mtime, long length)
        {
            this(revBase, name, new RevisionSuffix(kidx, mtime, rtime), length);
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
