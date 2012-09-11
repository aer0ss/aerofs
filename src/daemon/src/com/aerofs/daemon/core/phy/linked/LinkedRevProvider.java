package com.aerofs.daemon.core.phy.linked;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.google.inject.Inject;

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

    private static final String DATE_FORMAT = "yyyyMMdd_HHmmss_SSS";

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
            changeSpace(_fRev.length());
        }

        void rollback_() throws IOException
        {
            _fRev.moveInSameFileSystem(_fOrg);
            changeSpace(-_fRev.length());
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
    LinkedRevFile newLocalRevFile_(Path path, String absPath, KIndex kidx)
    {
        long mtime = new File(absPath).lastModified();
        String date = new SimpleDateFormat(DATE_FORMAT).format(new Date(mtime));

        String pathRev = Util.join(_pathBase, Util.join(path.elements()));
        pathRev += "-" + kidx + "_" + date;
        // BUG: this may fail, e.g. if the file name is too long
        return new LinkedRevFile(_factFile.create(pathRev), _factFile.create(absPath));
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
                    RevInfo info = RevInfo.fromFile(file);
                    if (info == null) {
                        continue;
                    }
                    name = info.baseName();
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
                RevInfo info = RevInfo.fromFile(file);
                if (info == null) {
                    continue;
                }
                if (path.last().equals(info.baseName())) {
                    revisions.add(new Revision(Util.string2utf(info.index()),
                                               info._date.getTime(),
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
        String auxPath = Util.join(_pathBase,
                                   Util.join(path.elements()))
                         + "-" + Util.utf2string(index);
        InjectableFile file = _factFile.create(auxPath);
        if (!file.exists() || file.isDirectory())
            throw new ExNotFound("Invalid revision index");
        return new RevInputStream(file.newInputStream(), file.length());
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
            l.info("deleting " + file);
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
                l.info("space: " + Util.formatSize(space));
                l.info("limit: " + Util.formatSize(spaceLimit));
                if (space < spaceLimit) return;

                ExternalSorter.Input<RevInfo> it = _sorter.sort();
                try {
                    if (!it.hasNext()) return;

                    long needed = space - getSpaceLowerLimit(spaceLimit);
                    while (it.hasNext()) {
                        if (needed <= 0) return;
                        RevInfo revInfo = it.next();
                        long length = revInfo._length;
                        InjectableFile file = _factFile.create(revInfo._path);
                        if (tryDeleteOldRev(file)) {
                            needed -= length;
                        }
                        delay();
                    }
                } finally {
                    it.close();
                }
            }

            private void walk(InjectableFile parent, InjectableFile file) throws InterruptedException, IOException {
                checkInterrupted();
                if (file.isFile()) {
                    ++_fileCount;
                    RevInfo revInfo = parse(parent, file);
                    if (revInfo != null) {
                        _totalSize += revInfo._length;
                        if (_startTime.compareTo(revInfo._date) > 0) {
                            l.info("Old file: " + revInfo);
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

            private RevInfo parse(InjectableFile parent, InjectableFile file) {
                return RevInfo.fromFile(file);
            }
        }
    }

    static class RevInfo implements Serializable
    {
        private static final long serialVersionUID = 1L;
        private static final DateFormat _dateFormat = new SimpleDateFormat(DATE_FORMAT);

        static final Comparator<RevInfo> CHRONOLOGICAL = new Comparator<RevInfo>() {
            @Override
            public int compare(RevInfo o1, RevInfo o2)
            {
                return o1._date.compareTo(o2._date);
            }
        };

        String _path;
        int _baseNameIdx;
        int _kidx;
        Date _date;
        long _length;

        @Override
        public String toString()
        {
            return _path;
        }

        private int indexSeparator() {
            int pos2 = _path.length() - DATE_FORMAT.length() - 1;
            assert(_path.charAt(pos2) == '_');
            int pos1 = _path.lastIndexOf('-', pos2);
            assert(pos1 > 0);
            return pos1;
        }

        public String baseName() {
            return _path.substring(_baseNameIdx, indexSeparator());
        }

        public String index() {
            return _path.substring(indexSeparator() + 1);
        }

        /**
         * Extract revision information from an existing revision file.
         *
         * The date and the index are derived from the file name.
         * The length is queried from the file system.
         *
         * NOTE: the file MUST exist until the end of the method and its name MUST match the format
         * produced by {@link LinkedRevProvider#newLocalRevFile_}
         *
         * @return an initialized RevInfo object or null for non-existing / invalid files
         */
        public static RevInfo fromFile(InjectableFile file) {
            String name = file.getName();
            if (name.length() < 3 + DATE_FORMAT.length()) return null;
            int pos2 = name.length() - DATE_FORMAT.length() - 1;
            if (name.charAt(pos2) != '_') return null;
            int pos1 = name.lastIndexOf('-', pos2);
            if (pos1 <= 0) return null;
            int kidx = Integer.parseInt(name.substring(pos1 + 1, pos2));
            Date date;
            try {
                date = _dateFormat.parse(name.substring(name.length() - DATE_FORMAT.length()));
            } catch (ParseException e) {
                return null;
            }

            RevInfo revInfo = new RevInfo();
            revInfo._path = file.getPath();
            revInfo._baseNameIdx = revInfo._path.length() - name.length();
            revInfo._kidx = kidx;
            revInfo._date = date;
            // avoid FileUtil.assertIsFile in case of concurrent deletion (support-143)
            revInfo._length = file.lengthNoAssertIsFile();
            // Check for file disappearance while we where building the RevInfo (support-143)
            return (file.exists() && file.isFile()) ? revInfo : null;
        }
    }
}
