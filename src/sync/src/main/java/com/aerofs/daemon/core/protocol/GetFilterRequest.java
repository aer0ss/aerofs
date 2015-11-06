package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.collector.SenderFilters.SenderFilterAndIndex;
import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.store.*;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.*;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;

public class GetFilterRequest implements CoreProtocolReactor.Handler
{
    private static final Logger l = Loggers.getLogger(GetFilterRequest.class);

    @Inject private TransportRoutingLayer _trl;
    @Inject private MapSIndex2Store _sidx2s;
    @Inject private IMapSID2SIndex _sid2sidx;
    @Inject private LocalACL _lacl;
    @Inject private CfgLocalUser _cfgLocalUser;
    @Inject private ChangeEpochDatabase _cedb;

    @Override
    public Type message() {
        return Type.GET_FILTER_REQUEST;
    }

    @Override
    public void handle_(DigestedMessage msg) throws Exception {
        try {
            processRequest_(msg);
        } catch (Exception e) {
            l.warn("{} fail process gf cause:", msg.did(), e);
            _trl.sendUnicast_(msg.ep(), CoreProtocolUtil.newErrorResponse(msg.pb(), e));
        }
    }

    public void processRequest_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasGetFilterRequest(), PBGetFilterRequest.class);

        DID from = msg.did();
        PBGetFilterRequest req = msg.pb().getGetFilterRequest();
        boolean fromBase = req.getFromBase();
        SID sid = new SID(BaseUtil.fromPB(req.getStoreId()));
        SIndex sidx = _sid2sidx.getNullable_(sid);

        // ignore store that is not locally present
        if (sidx == null) {
            l.warn("{} gf request for absent {} {}", from, sid, _sid2sidx.getLocalOrAbsentNullable_(sid));
            throw new ExNotFound();
        }

        // see Rule 1 in acl.md
        if (!_lacl.check_(msg.user(), sidx, Permissions.VIEWER)) {
            l.warn("{} ({}) has no viewer perm for {}", from, msg.user(), sidx);
            throw new ExNoPerm();
        }

        l.debug("{} receive gf request for {} {}", from, sidx, fromBase);

        Store s = _sidx2s.getThrows_(sidx);
        SenderFilterAndIndex sfi = s.iface(SenderFilters.class).get_(from, fromBase);
        long c = Objects.firstNonNull(_cedb.getContentChangeEpoch_(sidx), 0L);

        l.debug("{} send gf response for {} fs {}", from, sidx, (sfi == null ? null : sfi._filter));

        PBGetFilterResponse.Builder bd = PBGetFilterResponse.newBuilder();
        if (sfi != null) {
            bd.setSenderFilter(sfi._filter.toPB())
                    .setSenderFilterIndex(sfi._sfidx.getLong())
                    .setSenderFilterUpdateSeq(sfi._updateSeq)
                    .setSenderFilterEpoch(c)
                    .setFromBase(fromBase);
        }

        PBCore reply = CoreProtocolUtil.newResponse(msg.pb())
                .setGetFilterResponse(bd).build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        reply.writeDelimitedTo(os);
        _trl.sendUnicast_(msg.ep(), CoreProtocolUtil.typeString(reply), reply.getRpcid(), os);
    }
}
