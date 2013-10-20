package com.aerofs.daemon.rest.handler;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.stream.MultipartStream;
import com.aerofs.daemon.rest.stream.SimpleStream;
import com.aerofs.daemon.rest.util.AccessChecker;
import com.aerofs.daemon.rest.util.HttpStatus;
import com.aerofs.daemon.rest.util.MimeTypeDetector;
import com.aerofs.daemon.rest.util.Ranges;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.sun.jersey.core.header.MatchingEntityTag;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import java.sql.SQLException;
import java.util.Date;
import java.util.Set;

public class HdFileContent extends AbstractHdIMC<EIFileContent>
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final AccessChecker _access;
    private final NativeVersionControl _nvc;
    private final MimeTypeDetector _detector;

    @Inject
    public HdFileContent(AccessChecker access, DirectoryService ds, IPhysicalStorage ps,
            NativeVersionControl nvc, MimeTypeDetector detector)
    {
        _ds = ds;
        _ps = ps;
        _access = access;
        _nvc = nvc;
        _detector = detector;
    }

    /**
     * Build Http response for file content requests
     *
     * see RFC2616 for more details on conditional
      and partial content requests
     */
    @Override
    protected void handleThrows_(EIFileContent ev, Prio prio) throws ExNotFound, SQLException
    {
        final OA oa = _access.checkObject_(ev._object, ev._user);
        if (!oa.isFile()) throw new ExNotFound();

        final CA ca = oa.caMasterThrows();
        final EntityTag etag = etag(oa.soid());

        // conditional request: 304 Not Modified on ETAG match
        if (ev._ifNoneMatch != null && match(ev._ifNoneMatch, etag)) {
            ev.setResult_(Response.notModified(etag));
            return;
        }

        // build range list for partial request, honoring If-Range header
        RangeSet<Long> ranges = parseRanges(ev._rangeset, ev._ifRange, etag, ca.length());

        // base response template
        ResponseBuilder bd = Response.ok().tag(etag).lastModified(new Date(ca.mtime()));

        IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), KIndex.MASTER);

        ev.setResult_(ranges != null
                ? partialContent(bd, pf, ca, ranges)
                : fullContent(bd, oa.name(), pf, ca));
    }

    private ResponseBuilder fullContent(ResponseBuilder ok, String name, IPhysicalFile pf, CA ca)
    {
        return ok
                .type(_detector.detect(name))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, ca.length())
                .entity(new SimpleStream(pf, ca, 0, ca.length()));
    }

    private ResponseBuilder partialContent(ResponseBuilder ok, IPhysicalFile pf, CA ca, RangeSet<Long> ranges)
    {
        // if rangeset is empty, the request is not satisfiable
        if (ranges.isEmpty()) {
            return Response.status(HttpStatus.UNSATISFIABLE_RANGE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + ca.length());
        }

        Set<Range<Long>> parts = ranges.asRanges();
        if (parts.size() == 1) {
            // single range in the response -> 206 Partial Content w/ raw data body
            Range<Long> range = Iterables.getFirst(parts, null);
            long skip = range.lowerEndpoint();
            long last = range.upperEndpoint() - 1;
            long span = last - skip + 1;

            return ok.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_RANGE,
                            "bytes " + skip + "-" + last + "/" + ca.length())
                    .entity(new SimpleStream(pf, ca, skip, span));
        } else {
            // multiple ranges -> multipart response
            MultipartStream stream = new MultipartStream(pf, ca, parts);
            return ok.status(HttpStatus.PARTIAL_CONTENT)
                    .type("multipart/byteranges; boundary=" + stream._boundary)
                    .entity(stream);
        }
    }

    private static boolean match(Set<? extends EntityTag> matching, EntityTag etag)
    {
        return matching == MatchingEntityTag.ANY_MATCH || matching.contains(etag);
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
}
