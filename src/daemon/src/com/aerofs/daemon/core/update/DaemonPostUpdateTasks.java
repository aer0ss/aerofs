package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgKey;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.google.inject.Injector;

import javax.inject.Inject;

import static com.aerofs.lib.cfg.CfgDatabase.DAEMON_POST_UPDATES;
import static com.aerofs.lib.cfg.CfgDatabase.PHOENIX_CONVERSION;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class is similar to UIPostUpdateTasks.
 *
 * We strive to allow old clients to be able to update to the newest release but to
 * avoid dragging around vast amount of migration code which sometimes stand in the
 * way of refactoring we will every now and then prune old DPUTs.
 *
 * Pruning may not *ever* break upgrade from releases that are less than 6 months old.
 * Ideally all releases should be upgradable for roughly 1 year.
 */
public class DaemonPostUpdateTasks
{
    private final CfgDatabase _cfgDB;
    private final CfgUsePolaris _usePolaris;
    private final Injector _injector;

    private final static int UNSUPPORTED = 55;

    private final static Class<?>[] TASKS = {
            DPUTCleanupPrefixes.class,
            DPUTFixOSXFID.class,
            DPUTMigrateStorageConfig.class,
            DPUTAddStoreUsageColumn.class,
            DPUTAddMigrantColumn.class,
            DPUTAddCollectorTables.class
            // new tasks go here - also, update DAEMON_POST_UPDATE_TASKS counter!
    };

    // NB: all conversion tasks MUST be idempotent
    private final static Class<?>[] PHOENIX_TASKS = {
            DPUTAddPolarisFetchTables.class,
            DPUTSubmitLocalTreeToPolaris.class,
            DPUTHandlePrePhoenixConflicts.class,
            // new tasks go here - also, update PHOENIX_CONVERSION_TASKS counter!
    };

    @Inject
    public DaemonPostUpdateTasks(CfgDatabase cfgDB, CfgUsePolaris usePolaris, Injector inj)
    {
        _cfgDB = cfgDB;
        _usePolaris = usePolaris;
        _injector = inj;

        // please update counters whenever new tasks are added
        checkState(UNSUPPORTED + TASKS.length == ClientParam.PostUpdate.DAEMON_POST_UPDATE_TASKS);
        checkState(PHOENIX_TASKS.length == ClientParam.PostUpdate.PHOENIX_CONVERSION_TASKS);
    }

    public void run() throws Exception {
        run(false);
    }

    static int firstValid()
    {
        return UNSUPPORTED;
    }

    void run(boolean dryRun) throws Exception {
        // the zero value is required for oldest client to run all the tasks
        assert DAEMON_POST_UPDATES.defaultValue().equals(Integer.toString(0));

        run(TASKS, DAEMON_POST_UPDATES, UNSUPPORTED, dryRun);

        // This is kinda awkward
        // Because of the choice to do a staggered rollout of Phoenix, we cannot (yet) make the
        // conversion a simple DPUT. Instead, we need to trigger it based on a combination of
        // config value and db state. However we still want to leverage the DPUT infrastructure
        // for safe scheduling/retry and giving UI feedback.
        // Eventually, when the legacy codepath is dropped, this can be folded back into the regular
        // DPUT sequence. This will require some care to avoid breaking the upgrade sequence...
        if (_usePolaris.get()) {
            run(PHOENIX_TASKS, PHOENIX_CONVERSION, 0, dryRun);
        }
    }

    private void run(Class<?>[] tasks, CfgKey k, int first, boolean dryRun) throws Exception {
        int current = _cfgDB.getInt(k);

        if (current < first) {
            SystemUtil.ExitCode.TOO_OLD_TO_UPGRADE.exit();
        }

        // N.B: no-op if current >= tasks.length
        for (int i = current - first; i < tasks.length; i++) {
            IDaemonPostUpdateTask task = (IDaemonPostUpdateTask) _injector.getInstance(tasks[i]);
            if (task != null) {
                Loggers.getLogger(DaemonPostUpdateTasks.class).warn(task.getClass().getName());
                if (!dryRun) task.run();
            }

            // update db after every task so if later tasks fail earlier ones won't be run again
            // on the next launch
            if (!dryRun) _cfgDB.set(k, i + first + 1);
        }
    }
}
