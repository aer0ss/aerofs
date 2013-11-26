package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.stream.MultipartStream;
import com.aerofs.daemon.rest.stream.SimpleStream;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.HttpStatus;
import com.aerofs.daemon.rest.util.MimeTypeDetector;
import com.aerofs.daemon.rest.util.Ranges;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.KIndex;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;

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
    private final RestObjectResolver _access;
    private final EntityTagUtil _etags;
    private final MimeTypeDetector _detector;

    @Inject
    public HdFileContent(RestObjectResolver access, DirectoryService ds, IPhysicalStorage ps,
            MimeTypeDetector detector, EntityTagUtil etags)
    {
        _ds = ds;
        _ps = ps;
        _etags = etags;
        _access = access;
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
        final OA oa = _access.resolve_(ev._object, ev._user);
        if (!oa.isFile()) throw new ExNotFound();

        final CA ca = oa.caMasterThrows();
        final EntityTag etag = _etags.etagForObject(oa.soid());

        // conditional request: 304 Not Modified on ETAG match
        if (ev._ifNoneMatch.isValid() && ev._ifNoneMatch.matches(etag)) {
            ev.setResult_(Response.notModified(etag));
            return;
        }

        // build range list for partial request, honoring If-Range header
        RangeSet<Long> ranges = Ranges.parseRanges(ev._rangeset, ev._ifRange, etag, ca.length());

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
}
