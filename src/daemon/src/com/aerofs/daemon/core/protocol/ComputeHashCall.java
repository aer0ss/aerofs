/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.net.RPC;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBComputeHashCall;
import com.google.inject.Inject;
import org.slf4j.Logger;


public class ComputeHashCall
{
    private static Logger l = Loggers.getLogger(ComputeHashCall.class);
    private final RPC _rpc;
    private final NSL _nsl;
    private final Hasher _hasher;
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final TokenManager _tokenManager;

    @Inject
    public ComputeHashCall(NativeVersionControl nvc, DirectoryService ds, Hasher hasher, RPC rpc,
            TokenManager tokenManager, NSL nsl)
    {
        _nvc = nvc;
        _ds = ds;
        _hasher = hasher;
        _rpc = rpc;
        _nsl = nsl;
        _tokenManager = tokenManager;
    }

    public void rpc_(SOID soid, Version vRemote, DID did, Token tk)
            throws Exception
    {
        PBComputeHashCall.Builder bd = PBComputeHashCall.newBuilder()
                .setObjectId(soid.oid().toPB())
                .setRemoteVersion(vRemote.toPB_());

        PBCore call = CoreUtil.newCall(Type.COMPUTE_HASH_CALL)
                .setComputeHashCall(bd).build();

        _rpc.do_(did, soid.sidx(), call, tk, "computeHashCall " + soid);
    }

    public void sendReply_(DigestedMessage msg, SOCKID k) throws Exception
    {
        // Compute hash
        Token tk = _tokenManager.acquireThrows_(Cat.SERVER, "CHCSendReply");
        try {
            _hasher.computeHash_(k.sokid(), true, tk);
        } finally {
            tk.reclaim_();
        }
        // Reply that the hash has been computed with an empty message
        PBCore core = CoreUtil.newReply(msg.pb()).build();
        _nsl.sendUnicast_(msg.did(), msg.sidx(), core);
    }

    public void processCall_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasComputeHashCall(), PBComputeHashCall.class);
        PBComputeHashCall pb = msg.pb().getComputeHashCall();

        SOID soid = new SOID(msg.sidx(), new OID(pb.getObjectId()));

        Version vRemote = Version.fromPB(pb.getRemoteVersion());

        for (KIndex kIdx: _ds.getOAThrows_(soid).cas().keySet()) {
            SOCKID k = new SOCKID(soid, CID.CONTENT, kIdx);
            Version vLocal = _nvc.getLocalVersion_(k);

            // There should be only 1 match
            if (vLocal.equals(vRemote)) {
                sendReply_(msg, k);
                return;
            }
        }

        l.debug("No matching version. Throwing NOT_FOUND");
        throw new ExNotFound();
    }
}
