/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.migration.IEmigrantDetector;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.aerofs.proto.Core.PBMeta;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

public class GetComponentResponse
{
    private static final Logger l = Loggers.getLogger(GetComponentResponse.class);

    private final TransManager _tm;
    private final DirectoryService _ds;
    private final IncomingStreams _iss;
    private final ReceiveAndApplyUpdate _ru;
    private final MetaDiff _mdiff;
    private final Aliasing _al;
    private final MapAlias2Target _a2t;
    private final LocalACL _lacl;
    private final IEmigrantDetector _emd;

    @Inject
    public GetComponentResponse(TransManager tm, DirectoryService ds, IncomingStreams iss,
            ReceiveAndApplyUpdate ru, MetaDiff mdiff, Aliasing al, MapAlias2Target a2t,
            LocalACL lacl, IEmigrantDetector emd)
    {
        _tm = tm;
        _ds = ds;
        _iss = iss;
        _ru = ru;
        _mdiff = mdiff;
        _al = al;
        _a2t = a2t;
        _lacl = lacl;
        _emd = emd;
    }

    public static OA.Type fromPB(PBMeta.Type type)
    {
        return OA.Type.valueOf(type.ordinal());
    }

    private static enum CIDType {
        META,
        CONTENT,
        OTHER;

        static CIDType infer(CID cid)
        {
            if (cid.isMeta()) return META;
            else if (cid.equals(CID.CONTENT)) return CONTENT;
            else return OTHER;
        }
    }

