package com.aerofs.daemon.rest.stream;

import com.aerofs.base.C;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.lib.fs.FileChunker;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.restless.stream.ContentStream;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Helper class to stream non-multipart response body
 */
public class SimpleStream implements ContentStream
{
    // null after last chunk is written
    private final IPhysicalFile _pf;
    private final long _mtime;

    private @Nullable FileChunker _chunker;

    public SimpleStream(IPhysicalFile pf, long length, long mtime, long start, long span)
    {
        _pf = pf;
        _mtime = mtime;
        _chunker = new FileChunker(pf, _mtime, length, start, start + span,
                16 * C.KB, OSUtil.isWindows());
    }

    @Override
    public boolean hasMoreChunk() throws IOException
    {
        return _chunker != null;
    }

    @Override
    public void writeChunk(OutputStream o) throws IOException
    {
        Preconditions.checkNotNull(_chunker);

        byte[] chunk;
        try {
            chunk = _chunker.getNextChunk_();
        } catch (ExUpdateInProgress e) {
            _pf.onUnexpectedModification_(_mtime);
            _chunker.close_();
            throw new IOException(e);
        }

        if (chunk != null) {
            o.write(chunk);
        } else {
            _chunker.close_();
            _chunker = null;
        }
    }

    @Override
    public void close() throws IOException
    {
        if (_chunker != null) _chunker.close_();
    }
}
