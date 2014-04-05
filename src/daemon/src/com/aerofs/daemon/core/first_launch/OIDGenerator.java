/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.first_launch;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import org.slf4j.Logger;

import java.sql.SQLException;

import static com.aerofs.lib.LibParam.seedFileName;

/**
 * Abstract away OID generation for use by MightCreate
 *
 * During the first launch we may have a seed file lying around from which we want to extract
 * OIDs to reduce aliasing and we don't want to leak that logic inside MightCreate, hence this class
 */
public class OIDGenerator
{
    private static final Logger l = Loggers.getLogger(OIDGenerator.class);

    boolean _firstScanCompleted;

    private SeedDatabase _sdb;

    public OIDGenerator(SID sid, String absPath)
    {
        _sdb = SeedDatabase.load_(Util.join(absPath, seedFileName(sid)));
        if (_sdb != null) l.info("seed file loaded");
    }

    public boolean isFirstScanInProgress()
    {
        return !_firstScanCompleted;
    }

    public void onScanCompletion_()
    {
        _firstScanCompleted = true;
        if (_sdb != null) {
            _sdb.cleanup_();
            _sdb = null;
        }
    }

    public OID generate_(boolean dir, Path path)
    {
        if (_sdb != null) {
            try {
                OID oid = _sdb.getOID_(path, dir);
                l.debug("lookup: {} {}", path.toStringFormal(), oid);
                if (oid != null) return oid;
            } catch (SQLException e) {
                // can safely ignore seed-related errors
            }
            // TODO: restore versions on content hash match?
        }
        return new OID(UniqueID.generate());
    }
}
