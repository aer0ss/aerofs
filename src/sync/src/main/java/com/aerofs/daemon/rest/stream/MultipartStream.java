package com.aerofs.daemon.rest.stream;

import com.aerofs.base.C;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.lib.fs.FileChunker;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.restless.stream.ContentStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.common.net.HttpHeaders;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.Set;

/**
 * Helper class to stream multipart/byteranges response body
 */
public class MultipartStream implements ContentStream
{
    private final IPhysicalFile _pf;
    private final long _length;
    private final long _mtime;
    public final String _boundary;

    // null after final boundary is written
    private @Nullable Iterator<Range<Long>> _parts;

    // null before first chunk, to simplify boundary writing logic
    private @Nullable FileChunker _chunker;

    public MultipartStream(IPhysicalFile pf, long length, long mtime, Set<Range<Long>> parts)
    {
        _pf = pf;
        _length = length;
        _mtime = mtime;
        // random boundary string to reduce likelihood of boundary appearing within a part
        _boundary = UniqueID.generate().toStringFormal();

        Preconditions.checkState(!parts.isEmpty());
        _parts = parts.iterator();
    }

    @Override
    public boolean hasMoreChunk() throws IOException
    {
        return _parts != null;
    }

    private static final String BOUNDARY_DELIM = "--";
    private static final String CRLF = "\r\n";

    @Override
    public void writeChunk(OutputStream o) throws IOException
    {
        byte[] chunk;
        try {
            chunk = _chunker != null ? _chunker.getNextChunk_() : null;
        } catch (ExUpdateInProgress e) {
            _pf.onUnexpectedModification_(_mtime);
            _chunker.close_();
            throw new IOException(e);
        }

        if (chunk == null) {
            if (_chunker != null) _chunker.close_();
            writeBoundary(new OutputStreamWriter(o));
        } else {
            o.write(chunk);
        }
    }

    private void writeBoundary(OutputStreamWriter w) throws IOException
    {
        Preconditions.checkNotNull(_parts);

        w.write(CRLF);
        w.write(BOUNDARY_DELIM);
        w.write(_boundary);

        if (_parts.hasNext()) {
            // chunk boundary, w/ per-chunk headers
            Range<Long> part = _parts.next();

            w.write(CRLF);
            w.write(HttpHeaders.CONTENT_TYPE);
            w.write(": ");
            w.write(MediaType.APPLICATION_OCTET_STREAM);
            w.write(CRLF);
            w.write(HttpHeaders.CONTENT_RANGE);
            w.write(": bytes "
                    + part.lowerEndpoint() + "-"
                    + (part.upperEndpoint() - 1) + "/"
                    + _length);
            w.write(CRLF);
            w.write(CRLF);

            _chunker = new FileChunker(_pf, _mtime, _length,
                    part.lowerEndpoint(), part.upperEndpoint(),
                    16 * C.KB, OSUtil.isWindows());
        } else {
            // final boundary
            w.write(BOUNDARY_DELIM);
            w.write(CRLF);

            _parts = null;
        }
        w.flush();
    }

    @Override
    public void close() throws IOException
    {
        if (_chunker != null) _chunker.close_();
    }
}
