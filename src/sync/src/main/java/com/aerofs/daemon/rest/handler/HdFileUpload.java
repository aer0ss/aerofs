package com.aerofs.daemon.rest.handler;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.core.IVersionUpdater;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.core.polaris.submit.ContentAvailabilitySubmitter;
import com.aerofs.daemon.core.polaris.submit.ContentChangeSubmitter;
import com.aerofs.daemon.core.polaris.submit.WaitableSubmitter;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIFileUpload;
import com.aerofs.daemon.rest.util.ContentEntityTagUtil;
import com.aerofs.daemon.rest.util.UploadID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.*;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.restless.util.ContentRange;
import com.aerofs.restless.util.HttpStatus;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.aerofs.rest.api.Error.Type;
import static com.google.common.base.Preconditions.checkState;
import static javax.ws.rs.core.Response.ResponseBuilder;
import static javax.ws.rs.core.Response.Status;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;

public class HdFileUpload extends AbstractHdIMC<EIFileUpload>
{
    @Inject private IPhysicalStorage _ps;
    @Inject private TokenManager _tokenManager;
    @Inject protected TransManager _tm;
    @Inject private IVersionUpdater _vu;
    @Inject private RestContentHelper _helper;
    @Inject private ContentEntityTagUtil _etags;
    @Inject private IPathResolver _resolver;
    @Inject private ICollectorStateDatabase _csdb;
    @Inject private ContentChangeSubmitter _ccsub;
    @Inject private ContentAvailabilitySubmitter _casub;

    @Override
    protected void handleThrows_(EIFileUpload ev) throws Exception
    {
        SOID soid = checkSanity_(ev);
        if (soid == null) return;

        l.info("upload id {} range {}", (ev._ulid != null && ev._ulid.isValid()) ? ev._ulid : null, ev._range);

        // to avoid prefix clashes, generate a unique upload ID if none given
        UploadID uploadId = ev._ulid.isValid() ? ev._ulid : UploadID.generate();
        IPhysicalPrefix pf = _ps.newPrefix_(new SOKID(soid, KIndex.MASTER),
                uploadId.toStringFormal());

        if (ev._range != null) {
            Object r = preValidateChunk(ev._ulid, ev._range, pf);
            if (r != null) {
                ev.setResult_(r);
                return;
            }
        }

        try {
            // TODO: limit max upload size?
            PrefixOutputStream out = pf.newOutputStream_(true);
            long chunkLength = receiveContent_(ev._content, out);

            l.info("uploaded {} bytes", chunkLength);

            if (ev._range != null) {
                Object r = postValidateChunk(uploadId, ev._range, chunkLength, pf);
                if (r != null) {
                    ev.setResult_(r);
                    return;
                }
            }

            // anything can happen when the core lock is released...
            SOID newSoid = checkSanity_(ev);
            if (newSoid == null) return;

            applyPrefix_(pf, newSoid, out.digest());
            ev.setResult_(Response.noContent()
                    .tag(_etags.etagForContent(newSoid)));
        } catch (Exception e) {
            l.warn("upload failed", e);
            // if anything goes wrong, delete the prefix, unless the upload is resumable
            if (!ev._ulid.isValid()) {
                try {
                    l.info("delete prefix");
                    pf.delete_();
                } catch (IOException ee) {
                    l.warn("failed to delete prefix {}", pf);
                }
            }
            throw e;
        }
    }

    private SOID checkSanity_(EIFileUpload ev) throws Exception
    {
        SOID soid = _helper.resolveObjectWithPerm(ev._object, ev._token, Scope.WRITE_FILES,
                Permissions.EDITOR);
        if (soid == null) return null;

        if (!_csdb.isCollectingContent_(soid.sidx())) {
            ev.setResult_(Response.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .entity(new Error(Type.INSUFFICIENT_STORAGE, "Quota exceeded")));
            return null;
        }

        final EntityTag etag = _etags.etagForContent(soid);
        // TODO: select target branch based on etag instead of always trying to upload to MASTER?
        if (ev._ifMatch.isValid() && !ev._ifMatch.matches(etag)) {
            ev.setResult_(Response
                    .status(Status.PRECONDITION_FAILED)
                    .entity(new Error(Type.CONFLICT, "Etag mismatch. Found: " + etag)));
            return null;
        }
        return soid;
    }

