package com.aerofs.daemon.core.net.proto;

import java.io.IOException;
import java.sql.SQLException;
import java.text.Normalizer;
import java.text.Normalizer.Form;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.AliasingMover;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.migration.EmigrantCreator;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.spsv.SVClient;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBGetComReply;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetComCall;
import com.aerofs.proto.Core.PBGetComCall.Builder;

// TODO NAK for this and other primitives

// we split the code for this protocol primitive into classes due to complexity

public class GetComponentCall
{
    private static final Logger l = Util.l(GetComponentCall.class);

    private EmigrantCreator _emc;
    private PrefixVersionControl _pvc;
    private NativeVersionControl _nvc;
    private MapAlias2Target _a2t;
    private RPC _rpc;
    private DirectoryService _ds;
    private IPhysicalStorage _ps;
    private LocalACL _lacl;
    private NSL _nsl;
    private GCCSendContent _sendContent;
    // TODO (MJ) remove when no longer deleting non-alias ticks from alias objects
    private TransManager _tm;
    private AliasingMover _almv;

    @Inject
    public void inject_(NSL nsl, LocalACL lacl, IPhysicalStorage ps,
            DirectoryService ds, RPC rpc, PrefixVersionControl pvc, NativeVersionControl nvc,
            EmigrantCreator emc, GCCSendContent sendContent, MapAlias2Target a2t, TransManager tm,
            AliasingMover almv)
    {
        _nsl = nsl;
        _lacl = lacl;
        _ps = ps;
        _ds = ds;
        _rpc = rpc;
        _pvc = pvc;
        _nvc = nvc;
        _emc = emc;
        _sendContent = sendContent;
        _a2t = a2t;
        _tm = tm;
        _almv = almv;
    }


    /**
     * @return the response Message received from the remote peer
     */
    public DigestedMessage remoteRequestComponent_(SOCID socid, To src, Token tk)
            throws Exception
    {
        // Several of the version control and physical storage classes require a branch, not socid.
        // We know that downloads will only ever act on the master branch.
        SOCKID sockid = new SOCKID(socid, KIndex.MASTER);
        l.info("send for " + socid);

        Version vKML = _nvc.getKMLVersion_(socid);
        Version vLocal = _nvc.getLocalVersion_(sockid);

        OID target = _a2t.getNullable_(socid.soid());
        if (target != null) {
            // If socid is a locally-aliased object,
            // 1) it should have no local non-alias versions
            // 2) all of its KMLs must be alias ticks
            Version vAllLocal = _nvc.getAllLocalVersions_(socid);
            assert vAllLocal.withoutAliasTicks_().isZero_() : socid + " " + vAllLocal;
            assertRequestedAliasKMLsHaveOnlyAliasTicks(vKML, socid, target, vLocal, vAllLocal);
        }

        PBGetComCall.Builder bd = PBGetComCall.newBuilder()
            .setObjectId(socid.oid().toPB())
            .setComId(socid.cid().getInt())
            .setKmlVersion(vKML.toPB_())
            .setLocalVersion(vLocal.toPB_());
        // TODO (DF): Look into how the receiver uses the localVersion. Should we send all
        // versions?  Does the receiver care for which branch we're sending versions?

        if (socid.cid().equals(CID.CONTENT)) setIncrementalDownloadInfo_(socid, bd);

        PBCore call = CoreUtil.newCall(Type.GET_COM_CALL)
            .setGetComCall(bd).build();

        return _rpc.do_(src, socid.sidx(), call, tk, "gcc " + socid + " " + src);
    }

