package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.UnlinkedRootDatabase;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 *  Admitted - term used for currently syncing internal folders.
 *  Linked - term use for currently syncing external folders.
 *
 * Handler to return list of shared folders. For each store that is accessible by the local user:
 * 1. We first check (in this class itself) if the store is an unlinked store or not. If yes, we get
 * relevant information about that store and add it to the list of shared folders to return.
 * 2. If the store is an unlinked store, then depending upon if the local user is a TS or single user
 * specific function is invoked (see IListLinkedAndExpelledSharedFolders). This function is
 * responsible for returning linked/admitted and expelled stores (relevant only to single users).
 */
public class HdListSharedFolders extends AbstractHdIMC<EIListSharedFolders>
{
    private final IMapSIndex2SID _sidx2sid;
    private final LocalACL _lacl;
    private final UnlinkedRootDatabase _urdb;
    private final IListLinkedAndExpelledSharedFolders _llesf;

    @Inject
    public HdListSharedFolders(IMapSIndex2SID sidx2sid, LocalACL lacl, UnlinkedRootDatabase urdb,
            IListLinkedAndExpelledSharedFolders llesf)
    {
        _sidx2sid = sidx2sid;
        _lacl = lacl;
        _urdb = urdb;
        _llesf = llesf;
    }

    /**
     *  This function handles the listSharedFolder event. It looks into the user's accessible stores
     *  and returns them as long as store is not the user's root itself. This is done after
     *  identifying whether the folder is a linked/admitted, expelled or unlinked.
     */
    @Override
    protected void handleThrows_(EIListSharedFolders ev) throws Exception
    {
        Collection<SIndex> accessibleStores = _lacl.getAccessibleStores_();
        Collection<PBSharedFolder> sharedFolders =
                Lists.newArrayListWithCapacity(accessibleStores.size());

        Map<SID, String> unlinkedRoots = _urdb.getUnlinkedRoots();

        for (SIndex sidx : accessibleStores) {
            SID sid = _sidx2sid.getLocalOrAbsent_(sidx);
            // The accessible stores will also include the user root which is not really a shared
            // folder. So filter it.
            if (!sid.isUserRoot()) {
                PBSharedFolder sharedFolder = getSharedFolder(sidx, sid, unlinkedRoots);
                if (sharedFolder != null) {
                    sharedFolders.add(sharedFolder);
                }
            }
        }
        ev.setResult_(sharedFolders);
    }

    /**
     * This function takes in the SIndex and sid of a store and then:
     * 1. First checks if the store is an unlinked root(FYI unlinked root = unlinked stores).
     * This check can only pass, if at all in single user clients since multiuser (TS) don't have
     * the concept of unlinked roots yet.
     * 2. If check from 1 fails, then calls a function to get linked/admitted or expelled stores.
     * Both single user and multiuser client have their own implementations of that function.
     */
    private @Nullable PBSharedFolder getSharedFolder(SIndex sidx, SID sid, Map<SID,
            String> unlinkedRoots) throws Exception
    {
        if (unlinkedRoots.containsKey(sid)) {
            // An unlinked root store. Note: Unlinked roots aren't allowed in the TS.
            return PBSharedFolder.newBuilder()
                    .setName(unlinkedRoots.get(sid))
                    .setPath(new Path(sid).toPB())
                    .setAdmittedOrLinked(false)
                    .setStoreId(BaseUtil.toPB(sid))
                    .build();
        } else {
            return _llesf.getSharedFolder(sidx, sid);
        }
    }
}