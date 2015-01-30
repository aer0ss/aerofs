package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.lib.ExternalSorter;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.Path;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.injectable.TimeSource;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

/**
 * Provider for version history
 *
 * To correctly support case-sensitivity and non-representable objects, each path component is
 * (utf-8 then hex)-encoded.
 *
 * Why hex? Because Windows. Because OSX. Because some stupid morons somewhere someday decided that
 * case-insensitive filesystems where a good idea. This rules out Base64 because it uses both
 * lowercase and uppercase letters and would therefore lead to all sorts of crazyness on case
 * insensitive filesystems. If we really cared about density we could roll our own Base32 or Base38
 * that would be case-insensitive-safe but that's just not worth the effort at this point.
 *
 * TL;DR Case-sensitivity is a luxury. We want to offer it but cannot rely on it.
 *
 * To simplify version listing and reduce scalability problems when the number of versions
 * grow, each file gets a folder and versions are stored as sub files of that folder. A single
 * letter prefix is used to distinguish folders that contain subfolders from those that contain
 * versions.
 *
 * For instance, suppose you have two versions of file 'foo' and one version of file 'foo/bar',
 * the resulting history structure would be:
 *
 * {AuxFolder.HISTORY}/
 *      F666f6f/
 *          {revinfo}
 *          {revinfo}
 *      D666f6f
 *          F626172/
 *              {revinfo}
 *
 * where {revinfo} is an hex encoding of RevisionInfo (i.e. KIndex, mtime, rtime tuple)
 *
 * If the hex-encoding is larger than 255 the following splitting pattern is used:
 *
 * {AuxFolder.HISTORY}/
 *      E{hex(name).substring(0, 254)}/
 *          E{hex(name).substring(254, 509)}/
 *              F{hex(name).substring(510)}
 *
 * This approach ensures that long path can be moved to history while still allowing simple and
 * efficient listing of revision children.
 *
 * NB: this still isn't foolproof on Windows because the geniuses at Microsoft decided to place
 * an arbitrary limit on total path length (32767 characters). We could conceivably work around
 * that limitation using a different prefix char and "rebasing" the split hierarchy. However
 * the likelihood of users actually running into that limit is low enough that it's not worth
 * doing at this time.
 */
public class LinkedRevProvider implements IPhysicalRevProvider
{
    private static final Logger l = Loggers.getLogger(LinkedRevProvider.class);

    /*
     * TODO
     * - Keep track of amount of space used by old revisions
     *   - how do we make this persistent?
     * - Put a limit (high water mark) on space taken by old revisions
     *   - how do we pick this? e.g. 10% of AeroFS folder space + 100MB
     * - When space used exceeds limit, run garbage collection
     *   - collect until space used is below water mark, e.g. 50% of limit
     */

    // public for use in DPUTs
    public static class RevisionInfo implements Serializable, Comparable<RevisionInfo>
    {
        private static final long serialVersionUID = 1L;

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
        public RevisionInfo(int kidx, long mtime, long rtime)
        {
            _kidx = kidx;
            _mtime = mtime;
            _rtime = rtime;
        }

        public static @Nullable RevisionInfo fromHexEncodedNullable(String encoded)
        {
            byte[] decoded;
            try {
                decoded = BaseUtil.hexDecode(encoded);
            } catch (ExFormatError e) {
                return null;
            }
            if (decoded.length != DECODED_LENGTH) return null;
            ByteBuffer buf = ByteBuffer.wrap(decoded);
            int kidx = buf.getInt();
            long mtime = buf.getLong();
            long rtime = buf.getLong();
            return new RevisionInfo(kidx, mtime, rtime);
        }

        public String hexEncoded()
        {
            if (encoded == null) {
                ByteBuffer buf = ByteBuffer.allocate(DECODED_LENGTH);
                buf.putInt(_kidx);
                buf.putLong(_mtime);
                buf.putLong(_rtime);
                encoded = BaseUtil.hexEncode(buf.array());
            }
            return encoded;
        }

        @Override
        public int hashCode()
        {
            return hexEncoded().hashCode();
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o != null && o instanceof RevisionInfo
                                         && ((RevisionInfo)o).compareTo(this) == 0);
        }

