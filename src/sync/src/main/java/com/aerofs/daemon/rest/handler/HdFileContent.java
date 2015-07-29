package com.aerofs.daemon.rest.handler;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.protocol.ContentProvider;
import com.aerofs.daemon.core.protocol.SendableContent;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIFileContent;
import com.aerofs.daemon.rest.stream.MultipartStream;
import com.aerofs.daemon.rest.stream.SimpleStream;
import com.aerofs.daemon.rest.util.ContentEntityTagUtil;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.util.MimeTypeDetector;
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
import java.io.IOException;
import java.util.Date;
import java.util.Set;

import static com.aerofs.daemon.core.activity.OutboundEventLogger.CONTENT_REQUEST;

public class HdFileContent extends AbstractHdIMC<EIFileContent>
{
    @Inject private IPhysicalStorage _ps;
    @Inject private MimeTypeDetector _detector;
    @Inject private OutboundEventLogger _oel;
    @Inject private ContentEntityTagUtil _etags;
    @Inject private IPathResolver _resolver;
    @Inject private RestContentHelper _helper;
    @Inject private ContentProvider _provider;

    /**
     * Build Http response for file content requests
     *
     * see RFC2616 for more details on conditional
     and partial content requests
     */
    @Override
    protected void handleThrows_(EIFileContent ev) throws Exception
    {
        SOID soid = _helper.resolveObjectWithPerm(ev._object, ev._token,
                Scope.READ_FILES, Permissions.VIEWER);
        _helper.checkDeviceHasFile(soid);

        final EntityTag etag =  _etags.etagForContent(soid);
        // conditional request: 304 Not Modified on ETAG match
        if (ev._ifNoneMatch.isValid() && ev._ifNoneMatch.matches(etag)) {
            ev.setResult_(Response.notModified(etag));
            return;
        }

        _oel.log_(CONTENT_REQUEST, soid, ev.did());

        ResolvedPath path = _resolver.resolveNullable_(soid);
        SendableContent content = _provider.content(new SOKID(soid, _helper.selectBranch(soid)));
        content.pf.prepareForAccessWithoutCoreLock_();

        // Deletions take ~6s to register in the VFS which leaves a sizable window where files
        // remain listed but their content is gone.
        // Check for existence of physical file before returning a 200 to avoid closing the
        // connection when reading the content fails.
        if (!content.pf.exists_()) throw new ExNotFound();

        // build range list for partial request, honoring If-Range header
        RangeSet<Long> ranges = Ranges.parseRanges(ev._rangeset, ev._ifRange, etag, content.length);

        // base response template
        ResponseBuilder bd = Response.ok().tag(etag).lastModified(new Date(content.mtime));

        // TODO: send CONTENT_COMPLETION from ContentStream
        ev.setResult_(ranges != null
                ? partialContent(bd, content.pf, content.length, content.mtime, ranges)
                : fullContent(bd, path.last(), content.pf, content.length, content.mtime));
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

    private ResponseBuilder fullContent(ResponseBuilder bd, String name, IPhysicalFile pf,
            long length, long mtime) throws IOException
    {
        return bd
                .type(_detector.detect(name))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; " + filename(name))
                .header(HttpHeaders.CONTENT_LENGTH, length)
                .entity(new SimpleStream(pf, length, mtime, 0, length));
    }

    private ResponseBuilder partialContent(ResponseBuilder ok, IPhysicalFile pf, long length,
            long mtime, RangeSet<Long> ranges) throws IOException
    {
        // if rangeset is empty, the request is not satisfiable
        if (ranges.isEmpty()) {
            return Response.status(HttpStatus.UNSATISFIABLE_RANGE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
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
                            "bytes " + skip + "-" + last + "/" + length)
                    .entity(new SimpleStream(pf, length, mtime, skip, span));
        } else {
            // multiple ranges -> multipart response
            MultipartStream stream = new MultipartStream(pf, length, mtime, parts);
            return ok.status(HttpStatus.PARTIAL_CONTENT)
                    .type("multipart/byteranges; boundary=" + stream._boundary)
                    .entity(stream);
        }
    }
}
