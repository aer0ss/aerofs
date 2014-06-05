package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.migration.IEmigrantTargetSIDLister;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetComponentRequest;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.aerofs.proto.Core.PBMeta;
import com.aerofs.rocklog.RockLog;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.sql.SQLException;

import static com.aerofs.daemon.core.activity.OutboundEventLogger.CONTENT_COMPLETION;
import static com.aerofs.daemon.core.activity.OutboundEventLogger.CONTENT_REQUEST;
import static com.aerofs.daemon.core.activity.OutboundEventLogger.META_REQUEST;

// TODO NAK for this and other primitives

// we split the code for this protocol primitive into classes due to complexity

public class GetComponentRequest
{
    private static final Logger l = Loggers.getLogger(GetComponentRequest.class);

    private IEmigrantTargetSIDLister _emc;
    private PrefixVersionControl _pvc;
    private NativeVersionControl _nvc;
    private MapAlias2Target _a2t;
    private RPC _rpc;
    private DirectoryService _ds;
    private IPhysicalStorage _ps;
    private LocalACL _lacl;
    private TransportRoutingLayer _trl;
    private ComponentContentSender _contentSender;
    private IMapSIndex2SID _sidx2sid;
    private IMapSID2SIndex _sid2sidx;
    private CfgLocalUser _cfgLocalUser;
    private OutboundEventLogger _oel;
    private TransManager _tm;
    private RockLog _rl;

    @Inject
    public void inject_(TransportRoutingLayer trl, LocalACL lacl, IPhysicalStorage ps, OutboundEventLogger oel,
            DirectoryService ds, RPC rpc, PrefixVersionControl pvc, NativeVersionControl nvc,
            IEmigrantTargetSIDLister emc, ComponentContentSender contentSender, MapAlias2Target a2t,
            IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, CfgLocalUser cfgLocalUser,
            TransManager tm, RockLog rl)
    {
        _trl = trl;
        _lacl = lacl;
        _ps = ps;
        _ds = ds;
        _rpc = rpc;
        _pvc = pvc;
        _nvc = nvc;
        _emc = emc;
        _oel = oel;
        _contentSender = contentSender;
        _a2t = a2t;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _cfgLocalUser = cfgLocalUser;
        _tm = tm;
        _rl = rl;
    }

    /**
     * @return the response Message received from the remote peer
     */
    public DigestedMessage remoteRequestComponent_(SOCID socid, DID src, Token tk)
            throws Exception
    {
        abortIfComponentIsAliasedContent(socid);

        l.debug("req gcc for {}", socid);

        // NB: we send all local versions to allow the receiver to pick a branch that
        // has ticks we do not have locally (i.e. a branch whose version is not dominated
        // by our local versions)
        PBGetComponentRequest.Builder bd = PBGetComponentRequest
                .newBuilder()
                .setStoreId(_sidx2sid.getThrows_(socid.sidx()).toPB())
                .setObjectId(socid.oid().toPB())
                .setComId(socid.cid().getInt())
                .setLocalVersion(_nvc.getAllLocalVersions_(socid).toPB_());

        if (socid.cid().equals(CID.CONTENT)) {
            setIncrementalDownloadInfo_(socid, bd);
        }

        PBCore request = CoreProtocolUtil.newRequest(Type.GET_COMPONENT_REQUEST).setGetComponentRequest(
                bd).build();

        return _rpc.issueRequest_(src, request, tk, "gcc " + socid + " " + src);
    }


    /**
     * @throws ExAborted if socid is content for an aliased object
     * N.B. also asserts on version invariants for an aliased socid
     */
    private void abortIfComponentIsAliasedContent(SOCID socid)
            throws SQLException, ExAborted
    {
        if (!_a2t.isAliased_(socid.soid())) return;

        // socid is a locally-aliased object

        // all of its local versions must be alias ticks
        final Version vAllLocal = _nvc.getAllLocalVersions_(socid);
        assert vAllLocal.withoutAliasTicks_().isZero_() : socid + " " + vAllLocal;

        final Version vKML = _nvc.getKMLVersion_(socid);
        if (socid.cid().isMeta()) {
            // Aliased meta should only have alias ticks
            assert vKML.withoutAliasTicks_().isZero_() : socid + " " + vKML;
        } else {
            assert (socid.cid().equals(CID.CONTENT)) : socid;

            // Aliased content should have *no* ticks.
            // Furthermore, if we get here it's because we *just* aliased the meta-data and
            // didn't abort the Download loop. Should abort the request, since any content
            // should be downloaded for the target object
            assert vKML.isZero_() : socid + " " + vKML;
            throw new ExAborted("empty KML for aliased " + socid);
        }
    }

