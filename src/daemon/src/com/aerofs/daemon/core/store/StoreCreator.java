package com.aerofs.daemon.core.store;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.labeling.L;
import com.aerofs.ids.SID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class StoreCreator
{
    private static final Logger l = Loggers.getLogger(StoreCreator.class);

    private final StoreHierarchy _ss;
    private final IPhysicalStorage _ps;
    private final IMapSID2SIndex _sid2sidx;
    private final LogicalStagingArea _sa;
    private final StoreCreationOperators _sco;
    private final CfgUsePolaris _usePolaris;

    @Inject
    public StoreCreator(IMapSID2SIndex sid2sidx, StoreHierarchy ss, StoreCreationOperators sco,
            IPhysicalStorage ps, LogicalStagingArea sa, CfgUsePolaris usePolaris)
    {
        _ss = ss;
        _sid2sidx = sid2sidx;
        _ps = ps;
        _sa = sa;
        _sco = sco;
        _usePolaris = usePolaris;
    }

    /**
     * Add {@code sidxParent} as {@code sid}'s parent. Create the child store if it doesn't exist.
     */
    public void addParentStoreReference_(SID sid, SIndex sidxParent, String name, Trans t)
            throws Exception
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) {
            sidx = createStoreImpl_(sid, name, t);
            assert _ss.getParents_(sidx).isEmpty() : sidx + " " + sid;
        } else {
            // adding ref to the root store of a user is never fine
            // adding ref to a regular store that used to have no parent is:
            //   * fine in multiuser because the underlying storage is flat
            //   * problematic in singleuser because it indicates duplication
            assert !sid.isUserRoot() && (L.isMultiuser() || !_ss.getParents_(sidx).isEmpty())
                    : sidx + " " + sid;
        }
        _ss.addParent_(sidx, sidxParent, t);
    }

    /**
     * Create a store with no parent. See comments in IStores for detail
     */
    public SIndex createRootStore_(SID sid, String name, Trans t)
            throws Exception
    {
        return createStoreImpl_(sid, name, t);
    }

    /**
     * Create a store.
     */
    private SIndex createStoreImpl_(SID sid, String name, Trans t)
            throws Exception
    {
        // Note that during store creation, all in-memory data structures may not be fully set up
        // for the new store yet. Therefore, functions involved in store creation might not be able
        // to query information relevant to the store in usual ways. The reliable way to retrieve
        // information is by passing pre-computed values as parameters to the functions when
        // possible. Passing {@code path} to IPhysicalStorage#createStore below is one example.

        SIndex sidx = _sid2sidx.getAbsent_(sid, t);

        // ensure any staged deletion completes
        _sa.ensureStoreClean_(sidx, t);

        l.info("create store {} {}", sidx, sid);

        // TODO: ACL-controlled central/distrib store assignment and version conversion
        boolean usePolaris = _usePolaris.get();

        _sco.runAll_(sidx, usePolaris, t);
        _ps.createStore_(sidx, sid, name, t);
        _ss.add_(sidx, name, usePolaris, t);

        return sidx;
    }
}
