package com.aerofs.daemon.core.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.NativeVersionControl.IVersionControlListener;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.DelayedScheduler;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.Tick;
import com.aerofs.lib.id.CID;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBNewUpdate;
import com.aerofs.proto.Core.PBCore.Type;

/**
 * This class is responsible for sending and receiving NEW_UPDATE messages
 */
public class NewUpdates implements IVersionControlListener
{
    private static final Logger l = Loggers.getLogger(NewUpdates.class);

    private Metrics _m;
    private NativeVersionControl _nvc;
    private NSL _nsl;
    private TransManager _tm;
    private MapSIndex2Store _sidx2s;
    private IMapSIndex2SID _sidx2sid;
    private IMapSID2SIndex _sid2sidx;
    private LocalACL _lacl;
    private CfgLocalUser _cfgLocalUser;

    private final Set<SOCKID> _updated = Sets.newHashSet();
    private DelayedScheduler _dsNewUpdateMessage;

    @Inject
    public void inject_(TransManager tm, NSL nsl, NativeVersionControl nvc, Metrics m,
            MapSIndex2Store sidx2s, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx, LocalACL lacl,
            CfgLocalUser cfgLocalUser, CoreScheduler sched)
    {
        _tm = tm;
        _nsl = nsl;
        _nvc = nvc;
        _m = m;
        _sidx2s = sidx2s;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _lacl = lacl;
        _cfgLocalUser = cfgLocalUser;

        _nvc.addListener_(this);

        _dsNewUpdateMessage = new DelayedScheduler(sched, DaemonParam.NEW_UPDATE_MESSAGE_DELAY,
                new Runnable() {
                    @Override
                    public void run()
                    {
                        assert !_updated.isEmpty();

                        try {
                            // send a NEW_UPDATE message for all the branches that have been updated
                            // since the last NEW_UPDATE.
                            send_(_updated);
                        } catch (Exception e) {
                            // failed to push.
                            l.warn("ignored: " + Util.e(e));
                        }

                        _updated.clear();
                    }
                });
    }

    public void send_(Collection<SOCKID> ks)
        throws Exception
    {
        for (Entry<SIndex, List<SOCKID>> en : groupComponentsByStore(ks).entrySet()) {
            sendForStore_(en.getKey(), en.getValue());
        }
    }

    private static Map<SIndex, List<SOCKID>> groupComponentsByStore(Collection<SOCKID> ks)
    {
        Map<SIndex, List<SOCKID>> sidx2ks = Maps.newTreeMap();
        for (SOCKID k : ks) {
            List<SOCKID> list = sidx2ks.get(k.sidx());
            if (list == null) {
                list = new ArrayList<SOCKID>();
                sidx2ks.put(k.sidx(), list);
            }
            list.add(k);
        }
        return sidx2ks;
    }


    private void sendForStore_(SIndex sidx, List<SOCKID> ks)
            throws Exception
    {
        // see Rule 3 in acl.md
        if (!_lacl.check_(_cfgLocalUser.get(), sidx, Permissions.EDITOR)) {
            l.info("we have no editor perm for {}", sidx);
            return;
        }

        SID sid = _sidx2sid.getThrows_(sidx);

        // write a PBNewUpdate for each component. split into multiple
        // messages if a single message would be too big
        ByteArrayOutputStream os = null;
        for (SOCKID k : ks) {

            if (os == null) {
                os = new ByteArrayOutputStream();
                // write PBCore
                CoreUtil.newCore(Type.NEW_UPDATES)
                    .build()
                    .writeDelimitedTo(os);
                assert os.size() < _m.getRecommendedMaxcastSize_();
            }

            Tick tick = _nvc.getLocalTick_(k);
            if (l.isDebugEnabled()) l.debug("send " + k + "? " + tick);
            if (tick == null) continue;

            PBNewUpdate.newBuilder()
                    .setStoreId(sid.toPB())
                    .setObjectId(k.oid().toPB())
                    .setComId(k.cid().getInt())
                    .setTick(tick.getLong())
                    .build()
                    .writeDelimitedTo(os);

            if (os.size() >= _m.getRecommendedMaxcastSize_()) {
                _nsl.sendMaxcast_(sid, String.valueOf(Type.NEW_UPDATES.getNumber()),
                        CoreUtil.NOT_RPC, os);
                os = null;
            }
        }

        if (os != null) {
            _nsl.sendMaxcast_(sid, String.valueOf(Type.NEW_UPDATES.getNumber()),
                    CoreUtil.NOT_RPC, os);
        }
    }

    public void process_(DigestedMessage msg)
            throws ExNotFound, SQLException, IOException
    {
        l.debug("recv from {}", msg.ep());

        Trans t = _tm.begin_();
        try {
            BFOID filter = new BFOID();
            TreeSet<OID> done = new TreeSet<OID>();

            Set<SIndex> news = Sets.newHashSet();

            while (msg.is().available() > 0) {
                PBNewUpdate update = PBNewUpdate.parseDelimitedFrom(msg.is());

                SIndex sidx = _sid2sidx.getThrows_(new SID(update.getStoreId()));
                OID oid = new OID(update.getObjectId().toByteArray());
                CID cid = new CID(update.getComId());
                SOCID socid = new SOCID(sidx, oid, cid);

                // see Rule 2 in acl.md. Note that the maxcast sender can forge the device id
                // (unless maxcast messages are signed). therefore this is not a security measure.
                // see more in acl.md.
                if (!_lacl.check_(msg.user(), sidx, Permissions.EDITOR)) {
                    l.warn("{} on {} has no editor perm for {}", msg.user(), msg.ep(), socid);
                    continue;
                }

                Tick tick = new Tick(update.getTick());
                if (_nvc.tickReceived_(socid, msg.did(), tick, t)) {
                    news.add(sidx);
                    if (done.add(oid) && filter.add_(oid)) {
                        l.debug("add oid {} to {}", oid, filter);
                    }
                }
            }

            // After NEW_UPDATES relaying is implemented, the filter maintenance
            // code may need a revisit as it might not work as expected with the
            // new behavior. This is because msg.did() might not be the one we
            // should add the filter to, and because the news variable being false
            // wouldn't necessarily mean we shouldn't add the element to the
            // filter

            // must call add *after* everything else is writing to the db
            for (SIndex sidx : news) {
                _sidx2s.getThrows_(sidx).collector().add_(msg.did(), filter, t);
            }

            t.commit_();
        } finally {
            t.end_();
        }
    }

    private final TransLocal<Set<SOCKID>> _tlAdded = new TransLocal<Set<SOCKID>>() {
        @Override
        protected Set<SOCKID> initialValue(Trans t)
        {
            final Set<SOCKID> s = Sets.newHashSet();
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
        _tlAdded.get(t).add(sockid);
    }
}
