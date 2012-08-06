package com.aerofs.daemon.core.phy.s3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.SQLException;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import com.amazonaws.services.s3.AmazonS3;
import com.google.common.io.CharStreams;

import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.TCTestSetup;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.HashStream;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.aws.s3.S3TestConfig;
import com.aerofs.lib.aws.s3.chunks.S3Cache;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor;
import com.aerofs.lib.aws.s3.db.S3CacheDatabase;
import com.aerofs.lib.aws.s3.db.S3Database;
import com.aerofs.lib.db.S3Schema;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.s3.ShutdownHooks;
import com.aerofs.s3.S3Config.S3DirConfig;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.UnitTestTempDir;

public class TestS3Storage extends AbstractTest
{
    boolean _verbose = false;

    private final Random _random = new Random(getRandomSeed());

    @Rule
    public final UnitTestTempDir _testTempDirFactory = new UnitTestTempDir();
    private File _testDir;

    private final S3TestConfig _s3TestConfig = new S3TestConfig();
    private final ShutdownHooks _shutdownHooks = new ShutdownHooks();
    private final InjectableFile.Factory _fileFactory = new InjectableFile.Factory();

    private final SIndex _sidx = new SIndex(2);
//    private SID _sid = new SID(randomId_());

    private TCTestSetup _tcTestSetup = new TCTestSetup();

    private TransManager _tm = _tcTestSetup._transManager;
    private TokenManager _tokenManager = _tcTestSetup._tokenManager;
    private TC _tc = _tcTestSetup._tc;

    private AmazonS3 _s3Client;
    private S3Database _s3db;
    private S3CacheDatabase _s3CacheDB;
    private File _s3Dir;
    private S3DirConfig _s3DirConfig;
    private S3ChunkAccessor _s3ChunkAccessor;
    private S3Cache _s3Cache;
    private S3Storage _s3Storage;

    @Before
    public void setup() throws Exception
    {
        _testDir = _testTempDirFactory.getTestTempDir();

        _s3Dir = new File(_testDir, "s3");
        if (_s3Dir.exists()) FileUtil.deleteRecursively(_s3Dir);

        _s3DirConfig = Mockito.mock(S3DirConfig.class);
        Mockito.when(_s3DirConfig.getS3Dir()).thenReturn(_s3Dir);

        _s3db = new S3Database(_tcTestSetup._coreDBCW);
        _s3CacheDB = new S3CacheDatabase(_tcTestSetup._coreDBCW);

        _s3Client = _s3TestConfig.getS3Client(_testTempDirFactory);

        _s3ChunkAccessor = new S3ChunkAccessor(
                _s3Client,
                _s3TestConfig.getBucketIdConfig(),
                _s3TestConfig.getS3CryptoConfig());

        _s3Cache = new S3Cache(
                _tcTestSetup._transManager,
                _tcTestSetup._q,
                _tcTestSetup._sched,
                _s3db,
                _s3CacheDB,
                _s3ChunkAccessor,
                _s3DirConfig);

        _s3Storage = new S3Storage(
                _tcTestSetup._transManager,
                _tcTestSetup._sched,
                _fileFactory,
                _s3db,
                _s3DirConfig,
                _s3ChunkAccessor,
                _s3Cache);

        _tcTestSetup.start_();
        new S3Schema(_tcTestSetup._coreDBCW.get()).create_();
        _s3Storage.init_();
    }

    @After
    public void tearDown() throws Exception {
        if (_verbose) {
            S3Schema s3Schema = new S3Schema(_tcTestSetup._coreDBCW.get());
            PrintWriter pw = new PrintWriter(System.out, false);
            try {
                pw.println();
                s3Schema.dump_(pw);
            } finally {
                pw.flush();
            }
        }
        _shutdownHooks.shutdown_();
        _tcTestSetup.shutdown_();
    }

