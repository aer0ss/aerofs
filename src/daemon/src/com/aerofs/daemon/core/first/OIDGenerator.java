/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;

/**
 * Abstract away OID generation for use by MightCreate
 *
 * During the first launch we may have a seed file lying around from which we want to extract
 * OIDs to reduce aliasing and we don't want to leak that logic inside MightCreate, hence this class
 */
public class OIDGenerator
{
    private static final Logger l = Loggers.getLogger(OIDGenerator.class);

    boolean _shouldLookup;

    private final SeedDatabase _sdb;

    @Inject
    public OIDGenerator(CfgLocalUser localUser)
    {
        _sdb = SeedDatabase.load_(SID.rootSID(localUser.get()).toStringFormal());
        _shouldLookup = _sdb != null;
        if (_shouldLookup) l.info("seed file loaded");
    }

    void onFirstLaunchCompletion_()
    {
        _shouldLookup = false;
        if (_sdb != null) _sdb.cleanup_();
    }

    public OID generate_(boolean dir, Path path)
    {
        if (_shouldLookup) {
            try {
                OID oid = _sdb.getOID_(path, dir);
                l.info("lookup: {} {}", path.toStringFormal(), oid);
                if (oid != null) return oid;
            } catch (SQLException e) {
                // can safely ignore seed-related errors
            }
            // TODO: restore versions on content hash match?
        }
        return new OID(UniqueID.generate());
    }
}
