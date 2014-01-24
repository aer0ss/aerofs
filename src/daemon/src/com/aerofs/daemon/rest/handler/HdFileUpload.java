package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.ds.DirectoryService;
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
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIFileUpload;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

public class HdFileUpload extends AbstractRestHdIMC<EIFileUpload>
{
    private final TransManager _tm;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final EntityTagUtil _etags;
    private final RestObjectResolver _access;
    private final TokenManager _tokenManager;
    private final VersionUpdater _vu;

    @Inject
    public HdFileUpload(RestObjectResolver access, EntityTagUtil etags, MetadataBuilder mb,
            TokenManager tokenManager, DirectoryService ds, IPhysicalStorage ps, TransManager tm,
            VersionUpdater vu)
    {
        super(access, etags, mb, tm);
        _tm = tm;
        _ds = ds;
        _ps = ps;
        _vu = vu;
        _etags = etags;
        _access = access;
        _tokenManager = tokenManager;
    }

    @Override
    protected void handleThrows_(EIFileUpload ev) throws Exception
    {
        final OA oa = checkSanity_(ev);

        // generate a unique download ID to avoid prefix clashes
        // will also be used for resumable downloads
        UniqueID downloadId = UniqueID.generate();
        IPhysicalPrefix pf = _ps.newPrefix_(new SOCKID(oa.soid(), CID.CONTENT, KIndex.MASTER),
                downloadId.toStringFormal());

        try {
            uploadPrefix_(ev._content, pf);

            // anything can happen when the core lock is released...
            final OA newOA = checkSanity_(ev);

            applyPrefix_(pf, newOA);

            ev.setResult_(Response.ok().tag(_etags.etagForObject(newOA.soid())));
        } catch (Exception e) {
            l.warn("upload failed", e);
            // if anything goes wrong, delete the prefix
            // TODO: support incremental/resumable upload (cf Google Drive API)
            try {
                pf.delete_();
            } catch (IOException ee) {
                l.warn("failed to delete prefix {}", pf);
            }
            throw e;
        }
    }

    private void uploadPrefix_(InputStream in, IPhysicalPrefix pf)
            throws ExNoResource, ExAborted, IOException
    {
        Token tk = _tokenManager.acquireThrows_(Cat.CLIENT, "rest-upload");
        try {
            TCB tcb = tk.pseudoPause_("rest-upload");
            try {
                // TODO: version listener to check ifMatch on every change and abort transfer
                // as soon as possible
                // TODO: ideally we wouldn't need to use a core thread for that copy
                // we really should just pipe incoming packets into the prefix as they arrive
                // and schedule a self-handling event to apply the prefix at the end of the
                // transfer
                // TODO: compute ContentHash as bytes are received (NB: deal with interruption)
                OutputStream out = pf.newOutputStream_(false);
                try {
                    ByteStreams.copy(in, out);
                } finally {
                    out.close();
                }
            } finally {
                tcb.pseudoResumed_();
            }
        } finally {
            tk.reclaim_();
        }
    }

    private OA checkSanity_(EIFileUpload ev)
            throws ExNotFound, ExNoPerm, SQLException
    {
        if (!ev._token.isAllowedToWrite()) throw new ExNoPerm();
        OA oa = _access.resolveWithPermissions_(ev._object, ev.user(), Permissions.EDITOR);

        if (oa.isExpelled() || !oa.isFile()) throw new ExNotFound();

        final EntityTag etag = _etags.etagForObject(oa.soid());

        // TODO: select target branch based on etag instead of always trying to upload to MASTER?
        if (ev._ifMatch.isValid() && !ev._ifMatch.matches(etag)) {
            ev.setResult_(Response
                    .status(Status.PRECONDITION_FAILED)
                    .entity(new Error(Type.CONFLICT, "Etag mismatch. Found: " + etag)));
            return null;
        }
        return oa;
    }

    private void applyPrefix_(IPhysicalPrefix pf, OA oa)
            throws SQLException, IOException
    {
        SOID soid = oa.soid();
        Trans t = _tm.begin_();
        try {
            ResolvedPath path = _ds.resolve_(oa);
            long mtime = System.currentTimeMillis();
            boolean wasPresent = (oa.caMasterNullable() != null);
            _ps.apply_(pf, _ps.newFile_(path, KIndex.MASTER), wasPresent, mtime, t);

            // update CA
            if (!wasPresent) _ds.createCA_(soid, KIndex.MASTER, t);
            _ds.setCA_(new SOKID(soid, KIndex.MASTER), pf.getLength_(), mtime, null, t);

            // increment version number after update
            _vu.update_(new SOCKID(soid, CID.CONTENT, KIndex.MASTER), t);

            t.commit_();
        } finally {
            t.end_();
        }
    }
}
