package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ClientParam.PostUpdate;
import com.aerofs.lib.cfg.CfgDatabase;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Arrays;

import static com.aerofs.lib.cfg.CfgDatabase.PHOENIX_CONVERSION;
import static com.google.common.base.Preconditions.checkState;

/**
 * Wrapper class to cleanly reintegrate Phoenix conversion DPUTs into the main DPUT sequence.
 */
public abstract class PhoenixDPUT implements IDaemonPostUpdateTask {
    private final Logger l = Loggers.getLogger(getClass());

    @Inject private CfgDatabase _cfgDB;

    // NB: do NOT add anything here!
    private final static Class<?>[] PHOENIX_TASKS = {
            DPUTAddPolarisFetchTables.class,
            DPUTSubmitLocalTreeToPolaris.class,
            DPUTHandlePrePhoenixConflicts.class,
            DPUTDropLegacyTables.class,
            DPUTSyncStatusTableAlterations.class
    };

    static {
        checkState(PHOENIX_TASKS.length == PostUpdate.PHOENIX_CONVERSION_TASKS);
    }

    @Override
    public final void run() throws Exception {
        int idx = Arrays.asList(PHOENIX_TASKS).indexOf(getClass());
        checkState(idx != -1);

        if (_cfgDB.getInt(PHOENIX_CONVERSION) > idx) {
            l.info("conversion already done");
            return;
        }

        runPhoenix();

        _cfgDB.set(PHOENIX_CONVERSION, idx + 1);
    }

    protected abstract void runPhoenix() throws Exception;
}