        @Override
        public int compareTo(RevisionInfo o)
        {
            int c = BaseUtil.compare(_rtime, _rtime);
            return c != 0 ? c : BaseUtil.compare(_mtime, o._mtime);
        }
    }

    class LinkedRevFile
    {
        private final Path _path;
        private final InjectableFile _fRev, _fOrg;

        private LinkedRevFile(Path path, InjectableFile fRev, InjectableFile fOrg)
        {
            _path = path;
            _fRev = fRev;
            _fOrg = fOrg;
        }

        void save_() throws IOException
        {
            InjectableFile fParent = _fRev.getParentFile();
            // ignore mkdirs() errors. it will be caught when moving the file
            // into the directory
            fParent.ensureDirExists();
            _fOrg.moveInSameFileSystem(_fRev);
            changeSpace(_fRev.length());
        }

        void rollback_() throws IOException
        {
            _fRev.moveInSameFileSystem(_fOrg);
            deleteEmptyParentRecursively(_path.sid(), _fRev.getParentFile());
            changeSpace(-_fOrg.length());
        }

        void delete_() throws IOException
        {
            long sz = _fRev.length();
            _fRev.delete();
            changeSpace(-sz);
        }
    }

    private final LinkerRootMap _lrm;
    private final InjectableFile.Factory _factFile;

    CleanerScheduler _cleanerScheduler;
    long _spaceDelta;

    private final TimeSource _ts;

    @Inject
    public LinkedRevProvider(LinkerRootMap lrm, InjectableFile.Factory factFile, TimeSource ts)
    {
        _ts = ts;
        _lrm = lrm;
        _factFile = factFile;
    }

    void startCleaner_()
    {
        _cleanerScheduler = new CleanerScheduler();
        _cleanerScheduler.start();
    }

    public enum PathType
    {
        FILE('F'),
        EXTENDED('E'), // to handle long filenames
        DIR('D');

        public final Character _prefix;

        PathType(Character prefix)
        {
            _prefix = prefix;
        }

        private final static Map<Character, PathType> PREFIX_TO_VALUE;
        static {
            Builder<Character, PathType> bd = ImmutableMap.builder();
            for (PathType t : values()) bd.put(t._prefix, t);
            PREFIX_TO_VALUE = bd.build();
        }

        static @Nullable PathType fromPrefix(Character c)
        {
            return PREFIX_TO_VALUE.get(c);
        }
    }

    private static String splitLongName(String hex, int maxLength, PathType p)
    {
        return hex.length() < maxLength
                ? p._prefix + hex
                : Util.join(PathType.EXTENDED._prefix + hex.substring(0, maxLength - 1),
                        splitLongName(hex.substring(maxLength - 1), maxLength, p));
    }

    public static String encode(String filename, PathType type)
    {
        return splitLongName(BaseUtil.hexEncode(BaseUtil.string2utf(filename)), 255, type);
    }

    private String revPath(Path path, PathType type)
    {
        StringBuilder bd = new StringBuilder();

        String[] elems = path.elements();
        for (int i = 0; i < elems.length; ++i) {
            bd.append(File.separatorChar);
            bd.append(encode(elems[i], i == elems.length - 1 ? type : PathType.DIR));
        }

        return Util.join(_lrm.auxRoot_(path.sid()), AuxFolder.HISTORY._name) + bd.toString();
    }

    String newRevPath(Path path, String absPath, KIndex kidx) throws IOException
    {
        InjectableFile f = _factFile.create(absPath);
        String revPath = revPath(path, PathType.FILE);
        RevisionInfo info = new RevisionInfo(kidx.getInt(), _ts.getTime(), f.lastModified());
        return Util.join(revPath, info.hexEncoded());
    }

    LinkedRevFile newLocalRevFile(Path path, String absPath, KIndex kidx) throws IOException
    {
        return new LinkedRevFile(path,
                _factFile.create(newRevPath(path, absPath, kidx)),
                _factFile.create(absPath));
    }

    @Override
    public Collection<Child> listRevChildren_(Path path) throws IOException
    {
        Set<Child> children = Sets.newHashSet();
        listChildren(_factFile.create(revPath(path, PathType.DIR)), "", children);
        return children;
    }

    private void listChildren(InjectableFile parent, String prefix, Set<Child> children)
    {
        InjectableFile[] files = parent.listFiles();

        if (files == null) return;

        for (InjectableFile file : files) {
            if (!file.isDirectory()) continue;
            String name = file.getName();
            PathType type = PathType.fromPrefix(name.charAt(0));
            if (type == null) continue;

            try {
                name = prefix + BaseUtil.utf2string(BaseUtil.hexDecode(name.substring(1)));
            } catch (ExFormatError e) {
                continue;
            }

            if (type == PathType.EXTENDED) {
                listChildren(file, name, children);
            } else {
                children.add(new Child(name, type == PathType.DIR));
            }
        }
    }

    @Override
    public Collection<Revision> listRevHistory_(Path path) throws IOException
    {
        if (path.isEmpty()) return Collections.emptyList();

        InjectableFile parent = _factFile.create(revPath(path, PathType.FILE));
        InjectableFile[] files = parent.listFiles();

        if (files == null) return Collections.emptyList();

        SortedMap<RevisionInfo, Revision> revisions = Maps.newTreeMap();
        for (InjectableFile file : files) {
            RevisionInfo info = file.isFile()
                    ? RevisionInfo.fromHexEncodedNullable(file.getName())
                    : null;
            if (info != null) {
                revisions.put(info,
                        new Revision(BaseUtil.string2utf(file.getName()), info._mtime,
                                file.lengthOrZeroIfNotFile()));
            }
        }
        return revisions.values();
    }

    private InjectableFile getRevFile_(Path path, byte[] index) throws IOException
    {
        String auxPath = Util.join(revPath(path, PathType.FILE), BaseUtil.utf2string(index));
        return _factFile.create(auxPath);
    }

    private InjectableFile getExistingRevFile_(Path path, byte[] index)
            throws ExInvalidRevisionIndex, IOException
    {
        InjectableFile file = getRevFile_(path, index);
        if (!(file.exists() && file.isFile())) throw new ExInvalidRevisionIndex();
        return file;
    }

    @Override
    public RevInputStream getRevInputStream_(Path path, byte[] index)
            throws IOException, ExInvalidRevisionIndex
    {
        RevisionInfo suffix = RevisionInfo.fromHexEncodedNullable(BaseUtil.utf2string(index));
        if (suffix == null) throw new ExInvalidRevisionIndex();
        InjectableFile file = getExistingRevFile_(path, index);
        return new RevInputStream(file.newInputStream(), file.length(), suffix._mtime);
    }

    @Override
    public void deleteRevision_(Path path, byte[] index)
            throws IOException, ExInvalidRevisionIndex
    {
        InjectableFile file = getExistingRevFile_(path, index);
        file.deleteOrThrowIfExist();
        deleteEmptyParentRecursively(path.sid(), file.getParentFile());
    }

    @Override
    public void deleteAllRevisionsUnder_(Path path) throws IOException
    {
        InjectableFile dir = _factFile.create(revPath(path, PathType.DIR));
        dir.deleteOrThrowIfExistRecursively();
        if (path.isEmpty()) {
            // we just deleted the HISTORY folder, recreate it
            dir.mkdirs();
        } else {
            deleteEmptyParentRecursively(path.sid(), dir.getParentFile());
        }
    }

    private void deleteEmptyParentRecursively(SID sid, InjectableFile dir)
    {
        String base = revPath(Path.root(sid), PathType.DIR);
        while (!base.equals(dir.getAbsolutePath())) {
            String[] l = dir.list();
            if (l == null || l.length != 0) break;
            dir.deleteIgnoreError();
            dir = dir.getParentFile();
        }
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
            _thread = ThreadUtil.startDaemonThread("rev-cl", new Runnable()
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
            while (nextSweep()) {
                for (LinkerRoot root : Lists.newArrayList(_lrm.getAllRoots_())) sweep(root.sid());
            }
        }

        private boolean nextSweep()
        {
            try {
                synchronized (this) {
                    if (Thread.interrupted()) return false;
                    if (_thread == null) return false;
                    if (!_trigger) {
                        long sleepMillis = getSleepMillis();
                        TimeUnit.MILLISECONDS.timedWait(this, sleepMillis);
                    }
                    if (Thread.interrupted()) return false;
                    if (_thread == null) return false;
                    _trigger = false;
                }
            } catch (InterruptedException e) {
                l.warn("cleaner interrupted");
                return false;
            }
            return true;
        }

        private void sweep(SID sid)
        {
            try {
                _cleaner.run(sid, Util.join(_lrm.auxRoot_(sid), AuxFolder.HISTORY._name));
            } catch (InterruptedException e) {
                l.warn("cleaner interrupted");
            } catch (Exception e) {
                l.error("cleaner error: ", e);
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

        public void run(SID sid, String absRoot) throws IOException, InterruptedException {
            RunData runData = new RunData(sid, absRoot);
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
            l.debug("deleting {}", file);
            try {
                // TODO: move to trash?
                file.delete();
                return true;
            } catch (IOException e) {
                l.error("failed to delete {}: ", file, e);
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
            final SID _sid;
            final String _absRevRoot;

            RunData(SID sid, String absRoot)
            {
                _sid = sid;
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
                l.debug("space: {}", Util.formatSize(space));
                l.debug("limit: {}", Util.formatSize(spaceLimit));
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
                            deleteEmptyParentRecursively(_sid, file.getParentFile());
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
                        if (_startTime > revInfo._info._rtime) {
                            l.debug("Old file: {}", revInfo);
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
                int r = BaseUtil.compare(o1._info._rtime, o2._info._rtime);
                if (r == 0) r = BaseUtil.compare(o1._info._mtime, o2._info._mtime);
                return r;
            }
        };

        final String _path;             // absolute path of revision file
        final RevisionInfo _info;       // decoded revision info
        final long _length;             // length in bytes of revision file

        @Override
        public String toString()
        {
            return _path;
        }

        public RevInfo(String path, RevisionInfo info, long length)
        {
            _path = path;
            _info = info;
            _length = length;
        }

        /**
         * Extract revision information from a file in the revision tree.
         *
         * The date and the index are derived from the file name.
         * The length is queried from the file system.
         *
         * NOTE: the file MUST exist until the end of the method and its name MUST match the format
         * produced by {@link LinkedRevProvider#newLocalRevFile}
         */
        public static @Nullable RevInfo fromRevisionFileNullable(InjectableFile file)
        {
            String revName = file.getName();
            RevisionInfo info = RevisionInfo.fromHexEncodedNullable(revName);
            if (info == null) return null;
            long length = file.lengthOrZeroIfNotFile();
            // Check for file disappearance while we where building the RevInfo (support-143)
            return file.isFile() ? new RevInfo(file.getAbsolutePath(), info, length) : null;
        }
    }
}