    /**
     * @param msg the message sent in response to GetComponentRequest
     */
    public void processResponse_(SOCID socid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception
    {
        try {
            if (msg.pb().hasExceptionResponse()) {
                throw BaseLogUtil.suppress(Exceptions.fromPB(msg.pb().getExceptionResponse()));
            }
            Util.checkPB(msg.pb().hasGetComponentResponse(), PBGetComponentResponse.class);
            processResponseInternal_(socid, msg, cxt);
        } finally {
            // TODO put this statement into a more general method
            if (msg.streamKey() != null) _iss.end_(msg.streamKey());
        }
    }

    // on ExDependsOn, the download is suspended, and _dls.scheduleAndEnqueue_()
    // will be called upon resolution of the dependency.
    //
    // TODO on throwing DependsOn, save whatever downloaded in dl and avoid
    // downloading again after the dependency is solved.
    //

    private void processResponseInternal_(SOCID socid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception
    {
        final PBGetComponentResponse pbResponse = msg.pb().getGetComponentResponse();
        final CIDType type = CIDType.infer(socid.cid());

        /////////////////////////////////////////
        // determine meta diff, handle aliases, check permissions, etc
        //
        // N.B. ExSenderHasNoPerm rather than ExNoPerm should be thrown if the sender has no
        // permission to update the component, so that the caller of this method can differentiate
        // this case from the case when the sender replies a ExNoPerm to us, indicating that we as
        // the receiver has no permission to read. Therefore, we can't use _dacl.checkThrows().

        // TODO check for parent permissions for creation / moving?

        // if permission checking failed, the GetComponentResponse is malicious or the
        // file permission was reduced between GetComponentRequest and GetComponentResponse.
        // TODO notify user about this, and remove from KML the version
        // specified in the response message?

        OID oidParent = null;  // null for non-meta updates
        int metaDiff;
        if (type != CIDType.META) {
            metaDiff = 0;

            throwIfSenderHasNoPerm(socid.sidx(), msg.user(), msg.ep());

            // We should abort when receiving content for an aliased object
            if (_a2t.isAliased_(socid.soid())) {
                throw new ExAborted(socid + " aliased");
            }

        } else {
            PBMeta meta = pbResponse.getMeta();
            oidParent = new OID(meta.getParentObjectId());
            assert !oidParent.equals(socid.oid()) : "parent " + oidParent + " socid " + socid;

            // "Dereference" the parent OID if it has been aliased locally, otherwise:
            // *  the parent's OA is not present (aliased objects don't have
            //    corresponding OAs), so later code will throw ExDependsOn, causing the download
            //    subsystem to attempt the parent from the remote peer.
            // *  when the local peer receives the parent object, because the parent has been
            //    aliased, the local peer ignores the message and return success to the download
            //    subsystem.
            // *  after the dependency is incorrectly "resolved", the download subsystem attempts
            //    the original object, causing step 1 to repeat.
            // as a result, the system would enter an infinite loop.
            OID derefParent = _a2t.dereferenceAliasedOID_(new SOID(socid.sidx(), oidParent)).oid();
            if (!derefParent.equals(oidParent)) l.info("deref " + oidParent + " -> " + derefParent);
            oidParent = derefParent;

            // We don't gracefully handle the parent OID being the same as that sent in the msg
            assert !oidParent.equals(socid.oid()) : "p msg " + oidParent + " socid " + socid;

            metaDiff = _mdiff.computeMetaDiff_(socid.soid(), meta, oidParent);

            if (Util.test(metaDiff, MetaDiff.NAME | MetaDiff.PARENT)) {
                // perform emigration only for the target object as oppose to the aliased object,
                // because at this point it's difficult to decide whether an object, and which one,
                // will be aliased or renamed, etc. this is all right as very rare that
                // aliasing/name conflicts and emigration happen at the same time.
                _emd.detectAndPerformEmigration_(socid.soid(), oidParent, meta.getName(),
                        meta.getEmigrantTargetAncestorSidList(), cxt);
                // N.B after the call the local meta might have been updated.
                // but we don't need to update metaDiff.
            }

            if (meta.hasTargetOid()) {
                // Process the alias message.
                assert meta.hasTargetVersion();
                _al.processAliasMsg_(
                    socid.soid(),                                           // alias
                    Version.fromPB(pbResponse.getVersion()),                // vRemoteAlias
                    new SOID(socid.sidx(), new OID(meta.getTargetOid())),   // target
                    Version.fromPB(meta.getTargetVersion()),                // vRemoteTarget
                    oidParent,
                    metaDiff, meta, cxt);

                // processAliasMsg_() does all the processing necessary for alias msg hence
                // return from this point.
                return;
            } else {
                // Process non-alias message.
                OID targetOIDLocal = _a2t.getNullable_(socid.soid());
                if (targetOIDLocal != null) {
                    _al.processNonAliasMsgOnLocallyAliasedObject_(socid, targetOIDLocal);
                    // the above method does the necessary processing for update on a locally
                    // aliased object hence return from this point.
                    return;
                } else {
                    l.debug("meta diff: " + String.format("0x%1$x", metaDiff));
                    if (metaDiff != 0) {
                        throwIfSenderHasNoPerm(socid.sidx(), msg.user(), msg.ep());
                    }
                }
            }
        }

        /////////////////////////////////////////
        // determine causal relation

        Version vRemote = Version.fromPB(pbResponse.getVersion());
        ReceiveAndApplyUpdate.CausalityResult cr;
        switch (type) {
        case META:
            cr = _ru.computeCausalityForMeta_(socid.soid(), vRemote, metaDiff);
            break;
        case CONTENT:
            cr = _ru.computeCausalityForContent_(socid.soid(), vRemote, msg, cxt.token());
            break;
        default:
            throw SystemUtil.fatal("not implemented");
        }

        if (cr == null) return;

        // This is the branch to which the update should be applied, as determined by
        // ReceiveAndApplyUpdate.computeCausalityFor{Meta,Content}_().
        SOCKID targetBranch = new SOCKID(socid, cr._kidx);

        /////////////////////////////////////////
        // apply update

        // N.B. vLocal.isZero_() doesn't mean the component is new to us.
        // It may be the case that it's not new but all the local ticks
        // have been replaced by remote ticks.

        final boolean wasPresent = _ds.isPresent_(targetBranch);

        Trans t = null;
        Throwable rollbackCause = null;
        try {
            switch (type) {
            case META:
                t = _tm.begin_();
                if (metaDiff != 0) {
                    boolean oidsAliasedOnNameConflict =
                        _ru.applyMeta_(targetBranch.soid(), pbResponse.getMeta(), oidParent,
                                wasPresent, metaDiff, t,
                                // for non-alias message create a new version
                                // on aliasing name conflict.
                                null, vRemote, targetBranch.soid(), cr, cxt);

                    // Aliasing objects on name conflicts updates versions and bunch
                    // of stuff (see resolveNameConflictOnNewRemoteObjectByAliasing_()). No further
                    // processing is required hence return from this point.
                    if (oidsAliasedOnNameConflict) {
                        t.commit_();
                        return;
                    }
                }
                break;

            case CONTENT:
                // TODO merge/delete branches (variable kidcs), including their prefix files, that
                // are dominated by the new version
                t = _ru.applyContent_(msg, targetBranch, vRemote, cr, cxt.token());
                break;

            default:
                SystemUtil.fatal("to be implemented: support CID: " + socid.cid());
                break;
            }

            _ru.applyUpdateMetaAndContent_(targetBranch, vRemote, cr, t);

            t.commit_();
            l.info("{} ok {}", msg.ep(), socid);

        // See {@link com.aerofs.daemon.lib.db.trans.Trans#end_()} for the reason of these blocks
        } catch (Exception | Error e) {
            rollbackCause = e;
            throw e;
        } finally {
            if (t != null) t.end_(rollbackCause);
        }
    }

    // FIXME (AG): this same piece of code is repeated in every protocol class - find a way to do the ACL checks in a common place
    private void throwIfSenderHasNoPerm(SIndex sidx, UserID user, Endpoint ep)
            throws SQLException, ExSenderHasNoPerm
    {
        // see Rule 2 in acl.md
        if (!_lacl.check_(user, sidx, Permissions.EDITOR)) {
            l.warn("{} on {} has no editor perm for {}", user, ep, sidx);
            throw new ExSenderHasNoPerm();
        }
    }
}
