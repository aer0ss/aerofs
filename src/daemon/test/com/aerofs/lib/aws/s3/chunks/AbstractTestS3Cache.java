package com.aerofs.lib.aws.s3.chunks;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.mockito.Mockito;

import com.amazonaws.services.s3.AmazonS3;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.MoreExecutors;

import com.aerofs.daemon.core.tc.TCTestSetup;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.aws.s3.S3TestConfig;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor.FileUpload;
import com.aerofs.lib.aws.s3.db.S3CacheDatabase;
import com.aerofs.lib.aws.s3.db.S3Database;
import com.aerofs.s3.S3Schema;
import com.aerofs.s3.S3Config.S3DirConfig;
import com.aerofs.s3.ShutdownHooks;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.UnitTestTempDir;

public abstract class AbstractTestS3Cache extends AbstractTest
{
//    static final Logger l = Util.l(AbstractTestS3Cache.class);

    private static final String ASCII = "US-ASCII";

    boolean _verbose;

    @Rule
    public UnitTestTempDir _testTempDirFactory = new UnitTestTempDir();

    S3TestConfig _s3TestConfig = new S3TestConfig();

//    PhysicalFile.Factory _fileFactory = new PhysicalFile.Factory();

    TCTestSetup _tcTestSetup = new TCTestSetup();
    ShutdownHooks _shutdownHooks = new ShutdownHooks();

    File _testDir;
    File _tempDir;
    File _s3Dir;

    S3DirConfig _s3DirConfig;
    S3Database _s3db;
    S3CacheDatabase _s3CacheDB;
    AmazonS3 _s3Client;
    S3ChunkAccessor _s3ChunkAccessor;
    S3Cache _s3Cache;

    ExecutorService _executorService = MoreExecutors.sameThreadExecutor();

    @BeforeClass
    public static void beforeClass() throws Exception
    {
        Logger.getLogger("httpclient.wire.content").setLevel(Level.INFO);
    }

    @Before
    public void setup() throws Exception
    {
        _testDir = _testTempDirFactory.getTestTempDir();

        _tempDir = new File(_testDir, "tmp");
        _tempDir.mkdir();

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
        prepareS3Cache(_s3Cache);

        _tcTestSetup.start_();
        new S3Schema(_tcTestSetup._coreDBCW)
                .create_(_tcTestSetup._coreDBCW.get().getConnection().createStatement());
        _s3db.init_();
        _s3Cache.init_();
    }

    @After
    public void tearDown() throws Exception
    {
        if (_verbose) {
            try {
                System.out.println();
                new S3Schema(_tcTestSetup._coreDBCW).dump_(System.out);
            } finally {
                System.out.flush();
            }
        }

        _executorService.shutdown();
        _shutdownHooks.shutdown_();
        _tcTestSetup.shutdown_();
    }

    protected void prepareS3Cache(S3Cache s3Cache)
    {
    }

    void writeFile(File file, String str) throws IOException
    {
        Writer w = new OutputStreamWriter(new FileOutputStream(file), ASCII);
        try {
            w.append(str);
        } finally {
            w.close();
        }
    }

    void writeLargeFile(File file, long length, byte[] contents) throws IOException
    {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            long written = 0;
            while (written + contents.length <= length) {
                out.write(contents);
                written += contents.length;
            }
            if (length - written > 0) {
                out.write(contents, 0, (int)(length - written));
                written += length - written;
            }
        } finally {
            out.close();
        }
    }

    ContentHash uploadFile(File file) throws IOException
    {
        return newFileUpload(file).uploadChunks();
    }

    private FileUpload newFileUpload(File file)
    {
        return new FileUpload(_s3ChunkAccessor, _executorService, _tempDir, file);
    }

    static InputSupplier<InputStream> newRepeatedInput(final byte[] data)
    {
        InputSupplier<InputStream> input = new InputSupplier<InputStream>() {
            @Override
            public InputStream getInput() throws IOException
            {
                return new MultiInputStream(new Iterator<InputStream>() {
                    private ByteArrayInputStream _in = new ByteArrayInputStream(data);
                    @Override
                    public boolean hasNext()
                    {
                        return true;
                    }
                    @Override
                    public InputStream next()
                    {
                        _in.reset();
                        return _in;
                    }
                    @Override
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                });
            }
        };
        return input;
    }
}

class MultiInputStream extends InputStream
{
    private final Iterator<? extends InputStream> _it;
    private InputStream _in;

    public MultiInputStream(Iterator<? extends InputStream> it)
    {
        _it = it;
    }

    public int read() throws IOException
    {
        while (true) {
            if (_in == null) {
                if (!_it.hasNext()) break;
                _in = _it.next();
            }
            int c = _in.read();
            if (c >= 0) return c;
            _in = null;
        }
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (len == 0) return 0;
        final int start = off;
        final int end = start + len;
        while (off < end) {
            if (_in == null) {
                if (!_it.hasNext()) break;
                _in = _it.next();
            }
            int n = _in.read(b, off, end - off);
            if (n < 0) {
                _in = null;
            } else {
                off += n;
            }
        }

        if (off - start == 0) return -1;
        return off - start;
    }
}