    private void setIncrementalDownloadInfo_(SOCID socid, PBGetComponentRequest.Builder bd)
            throws SQLException, ExNotFound
    {
        OA oa = _ds.getOANullable_(socid.soid());
        if (oa == null) return;
        assert oa.isFile();

        SOCKID branch = new SOCKID(socid, KIndex.MASTER);

        ContentHash h = oa.caMasterNullable() != null ? _ds.getCAHash_(branch.sokid()) : null;
        if (h != null) {
            bd.setHashContent(h.toPB());
            l.info("advertise hash in gcc {}", socid);
        }

        // TODO (DF): is this a reasonable usage of IPhysicalStorage?
        // I can't tell if prefix files should even track branches
        IPhysicalPrefix prefix = _ps.newPrefix_(branch, null);
        long len = prefix.getLength_();
        if (len == 0) return;

        Version vPre = _pvc.getPrefixVersion_(branch.soid(), branch.kidx());
        l.debug("prefix ver {} len {}", vPre, len);

        bd.setPrefixLength(len);
        bd.setPrefixVersion(vPre.toPB_());
    }

    /**
     * When requesting content, attempt to find a local branch that is not dominated
     * by the version advertised by the caller.
     *
     * Returns MASTER for META requests or when no such branch is found.
     *
     * This allows all devices to propagate all content branches, instead of constraining
     * each device to propagate only its MASTER branch.
     */
    private KIndex findBranchNotDominatedBy_(SOCID socid, Version vRemote) throws SQLException
    {
        if (socid.cid().isMeta()) return KIndex.MASTER;
        OA oa = _ds.getOANullable_(socid.soid());
        if (oa == null) return KIndex.MASTER;
        for (KIndex kidx : oa.cas().keySet()) {
            Version v = _nvc.getLocalVersion_(new SOCKID(socid, kidx));
            if (!v.isDominatedBy_(vRemote)) return kidx;
        }
        return KIndex.MASTER;
    }

    public void processRequest_(DigestedMessage msg)
        throws Exception
    {
        l.debug("{} process incoming gcc request over {}", msg.did(), msg.tp());

        Util.checkPB(msg.pb().hasGetComponentRequest(), PBGetComponentRequest.class);
        PBGetComponentRequest request = msg.pb().getGetComponentRequest();

        SID sid = new SID(request.getStoreId());
        SIndex sidx = _sid2sidx.getThrows_(sid);

        // see Rule 3 in acl.md
        if (!_lacl.check_(_cfgLocalUser.get(), sidx, Permissions.EDITOR)) {
            l.info("{} we have no editor perm for {}", msg.did(), sidx);
            throw new ExSenderHasNoPerm();
        }

        // see Rule 1 in acl.md
        if (!_lacl.check_(msg.user(), sidx, Permissions.VIEWER)) {
            l.warn("{} ({}) has no viewer perm for {}", msg.did(), msg.user(), sidx);
            throw new ExNoPerm();
        }

        SOCID socid = new SOCID(sidx, new OID(request.getObjectId()), new CID(request.getComId()));
        Version vRemote = Version.fromPB(request.getLocalVersion());
        SOCKID k = new SOCKID(socid, findBranchNotDominatedBy_(socid, vRemote));
        l.info("{} receive gcc request for {} {} over {}", msg.did(), k, vRemote, msg.tp());

        // Give up if the requested SOCKID is not present locally (either meta or content)
        // N.B. An aliased object is reported not present, but we should not throw if the
        // caller requested meta for a locally-aliased object (hence the second AND block)
        // TODO (MJ) does this mean we should change the definition of isPresent to handle
        // aliased objects?
        if (!_ds.isPresent_(k) &&
                !(k.cid().isMeta() && _ds.hasAliasedOA_(k.soid()))) {
            l.debug("{} {} not present", msg.did(), k);
            throw new ExNoComponentWithSpecifiedVersion();
        }

        Version vLocal = _nvc.getLocalVersion_(k);
        if (vLocal.isDominatedBy_(vRemote)) {
            l.debug("{} r {} >= l {}", msg.did(), vRemote, vLocal);
            throw new ExNoComponentWithSpecifiedVersion();
        }
        sendResponse_(msg, k, vLocal);
    }

