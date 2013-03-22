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
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.ObjectWalkerAdapter;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
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

    private final DirectoryService _ds;

    @Inject
    public SeedCreator(DirectoryService ds)
    {
        _ds = ds;
    }

    /**
     * Create a seed file from the current contents of the database
     * @return Absolute path of the created seed file
     */
    public String create_() throws Exception
    {
        // TODO: adapt for multi user?
        SOID soid = _ds.resolveNullable_(new Path());
        if (soid == null) throw new ExBadArgs("Cannot seed multi-user setup");

        l.info("creating seed");
        try {
            final SeedDatabase sdb = SeedDatabase.create_();
            try {
                populate_(soid, sdb);
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

    private void populate_(SOID root, final SeedDatabase sdb)
            throws ExNotFound, SQLException, IOException, ExNotDir, ExStreamInvalid, ExAlreadyExist
    {
        l.info("populating seed");

        // TODO: ds.walk_ is blocking but the seed file does not necessarily need an atomic
        // walk to be useful -> consider ways to break down this operation?
        _ds.walk_(root, new Path(), new ObjectWalkerAdapter<Path>() {
            @Override
            @Nullable
            public Path prefixWalk_(Path parentPath, OA oa) throws SQLException
            {
                if (oa.isExpelled()) return null;
                if (oa.soid().oid().isRoot()) return parentPath;

                OID oid = oa.soid().oid();
                Path p = parentPath.append(oa.name());
                if (oa.isAnchor()) {
                    // If a valid tag file is present, this entry in the seed file will not
                    // be used during the first scan. However, if the tag file is absent and
                    // the shared folder is still around on another device, migration will kick
                    // in eventually
                    sdb.setOID_(p, true, SID.anchorOID2folderOID(oid));
                } else {
                    sdb.setOID_(p, oa.isDir(), oid);
                    // TODO: store MASTER versions and content hash as well
                }
                return p;
            }
        });

        l.info("seed populated");
    }
}
