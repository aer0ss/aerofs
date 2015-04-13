package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.protocol.ContentUpdater.ReceivedContent;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.DID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.SortedMap;

import static com.google.common.base.Preconditions.checkState;

public class PolarisCausality implements Causality {
    private final Logger l = Loggers.getLogger(PolarisCausality.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final CentralVersionDatabase _cvdb;
    private final ContentChangesDatabase _ccdb;
    private final RemoteContentDatabase _rcdb;

    @Inject
    public PolarisCausality(DirectoryService ds, IPhysicalStorage ps, CentralVersionDatabase cvdb,
                            ContentChangesDatabase ccdb, RemoteContentDatabase rcdb)
    {
        _ds = ds;
        _ps = ps;
        _cvdb = cvdb;
        _ccdb = ccdb;
        _rcdb = rcdb;
    }

    /**
     * @return null if not to apply the update
     */
    @Override
    public @Nullable CausalityResult computeCausality_(SOID soid, ReceivedContent content)
            throws Exception
    {
        OA remoteOA = _ds.getOAThrows_(soid);
        if (remoteOA.isExpelled()) throw new ExAborted("expelled " + soid);

        List<KIndex> kidcsDel = Lists.newArrayList();

        Long rcv = content.vRemote.unwrapCentral();
        Long lcv = _cvdb.getVersion_(soid.sidx(), soid.oid());
        if (lcv != null && lcv >= rcv) {
            l.info("local {} >= {} remote", lcv, rcv);
            return null;
        }

        // TODO(phoenix): validate against RCDB entries
        KIndex target = KIndex.MASTER;
        boolean avoidContentIO = false;
        SortedMap<KIndex, CA> cas = remoteOA.cas();

        if (cas.size() > 1 || _ccdb.hasChange_(soid.sidx(), soid.oid())) {
            checkState(cas.size() <= 2);
            ContentHash h = _ds.getCAHash_(new SOKID(soid, KIndex.MASTER));

            // if the MASTER CA matches the remote object, avoid creating a conflict
            if (h != null && h.equals(content.hash)
                    && cas.get(KIndex.MASTER).length() == content.length) {
                target = KIndex.MASTER;
                avoidContentIO = true;
                // merge if local change && remote hash == local hash
                if (cas.size() > 1) {
                    kidcsDel.add(KIndex.MASTER.increment());
                }
            } else {
                target = KIndex.MASTER.increment();
            }
        }
        return new CausalityResult(target, content.vRemote, kidcsDel, content.hash,
                Version.wrapCentral(lcv), avoidContentIO);
    }

    @Override
    public void updateVersion_(SOKID k, ReceivedContent content, CausalityResult res, Trans t)
            throws SQLException, IOException, ExNotFound, ExAborted {
        Long rcv = content.vRemote.unwrapCentral();
        Long lcv = _cvdb.getVersion_(k.sidx(), k.oid());
        if (lcv != null && rcv < lcv) throw new ExAborted(k + " version changed");

        l.debug("{} version {} -> {}", k, lcv, rcv);
        _cvdb.setVersion_(k.sidx(), k.oid(), rcv, t);

        _rcdb.deleteUpToVersion_(k.sidx(), k.oid(), rcv, t);
        if (!_rcdb.hasRemoteChange_(k.sidx(), k.oid(), rcv)) {
            // add "remote" content entry for latest version (in case of expulsion)
            _rcdb.insert_(k.sidx(), k.oid(), rcv, new DID(UniqueID.ZERO), res._hash, content.length, t);
        }

        // following is daemon-only
        // TODO: extract/inject for daemon/SA

        // del branch if needed
        if (!res._kidcsDel.isEmpty()) {
            checkState(res._kidcsDel.size() == 1);
            KIndex kidx = Iterables.getFirst(res._kidcsDel, null);
            checkState(kidx == KIndex.MASTER.increment());
            checkState(kidx != k.kidx());
            OA oa = _ds.getOA_(k.soid());
            if (oa.caNullable(kidx) != null) {
                l.info("delete branch {}k{}", k.soid(), kidx);
                _ds.deleteCA_(k.soid(), kidx, t);
                _ps.newFile_(_ds.resolve_(k.soid()), kidx).delete_(PhysicalOp.APPLY, t);
            } else {
                l.warn("{} mergeable ca {} disappeared", k.soid(), kidx);
            }
        }
    }
}
