package com.aerofs.daemon.rest.handler;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Role;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.util.HttpStatus;
import com.aerofs.daemon.rest.util.Ranges;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotFile;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.Date;
import java.util.Set;

public class HdFileContent extends AbstractHdIMC<EIFileContent>
{
    private final ACLChecker _acl;
    private final DirectoryService _ds;
    private final IMapSID2SIndex _sid2sidx;
    private final NativeVersionControl _nvc;

    @Inject
    public HdFileContent(DirectoryService ds, ACLChecker acl, IMapSID2SIndex sid2sidx,
            NativeVersionControl nvc)
    {
        _ds = ds;
        _acl = acl;
        _nvc = nvc;
        _sid2sidx = sid2sidx;
    }

    @Override
    protected void handleThrows_(EIFileContent ev, Prio prio) throws Exception
    {
        final OA oa = check_(ev);
        final CA ca = oa.caMasterThrows();
        final EntityTag etag = etag(oa.soid());
        final long length = ca.length();
        final long mtime = ca.mtime();
        final IPhysicalFile pf = ca.physicalFile();

        long skip = 0, span = length;
        ResponseBuilder bd = Response.ok().tag(etag).lastModified(new Date(mtime));

        RangeSet<Long> ranges = parseRanges(ev._rangeset, ev._ifRange, etag, length);
        if (ranges != null) {
            if (ranges.isEmpty()) {
                ev.setResult_(Response.status(HttpStatus.UNSATISFIABLE_RANGE)
                        .header("Content-Length", "*/" + length));
                return;
            }

            Set<Range<Long>> parts = ranges.asRanges();
            if (parts.size() == 1) {
                Range<Long> range = Iterables.getFirst(parts, null);
                skip = range.lowerEndpoint();
                long last = range.upperEndpoint();
                span = last - skip + 1;

                bd.status(HttpStatus.PARTIAL_CONTENT)
                        .header("Content-Range", "bytes " + skip + "-" + last + "/" + length);
            } else {
                ev.setResult_(bd.status(HttpStatus.PARTIAL_CONTENT)
                        .type("multipart/byteranges; boundary=" + MultiPartUploader.BOUNDARY)
                        .entity(new MultiPartUploader(pf, length, mtime, parts)));
                return;
            }
        } else {
            bd.header("Content-Disposition", "attachment; filename=\"" + oa.name() + "\"");
        }

        ev.setResult_(bd.type(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .header("Content-Length", span)
                .entity(new SimpleUploader(pf, length, mtime, skip, span)));
    }

    private OA check_(EIFileContent ev) throws Exception
    {
        SID sid = ev._object.sid;
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExNotFound();

        _acl.checkThrows_(ev._user, sidx, Role.VIEWER);

        SOID soid = new SOID(sidx, ev._object.oid);
        OA oa = _ds.getOAThrows_(soid);

        if (!oa.isFile()) throw new ExNotFile();
        if (oa.isExpelled()) throw new ExExpelled();

        return oa;
    }

    private static @Nullable RangeSet<Long> parseRanges(@Nullable String rangeset,
            @Nullable EntityTag ifRange, EntityTag etag, long length)
    {
        if (rangeset != null && (ifRange == null || etag.equals(ifRange))) {
            try {
                return Ranges.parse(rangeset, length);
            } catch (ExBadArgs e) {
                // RFC 2616: MUST ignore Range header if any range spec is syntactically invalid
            }
        }
        return null;
    }

    /**
     * @return HTTP Entity tag for a given SOID
     *
     * We use version hashes as entity tags for simplicity
     */
    private EntityTag etag(SOID soid) throws SQLException
    {
        return new EntityTag(BaseUtil.hexEncode(_nvc.getVersionHash_(soid)));
    }

    private static class MultiPartUploader implements StreamingOutput
    {
        // TODO: use pseudo-random boundary separator?
        private static final String BOUNDARY = "xxxRANGE-BOUNDARYxxx";

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

    private static class SimpleUploader implements StreamingOutput
    {
        private final IPhysicalFile _pf;
        private final long _length;
        private final long _mtime;
        private final long _start;
        private final long _span;

        SimpleUploader(IPhysicalFile pf, long length, long mtime, long start, long span)
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
}
