package com.aerofs.lib.aws.s3.chunks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.SecretKey;

import org.apache.log4j.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.internal.RepeatableFileInputStream;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.inject.Inject;

import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.Base64;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LengthTrackingOutputStream;
import com.aerofs.lib.Param;
import com.aerofs.lib.SecUtil.CipherFactory;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.common.AWSRetry;
import com.aerofs.lib.aws.s3.S3CredentialsException;
import com.aerofs.lib.aws.s3.S3InitException;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.s3.S3Config.S3BucketIdConfig;
import com.aerofs.s3.S3Config.S3CryptoConfig;

public class S3ChunkAccessor
{
    private static final Logger l = Util.l(S3ChunkAccessor.class);

    public static final int CHUNK_HASH_SIZE = ContentHash.UNIT_LENGTH;
    public static final long FILE_CHUNK_SIZE = Param.FILE_CHUNK_SIZE;

    private static final String CHUNK_SUFFIX = ".chunk.gz.aes";


    public static int getNumChunks(ContentHash hash)
    {
        byte[] bytes = hash.getBytes();
        return bytes.length / CHUNK_HASH_SIZE;
    }

    public static boolean isOneChunk(ContentHash hash)
    {
        return getNumChunks(hash) == 1;
    }

    public static ContentHash getChunk(ContentHash hash, int i)
    {
        return new ContentHash(Arrays.copyOfRange(hash.getBytes(),
                i * CHUNK_HASH_SIZE, (i + 1) * CHUNK_HASH_SIZE));
    }

    public static List<ContentHash> splitChunks(ContentHash hash)
    {
        byte[] bytes = hash.getBytes();
        int numChunks = getNumChunks(hash);
        List<ContentHash> list = Lists.newArrayListWithCapacity(numChunks);
        for (int i = 0; i < numChunks; ++i) {
            list.add(new ContentHash(
                    Arrays.copyOfRange(bytes, i * CHUNK_HASH_SIZE, (i + 1) * CHUNK_HASH_SIZE)));
        }
        return list;
    }

    public static String getChunkName(ContentHash hash)
    {
        assert isOneChunk(hash);
        return hash.toHex() + CHUNK_SUFFIX;
    }

    public static ContentHash parseChunkName(String name)
    {
        if (!name.endsWith(CHUNK_SUFFIX)) throw new IllegalArgumentException(name);
        String hex = name.substring(0, name.length() - CHUNK_SUFFIX.length());
        byte[] bytes;
        try {
            bytes = Util.hexDecode(hex);
        } catch (ExFormatError e) {
            throw new IllegalArgumentException(name, e);
        }
        ContentHash hash = new ContentHash(bytes);
        if (!isOneChunk(hash)) throw new IllegalArgumentException(name);
        return hash;
    }


    private final AmazonS3 _s3Client;
    private final S3BucketIdConfig _s3BucketIdConfig;
    private final S3CryptoConfig _s3CryptoConfig;

    private SecretKey _secretKey;

    @Inject
    public S3ChunkAccessor(
            AmazonS3 s3Client,
            S3BucketIdConfig s3BucketIdConfig,
            S3CryptoConfig s3CryptoConfig)
    {
        super();
        _s3Client = s3Client;
        _s3BucketIdConfig = s3BucketIdConfig;
        _s3CryptoConfig = s3CryptoConfig;
    }

    public void init_() throws S3InitException
    {
        try {
            _secretKey = _s3CryptoConfig.getSecretKey();
        } catch (NoSuchAlgorithmException e) {
            throw new S3CredentialsException(e);
        } catch (InvalidKeySpecException e) {
            throw new S3CredentialsException(e);
        }

        try {
            checkMagicChunk();
        } catch (IOException e) {
            throw new S3InitException(e);
        }
    }


    public void checkMagicChunk() throws IOException, S3InitException
    {
        S3MagicChunk s3MagicChunk = new S3MagicChunk(this);
        s3MagicChunk.init_();
    }

    String getChunkKeyPrefix()
    {
        String prefix = _s3BucketIdConfig.getS3DirPath();
        if (!prefix.isEmpty()) {
            if (prefix.charAt(prefix.length() - 1) != '/') prefix += '/';
        }
        return prefix + "chunks/";
    }

