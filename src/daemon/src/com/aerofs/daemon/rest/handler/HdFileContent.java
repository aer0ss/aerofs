package com.aerofs.daemon.rest.handler;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.stream.MultipartStream;
import com.aerofs.daemon.rest.stream.SimpleStream;
import com.aerofs.daemon.rest.util.MimeTypeDetector;
import com.aerofs.lib.id.KIndex;
import com.aerofs.oauth.Scope;
import com.aerofs.restless.util.HttpStatus;
import com.aerofs.restless.util.Ranges;
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

import static com.aerofs.daemon.core.activity.OutboundEventLogger.CONTENT_REQUEST;

public class HdFileContent extends AbstractRestHdIMC<EIFileContent>
{
    @Inject private IPhysicalStorage _ps;
    @Inject private MimeTypeDetector _detector;
    @Inject private OutboundEventLogger _oel;
    @Inject private ICollectorStateDatabase _csdb;

    /**
     * Build Http response for file content requests
     *
     * see RFC2616 for more details on conditional
      and partial content requests
     */
    @Override
    protected void restHandleThrows_(EIFileContent ev) throws ExNotFound, SQLException
    {
        final OA oa = _access.resolve_(ev._object, ev._token);
        if (!oa.isFile()) throw new ExNotFound("No such file");

        requireAccessToFile(ev._token, Scope.READ_FILES, oa);

        final CA ca = oa.caMasterNullable();
        if (ca == null) {
            String message;
            if (oa.isExpelled()) {
                message = _ds.isDeleted_(oa) ? "No such file" : "Content not synced on this device";
            } else if (!_csdb.isCollectingContent_(oa.soid().sidx())) {
                message = "Quota exceeded";
            }  else {
                message = "Content not yet available on this device";
            }
            throw new ExNotFound(message);
        }
        final EntityTag etag = _etags.etagForContent(oa.soid());

        // conditional request: 304 Not Modified on ETAG match
        if (ev._ifNoneMatch.isValid() && ev._ifNoneMatch.matches(etag)) {
            ev.setResult_(Response.notModified(etag));
            return;
        }

        _oel.log_(CONTENT_REQUEST, oa.soid(), ev.did());

        // build range list for partial request, honoring If-Range header
        RangeSet<Long> ranges = Ranges.parseRanges(ev._rangeset, ev._ifRange, etag, ca.length());

        // base response template
        ResponseBuilder bd = Response.ok().tag(etag).lastModified(new Date(ca.mtime()));

        IPhysicalFile pf = _ps.newFile_(_ds.resolve_(oa), KIndex.MASTER);
        pf.prepareForAccessWithoutCoreLock_();

        // Deletions take ~6s to register in the VFS which leaves a sizable window where files
        // remain listed but their content is gone.
        // Check for existence of physical file before returning a 200 to avoid closing the
        // connection when reading the content fails.
        if (!pf.exists_()) throw new ExNotFound();

        // TODO: send CONTENT_COMPLETION from ContentStream

        ev.setResult_(ranges != null
                ? partialContent(bd, pf, ca, ranges)
                : fullContent(bd, oa.name(), pf, ca));
    }

    /**
     * Provide both ASCII-clean'd filename and UTF-8/percent-encoded filename* params, as described
     * in RFCs 6266 and 5987
     *
     * Both Firefox (25) and Safari (6.1) handle filename* correctly, however, not all User Agents
     * can deal with this relatively new extension of the HTTP spec (2011):
     *   - Chrome (32) does not consistently pick the filename* value
     *   - wget (1.14) concatenates both values and fails to decode the percent-encoding
     */
    private String filename(String s)
    {
        StringBuilder bd = new StringBuilder("filename=\"");
        bd.append(s.replaceAll("[^a-zA-Z0-9!#$&+-._`|~^]", "-"));
        bd.append("\" ; filename*=UTF-8''");
        // TODO: no need to encode attr-char
        String encoded = BaseUtil.hexEncode(BaseUtil.string2utf(s));
        for (int i = 0; i < encoded.length(); i += 2) {
            bd.append("%").append(encoded.charAt(i)).append(encoded.charAt(i + 1));
        }
        return bd.toString();
    }

    private ResponseBuilder fullContent(ResponseBuilder ok, String name, IPhysicalFile pf, CA ca)
    {
        return ok
                .type(_detector.detect(name))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; " + filename(name))
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
