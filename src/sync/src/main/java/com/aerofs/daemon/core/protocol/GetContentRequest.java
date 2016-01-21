package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExUpdateInProgress;
import com.aerofs.daemon.core.net.CoreProtocolReactor;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.*;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBGetContentRequest;
import com.aerofs.proto.Core.PBGetContentResponse;
import com.google.protobuf.LeanByteString;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.sql.SQLException;

import static com.aerofs.daemon.core.activity.OutboundEventLogger.CONTENT_COMPLETION;
import static com.aerofs.daemon.core.activity.OutboundEventLogger.CONTENT_REQUEST;


public class GetContentRequest implements CoreProtocolReactor.Handler {
    private static final Logger l = Loggers.getLogger(GetContentRequest.class);

    @Inject private PrefixVersionControl _pvc;
    @Inject private RPC _rpc;
    @Inject private LocalACL _lacl;
    @Inject private IPhysicalStorage _ps;
    @Inject private TransportRoutingLayer _trl;
    @Inject private ContentProvider _provider;
    @Inject private ContentSender _contentSender;
    @Inject private IMapSIndex2SID _sidx2sid;
    @Inject private IMapSID2SIndex _sid2sidx;
    @Inject private CfgLocalUser _cfgLocalUser;
    @Inject private TransManager _tm;
    @Inject private ChangeEpochDatabase _cedb;
    @Inject private CentralVersionDatabase _cvdb;
    @Inject private OutboundEventLogger _oel;

    @Override
    public Type message() {
        return Type.GET_CONTENT_REQUEST;
    }

    /**
     * @return the response Message received from the remote peer
     */
    public DigestedMessage remoteRequestContent_(SOID soid, DID src, Token tk)
            throws Exception {
        l.debug("req gcc for {}", soid);

        // NB: we send all local versions to allow the receiver to pick a branch that
        // has ticks we do not have locally (i.e. a branch whose version is not dominated
        // by our local versions)
        PBGetContentRequest.Builder bd = PBGetContentRequest
                .newBuilder()
                .setStoreId(BaseUtil.toPB(_sidx2sid.getThrows_(soid.sidx())))
                .setObjectId(BaseUtil.toPB(soid.oid()));

        Long v = _cvdb.getVersion_(soid.sidx(), soid.oid());
        bd.setLocalVersion(v != null ? v : -1);

        if (v == null) {
            try {
                SendableContent c = _provider.content(new SOKID(soid, KIndex.MASTER));
                if (c.hash == null) {
                    // local file present but not yet hashed
                    // it is possible that once hashed it will be found to match one of the remote
                    // content entries,and we don't want to waste bandwidth re-downloading data
                    // already present locally, especially when unlinking/reinstalling as the
                    // transfer notifications causes much confusion
                    throw new ExAborted("wait for hash " + soid);
                }
            } catch (ExNotFound e) {}
        }

        PBGetContentRequest.Prefix prefix = getIncrementalDownloadInfo_(soid);
        if (prefix != null) bd.setPrefix(prefix);

        PBCore request = CoreProtocolUtil.newRequest(Type.GET_CONTENT_REQUEST)
                .setGetContentRequest(bd).build();

        return _rpc.issueRequest_(src, request, tk, "gcc " + soid + " " + src);
    }

    private PBGetContentRequest.Prefix getIncrementalDownloadInfo_(SOID soid)
            throws SQLException, ExNotFound {
        SOKID branch = new SOKID(soid, KIndex.MASTER);
        IPhysicalPrefix prefix = _ps.newPrefix_(branch, null);
        long len = prefix.getLength_();
        if (len == 0) return null;
        byte[] hashState = prefix.hashState_();
        if (hashState == null) return null;
        Long vPre = _pvc.getPrefixVersion_(branch.soid(), branch.kidx()).unwrapCentral();
        if (vPre == null) return null;
        l.info("prefix ver {} len {}", vPre, len);
        PBGetContentRequest.Prefix.Builder bd = PBGetContentRequest.Prefix.newBuilder();
        bd.setLength(len);
        bd.setVersion(vPre);
        bd.setHashState(new LeanByteString(hashState));
        return bd.build();
    }

    @Override
    public void handle_(DigestedMessage msg) throws Exception {
        try {
            processRequest_(msg);
        } catch (Exception e) {
            l.warn("{} fail process msg cause:{}", msg.did(), CoreProtocolUtil.typeString(msg.pb()),
                    BaseLogUtil.suppress(e,
                            ExUpdateInProgress.class,
                            ExNoComponentWithSpecifiedVersion.class));
            _trl.sendUnicast_(msg.ep(), CoreProtocolUtil.newErrorResponse(msg.pb(), e));
        }
    }

    public void processRequest_(DigestedMessage msg)
            throws Exception {
        l.debug("{} process incoming gcc request over {}", msg.did(), msg.tp());

        Util.checkPB(msg.pb().hasGetContentRequest(), PBGetContentRequest.class);
        PBGetContentRequest request = msg.pb().getGetContentRequest();

        SID sid = new SID(BaseUtil.fromPB(request.getStoreId()));

        // TODO: share ACL checks with legacy codepath?
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

        OID oid = new OID(BaseUtil.fromPB(request.getObjectId()));
        long rcv = request.getLocalVersion();

        SOID soid = new SOID(sidx, oid);

        Long lcv = _cvdb.getVersion_(sidx, oid);
        if (lcv == null || lcv < rcv) {
            // avoid sending a permanent error if case the remote peer is racing against a
            // response from polaris
            if (_provider.hasUnacknowledgedLocalChange(soid)) {
                l.debug("{} {} r {} local change in progress", msg.did(), soid, rcv);
                throw new ExUpdateInProgress();
            } else {
                l.debug("{} {} r {} >= {} l", msg.did(), soid, rcv, lcv);
                throw new ExNoComponentWithSpecifiedVersion();
            }
        }

        KIndex kidx = _provider.pickBranch(soid);
        sendResponse_(msg, new SOKID(soid, kidx), lcv);
    }

    // Mark it as public only to facilitate testing
    public void sendResponse_(DigestedMessage msg, SOKID k, Long vLocal)
            throws Exception {
        l.debug("{} issue gcc response for {} over {}", msg.did(), k, msg.tp());

        _oel.log_(CONTENT_REQUEST, k.soid(), msg.did());

        PBCore.Builder bdCore = CoreProtocolUtil.newResponse(msg.pb());
        PBGetContentResponse.Builder bd = PBGetContentResponse.newBuilder();
        bd.setVersion(vLocal);
        bd.setLts(_cedb.getContentChangeEpoch_(k.sidx()));

        PBGetContentRequest.Prefix prefix = msg.pb().getGetContentRequest().getPrefix();
        if (prefix != null) {
            l.info("recved prefix len {} v {}. local {}",
                    prefix.getLength(), prefix.getVersion(), vLocal);
        }
        if (prefix != null && vLocal != prefix.getVersion()) {
            prefix = null;
        }

        SendableContent c = _provider.content(k);
        ContentHash h = _contentSender.send_(msg.ep(), c, prefix, bdCore, bd);
        if (!h.equals(c.hash)) {
            // well, shit.
            // The hash mismatch might be a transient race condition that will be resolved shortly
            // by filesystem notifications. It may however be a sign of something more sinister,
            // like broken filesystem notifications, or aggravating users making multiple changes
            // to the same file within the same second without changing the size.
            // To be on the safe side we need to re-hash the file
            c.pf.onContentHashMismatch_();
        }

        _oel.log_(CONTENT_COMPLETION, k.soid(), msg.did());
    }
}