    String getChunkKey(ContentHash hash)
    {
        return getChunkKeyPrefix() + getChunkName(hash);
    }

    OutputStream encodeChunk(OutputStream out) throws IOException
    {
        boolean ok = false;
        try {
            out = new CipherFactory(_secretKey).encryptingHmacedOutputStream(out);
            out = new GZIPOutputStream(out);
            ok = true;
            return out;
        } finally {
            if (!ok) out.close();
        }
    }

    InputStream decodeChunk(InputStream in) throws IOException
    {
        boolean ok = false;
        try {
            in = new CipherFactory(_secretKey).decryptingHmacedInputStream(in);
            in = new GZIPInputStream(in);
            ok = true;
            return in;
        } finally {
            if (!ok) in.close();
        }
    }

    public InputStream readOneChunk(ContentHash hash) throws IOException
    {
        String key = getChunkKey(hash);
        S3Object o;
        try {
            o = _s3Client.getObject(_s3BucketIdConfig.getS3BucketId(), key);
        } catch (AmazonServiceException e) {
            throw new IOException(e);
        } catch (AmazonClientException e) {
            throw new IOException(e);
        }
        InputStream in = o.getObjectContent();
        in = decodeChunk(in);
        return in;
    }

    public InputStream readChunks(ContentHash hash) throws IOException
    {
        return new ChunkedInputStream(this, hash);
    }

    abstract static class AbstractChunkedInputStream extends InputStream
    {
        protected final ContentHash _hash;
        protected final int _numChunks;

        private int _chunkIndex;
        private long _pos;

        private InputStream _in;

        public AbstractChunkedInputStream(ContentHash hash)
        {
            _hash = hash;
            _numChunks = getNumChunks(hash);
        }

        @Override
        public int read() throws IOException
        {
            byte[] b = new byte[1];
            if (read(b) < 0) return -1;
            else return b[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            if (len == 0) return 0;
            if (_chunkIndex >= _numChunks) return -1;
            if (_in == null) resetInputStream();

            int pos = off;
            final int end = off + len;
            while (pos < end && _chunkIndex < _numChunks) {
                int read = _in.read(b, pos, end - pos);
                if (read < 0) {
                    ++_chunkIndex;
                    resetInputStream();
                    if (_chunkIndex >= _numChunks) break;
                } else {
                    pos += read;
                    _pos += read;
                }
            }

            int read = pos - off;
            if (read == 0) return -1;
            return read;
        }

        @Override
        public long skip(long n) throws IOException
        {
            Preconditions.checkArgument(n >= 0);
            final long oldPos = _pos;
            long newPos = _pos + n;
            int newChunkIndex = (int)(newPos / FILE_CHUNK_SIZE);
            if (newChunkIndex != _chunkIndex) {
                _chunkIndex = newChunkIndex;
                _pos = _chunkIndex * FILE_CHUNK_SIZE;
                closeInputStream();
            }
            if (_in == null) resetInputStream();
            if (_pos != newPos) {
                long skipped = _in.skip(newPos - _pos);
                _pos += skipped;
            }
            return _pos - oldPos;
        }

        @Override
        public void close() throws IOException
        {
            _chunkIndex = _numChunks;
            closeInputStream();
        }

        private void closeInputStream() throws IOException
        {
            if (_in != null) {
                _in.close();
                _in = null;
            }
        }

        private void resetInputStream() throws IOException
        {
            closeInputStream();
            if (_chunkIndex < _numChunks) {
                ContentHash chunk = getChunk(_hash, _chunkIndex);
                _in = readChunk(_chunkIndex, chunk);
            }
        }

        protected abstract InputStream readChunk(int index, ContentHash chunk) throws IOException;
    }


    static class ChunkedInputStream extends AbstractChunkedInputStream
    {
        private final S3ChunkAccessor _accessor;

        public ChunkedInputStream(S3ChunkAccessor accessor, ContentHash hash)
        {
            super(hash);
            _accessor = accessor;
        }

        @Override
        protected InputStream readChunk(int index, ContentHash chunk) throws IOException
        {
            return _accessor.readOneChunk(chunk);
        }
    }


    public static class FileUpload
    {
        protected final S3ChunkAccessor _chunkAccessor;
        protected final ExecutorService _executor;
        protected final File _tempDir;
        protected final File _sourceFile;
        protected final InputSupplier<? extends InputStream> _input;
        protected final long _length;