    private @Nullable ResponseBuilder preValidateChunk(UploadID uploadId,
            @Nonnull ContentRange range, IPhysicalPrefix pf) throws ExBadArgs, IOException
    {
        Range<Long> r = range.range();
        Long totalLength = range.totalLength();

        if (!uploadId.isValid()) {
            if (r != null && r.lowerEndpoint() != 0) {
                return Response.status(Status.BAD_REQUEST)
                        .entity(new Error(Type.BAD_ARGS, "Missing or invalid Upload-ID"));
            }
            // accept chunk and generate new upload id
            return null;
        }

        long prefixLength = pf.getLength_();

        if (r == null) {
            if (totalLength != null && totalLength == prefixLength) {
                // complete upload by an empty chunk
                return null;
            } else if (prefixLength == 0) {
                // TODO more specific status code or error type
                return Response
                        .status(Status.BAD_REQUEST)
                        .entity(new Error(Type.BAD_ARGS, "Invalid upload identifier"));
            }
            return withRange(prefixLength,
                    Response.noContent()
                            .header("Upload-ID", uploadId.toStringFormal()));
        }

        checkState(r.upperBoundType() == BoundType.OPEN);

        if (totalLength != null && r.upperEndpoint() > totalLength) {
            return Response
                    .status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS,
                            "Invalid Content-Range: last-byte-pos >= instance-length (RFC2616 14.16)"));
        }

        if (r.lowerEndpoint() != prefixLength) {
            l.info("invalid chunk upload request: {} {}", r.lowerEndpoint(), prefixLength);
            return withRange(prefixLength,
                    Response.status(HttpStatus.UNSATISFIABLE_RANGE)
                            .header("Upload-ID", uploadId.toStringFormal()));
        }

        return null;
    }

    private static ResponseBuilder withRange(long prefixLength, ResponseBuilder bd)
    {
        return prefixLength > 0 ? bd.header(Names.RANGE, "bytes=0-" + (prefixLength - 1)) : bd;
    }

    private @Nullable ResponseBuilder postValidateChunk(UploadID uploadId,
            @Nonnull ContentRange range, long chunkLength, IPhysicalPrefix pf) throws IOException
    {
        Range<Long> r = range.range();
        long prefixLength = pf.getLength_();
        if (r != null) {
            // NB: validateChunk ensures that these values make sense
            checkState(r.upperBoundType() == BoundType.OPEN);
            long rangeLength = r.upperEndpoint() - r.lowerEndpoint();
            if (chunkLength != rangeLength) {
                l.warn("discard corrupted prefix", chunkLength);
                pf.delete_();
                return Response.status(Status.BAD_REQUEST)
                        .header("Upload-ID", uploadId.toStringFormal())
                        .entity(new Error(Type.BAD_ARGS,
                                "Content-Range not consistent with body length"));
            }
        }

        Long len = range.totalLength();
        if (len == null || prefixLength < len) {
            // ack chunk
            return withRange(prefixLength,
                    Response.noContent()
                            .header("Upload-ID", uploadId.toStringFormal()));
        }

        if (prefixLength > len) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS,
                            "Upload body larger than advertised size"));
        }
        return null;
    }

    private long receiveContent_(InputStream in, PrefixOutputStream out)
            throws ExNoResource, ExAborted, IOException
    {
        try {
            return _tokenManager.inPseudoPause_(Cat.API_UPLOAD, "rest-upload",
                    () -> ByteStreams.copy(in, out));
        } finally {
            out.close();
        }
    }

    private void applyPrefix_(IPhysicalPrefix pf, SOID soid, ContentHash h)
            throws SQLException, IOException, ExNoResource {
        try (Trans t = _tm.begin_()) {
            ResolvedPath path = _resolver.resolveNullable_(soid);
            // NB: MUST get prefix length BEFORE apply_
            long length = pf.getLength_();
            long mtime = System.currentTimeMillis();
            boolean wasPresent = _helper.wasPresent(soid);
            mtime = _ps.apply_(pf, _ps.newFile_(path, KIndex.MASTER), wasPresent, mtime, t);
            _helper.updateContent(soid, h, t, length, mtime, wasPresent);

            // increment version number after update
            _vu.update_(new SOCID(soid, CID.CONTENT), t);

            t.commit_();
        }
        l.debug("wait sub {}", soid);
        waitSubmitted_(soid, _ccsub, _casub);
    }

    private void waitSubmitted_(SOID soid, WaitableSubmitter<?> ...subs) {
        ArrayList<Future<?>> fl = new ArrayList<>(subs.length);
        // NB: must kick register all waiter *BEFORE* releasing the core lock
        // otherwise the second operation may complete immediately after the first, before
        // control is regained and the second waiter can be registered, which would cause
        // it to wait for the full timeout duration for no good reason.
        for (WaitableSubmitter<?> sub : subs) {
            fl.add(sub.waitSubmitted_(soid));
        }
        for (Future<?> f : fl) {
            try {
                if (f.isDone()) return;
                _tokenManager.inPseudoPause_(Cat.UNLIMITED, "rest-sub", () -> {
                    try {
                        return f.get(3, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        f.cancel(false);
                        throw e;
                    }
                });
            } catch (Exception e) {
                l.info("content sub failed", BaseLogUtil.suppress(e));
            }
        }
    }
}
