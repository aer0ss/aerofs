package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.CfgDatabase;
import org.slf4j.Logger;

import javax.inject.Inject;

import static com.aerofs.lib.cfg.CfgDatabase.S3_LEGACY_ENCRYPTION_PASSWORD;
import static com.aerofs.lib.cfg.CfgDatabase.STORAGE_ENCRYPTION_PASSWORD;

/**
 * Rename the `s3_encryption_password` configuration value to `remote_storage_encryption_password`
 * (because this password is now used by both S3 and Swift backends)
 */
public class DPUTMigrateStorageConfig implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTMigrateStorageConfig.class);
    private final CfgDatabase _db;

    @Inject
    public DPUTMigrateStorageConfig(CfgDatabase db)
    {
        _db = db;
    }

    @Override
    public void run() throws Exception {
        // Legacy value ?
        String legacyPassword = _db.getNullable(S3_LEGACY_ENCRYPTION_PASSWORD);

        if (legacyPassword != null) {
            // Abort if already existing
            if (_db.getNullable(STORAGE_ENCRYPTION_PASSWORD) != null) {
                l.debug("There is already a STORAGE_ENCRYPTION_PASSWORD value in the db along the legacy one. Aborting.");
                return;
            }

            _db.set(STORAGE_ENCRYPTION_PASSWORD, legacyPassword);
        }
    }
}
