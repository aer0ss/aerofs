/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;

/**
 * This class is responsible for sending NEW_UPDATE messages
 */
public class NewUpdatesSender
{
    private static final Logger l = Loggers.getLogger(NewUpdatesSender.class);

    private final TransportRoutingLayer _trl;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public NewUpdatesSender(TransportRoutingLayer trl, IMapSIndex2SID sidx2sid)
    {
        _trl = trl;
        _sidx2sid = sidx2sid;
    }

    public void sendForStore_(SIndex sidx, @Nullable Long epoch)
            throws Exception
    {
        SID sid = _sidx2sid.getNullable_(sidx);
        if (sid == null) {
            l.info("{} no longer present", sidx);
            return;
        }

        PBNewUpdates.Builder bd = PBNewUpdates.newBuilder().setStoreId(BaseUtil.toPB(sid));
        if (epoch != null) bd.setChangeEpoch(epoch);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        CoreProtocolUtil.newCoreMessage(Type.NEW_UPDATES)
                .setNewUpdates(bd.build())
                .build()
                .writeDelimitedTo(os);

        // TODO: use epidemic propagation instead of maxcast
        // (part of a bigger effort towards a quieter steady-state of AntiEntropy)
        _trl.sendMaxcast_(sid, String.valueOf(Type.NEW_UPDATES.getNumber()),
                CoreProtocolUtil.NOT_RPC, os);
    }
}