    @Test
    public void shouldSupportReadingWithoutLock() throws Exception
    {
        final SOCKID sockid = new SOCKID(_sidx, new OID(randomId_()), CID.CONTENT, KIndex.MASTER);
        final Path filePath = new Path("file");
        final String testString = "test";

        Assert.assertTrue(!_tc.getLock().isHeldByCurrentThread());

        runInCoreThread(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                Assert.assertTrue(_tc.getLock().isHeldByCurrentThread());

                writeTestFile(sockid, filePath, testString);

                Token tk = _tokenManager.acquireThrows_(Cat.CLIENT, "testing");
                try {
                    IPhysicalFile file = _s3Storage.newFile_(sockid.sokid(), filePath);
                    InputStream in = file.newInputStream_();
                    try {
                        TCB tcb = tk.pseudoPause_("reading in bed");
                        try {
                            Assert.assertFalse(_tc.getLock().isHeldByCurrentThread());
                            String content = readString(in);
                            Assert.assertEquals(testString, content);
                        } finally {
                            tcb.pseudoResumed_();
                        }
                    } finally {
                        in.close();
                    }
                } finally {
                    tk.reclaim_();
                }

                return null;
            }
        }).get();
    }

    @Test
    public void shouldSupportReadingInTransaction() throws Exception
    {
        // IPhysicalFile.newInputStream_() is called within a transaction
        // in Hasher.computeHashBlocking_()
        // in AliasingMover.moveContentOnAliasing_()

        final SOCKID sockid = new SOCKID(_sidx, new OID(randomId_()), CID.CONTENT, KIndex.MASTER);
        final Path filePath = new Path("file");
        final String testString = "test";

        Assert.assertTrue(!_tc.getLock().isHeldByCurrentThread());

        runInCoreThread(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                Assert.assertTrue(_tc.getLock().isHeldByCurrentThread());

                writeTestFile(sockid, filePath, testString);

                IPhysicalFile file = _s3Storage.newFile_(sockid.sokid(), filePath);
                Trans t = _tm.begin_();
                try {
                    InputStream in = file.newInputStream_();
                    try {
                        String content = readString(in);
                        Assert.assertEquals(testString, content);
                    } finally {
                        in.close();
                    }
                    t.commit_();
                } finally {
                    t.end_();
                }

                return null;
            }
        }).get();
    }

    @Test
    public void shouldAdjustChunkCounts() throws Exception
    {
        Assert.assertTrue(!_tc.getLock().isHeldByCurrentThread());

        runInCoreThread(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                Assert.assertTrue(_tc.getLock().isHeldByCurrentThread());

                String testString = "test";
                SOCKID sockid = new SOCKID(_sidx, new OID(randomId_()), CID.CONTENT, KIndex.MASTER);
                ContentHash chunk = writeTestFile(sockid, new Path("file"), testString);
                Assert.assertEquals(1, _s3db.getChunkCount_(chunk));

                SOCKID sockid2 = new SOCKID(_sidx, new OID(randomId_()), CID.CONTENT, KIndex.MASTER);
                ContentHash chunk2 = writeTestFile(sockid2, new Path("file2"), testString);
                Assert.assertEquals(chunk, chunk2);
                Assert.assertEquals(2, _s3db.getChunkCount_(chunk));

                return null;
            }
        }).get();
    }

    @Test
    public void shouldRejectOverwritingDirectory() throws Exception
    {
        runInCoreThread(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                SOID folderId1 = new SOID(_sidx, new OID(randomId_()));
                Path folderPath1 = new Path("dir1");

                {
                    Trans t = _tm.begin_();
                    try {
                        IPhysicalFolder folder = _s3Storage.newFolder_(folderId1, folderPath1);
                        folder.create_(PhysicalOp.APPLY, t);
                        t.commit_();
                    } finally {
                        t.end_();
                    }
                }

                SOID folderId2 = new SOID(_sidx, new OID(randomId_()));
                Path folderPath2 = new Path("dir2");

                {
                    Trans t = _tm.begin_();
                    try {
                        IPhysicalFolder folder = _s3Storage.newFolder_(folderId2, folderPath2);
                        folder.create_(PhysicalOp.APPLY, t);
                        t.commit_();
                    } finally {
                        t.end_();
                    }
                }

                try {
                    Trans t = _tm.begin_();
                    try {
                        IPhysicalFolder folder1 = _s3Storage.newFolder_(folderId1, folderPath1);
                        IPhysicalFolder folder2 = _s3Storage.newFolder_(folderId2, folderPath2);
                        folder1.move_(folder2, PhysicalOp.APPLY, t);
                        t.commit_();
                    } finally {
                        t.end_();
                    }
                    Assert.fail("should not overwrite existing directory");
                } catch (IOException ignored) {
                }

                {
                    Trans t = _tm.begin_();
                    try {
                        IPhysicalFolder folder = _s3Storage.newFolder_(folderId2, folderPath2);
                        folder.delete_(PhysicalOp.APPLY, t);
                        t.commit_();
                    } finally {
                        t.end_();
                    }
                }

                {
                    Trans t = _tm.begin_();
                    try {
                        IPhysicalFolder folder1 = _s3Storage.newFolder_(folderId1, folderPath1);
                        IPhysicalFolder folder2 = _s3Storage.newFolder_(folderId2, folderPath2);
                        folder1.move_(folder2, PhysicalOp.APPLY, t);
                        t.commit_();
                    } finally {
                        t.end_();
                    }
                }

                return null;
            }
        }).get();
    }

    @Ignore
    @Test
    public void shouldRejectDuplicateDirectory() throws Exception
    {
        runInCoreThread(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                SOID folderId = new SOID(_sidx, new OID(randomId_()));
                Path folderPath = new Path("dir");

                Trans t = _tm.begin_();
                try {
                    IPhysicalFolder folder = _s3Storage.newFolder_(folderId, folderPath);
                    folder.create_(PhysicalOp.APPLY, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                t = _tm.begin_();
                try {
                    IPhysicalFolder folder = _s3Storage.newFolder_(folderId, folderPath);
                    folder.create_(PhysicalOp.APPLY, t);
                    Assert.fail("should not create folder if it already exists");
                } catch (IOException ignored) {
                } finally {
                    t.end_();
                }
                return null;
            }
        }).get();
    }

    @Test
    public void shouldTrackPathChanges() throws Exception
    {
        runInCoreThread(new Callable<Void>() {
            @Override
            public Void call() throws Exception
            {
                SOID folderId = new SOID(_sidx, new OID(randomId_()));
                Path folderPath = new Path("dir");

                Trans t = _tm.begin_();
                try {
                    IPhysicalFolder folder = _s3Storage.newFolder_(folderId, folderPath);
                    folder.create_(PhysicalOp.APPLY, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                SOCKID sockid = new SOCKID(_sidx, new OID(randomId_()), CID.CONTENT, KIndex.MASTER);
                IPhysicalPrefix prefix = _s3Storage.newPrefix_(sockid);
                {
                    OutputStream out = prefix.newOutputStream_(true);
                    try {
                        out.write("test".getBytes());
                    } finally {
                        out.close();
                    }
                }


                Path filePath = folderPath.append("file");

                Token tk = _tokenManager.acquire_(Cat.CLIENT, "prepare prefix");
                try {
                    prefix.prepare_(tk);
                } finally {
                    tk.reclaim_();
                }

                t = _tm.begin_();
                try {
                    IPhysicalFile file = _s3Storage.newFile_(sockid.sokid(), filePath);
                    long mtime = System.currentTimeMillis();
                    _s3Storage.apply_(prefix, file, false, mtime, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                t = _tm.begin_();
                try {
                    IPhysicalFile file = _s3Storage.newFile_(sockid.sokid(), filePath);
                    IPhysicalFile file2 = _s3Storage.newFile_(sockid.sokid(), filePath = folderPath.append("file2"));
                    file.move_(file2, PhysicalOp.APPLY, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                t = _tm.begin_();
                try {
                    IPhysicalFolder folder = _s3Storage.newFolder_(folderId, folderPath);
                    IPhysicalFolder folder2 = _s3Storage.newFolder_(folderId, folderPath = new Path("dir2"));
                    folder.move_(folder2, PhysicalOp.APPLY, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                // this should fail because the source path is wrong
                t = _tm.begin_();
                try {
                    IPhysicalFile file2 = _s3Storage.newFile_(sockid.sokid(), filePath);
                    IPhysicalFile file3 = _s3Storage.newFile_(sockid.sokid(), folderPath.append("file3"));
                    file2.move_(file3, PhysicalOp.APPLY, t);
                    Assert.fail("should not move file with wrong source path");
                } catch (IOException ignored) {
                } finally {
                    t.end_();
                }

                t = _tm.begin_();
                try {
                    filePath = folderPath.append(filePath.last());
                    IPhysicalFile file2 = _s3Storage.newFile_(sockid.sokid(), filePath);
                    IPhysicalFile file3 = _s3Storage.newFile_(sockid.sokid(), filePath = folderPath.append("file3"));
                    file2.move_(file3, PhysicalOp.APPLY, t);
                    t.commit_();
                } finally {
                    t.end_();
                }

                return null;
            }
        }).get();
    }

    private ContentHash writeTestFile(SOCKID sockid, Path filePath, String content)
            throws IOException, SQLException
    {
        Assert.assertTrue(_tc.getLock().isHeldByCurrentThread());

        IPhysicalPrefix prefix = _s3Storage.newPrefix_(sockid);
        HashStream hs = HashStream.newFileHasher();
        OutputStream out = prefix.newOutputStream_(true);
        try {
            out = hs.wrap(out);
            out.write(content.getBytes());
        } finally {
            out.close();
        }

        Token tk = _tokenManager.acquire_(Cat.CLIENT, "prepare prefix");
        try {
            prefix.prepare_(tk);
        } finally {
            tk.reclaim_();
        }

        Trans t = _tm.begin_();
        try {
            IPhysicalFile file = _s3Storage.newFile_(sockid.sokid(), filePath);
            long mtime = System.currentTimeMillis();
            _s3Storage.apply_(prefix, file, false, mtime, t);
            t.commit_();
        } finally {
            t.end_();
        }

        return hs.getHashAttrib();
    }

    private String readString(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        Reader r = new InputStreamReader(in, "US-ASCII");
        try {
            CharStreams.copy(r, sb);
        } finally {
            r.close();
        }
        return sb.toString();
    }

    <T> Future<T> runInCoreThread(Callable<T> callable)
    {
        return _tcTestSetup.runInCoreThread(callable);
    }

    UniqueID randomId_()
    {
        byte[] bytes = new byte[UniqueID.LENGTH];
        _random.nextBytes(bytes);
        return new UniqueID(bytes);
    }
}