    /**
     * @param socid is assumed to be aliased to {@code target}
     */
    private void assertRequestedAliasKMLsHaveOnlyAliasTicks(Version vKML, SOCID socid, OID target,
            Version vLocal, Version vAllLocal)
            throws Exception
    {
        Version vKMLNonAlias = vKML.withoutAliasTicks_();
        if (!vKMLNonAlias.isZero_()) {
            // Temporarily migrate the alias non-tick KMLs if they are
            // present in the target object's versions.
            // TODO (MJ) remove this migration and replace with assert (vKMLNonAlias.isZero())
            SOCID socidTarget = new SOCID(socid.sidx(), target, socid.cid());

            String msg =  "(alias " + socid + ")->(" + socidTarget + " target) "
                    + "vkmlnonalias " + vKMLNonAlias + " vLocal " + vLocal;

            Trans t = _tm.begin_();
            try {
                if (vKMLNonAlias.isEntirelyShadowedBy_(_nvc.getAllVersions_(socidTarget))) {
                    // Delete the non-alias versions from the alias, *if* those versions have
                    // already been migrated to the target
                    _nvc.deleteKMLVersionPermanently_(socid, vKMLNonAlias, t);
                    msg = "by delete. " + msg;
                } else {
                    // Migrate the non-alias versions from the alias object to its target
                    _almv.moveKMLVersion_(socid, socidTarget, vAllLocal,
                            _nvc.getAllLocalVersions_(socidTarget), t);
                    msg = "by migration. " + msg;
                }
                t.commit_();
            } catch (Exception e) {
                l.warn(Util.e(e));
                SVClient.logSendDefectAsync(true, "gcc failed repair", e);
                throw e;
            } finally {
                t.end_();
            }

            Version vAllTarget = _nvc.getAllVersions_(socidTarget);
            msg = msg + " vAllTarget " + vAllTarget;

            assert _nvc.getKMLVersion_(socid).withoutAliasTicks_().isZero_() : msg;
            assert vKMLNonAlias.isEntirelyShadowedBy_(vAllTarget) : msg;

            // Report this event to SV, then abort the current request.
            ExAborted e = new ExAborted("Invalid alias KML resolved. Should see this log once. "
                    + msg);
            SVClient.logSendDefectAsync(true, "abort gcc", e);
            throw e;
        }
    }

    private void setIncrementalDownloadInfo_(SOCID socid, Builder bd)
        throws SQLException, IOException, ExNotFound
    {
        OA oa = _ds.getOANullable_(socid.soid());
        if (oa == null) return;
        assert oa.isFile();

        // TODO (DF): is this a reasonable usage of IPhysicalStorage?
        // I can't tell if prefix files should even track branches
        SOCKID branch = new SOCKID(socid, KIndex.MASTER);
        IPhysicalPrefix prefix = _ps.newPrefix_(branch);
        long len = prefix.getLength_();
        if (len == 0) return;

        Version vPre = _pvc.getPrefixVersion_(branch.soid(), branch.kidx());
        l.info("prefix ver " + vPre + " len " + len);

        bd.setPrefixLength(len);
        bd.setPrefixVersion(vPre.toPB_());
    }

    public void processCall_(DigestedMessage msg)
        throws Exception
    {
        Util.checkPB(msg.pb().hasGetComCall(), PBGetComCall.class);
        PBGetComCall pb = msg.pb().getGetComCall();

        SOCKID k = new SOCKID(msg.sidx(), new OID(pb.getObjectId()),
                new CID(pb.getComId()));
        l.info("recv from " + msg.ep() + " for " + k);

        try {
            // Give up if the requested SOCKID is not present locally (either meta or content)
            // N.B. An aliased object is reported not present, but we should not throw if the
            // caller requested meta for a locally-aliased object (hence the second AND block)
            // TODO (MJ) does this mean we should change the definition of isPresent to handle
            // aliased objects?
            if (!_ds.isPresent_(k) &&
                !(k.cid().isMeta() && _ds.hasAliasedOA_(k.soid()))) {
                l.info(k + " not present. Throwing");
                throw new ExNoComponentWithSpecifiedVersion();
            }

            /* check permissions
             *
             * member users may download metadata even if they don't have access
             * to them. this's to ease metadata management on local devices,
             * in particular ACL inheritance, as well as to expedite metadata
             * dissemination among member devices. However, this leads to a
             * security breach as the user may access non-authorized metadata
             * by directly peaking at underlying data stores.
             */
            if (k.cid().isMeta()) {
                // we comment out the code blow to temporarily avoid
                // complexities of 1) NO_PERM processing and 2) issues due to
                // non-member users' inability to store metadata. one issue is
                // that if a user has permission on /a/b but not /a then /a on
                // the user's device has to be handled specially.
                //
//                short ops = ACE.or(ACE.OP_READ_ATTR, ACE.OP_READ_ACL);
//                if (!_dacl.check_(as.user(), k.soid(), ops) &&
//                        !as.store().isMemberUser_(as.user(), c)) {
//                    l.info("no permission & non-member user. Throwing NO_PERM");
//                    reason = Reason.NO_PERM;
//                    return;
//                }
            } else {
                if (!_lacl.check_(msg.user(), k.sidx(), Role.VIEWER)) {
                    l.info("receiver has no permission");
                    throw new ExNoPerm();
                }
            }

            Version vRemote = new Version(pb.getLocalVersion());
            Version vLocal = _nvc.getLocalVersion_(k);

            if (!vLocal.sub_(vRemote).isZero_()) {
                sendReply_(msg, k);
            } else {
                if (l.isInfoEnabled()) {
                    l.info("r " + vRemote + " >= l " + vLocal + ". Throw no_new_update");
                }
                throw new ExNoComponentWithSpecifiedVersion();
            }

            // the kml_version field is used only as a hint for the receiver to
            // know more available versions
            // TODO:
//            Version vRemoteKnown = Version.fromPB(pb.getKmlVersion()).
//                add_(vRemote);
//            Version vKnown = _cd.getKnownVersion_(k.socid());
//
//            if (!vRemoteKnown.sub_(vKnown).isZero_()) {
//                l.info("TODO add diff to db and issue a download");
//            }
        } catch (Exception e) {
            PBCore core = CoreUtil.newReply(msg.pb())
                    // use toPBWithStackTrace to ease debugging.
                    // TODO (WW) change it back to toPB() for security concerns
                    .setExceptionReply(Exceptions.toPBWithStackTrace(e))
                    .build();
            _nsl.sendUnicast_(msg.did(), msg.sidx(), core);
        }
    }


