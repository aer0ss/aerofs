package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.AntiEntropy;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.NativeVersionControl.IVersionControlListener;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Core.PBNewUpdates;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class is responsible for sending and receiving NEW_UPDATE messages
 */
public class NewUpdates implements IVersionControlListener
{
    private static final Logger l = Loggers.getLogger(NewUpdates.class);

    private TransportRoutingLayer _trl;
    private IMapSIndex2SID _sidx2sid;
    private IMapSID2SIndex _sid2sidx;
    private LocalACL _lacl;
    private CfgLocalUser _cfgLocalUser;
    private AntiEntropy _ae;

    private final Set<SIndex> _updated = Sets.newHashSet();
    private DelayedScheduler _dsNewUpdateMessage;

    @Inject
    public void inject_(TransportRoutingLayer trl, NativeVersionControl nvc,
            IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, LocalACL lacl,
            CfgLocalUser cfgLocalUser, CoreScheduler sched, AntiEntropy ae)
    {
        _trl = trl;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _lacl = lacl;
        _cfgLocalUser = cfgLocalUser;
        _ae = ae;

        nvc.addListener_(this);

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

    public void send_(Collection<SIndex> ks)
        throws Exception
    {
        for (SIndex sidx : ks) sendForStore_(sidx);
    }

    private void sendForStore_(SIndex sidx)
            throws Exception
    {
        // see Rule 3 in acl.md
        if (!_lacl.check_(_cfgLocalUser.get(), sidx, Permissions.EDITOR)) {
            l.info("we have no editor perm for {}", sidx);
            return;
        }

        SID sid = _sidx2sid.getThrows_(sidx);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        CoreProtocolUtil.newCoreMessage(Type.NEW_UPDATES)
                .setNewUpdates(PBNewUpdates.newBuilder()
                        .setStoreId(sid.toPB()))
                .build()
                .writeDelimitedTo(os);

        // TODO: use epidemic propagation instead of maxcast
        // (part of a bigger effort towards a quieter steady-state of AntiEntropy)
        _trl.sendMaxcast_(sid, String.valueOf(Type.NEW_UPDATES.getNumber()),
                CoreProtocolUtil.NOT_RPC, os);
    }

    public void process_(DigestedMessage msg)
            throws ExNotFound, SQLException, IOException, ExProtocolError
    {
        l.debug("{} process incoming nu over {}", msg.did(), msg.tp());

        if (!msg.pb().hasNewUpdates()) throw new ExProtocolError();
        SIndex sidx = _sid2sidx.getThrows_(new SID(msg.pb().getNewUpdates().getStoreId()));

        // see Rule 2 in acl.md. Note that the maxcast sender can forge the device id
        // (unless maxcast messages are signed). therefore this is not a security measure.
        // see more in acl.md.
        if (!_lacl.check_(msg.user(), sidx, Permissions.EDITOR)) {
            l.warn("{} ({}) on {} has no editor perm for {}", msg.did(), msg.user(), sidx);
            return;
        }

        // TODO: epidemic propagation

        // TODO: impact AE scheduling
        _ae.request_(sidx, msg.did());
    }

    private final TransLocal<Set<SIndex>> _tlAdded = new TransLocal<Set<SIndex>>() {
        @Override
        protected Set<SIndex> initialValue(Trans t)
        {
            final Set<SIndex> s = Sets.newHashSet();
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    _updated.addAll(s);
                    _dsNewUpdateMessage.schedule_();
                }
            });
            return s;
        }
    };

    @Override
    public void localVersionAdded_(SOCKID sockid, Version v, Trans t) throws SQLException
    {
        _tlAdded.get(t).add(sockid.sidx());
    }
}
