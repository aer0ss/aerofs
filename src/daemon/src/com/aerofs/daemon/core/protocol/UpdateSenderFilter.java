package com.aerofs.daemon.core.protocol;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.collector.SenderFilterIndex;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBUpdateSenderFilter;
import com.google.inject.Inject;

import java.sql.SQLException;

public class UpdateSenderFilter
{
    private final TransportRoutingLayer _trl;
    private final MapSIndex2Store _sidx2s;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;


    @Inject
    public UpdateSenderFilter(TransportRoutingLayer trl, MapSIndex2Store sidx2s, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx)
    {
        _trl = trl;
        _sidx2s = sidx2s;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    public void send_(SIndex sidx, long sfidx, long updateSeq, DID did) throws Exception
    {
        PBCore pb = CoreProtocolUtil.newCoreMessage(Type.UPDATE_SENDER_FILTER)
            .setUpdateSenderFilter(PBUpdateSenderFilter.newBuilder()
                    .setStoreId(_sidx2sid.getThrows_(sidx).toPB())
                    .setSenderFilterIndex(sfidx)
                    .setSenderFilterUpdateSeq(updateSeq))
                    .build();
        _trl.sendUnicast_(did, pb);
    }

    public void process_(DigestedMessage msg)
            throws ExProtocolError, ExNotFound, SQLException
    {
        Util.checkPB(msg.pb().hasUpdateSenderFilter(),
                PBUpdateSenderFilter.class);

        PBUpdateSenderFilter sf = msg.pb().getUpdateSenderFilter();
        SIndex sidx = _sid2sidx.getThrows_(new SID(sf.getStoreId()));
        _sidx2s.getThrows_(sidx).senderFilters().update_(msg.did(), new SenderFilterIndex(
                sf.getSenderFilterIndex()), sf.getSenderFilterUpdateSeq());
    }
}
