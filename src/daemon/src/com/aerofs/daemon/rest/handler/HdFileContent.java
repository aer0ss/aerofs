package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.Error;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HdFileContent extends AbstractHdIMC<EIFileContent>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdFileContent(DirectoryService ds, ACLChecker acl, IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _acl = acl;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void handleThrows_(EIFileContent ev, Prio prio) throws Exception
    {
        SID sid = ev._object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        _acl.checkThrows_(ev._user, sidx, Role.VIEWER);

        SOID soid = new SOID(sidx, ev._object.oid);
        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isFile()) throw new ExNotFile();
        if (oa.isExpelled()) throw new ExExpelled();

        CA ca = oa.caMasterThrows();
        final long length = ca.length();
        final long mtime = ca.mtime();
        final IPhysicalFile pf = ca.physicalFile();

        ResponseBuilder bd;
        long skip = 0, span = length;

        // parse Range header, if any
        RangeSet<Long> ranges = null;
        if (ev._rangeset != null) {
            // TODO: check etag, ignore Range header on mismatch

            try {
                ranges = ranges(ev._rangeset, length);
            } catch (ExBadArgs e) {
                // RFC 2616: MUST ignore Range header if any range spec is syntactically invalid
            }
        }

        if (ranges != null) {
            // partial content response
            if (ranges.isEmpty()) {
                ev.setResult_(Response.status(416).header("Content-Length", "*/" + length));
                return;
            }

            Set<Range<Long>> parts = ranges.asRanges();

            if (parts.size() == 1) {
                Range<Long> range = Iterables.getFirst(parts, null);
                skip = range.lowerEndpoint();
                long last = range.upperEndpoint();
                span = last - skip + 1;

                bd = Response.status(206)
                        .header("Content-Range", "bytes " + skip + "-" + last + "/" + length);
            } else {
                // multipart/byteranges response
                ev.setResult_(Response.status(206)
                        .type("multipart/byteranges; boundary=" + BOUNDARY)
                        .entity(new MultiPartUploader(pf, length, mtime, parts)));
                return;
            }
        } else {
            bd = Response.status(Status.OK)
                    .header("Content-Disposition", "attachment; filename=\"" + oa.name() + "\"");
        }

        ev.setResult_(bd
                .type(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .header("Content-Length", span)
                .lastModified(new Date(mtime))
                .entity(new FileUploader(pf, length, mtime, skip, span)));
    }

    private static final String BOUNDARY = "xxxRANGE-BOUNDARYxxx";

    private static class MultiPartUploader implements StreamingOutput
    {
        private final IPhysicalFile _pf;
        private final long _length;
        private final long _mtime;
        private final Set<Range<Long>> _parts;

        MultiPartUploader(IPhysicalFile pf, long length, long mtime, Set<Range<Long>> parts)
        {
            _pf = pf;
            _length = length;
            _mtime = mtime;
            _parts = parts;
        }

        @Override
        public void write(OutputStream out) throws IOException, WebApplicationException
        {
            OutputStreamWriter w = new OutputStreamWriter(out);

            for (Range<Long> part : _parts) {
                long skip = part.lowerEndpoint();
                long last = part.upperEndpoint();
                long span = last - skip + 1;

                w.write("\r\n--" + BOUNDARY + "\r\n");
                w.write("Content-Type: application/octet-stream\r\n");
                w.write("Content-Range: bytes " + skip + "-" + last + "/" + _length + " \r\n\r\n");
                w.flush();

                InputStream in = _pf.newInputStream_();
                in.skip(skip);

                // TODO: manual copy to check for changes before actually writing data on the wire
                // TODO: base64 encoding to ensure the boundary delim cannot occur in the body?
                ByteStreams.copy(ByteStreams.limit(in, span), out);

                if (_pf.wasModifiedSince(_mtime, _length)) {
                    // TODO: figure out the impact on the client...
                    throw new WebApplicationException(Status.CONFLICT);
                }
            }

            w.write("\r\n--" + BOUNDARY + "--\r\n");
            w.flush();
        }
    }

    private static class FileUploader implements StreamingOutput
    {
        private final IPhysicalFile _pf;
        private final long _length;
        private final long _mtime;
        private final long _start;
        private final long _span;

        FileUploader(IPhysicalFile pf, long length, long mtime, long start, long span)
        {
            _pf = pf;
            _length = length;
            _mtime = mtime;
            _start = start;
            _span = span;
        }

        @Override
        public void write(OutputStream outputStream) throws IOException, WebApplicationException
        {
            // TODO: manual copy to check for changes before actually writing data on the wire
            // ideally code should be shared w/ GCCSendContent
            InputStream in = _pf.newInputStream_();
            in.skip(_start);
            ByteStreams.copy(ByteStreams.limit(in, _span), outputStream);
            if (_pf.wasModifiedSince(_mtime, _length)) {
                // TODO: figure out the impact on the client...
                throw new WebApplicationException(Status.CONFLICT);
            }
        }
    }

    // Range header parsing, as per RFC 2616

    private static final String BYTES_UNIT = "bytes=";
    private static final String RANGESET_SEP = ",";
    private static Pattern specPattern = Pattern.compile("([0-9]*)-([0-9]*)");

    private static RangeSet<Long> ranges(String rangeSet, long length) throws ExBadArgs
    {
        if (!rangeSet.startsWith(BYTES_UNIT)) throw new ExBadArgs("Unsupported range unit");
        RangeSet<Long> ranges = TreeRangeSet.create();
        String[] rangeSpecs = rangeSet.substring(BYTES_UNIT.length()).split(RANGESET_SEP);
        for (String spec : rangeSpecs) {
            ranges.add(range(spec, length));
        }
        return ranges;
    }

    private static Range<Long> range(String rangeSpec, long length) throws ExBadArgs
    {
        Matcher m = specPattern.matcher(rangeSpec);
        if (!m.matches()) throw new ExBadArgs("Invalid range spec");
        String start = m.group(1);
        String end = m.group(2);
        long low, high;

        if (start.isEmpty()) {
            if (end.isEmpty()) throw new ExBadArgs("Invalid range spec");
            // suffix range
            low = length - Long.parseLong(end);
            high = length - 1;
        } else {
            low = Long.parseLong(start);
            // empty range to avoid polluting the range set
            if (low >= length) return Range.closedOpen(0L, 0L);

            high = end.isEmpty() ? length - 1 : bound(end, length);
        }

        if (low > high) throw new ExBadArgs("Invalid range spec");

        return Range.closed(low, high);
    }

    private static long bound(String num, long length)
    {
        return Math.max(0, Math.min(length - 1, Long.parseLong(num)));
    }
}
