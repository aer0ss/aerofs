/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBComputeHashRequest;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.inject.Inject;
import org.slf4j.Logger;


public class ComputeHash
{
    private static Logger l = Loggers.getLogger(ComputeHash.class);
    private final RPC _rpc;
    private final TransportRoutingLayer _trl;
    private final Hasher _hasher;
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final TokenManager _tokenManager;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public ComputeHash(
            NativeVersionControl nvc,
            DirectoryService ds,
            Hasher hasher,
            RPC rpc,
            TokenManager tokenManager,
            TransportRoutingLayer trl,
            IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx)
    {
        _nvc = nvc;
        _ds = ds;
        _hasher = hasher;
        _rpc = rpc;
        _trl = trl;
        _tokenManager = tokenManager;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    public void issueRequest_(SOID soid, Version vRemote, DID did, Token tk)
            throws Exception
    {
        PBComputeHashRequest.Builder bd = PBComputeHashRequest
                .newBuilder()
                .setStoreId(BaseUtil.toPB(_sidx2sid.getThrows_(soid.sidx())))
                .setObjectId(BaseUtil.toPB(soid.oid()))
                .setRemoteVersion(vRemote.toPB_());

        PBCore request = CoreProtocolUtil
                .newRequest(Type.COMPUTE_HASH_REQUEST)
                .setComputeHashRequest(bd)
                .build();

        _rpc.issueRequest_(did, request, tk, "ComputeHashRequest " + soid);
    }

    public void sendResponse_(DigestedMessage msg, SOCKID k) throws Exception
    {
        // Compute hash
        try (Token tk = _tokenManager.acquireThrows_(Cat.SERVER, "ComputeHashResponse")) {
            _hasher.computeHash_(k.sokid(), true, tk);
        }
        // Reply that the hash has been computed with an empty message
        PBCore response = CoreProtocolUtil.newResponse(msg.pb()).build();
        _trl.sendUnicast_(msg.ep(), response);
    }

    public void processRequest_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasComputeHashRequest(), PBComputeHashRequest.class);
        PBComputeHashRequest request = msg.pb().getComputeHashRequest();

        SID sid = new SID(BaseUtil.fromPB(request.getStoreId()));
        SOID soid = new SOID(_sid2sidx.getThrows_(sid), new OID(BaseUtil.fromPB(request.getObjectId())));

        Version vRemote = Version.fromPB(request.getRemoteVersion());
        if (!vRemote.isNonAliasOnly_()) {
            l.warn("hash request for invalid version {} {}", soid, vRemote);
        }

        for (KIndex kIdx: _ds.getOAThrows_(soid).cas().keySet()) {
            SOCKID k = new SOCKID(soid, CID.CONTENT, kIdx);
            Version vLocal = _nvc.getLocalVersion_(k);

            // There should be only 1 match
            if (vLocal.equals(vRemote)) {
                sendResponse_(msg, k);
                return;
            }
        }

        l.debug("No matching version. Throwing NOT_FOUND");
        throw new ExNotFound();
    }
}
