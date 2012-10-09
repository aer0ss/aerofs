/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.proto;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.alias.Aliasing;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.EmigrantDetector;
import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.*;

import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBMeta;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Set;

public class GetComponentReply
{
    private static final Logger l = Util.l(GetComponentReply.class);

    private final DirectoryService _ds;
    private final ReceiveAndApplyUpdate _ru;
    private final Aliasing _al;
    private final MapAlias2Target _a2t;
    private final LocalACL _lacl;
    private final IncomingStreams _iss;
    private final EmigrantDetector _emd;
    private final TransManager _tm;
    private final MetaDiff _mdiff;

    @Inject
    public GetComponentReply(DirectoryService ds, LocalACL lacl, ReceiveAndApplyUpdate ru,
            Aliasing al, MetaDiff mdiff, IncomingStreams iss,
            EmigrantDetector emd, MapAlias2Target a2t, TransManager tm)
    {
        _ds = ds;
        _ru = ru;
        _al = al;
        _lacl = lacl;
        _iss = iss;
        _emd = emd;
        _tm = tm;
        _a2t = a2t;
        _mdiff = mdiff;
    }

    public static OA.Type fromPB(PBMeta.Type type)
    {
        return OA.Type.valueOf(type.ordinal());
    }

    static enum CIDType {
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

    /*
     * @param requested see Download._requested for more information
     */
    void processReply_(SOCID socid, DigestedMessage msg, Set<OCID> requested, Token tk)
            throws Exception
    {
        try {
            Util.checkPB(msg.pb().hasGetComReply() || msg.pb().hasExceptionReply(),
                PBGetComReply.class);

            if (msg.pb().hasExceptionReply()) {
                throw Exceptions.fromPB(msg.pb().getExceptionReply());
            }

            doProcessReply_(socid, msg, requested, tk);

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

    private void doProcessReply_(SOCID socid, DigestedMessage msg, Set<OCID> requested, Token tk)
            throws Exception
    {
        PBGetComReply pbReply = msg.pb().getGetComReply();
        final CIDType type = CIDType.infer(socid.cid());

        /////////////////////////////////////////
        // determine meta diff, handle aliases, check permissions, etc
        //
        // N.B. ExSenderHasNoPerm rather than ExNoPerm should be thrown if the sender has no
        // permission to update the component, so that the caller of this method can differentiate
        // this case from the case when the sender replies a ExNoPerm to us, indicating that we as
        // the receiver has no permission to read. Therefore, we can't use _dacl.checkThrows().

        // TODO check for parent permissions for creation / moving?

        // if permission checking failed, the GetComponentReply is malicious or the
        // file permission was reduced between GetComponentCall and Reply.
        // TODO notify user about this, and remove from KML the version
        // specified in the Reply message?

        OID oidParent = null;  // null for non-meta updates
        int metaDiff;
        if (type != CIDType.META) {
            metaDiff = 0;
            if (_ds.hasOA_(socid.soid())) {
                if (!_lacl.check_(msg.user(), socid.sidx(), Role.EDITOR)) {
                    throw new ExSenderHasNoPerm();
                }
            }

        } else {
            PBMeta meta = pbReply.getMeta();
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
            final OID oidParentTarget = _a2t.getNullable_(new SOID(socid.sidx(), oidParent));
            if (oidParentTarget != null) {
                l.warn(socid + ": dereferenced aliased parent "
                        + oidParent + "->" + oidParentTarget);

                // We don't gracefully handle the parent OID being the same as that sent in the msg
                assert !oidParentTarget.equals(socid.oid()) : "parent msg " + oidParent
                        + " parent target " + oidParentTarget + " socid " + socid;

                oidParent = oidParentTarget;
            }

            // Verify that encoding of the received meta is UTF-8 Normal Form C
            assert Normalizer.isNormalized(meta.getName(), Form.NFC)
                    : socid + " " + Form.valueOf(meta.getName());

            metaDiff = _mdiff.computeMetaDiff_(socid.soid(), meta, oidParent);

            if (Util.test(metaDiff, MetaDiff.NAME | MetaDiff.PARENT)) {
                // perform emigration only for the target object, because at this
                // point it's difficult to decide whether an object, and which one,
                // will be aliased or renamed, etc. this is all right as very rare
                // that aliasing/name conflicts and emigration happen at the same
                // time.
                _emd.detectAndPerformEmigration_(socid.soid(), oidParent, meta.getName(),
                        meta.getEmigrantTargetAncestorSidList(), msg.did(), tk);
                // N.B after the call the local meta might have been updated.
                // but we don't need to update metaDiff.
            }

            if (meta.hasTargetOid()) {
                // Process the alias message.
                assert meta.hasTargetVersion();
                _al.processAliasMsg_(
                    msg.did(),
                    socid.soid(),                                        // alias
                    new Version(pbReply.getVersion()),                   // vRemoteAlias
                    new SOID(socid.sidx(), new OID(meta.getTargetOid())),// target
                    new Version(meta.getTargetVersion()),                // vRemoteTarget
                    oidParent,
                    metaDiff, meta, requested);

                // processAliasMsg_() does all the processing necessary for alias msg hence
                // return from this point.
                return;
            } else {
                // Process non-alias message.
                OID targetOIDLocal = _a2t.getNullable_(socid.soid());
                if (targetOIDLocal != null) {
                    _al.processNonAliasMsgOnLocallyAliasedObject_(socid, targetOIDLocal);
                    // processNonAliasMsgOnLocallyAliasedObject_() does the necessary processing
                    // for update on a locally aliased object hence return from this point.
                    return;
                } else {
                    l.info("meta diff: " + String.format("0x%1$x", metaDiff));
                    if (metaDiff != 0 && _ds.hasOA_(socid.soid())) {
                        if (!_lacl.check_(msg.user(), socid.sidx(), Role.EDITOR)) {
                            throw new ExSenderHasNoPerm();
                        }
                    }
                }
            }
        }

        /////////////////////////////////////////
        // determine causal relation

        Version vRemote = new Version(pbReply.getVersion());
        ReceiveAndApplyUpdate.CausalityResult cr;
        switch (type) {
        case META:
            cr = _ru.computeCausalityForMeta_(socid.soid(), vRemote, metaDiff);
            break;
        case CONTENT:
            cr = _ru.computeCausalityForContent_(socid.soid(), vRemote, msg, tk);
            break;
        default:
            throw Util.fatal("not implemented");
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
        try {
            switch (type) {
            case META:
                t = _tm.begin_();
                if (metaDiff != 0) {
                    boolean oidsAliasedOnNameConflict =
                        _ru.applyMeta_(msg.did(), targetBranch.soid(), pbReply.getMeta(),
                            oidParent,
                            wasPresent, metaDiff, t,
                            // for non-alias message create a new version
                            // on aliasing name conflict.
                            null,
                            vRemote,
                            targetBranch.soid(),
                            requested,
                            cr);

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

                // N.B. kidxOld appears to only be used to detect if prefix files should be
                // accepted, migrated, or rejected in applyContent_(). It's unclear to me (DF)
                // how kidxOld would ever acquire a value other than MASTER.
                KIndex kidxOld = KIndex.MASTER;

                t = _ru.applyContent_(msg, targetBranch, kidxOld, wasPresent, vRemote, cr, tk);
                break;

            default:
                Util.unimplemented("support CID: " + socid.cid());
                break;
            }

            _ru.applyUpdateMetaAndContent_(targetBranch, vRemote, cr, t);

            t.commit_();
            l.warn(socid + " ok " + msg.ep());

        } finally {
            if (t != null) t.end_();
        }
    }
}