    // Mark it as public only to facilitate testing
    public void sendResponse_(DigestedMessage msg, SOCKID k, Version vLocal)
        throws Exception
    {
        l.debug("{} issue gcc response for {} over {}", msg.did(), k, msg.tp());

        _oel.log_(k.cid().isMeta() ? META_REQUEST : CONTENT_REQUEST, k.soid(), msg.did());

        PBCore.Builder bdCore = CoreProtocolUtil.newResponse(msg.pb());

        PBGetComponentResponse.Builder bdResponse = PBGetComponentResponse
                .newBuilder()
                .setVersion(vLocal.toPB_());

        PBGetComponentRequest request = msg.pb().getGetComponentRequest();

        ContentHash h = request.hasHashContent() ? new ContentHash(request.getHashContent()) : null;

        if (k.cid().isMeta()) {
            sendMeta_(msg.ep(), k, bdCore, bdResponse);
        } else if (k.cid().equals(CID.CONTENT)) {
            ContentHash contentHash = _contentSender.send_(
                    msg.ep(),
                    k,
                    bdCore,
                    bdResponse,
                    vLocal,
                    request.getPrefixLength(),
                    Version.fromPB(request.getPrefixVersion()),
                    h);
            if (contentHash != null) updateHash_(k.sokid(), contentHash);
        } else {
            SystemUtil.fatal("unsupported CID: " + k.cid());
        }

        if (k.cid().isContent()) _oel.log_(CONTENT_COMPLETION, k.soid(), msg.did());
    }

    private void updateHash_(SOKID sokid, @Nonnull ContentHash h)
    {
        try {
            ContentHash db = _ds.getCAHash_(sokid);
            l.debug("hash {} {} {}", sokid, db, h);
            if (db != null) {
                if (!db.equals(h)) {
                    // It would be unsafe to overwrite a conflicting hash value from here.
                    //
                    // It's possible the file was updated while we were sending the last chunk,
                    // in which case the db would actually be more up-to-date.
                    //
                    // It's also possible for the "transfer" hash to be more up-to-date if a file
                    // was written to and the application doing the writing prevented the timestamp
                    // from changing, in which case we should force a linker/scanner update somehow.
                    //
                    // Both seem fairly unlikely though so a good first step is to simply be log and
                    // send a rocklog defect.
                    // TODO: force linker update or something?
                    l.info("hash mismatch {} {} {}", sokid, db, h);
                    _rl.newDefect("gcc.hash.mismatch")
                            .addData("sokid", sokid)
                            .addData("db_hash", db)
                            .addData("fs_hash", h)
                            .send();
                }
                return;
            }
            Trans t = _tm.begin_();
            try {
                _ds.setCAHash_(sokid, h, t);
                t.commit_();
            } finally {
                t.end_();
            }
        } catch (Exception e) {
            l.warn("failed to update ca hash", e);
        }
    }

    /* TODO
     *  - don't send ACL to non-member devices
     *  - skip local permission checking on these devices
     *  - build ancestors for leaf nodes on these devices
     */
    private void sendMeta_(Endpoint ep, SOCKID k, PBCore.Builder bdCore, PBGetComponentResponse.Builder bdResponse)
        throws Exception
    {
        // guaranteed by the caller
        assert _ds.isPresent_(k) || _ds.hasAliasedOA_(k.soid());

        OA oa = _ds.getAliasedOANullable_(k.soid());
        assert oa != null : k;

        PBMeta.Builder bdMeta = PBMeta.newBuilder()
            .setType(toPB(oa.type()))
            .setParentObjectId(oa.parent().toPB())
            .setName(oa.name())
            .setFlags(0);

        ////////
        // send alias information

        OID targetOID = _a2t.getNullable_(k.soid());
        if (targetOID != null) {
            assert !k.oid().equals(targetOID); // k is alias here.
            bdMeta.setTargetOid(targetOID.toPB());
            Version vTarget = _nvc.getLocalVersion_(new SOCKID(k.sidx(), targetOID, CID.META));
            bdMeta.setTargetVersion(vTarget.toPB_());
            l.debug("{} send target oid: {} target version: {} alias SOCKID: {} over {}", ep.did(), targetOID, vTarget, k, ep.tp());
        }

        ////////
        // send emigration info

        for (SID sid : _emc.getEmigrantTargetAncestorSIDsForMeta_(oa.parent(), oa.name())) {
            bdMeta.addEmigrantTargetAncestorSid(sid.toPB());
        }

        bdResponse.setMeta(bdMeta);

        _trl.sendUnicast_(ep, bdCore.setGetComponentResponse(bdResponse).build());
    }

    private static PBMeta.Type toPB(OA.Type type)
    {
        return PBMeta.Type.valueOf(type.ordinal());
    }
}
