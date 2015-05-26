/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class is responsible for sending NEW_UPDATE messages
 */
public class NewUpdatesSender
{
    private static final Logger l = Loggers.getLogger(NewUpdatesSender.class);

    private final TransportRoutingLayer _trl;
    private final IMapSIndex2SID _sidx2sid;
    private final LocalACL _lacl;
    private final CfgLocalUser _cfgLocalUser;

    private final Set<SIndex> _updated = Sets.newHashSet();
    private final DelayedScheduler _dsNewUpdateMessage;

    @Inject
    public NewUpdatesSender(TransportRoutingLayer trl, IMapSIndex2SID sidx2sid, LocalACL lacl,
            CfgLocalUser cfgLocalUser, CoreScheduler sched)
    {
        _trl = trl;
        _sidx2sid = sidx2sid;
        _lacl = lacl;
        _cfgLocalUser = cfgLocalUser;

        // TODO: refine delay pattern?
        _dsNewUpdateMessage = new DelayedScheduler(sched, DaemonParam.NEW_UPDATE_MESSAGE_DELAY,
                () -> {
                    checkState(!_updated.isEmpty());

                    try {
                        // send a NEW_UPDATE message for all the stores that have been updated
                        // since the last NEW_UPDATE.
                        send_(_updated);
                    } catch (Exception e) {
                        // failed to push.
                        l.warn("ignored: " + Util.e(e));
                    }

                    _updated.clear();
                });
    }

    public void send_(Set<SIndex> ks)
            throws Exception
    {
        for (SIndex sidx : ks) sendForStore_(sidx, null);
    }

    public void sendForStore_(SIndex sidx, @Nullable Long epoch)
            throws Exception
    {
        // see Rule 3 in acl.md
        if (!_lacl.check_(_cfgLocalUser.get(), sidx, Permissions.EDITOR)) {
            l.info("we have no editor perm for {}", sidx);
            return;
        }

        SID sid = _sidx2sid.getThrows_(sidx);
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

    public void schedStoreUpdates(Set<SIndex> s)
    {
        _updated.addAll(s);
        _dsNewUpdateMessage.schedule_();
    }
}