    public void sendReply_(DigestedMessage msg, SOCKID k)
        throws Exception
    {
        l.info("send to " + msg.ep() + " for " + k);

        Version vLocal = _nvc.getLocalVersion_(k);

        PBCore.Builder bdCore = CoreUtil.newReply(msg.pb());

        PBGetComReply.Builder bdReply = PBGetComReply.newBuilder()
            .setVersion(vLocal.toPB_());

        if (k.cid().isMeta()) {
            sendMeta_(msg.did(), k, bdCore, bdReply);
        } else if (k.cid().equals(CID.CONTENT)) {
            _sendContent.send_(msg.ep(), k, bdCore, bdReply, vLocal,
                    msg.pb().getGetComCall().getPrefixLength(),
                    new Version(msg.pb().getGetComCall().getPrefixVersion()));
        } else {
            Util.fatal("unsupported CID: " + k.cid());
        }
    }

    /* TODO
     *  - don't send ACL to non-member devices
     *  - skip local permission checking on these devices
     *  - build ancestors for leaf nodes on these devices
     */
    private void sendMeta_(DID did, SOCKID k, PBCore.Builder bdCore, PBGetComReply.Builder bdReply)
        throws ExNotFound, SQLException, Exception
    {
        // guaranteed by the caller
        assert _ds.isPresent_(k) || _ds.hasAliasedOA_(k.soid());

        OA oa = _ds.getAliasedOANullable_(k.soid());
        assert oa != null : k;

        // verify that the name we send is in Normalized Form C
        assert Normalizer.isNormalized(oa.name(), Form.NFC) : oa + " " + Form.valueOf(oa.name());

        PBMeta.Builder bdMeta = PBMeta.newBuilder()
            .setType(toPB(oa.type()))
            .setParentObjectId(oa.parent().toPB())
            .setName(oa.name())
            .setFlags(oa.flags() & ~OA.FLAGS_LOCAL);

        ////////
        // send alias information

        OID targetOID = _a2t.getNullable_(k.soid());
        if (targetOID != null) {
            assert !k.oid().equals(targetOID); // k is alias here.
            bdMeta.setTargetOid(targetOID.toPB());
            Version vTarget = _nvc.getLocalVersion_(new SOCKID(k.sidx(), targetOID, CID.META));
            bdMeta.setTargetVersion(vTarget.toPB_());
            l.info("Sending target oid: " + targetOID + " target version: "
                   + vTarget + " alias SOCKID: " + k);
        }

        ////////
        // send emigration info

        for (SID sid : _emc.getEmigrantTargetAncestorSIDsForMeta_(oa.parent(), oa.name())) {
            bdMeta.addEmigrantTargetAncestorSid(sid.toPB());
        }

        bdReply.setMeta(bdMeta);

        _nsl.sendUnicast_(did, k.sidx(), bdCore.setGetComReply(bdReply).build());
    }

    private static PBMeta.Type toPB(OA.Type type)
    {
        return PBMeta.Type.valueOf(type.ordinal());
    }
}
