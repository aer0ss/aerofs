package com.aerofs.daemon.core.net.proto;

import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.collector.SenderFilterIndex;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBUpdateSenderFilter;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.inject.Inject;

public class UpdateSenderFilter
{
    private final NSL _nsl;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public UpdateSenderFilter(NSL nsl, MapSIndex2Store sidx2s)
    {
        _nsl = nsl;
        _sidx2s = sidx2s;
    }

    public void send_(SIndex sidx, long sfidx, long updateSeq, DID did) throws Exception
    {
        PBCore pb = CoreUtil.newCore(Type.UPDATE_SENDER_FILTER)
            .setUpdateSenderFilter(PBUpdateSenderFilter.newBuilder()
                    .setSenderFilterIndex(sfidx)
                    .setSenderFilterUpdateSeq(updateSeq))
                    .build();
        _nsl.sendUnicast_(did, sidx, pb);
    }

    public void process_(DigestedMessage msg) throws Exception
    {
        Util.checkPB(msg.pb().hasUpdateSenderFilter(),
                PBUpdateSenderFilter.class);

        PBUpdateSenderFilter sf = msg.pb().getUpdateSenderFilter();
        _sidx2s.getThrows_(msg.sidx()).senderFilters().update_(msg.did(), new SenderFilterIndex(
                sf.getSenderFilterIndex()), sf.getSenderFilterUpdateSeq());
    }
}
