package com.aerofs.daemon.core.migration;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;

import javax.annotation.Nonnull;

/**
 * This class implements cross-store movement of file contents and child stores.
 * ImmigrantDetector avoids these contents and child stores from being deleted and
 * recreated when their holding objects are moved from one store to another.
 * See the design document for more information.
 */
public class ImmigrantDetector
{
    static final Logger l = Util.l(ImmigrantDetector.class);

    private DirectoryService _ds;
    private NativeVersionControl _nvc;
    private ImmigrantVersionControl _ivc;
    private IPhysicalStorage _ps;
    private ObjectDeleter _od;
    private IMapSID2SIndex _sid2sidx;
    private IMapSIndex2SID _sidx2sid;
    private AdmittedObjectLocator _aol;
    private IStores _ss;

    @Inject
    public void inject_(DirectoryService ds, NativeVersionControl nvc, ImmigrantVersionControl ivc,
            IPhysicalStorage ps, ObjectDeleter od, IMapSID2SIndex sid2sidx,
            AdmittedObjectLocator aol, IMapSIndex2SID sidx2sid, IStores ss)
    {
        _ss = ss;
        _sid2sidx = sid2sidx;
        _ds = ds;
        _nvc = nvc;
        _ivc = ivc;
        _ps = ps;
        _od = od;
        _aol = aol;
        _sidx2sid = sidx2sid;
    }

    private void immigrateFile_(OA oaFrom, OA oaTo, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExNotFound
    {
        assert oaFrom.soid().oid().equals(oaTo.soid().oid());
        assert oaFrom.isFile() && oaTo.isFile();
        assert !oaFrom.isExpelled() && !oaTo.isExpelled();

        Path pathTo = _ds.resolve_(oaTo.soid());

        SOCID socidFrom = new SOCID(oaFrom.soid(), CID.CONTENT);
        SOCID socidTo = new SOCID(oaTo.soid(), CID.CONTENT);

        // get the kml version before updating local versions to avoid assertion
        // failure in getKMLVersion_()
        Version vKMLToOld = _nvc.getKMLVersion_(socidTo);

        Version vLocalSum = new Version();
        for (Entry<KIndex, CA> en : oaFrom.cas().entrySet()) {
            KIndex kidx = en.getKey();
            CA caFrom = en.getValue();
            SOCKID kFrom = new SOCKID(socidFrom, kidx);
            Version vFrom = _nvc.getLocalVersion_(kFrom);
            ContentHash hFrom = _ds.getCAHash_(kFrom.sokid());

            Util.l().info("migrate do " + kFrom);

            SOCKID kTo = new SOCKID(socidTo, kidx);

            // set content attribute
            _ds.createCA_(kTo.soid(), kidx, t);
            _ds.setCA_(kTo.sokid(), caFrom.length(), caFrom.mtime(), hFrom, t);

            // set local version
            _nvc.addLocalVersion_(kTo, vFrom, t);
            vLocalSum = vLocalSum.add_(vFrom);

            // move physical files
            IPhysicalFile pfTo = _ps.newFile_(kTo.sokid(), pathTo);
            caFrom.physicalFile().move_(pfTo, op, t);

            // TODO send NEW_UPDATE-like messages for migrated branches, but
            // only from the initiating peer of the migration. this will speed
            // up propagation of branches for peers that don't have the source
            // store. peers that do will apply the branches by the migration
            // process
        }

        if (!vLocalSum.isZero_()) {
            // update kml version
            Version vKMLToDel = vKMLToOld.sub_(vKMLToOld.sub_(vLocalSum));
            _nvc.deleteKMLVersion_(socidTo, vKMLToDel, t);

            // add immigrant version
            for (Entry<DID, Tick> en : vLocalSum.getAll_().entrySet()) {
                _ivc.updateMyImmigrantVersion_(socidTo, en.getKey(), en.getValue(), t);
            }
        }
    }

    private void immigrateAnchor_(OA oaFrom, OA oaTo, PhysicalOp op, Trans t)
        throws IOException, ExAlreadyExist, ExNotFound, ExNotDir, SQLException
    {
        assert !oaFrom.soid().sidx().equals(oaTo.soid().sidx());
        assert oaFrom.soid().oid().equals(oaTo.soid().oid());
        assert oaFrom.isAnchor() && oaTo.isAnchor();
        assert !oaFrom.isExpelled() && !oaTo.isExpelled();

        SOID soidTo = oaTo.soid();

        SID sid = SID.anchorOID2storeSID(soidTo.oid());
        SIndex sidx = _sid2sidx.get_(sid);

        // update the child store's parent
        assert _ss.getParent_(sidx).equals(oaFrom.soid().sidx());
        _ss.setParent_(sidx, soidTo.sidx(), t);

        // move physical objects
        oaFrom.physicalFolder().move_(oaTo.physicalFolder(), op, t);
    }

    /**
     * this method assumes that:
     *
     * 1) the destination is admitted
     * 2) permissions have been checked
     * 3) the destination metadata has been created
     * 4) no content exists in the destination object if it is a file
     * 5) no child store exists in the destination object if it is an anchor
     *
     * @param oaTo the OA of the destination object
     * @return true if immigration has been performed
     */
    public boolean detectAndPerformImmigration_(@Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir, ExStreamInvalid
    {
        // assert for assumption 1) above
        assert !oaTo.isExpelled();
        // assert for assumption 4) above
        assert !oaTo.isFile() || oaTo.cas().isEmpty();

        // directories can't be migrated
        if (oaTo.isDir()) return false;

        OA oaFrom = _aol.locate_(oaTo.soid().oid(), oaTo.soid().sidx(), oaTo.type());
        if (oaFrom == null) return false;

        assert oaFrom.type() == oaTo.type();

        if (oaFrom.isFile()) {
            immigrateFile_(oaFrom, oaTo, op, t);
        } else {
            // guaranteed by the above code
            assert oaFrom.isAnchor();
            immigrateAnchor_(oaFrom, oaTo, op, t);
        }

        SID sid = _sidx2sid.get_(oaTo.soid().sidx());
        _od.delete_(oaFrom.soid(), op, sid, t);

        // TODO notify with a MOVE or DELETE+CREATE event?

        return true;
    }
}
