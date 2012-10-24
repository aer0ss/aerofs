package com.aerofs.daemon.core.net.proto;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.net.Metrics;
import com.aerofs.daemon.core.net.NSL;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.proto.Core.PBNewUpdate;
import com.aerofs.proto.Core.PBCore.Type;

/**
 * This class is responsible for sending and receiving NEW_UPDATE messages
 */
public class NewUpdates
{
    private static final Logger l = Util.l(NewUpdates.class);

    private Metrics _m;
    private NativeVersionControl _nvc;
    private NSL _nsl;
    private TransManager _tm;
    private MapSIndex2Store _sidx2s;

    @Inject
    public void inject_(TransManager tm, NSL nsl, NativeVersionControl nvc, Metrics m,
            MapSIndex2Store sidx2s)
    {
        _tm = tm;
        _nsl = nsl;
        _nvc = nvc;
        _m = m;
        _sidx2s = sidx2s;
    }

    public void send_(Collection<SOCKID> ks)
        throws Exception
    {
        // group components into stores
        final Map<SIndex, List<SOCKID>> sidx2ks = Maps.newTreeMap();
        for (SOCKID k : ks) {
            List<SOCKID> list = sidx2ks.get(k.sidx());
            if (list == null) {
                list = new ArrayList<SOCKID>();
                sidx2ks.put(k.sidx(), list);
            }
            list.add(k);
        }

        // send one message for each group
        for (Entry<SIndex, List<SOCKID>> en : sidx2ks.entrySet()) {

            SIndex sidx = en.getKey();

            // write a PBUpdate for each component. split into multiple
            // messages if a single message would be too big
            ByteArrayOutputStream os = null;
            for (SOCKID k : en.getValue()) {

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
                    .setObjectId(k.oid().toPB())
                    .setComId(k.cid().getInt())
                    .setTick(tick.getLong())
                    .build()
                    .writeDelimitedTo(os);

                if (os.size() >= _m.getRecommendedMaxcastSize_()) {
                    _nsl.sendMaxcast_(sidx, String.valueOf(Type.NEW_UPDATES.getNumber()),
                            CoreUtil.NOT_RPC, os);
                    os = null;
                }
            }

            if (os != null) {
                l.debug("send out");
                _nsl.sendMaxcast_(sidx, String.valueOf(Type.NEW_UPDATES.getNumber()),
                        CoreUtil.NOT_RPC, os);
            }
        }
    }

    public void process_(DigestedMessage msg) throws Exception
    {
        l.debug("recv from " + msg.ep());

        Trans t = _tm.begin_();
        try {
            BFOID filter = new BFOID();
            TreeSet<OID> done = new TreeSet<OID>();
            boolean news = false;

            while (msg.is().available() > 0) {
                PBNewUpdate update = PBNewUpdate.parseDelimitedFrom(msg.is());

                OID oid = new OID(update.getObjectId().toByteArray());
                CID cid = new CID(update.getComId());
                SOCID socid = new SOCID(msg.sidx(), oid, cid);

                Tick tick = new Tick(update.getTick());
                if (_nvc.tickReceived_(socid, msg.did(), tick, t)) {
                    news = true;
                    if (done.add(oid) && filter.add_(oid) && l.isDebugEnabled()) {
                        l.debug("add oid " + oid + " to " + filter);
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
            if (news) _sidx2s.getThrows_(msg.sidx()).collector().add_(msg.did(), filter, t);

            t.commit_();
        } finally {
            t.end_();
        }
    }
}
