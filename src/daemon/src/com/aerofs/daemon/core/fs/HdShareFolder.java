package com.aerofs.daemon.core.fs;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.aerofs.daemon.core.phy.PhysicalOp.NOP;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdShareFolder extends AbstractHdIMC<EIShareFolder>
{
    private final LocalACL _lacl;
    private final ACLSynchronizer _aclsync;
    private final TransManager _tm;
    private final ObjectCreator _oc;
    private final DirectoryService _ds;
    private final ImmigrantCreator _imc;
    private final ObjectMover _om;
    private final ObjectDeleter _od;
    private final IMapSID2SIndex _sid2sidx;
    private final IStores _stores;

    @Inject
    public HdShareFolder(LocalACL lacl, TransManager tm, ObjectCreator oc, DirectoryService ds,
            ImmigrantCreator imc, ObjectMover om, ObjectDeleter od, IMapSID2SIndex sid2sidx,
            ACLSynchronizer aclsync, IStores stores)
    {
        _stores = stores;
        _lacl = lacl;
        _tm = tm;
        _oc = oc;
        _ds = ds;
        _imc = imc;
        _om = om;
        _od = od;
        _sid2sidx = sid2sidx;
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EIShareFolder ev, Prio prio) throws Exception
    {
        ////////
        // sanity and security check

        SOID soid = _ds.resolveThrows_(ev._path);
        if (soid.oid().equals(OID.ROOT) || soid.oid().equals(OID.TRASH)) {
            throw new ExNoPerm("can't share system folders");
        }

        // throw if the object is expelled
        OA oa = _ds.getOANullable_(soid);
        if (oa.isExpelled()) throw new ExExpelled();

        // throw if a parent folder is already shared
        if (!_stores.getRoot_().equals(soid.sidx())) throw new ExParentAlreadyShared();

        Set<SIndex> descendants = _stores.getDescendants_(soid);
        if (!descendants.isEmpty()) throw new ExChildAlreadyShared();

        // check ACL
        assert !ev._path.isEmpty(); // guaranteed by the above check
        Path pathParent = ev._path.removeLast();
        _lacl.checkThrows_(ev.user(), pathParent, Role.OWNER);

        //
        // IMPORTANT: the order of operations in the following code matters
        //
        // You have to:
        // 1) calculate the SID
        // 2) contact the central ACL server to update the acl
        // 3) convert the local folder into a shared folder
        //
        // The output of 1) is used by 2) and 3), so it has to run first.
        //
        // We contact the remote _first_ to update the acls because remote calls are much more
        // likely to fail than local ones. A failure will safely prevent the folder from being
        // converted into a store (i.e. a half-state in the system).
        //
        // If the order of 2) and 3) were reversed we would convert the store locally first,
        // and then update the acls for this store. At the same time,
        // the migration process has started, and objects will be deleted from the old folder and
        // added to the store. At the same time, this update would be propagated to other devices
        // for the user. If the RPC call fails however, neither the sharer or their devices would
        // have permissions to receive the files or make modifications to the store,
        // resulting in all the contents vanishing. Not good. Moreover,
        // the system is in a half-state; the folder has been shared,
        // but no acls for the owner exist; again, not good.
        //
        // To prevent this we contact the remote first, verify that the acls are added, and then,
        // make changes locally. Since the acl update process is idempotent from the perspective
        // of the stores' owner, multiple local failures can be handled properly,
        // while remote failures will prevent the system from being in a half-state
        //

        ////////
        // calculate the SID

        boolean alreadyShared = false;
        SID sid;
        if (oa.isAnchor()) {
            alreadyShared = true;
            sid = SID.anchorOID2storeSID(oa.soid().oid());
        } else if (oa.isDir()) {
            sid = SID.folderOID2convertedStoreSID(oa.soid().oid());
        } else {
            throw new ExNotDir();
        }

        ////////
        // add the acl

        Map<String, Role> subject2role = new TreeMap<String, Role>(ev._subject2role);

        // always add the user as the owner
        if (!alreadyShared) subject2role.put(ev.user(), Role.OWNER);

        if (!subject2role.isEmpty()) {
            // the store is guaranteed not expelled by the above code
            SIndex sidx;
            if (alreadyShared) {
                sidx = _sid2sidx.get_(sid);
            } else {
                Trans t = _tm.begin_();
                try {
                    sidx = _sid2sidx.getAbsent_(sid, t);
                    t.commit_();
                } finally {
                    t.end_();
                }
            }

            // this method may block and therefore the transaction can't be reused below
            _aclsync.set_(sidx, subject2role);
        }

        ////////
        // convert the folder to a shared folder

        if (!alreadyShared) {
            assert oa.isDir();

            Trans t = _tm.begin_();
            try {
                convert_(oa.soid(), oa.parent(), sid, ev._path, t);
                t.commit_();
            } finally {
                t.end_();
            }
        }

        ev.setResult_(sid);
    }

    /**
     * Convert an existing folder into a store.
     */
    private void convert_(SOID soid, OID oidParent, SID sid, Path path, Trans t)
            throws Exception
    {
        // Step 1: rename the folder into a temporary name, without incrementing its version.
        assert !path.isEmpty(); // the caller guarantees this
        Path pathTemp = path;
        do {
            pathTemp = path.removeLast().append(Util.newNextFileName(pathTemp.last()));
        } while (_ds.resolveNullable_(pathTemp) != null);
        _om.moveInSameStore_(soid, oidParent, pathTemp.last(), NOP, false, false, t);

        // Step 2: create the new store with a derived SID
        SOID soidAnchor = new SOID(soid.sidx(), SID.storeSID2anchorOID(sid));
        _oc.createMeta_(ANCHOR, soidAnchor, oidParent, path.last(), 0, MAP, true, true, t);

        // Step 3: migrate files
        SIndex sidxTo = _sid2sidx.get_(sid);
        SOID soidToRoot = new SOID(sidxTo, OID.ROOT);
        for (OID oidChild : _ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), oidChild);
            OA oaChild = _ds.getOA_(soidChild);
            _imc.createImmigrantRecursively_(soidChild, soidToRoot, oaChild.name(), MAP, t);
        }

        // Step 4: delete the root folder
        _od.delete_(soid, NOP, sid, t);
    }
}
