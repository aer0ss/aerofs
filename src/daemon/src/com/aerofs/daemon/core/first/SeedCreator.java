/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.lib.db.IMetaDatabaseWalker;
import com.aerofs.daemon.lib.db.IMetaDatabaseWalker.TypeNameOID;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Helper class to create a seed file
 *
 * See {@link SeedDatabase} for more details about the seed file concept.
 */
public class SeedCreator
{
    private static final Logger l = Loggers.getLogger(SeedCreator.class);

    private final IMetaDatabaseWalker _mdbw;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public SeedCreator(IMetaDatabaseWalker mdbw, IMapSID2SIndex sid2sidx)
    {
        _mdbw = mdbw;
        _sid2sidx = sid2sidx;
    }

    /**
     * Create a seed file from the current contents of the database
     * @return Absolute path of the created seed file
     */
    public String create_(SID sid) throws Exception
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) throw new ExBadArgs("Cannot create seed for " + sid.toStringFormal());

        l.info("creating seed");
        try {
            final SeedDatabase sdb = SeedDatabase.create_(sid.toStringFormal());
            try {
                populate_(sidx, sdb);
                return sdb.save_();
            } catch (Exception e) {
                l.info("failed to populate seed", e);
                sdb.cleanup_();
                throw e;
            }
        } catch (Exception e) {
            l.warn("failed to create seed", e);
            throw e;
        }
    }

    private void populate_(SIndex sidx, final SeedDatabase sdb)
            throws ExNotFound, SQLException, IOException, ExNotDir, ExStreamInvalid, ExAlreadyExist
    {
        l.info("populating seed");

        populateImpl_(sidx, OID.ROOT, Type.DIR, "", sdb);

        l.info("seed populated");
    }

    private void populateImpl_(SIndex sidx, OID oid, OA.Type type, String path, SeedDatabase sdb)
            throws SQLException
    {
        switch (type) {
        case ANCHOR:
            // If a valid tag file is present, this entry in the seed file will not
            // be used during the first scan. However, if the tag file is absent and
            // the shared folder is still around on another device, migration will kick
            // in eventually
            sdb.setOID_(path, true, SID.anchorOID2folderOID(oid));
            populateImpl_(_sid2sidx.getLocalOrAbsentNullable_(SID.anchorOID2storeSID(oid)),
                    OID.ROOT, Type.DIR, path, sdb);
            break;
        case DIR:
            if (oid.isTrash()) return;
            if (!oid.isRoot()) sdb.setOID_(path, true, oid);
            for (TypeNameOID tno : _mdbw.getTypedChildren_(sidx, oid)) {
                populateImpl_(sidx, tno._oid, tno._type,
                        path.isEmpty() ? tno._name : Util.join(path, tno._name), sdb);
            }
            break;
        case FILE:
            sdb.setOID_(path, false, oid);
            break;
        default:
            throw new AssertionError();
        }
    }
}
