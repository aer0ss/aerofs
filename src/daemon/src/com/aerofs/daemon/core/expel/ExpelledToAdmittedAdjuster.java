package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.ObjectWalkerAdapter;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

class ExpelledToAdmittedAdjuster implements IExpulsionAdjuster
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final ImmigrantDetector _imd;
    private final ICollectorSequenceDatabase _csdb;
    private final NativeVersionControl _nvc;
    private final MapSIndex2Store _sidx2s;
    private final ChangeEpochDatabase _cedb;
    private final RemoteContentDatabase _rcdb;
    private final StoreCreator _sc;
    private final LogicalStagingArea _sa;

    @Inject
    public ExpelledToAdmittedAdjuster(StoreCreator sc, ImmigrantDetector imd,
            DirectoryService ds, IPhysicalStorage ps, LogicalStagingArea sa,
            NativeVersionControl nvc, ICollectorSequenceDatabase csdb, MapSIndex2Store sidx2s,
            ChangeEpochDatabase cedb, RemoteContentDatabase rcdb)
    {
        _sc = sc;
        _imd = imd;
        _ds = ds;
        _ps = ps;
        _sa = sa;
        _nvc = nvc;
        _csdb = csdb;
        _sidx2s = sidx2s;
        _cedb = cedb;
        _rcdb = rcdb;
    }

    @Override
    public void adjust_(ResolvedPath pathOld, final SOID soidRoot, final PhysicalOp op, final Trans t)
            throws Exception
    {
        // must recreate the *new* tree
        // otherwise we may try to create physical object under the trash folder
        // which would fail and result in the apparition of nro which would be
        // mightily confusing
        ResolvedPath p = _ds.resolve_(soidRoot);

        // FIXME: ugh! nasty immigration business down here
        // unfortunately it is far from trivial to refactor
        _ds.walk_(soidRoot, p, new ObjectWalkerAdapter<ResolvedPath>() {
            @Override
            public ResolvedPath prefixWalk_(ResolvedPath parentPath, OA oa)
                    throws Exception {
                boolean isRoot = soidRoot.equals(oa.soid());

                ResolvedPath path = isRoot ? parentPath : parentPath.join(oa);

                // skip the current node and its children if the effective state of the current
                // object doesn't change
                if (oa.isSelfExpelled()) return null;

                _sa.ensureClean_(path, t);

                // NB: MUST refresh OA in case the staging area had to clean things up
                oa = _ds.getOA_(oa.soid());

                switch (oa.type()) {
                case FILE:
                    _imd.detectAndPerformImmigration_(oa, op, t);
                    fileAdmitted_(oa.soid(), t);
                    return null;
                case DIR:
                    _ps.newFolder_(path).create_(op, t);
                    return path;
                case ANCHOR:
                    boolean immigrated = _imd.detectAndPerformImmigration_(oa, op, t);
                    if (!immigrated) {
                        SID sid = SID.anchorOID2storeSID(oa.soid().oid());
                        IPhysicalFolder pf = _ps.newFolder_(path);
                        pf.create_(op, t);
                        _sc.addParentStoreReference_(sid, oa.soid().sidx(), oa.name(), t);
                        pf.promoteToAnchor_(sid, op, t);
                    }
                    return null;
                default:
                    assert false;
                    return null;
                }
            }
        });
    }

    // Because huge amount of files may be admitted at once, we batch store operations for these
    // files at the end of the transaction.
    private class StoresWithAdmittedFiles extends AbstractTransListener
    {
        Set<SIndex> _sidxs = Sets.newHashSet();

        void add_(SIndex sidx)
        {
            _sidxs.add(sidx);
        }

        @Override
        public void committing_(Trans t) throws SQLException
        {
            for (SIndex sidx : _sidxs) {
                _sidx2s.get_(sidx).resetCollectorFiltersForAllDevices_(t);
            }
        }
    }

    private final TransLocal<StoresWithAdmittedFiles> _tlaf =
            new TransLocal<StoresWithAdmittedFiles>() {
                @Override
                protected StoresWithAdmittedFiles initialValue(Trans t)
                {
                    StoresWithAdmittedFiles swaf = new StoresWithAdmittedFiles();
                    t.addListener_(swaf);
                    return swaf;
                }
            };

    /**
     * called when a file is admitted from the expelled state
     */
    private void fileAdmitted_(SOID soid, Trans t) throws SQLException
    {
        checkArgument(_ds.getOA_(soid).isFile());

        if (_cedb.getChangeEpoch_(soid.sidx()) != null) {
            if (_rcdb.hasRemoteChanges_(soid.sidx(), soid.oid(), 0L)) {
                // TODO(phoenix): BF adjustment (when BF brought back)
                _sidx2s.get_(soid.sidx()).contentFetcher().schedule_(soid.oid(), t);
            }
        } else {
            SOCID socid = new SOCID(soid, CID.CONTENT);

            // Ignore if the content doesn't have KML version. Strictly speaking this is not needed
            // because the collector automatically skips objects with zero KML
            if (_nvc.getKMLVersion_(socid).isZero_()) return;

            _csdb.insertCS_(socid, t);
            _tlaf.get(t).add_(soid.sidx());
        }
    }
}
