package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.collector.SenderFilters;
import com.aerofs.daemon.core.collector.SenderFilters.SenderFilterAndIndex;
import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.store.*;
import com.aerofs.daemon.transport.lib.OutgoingStream;
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
    @Inject private Metrics _m;

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

        int count = msg.pb().getGetFilterRequest().getCount();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CoreProtocolUtil.newResponse(msg.pb())
                .setGetFilterResponse(PBGetFilterResponse.newBuilder()
                        .setCount(count)
                        .build())
                .build()
                .writeDelimitedTo(out);

        OutgoingStream os = null;
        int maxUcast = _m.getMaxUnicastSize_();

        for (int i = 0; i < count; ++i) {
            PBGetFilterRequest.Store req = PBGetFilterRequest.Store.parseDelimitedFrom(msg.is());
            boolean fromBase = req.getFromBase();
            SID sid = new SID(BaseUtil.fromPB(req.getStoreId()));
            SIndex sidx = _sid2sidx.getNullable_(sid);

            PBGetFilterResponse.Store.Builder bd = PBGetFilterResponse.Store.newBuilder()
                    .setStoreId(req.getStoreId());

            if (sidx == null) {
                l.warn("{} gf request for absent {} {}", from, sid, _sid2sidx.getLocalOrAbsentNullable_(sid));
                bd.setEx(Exceptions.toPB(new ExNotFound()));
            } else if (!_lacl.check_(msg.user(), sidx, Permissions.VIEWER)) {
                l.warn("{} gf request wo/ viewer perm ({}) for {}", from, msg.user(), sidx);
                bd.setEx(Exceptions.toPB(new ExNoPerm()));
            } else {
                Store s = _sidx2s.getThrows_(sidx);
                SenderFilterAndIndex sfi = s.iface(SenderFilters.class).get_(from, fromBase);
                long c = Objects.firstNonNull(_cedb.getContentChangeEpoch_(sidx), 0L);

                l.debug("{} send gf response for {} fs {}", from, sidx, (sfi == null ? null : sfi._filter));

                if (sfi != null) {
                    bd.setSenderFilter(sfi._filter.toPB())
                            .setSenderFilterIndex(sfi._sfidx.getLong())
                            .setSenderFilterUpdateSeq(sfi._updateSeq)
                            .setSenderFilterEpoch(c)
                            .setFromBase(fromBase);
                }
            }

            PBGetFilterResponse.Store rs = bd.build();
            if (out.size() + C.INTEGER_SIZE + rs.getSerializedSize() > maxUcast) {
                if (os == null) {
                    os = msg.ep().tp().newOutgoingStream(from);
                }
                os.write(out.toByteArray());
                out = new ByteArrayOutputStream();
            }
            rs.writeDelimitedTo(out);
        }
        if (out.size() > 0) {
            if (os != null) {
                os.write(out.toByteArray());
            } else {
                _trl.sendUnicast_(msg.ep(), CoreProtocolUtil.typeString(Type.REPLY),
                        msg.pb().getRpcid(), out);
            }
        }
    }
}