        protected boolean _skipEmpty = true;

        public FileUpload(S3ChunkAccessor chunkAccessor,
                ExecutorService executor, File tempDir,
                File file, InputSupplier<? extends InputStream> input, long length)
        {
            _chunkAccessor = chunkAccessor;
            _executor = executor;
            _tempDir = tempDir;
            _sourceFile = file;
            _input = input;
            _length = length;
        }

        public FileUpload(S3ChunkAccessor chunkAccessor,
                ExecutorService executor, File tempDir,
                File file)
        {
            this(chunkAccessor, executor, tempDir,
                    file, Files.newInputStreamSupplier(file), file.length());
        }

        public void setSkipEmpty(boolean value)
        {
            _skipEmpty = value;
        }

        public ContentHash uploadChunks() throws IOException
        {
            List<ChunkUpload> chunks = splitIntoChunks();
            try {
                prepareChunks(chunks);
            } catch (InterruptedException e) {
                throw new IOException(e);
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
            return getHash(chunks);
        }

        private List<ChunkUpload> splitIntoChunks()
        {
            if (_length == 0 && _skipEmpty) {
                HashStream hs = HashStream.newFileHasher();
                hs.close();
                return Collections.emptyList();
            }
            int numChunks = (_length == 0) ? 1 :
                (int)((_length + FILE_CHUNK_SIZE - 1) / FILE_CHUNK_SIZE);
            List<ChunkUpload> chunks = Lists.newArrayListWithCapacity(numChunks);
            for (int i = 0; i < numChunks; ++i) {
                chunks.add(new ChunkUpload(i));
            }
            return chunks;
        }

        private void prepareChunks(List<ChunkUpload> chunks) throws InterruptedException, ExecutionException
        {
            List<Callable<ChunkUpload>> callables = Lists.newArrayListWithCapacity(chunks.size());
            for (final ChunkUpload chunk : chunks) {
                callables.add(new Callable<ChunkUpload>() {
                    @Override
                    public ChunkUpload call() throws IOException {
                        handleChunk(chunk);
                        return chunk;
                    }
                });
            }
            List<Future<ChunkUpload>> futures = invokeAll(callables);
            for (Future<?> f : futures) f.get();
        }

        private ContentHash getHash(List<ChunkUpload> chunks)
        {
            int numChunks = chunks.size();
            byte[] hashBytes = new byte[numChunks * CHUNK_HASH_SIZE];
            for (int i = 0; i < numChunks; ++i) {
                ChunkUpload chunk = chunks.get(i);
                System.arraycopy(chunk._hash.getBytes(), 0,
                        hashBytes, i * CHUNK_HASH_SIZE, CHUNK_HASH_SIZE);
            }
            return new ContentHash(hashBytes);
        }

        protected List<Future<ChunkUpload>> invokeAll(
                List<Callable<ChunkUpload>> callables) throws InterruptedException
        {
//            ListeningExecutorService executor = MoreExecutors.sameThreadExecutor();
            return _executor.invokeAll(callables);

            // return _chunkAccessor._executor.invokeAll(callables);
//            List<Future<ChunkUpload>> futures = Lists.newArrayListWithCapacity(callables.size());
//            for (Callable<ChunkUpload> callable : callables) {
//                FutureTask<ChunkUpload> ft = new FutureTask<ChunkUpload>(callable);
//                futures.add(ft);
//                ft.run();
//            }
//            return futures;
        }

        private void handleChunk(ChunkUpload chunk) throws IOException
        {
            File tempFile = getTempFile(chunk);
            try {
                chunk._file = tempFile;
                prepareChunk(chunk, chunk._file);
                uploadChunk(chunk, chunk._file);
            } finally {
                FileUtil.tryDeleteNow(tempFile);
                FileUtil.noDeleteOnExit(tempFile);
            }
        }

        protected File getTempFile(ChunkUpload chunk) throws IOException
        {
            String name = (_sourceFile == null) ? "chunk" : _sourceFile.getName();
            File temp = FileUtil.createTempFile(name + '-' + chunk._index + '-', CHUNK_SUFFIX,
                    _tempDir, true);
            return temp;
        }

        protected void prepareChunk(ChunkUpload chunk, File file) throws IOException
        {
            l.debug("writing to " + file);

            InputSupplier<? extends InputStream> input = ByteStreams.slice(_input,
                    chunk._index * FILE_CHUNK_SIZE, FILE_CHUNK_SIZE);

            InputStream in = input.getInput();
            try {
                OutputStream out = openOutputStream(chunk, new FileOutputStream(file));
                try {
                    byte[] buffer = new byte[Param.FILE_BUF_SIZE];
                    for (int n; (n = in.read(buffer)) > 0;) {
                        out.write(buffer, 0, n);
                    }
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        }

        protected void uploadChunk(final ChunkUpload chunk, final File file) throws IOException
        {
            AWSRetry.retry(new Callable<Void>() {
                @Override
                public Void call() throws IOException
                {
                    String bucketName = _chunkAccessor._s3BucketIdConfig.getS3BucketId();
                    String key = _chunkAccessor.getChunkKey(chunk._hash);

                    boolean verbose = false;
                    if (verbose) {
                        // this returns a more verbose error message
                        try {
                            GetObjectRequest request = new GetObjectRequest(bucketName, key);
                            request.setRange(0, 0);
                            S3Object object = _chunkAccessor._s3Client.getObject(request);
                            l.debug(object);
                            object.getObjectContent().close();
                        } catch (AmazonServiceException e) {
                            l.debug(e);
//                            l.warn(e, e);
                        }
                    }

                    try {
                        ObjectMetadata metadata =
                                _chunkAccessor._s3Client.getObjectMetadata(bucketName, key);
                        l.debug(metadata);
                        if (true) return null;
                    } catch (AmazonServiceException e) {
                        l.debug(e);
//                        l.warn(e, e);
//                        throw e;
                    }

                    assert file.length() == chunk._encodedLength;

                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setContentType("application/octet-stream");
                    metadata.setContentLength(chunk._encodedLength);
                    metadata.setContentMD5(Base64.encodeBytes(chunk._md5));
                    metadata.addUserMetadata("Chunk-Length", Long.toString(chunk._decodedLength));
                    metadata.addUserMetadata("Chunk-Hash", chunk._hash.toHex());
                    InputStream in = new RepeatableFileInputStream(file);
                    try {
                        _chunkAccessor._s3Client.putObject(bucketName, key, in, metadata);
                    } finally {
                        in.close();
                    }
                    return null;
                }
            });
        }

        private OutputStream openOutputStream(final ChunkUpload chunk, OutputStream out) throws IOException
        {
            boolean ok = false;
            try {
                final HashStream hs = HashStream.newFileHasher();
                final MessageDigest md;
                try {
                    // compute the MD5 hash of the compressed, encrypted data
                    // for Amazon S3
                    md = MessageDigest.getInstance("MD5");
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException(e);
                }

                out = new LengthTrackingOutputStream(out) {
                    @Override
                    public void close() throws IOException
                    {
                        super.close();
                        chunk._encodedLength = getLength();
                    }
                };
                out = new DigestOutputStream(out, md);
                out = new BufferedOutputStream(out);
                out = _chunkAccessor.encodeChunk(out);
                out = hs.wrap(out);
                out = new LengthTrackingOutputStream(out) {
                    @Override
                    public void close() throws IOException
                    {
                        super.close();
                        ContentHash hash = hs.getHashAttrib();
                        if (!isOneChunk(hash)) {
                            throw new IOException("Too much data for one chunk!");
                        }
                        chunk._hash = hash;
                        chunk._md5 = md.digest();
                        chunk._decodedLength = getLength();
                    }
                };
                ok = true;
                return out;
            } finally {
                if (!ok) out.close();
            }
        }

        public static class ChunkUpload
        {
            final int _index;
            ContentHash _hash;
            long _encodedLength;
            long _decodedLength;
            byte[] _md5;
            File _file;

            ChunkUpload(int index)
            {
                _index = index;
            }

            public int getIndex()
            {
                return _index;
            }

            public ContentHash getHash()
            {
                return _hash;
            }

            public long getEncodedLength()
            {
                return _encodedLength;
            }

            public long getDecodedLength()
            {
                return _decodedLength;
            }
        }
    }

}
