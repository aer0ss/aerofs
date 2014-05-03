package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.rest.event.EIFileUpload;
import com.aerofs.daemon.rest.util.UploadID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.SecUtil;
import com.aerofs.oauth.Scope;
import com.aerofs.restless.util.ContentRange;
import com.aerofs.restless.util.HttpStatus;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkState;

public class HdFileUpload extends AbstractRestHdIMC<EIFileUpload>
{
    @Inject  private IPhysicalStorage _ps;
    @Inject private TokenManager _tokenManager;
    @Inject private VersionUpdater _vu;

    @Override
    protected void handleThrows_(EIFileUpload ev) throws Exception
    {
        final OA oa = checkSanity_(ev);
        if (oa == null) return;

        l.info("upload {} {}", ev._ulid, ev._range);

        // to avoid prefix clashes, generate a unique upload ID if none given
        UploadID uploadId = ev._ulid.isValid() ? ev._ulid : UploadID.generate();
        IPhysicalPrefix pf = _ps.newPrefix_(new SOCKID(oa.soid(), CID.CONTENT, KIndex.MASTER),
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
            // avoid hashing when we know for sure the chunk cannot end the upload
            MessageDigest md = mightCompleteUpload(ev._range) ? SecUtil.newMessageDigest() : null;
            long chunkLength = uploadPrefix_(ev._content, pf, md);

            l.info("uploaded {} bytes", chunkLength);

            if (ev._range != null) {
                Object r = postValidateChunk(uploadId, ev._range, chunkLength, pf);
                if (r != null) {
                    ev.setResult_(r);
                    return;
                }
            }

            // enforced by mightCompleteUpload
            checkState(md != null);

            // anything can happen when the core lock is released...
            final OA newOA = checkSanity_(ev);
            if (newOA == null) return;

            applyPrefix_(pf, newOA, new ContentHash(md.digest()));

            ev.setResult_(Response.ok()
                    .tag(_etags.etagForContent(newOA.soid())));
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

    private static boolean mightCompleteUpload(@Nullable ContentRange cr)
    {
        if (cr == null) return true;
        Range<Long> range = cr.range();
        Long length = cr.totalLength();
        return length != null && (range == null || length.equals(range.upperEndpoint()));
    }

    private OA checkSanity_(EIFileUpload ev)
            throws ExNotFound, ExNoPerm, SQLException
    {
        OA oa = _access.resolveWithPermissions_(ev._object, ev._token, Permissions.EDITOR);

        if (oa.isExpelled() || !oa.isFile()) throw new ExNotFound();

        requireAccessToFile(ev._token, Scope.WRITE_FILES, oa);

        final EntityTag etag = _etags.etagForContent(oa.soid());

        // TODO: select target branch based on etag instead of always trying to upload to MASTER?
        if (ev._ifMatch.isValid() && !ev._ifMatch.matches(etag)) {
            ev.setResult_(Response
                    .status(Status.PRECONDITION_FAILED)
                    .entity(new Error(Type.CONFLICT, "Etag mismatch. Found: " + etag)));
            return null;
        }
        return oa;
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
                    Response.ok()
                            .header("Upload-ID", uploadId.toStringFormal()));
        }

        checkState(r.upperBoundType() == BoundType.OPEN);

        if (totalLength != null && r.upperEndpoint() > totalLength) {
            return Response
                    .status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS,
                            "Invalid Content-Range: last-byte-pos >= instance-length (RFC2616 14.16)"));
        }

        if (r.lowerEndpoint() > prefixLength) {
            return withRange(prefixLength,
                    Response.status(HttpStatus.UNSATISFIABLE_RANGE)
                            .header("Upload-ID", uploadId.toStringFormal()));
        }

        long lo = r.lowerEndpoint();
        if (lo < prefixLength) {
            l.info("truncate {}", lo);
            pf.truncate_(lo);
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
                // discard last chunk
                l.warn("discard inconsistent chunk {}", chunkLength);
                pf.truncate_(prefixLength - chunkLength);
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
                    Response.ok()
                            .header("Upload-ID", uploadId.toStringFormal()));
        }

        if (prefixLength > len) {
            return Response.status(Status.BAD_REQUEST)
                    .entity(new Error(Type.BAD_ARGS,
                            "Upload body larger than advertised size"));
        }
        return null;
    }

    private long uploadPrefix_(InputStream in, IPhysicalPrefix pf, MessageDigest md)
            throws ExNoResource, ExAborted, IOException
    {
        Token tk = _tokenManager.acquireThrows_(Cat.API_UPLOAD, "rest-upload");
        try {
            TCB tcb = tk.pseudoPause_("rest-upload");
            try {
                // TODO: version listener to check ifMatch on every change and abort transfer
                // as soon as possible
                // TODO: ideally we wouldn't need to use a core thread for that copy
                // we really should just pipe incoming packets into the prefix as they arrive
                // and schedule a self-handling event to apply the prefix at the end of the
                // transfer
                if (md != null && pf.getLength_() > 0) {
                    InputStream is = pf.newInputStream_();
                    try {
                        ByteStreams.copy(is,
                                new DigestOutputStream(ByteStreams.nullOutputStream(), md));
                    } finally {
                        is.close();
                    }
                }
                OutputStream out = pf.newOutputStream_(true);
                try {
                    return ByteStreams.copy(in, md != null ? new DigestOutputStream(out, md) : out);
                } finally {
                    out.flush();
                    if (out instanceof FileOutputStream) {
                        ((FileOutputStream)out).getChannel().force(true);
                    }
                    out.close();
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    private void applyPrefix_(IPhysicalPrefix pf, OA oa, ContentHash h)
            throws SQLException, IOException, ExNoResource
    {
        // sigh....................................................................................
        // ideally we should not neeed that (if BlockPrefix performed incremental chunking)
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "prepare");
        pf.prepare_(tk);

        SOID soid = oa.soid();
        Trans t = _tm.begin_();
        try {
            ResolvedPath path = _ds.resolve_(oa);
            // NB: MUST get prefix length BEFORE apply_
            long length = pf.getLength_();
            long mtime = System.currentTimeMillis();
            boolean wasPresent = (oa.caMasterNullable() != null);
            mtime = _ps.apply_(pf, _ps.newFile_(path, KIndex.MASTER), wasPresent, mtime, t);

            // update CA
            if (!wasPresent) _ds.createCA_(soid, KIndex.MASTER, t);
            _ds.setCA_(new SOKID(soid, KIndex.MASTER), length, mtime, h, t);

            // increment version number after update
            _vu.update_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER), t);

            t.commit_();
        } finally {
            t.end_();
        }
    }
}
