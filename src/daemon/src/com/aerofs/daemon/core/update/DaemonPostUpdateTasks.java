package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam.PostUpdate;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class is structurally identical to UIPostUpdateTasks.
 * TODO (WW) use the Template Methods pattern.
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
    private final Injector _injector;

    private static class TOO_OLD_TO_UPGRADE implements IDaemonPostUpdateTask {
        @Override
        public void run() throws Exception {
            SystemUtil.ExitCode.TOO_OLD_TO_UPGRADE.exit();
        }
    }

    @SuppressWarnings("unchecked")
    private final static ImmutableList<Class<? extends IDaemonPostUpdateTask>> _tasks = ImmutableList.of(
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            TOO_OLD_TO_UPGRADE.class,
            DPUTFixStoreContributors.class,
            DPUTAddStoreCollectingContentColumn.class,
            DPUTUpdateOAFlags.class,
            DPUTAddPhysicalStagingArea.class,
            DPUTAddLogicalStagingArea.class,
            DPUTRemoveBackupTicks.class,
            DPUTCleanupPrefixes.class,
            DPUTFixOSXFID.class,
            DPUTMigrateStorageConfig.class
            // new tasks go here - also, update DAEMON_POST_UPDATE_TASKS counter value below!

            // only uncomment when rolling out Polaris to keep maximum flexibility
            // during dev and integration tests
            // DPUTAddPolarisFetchTables.class,
    );


    @Inject
    public DaemonPostUpdateTasks(CfgDatabase cfgDB, Injector inj)
    {
        _cfgDB = cfgDB;
        _injector = inj;

        // please update this macro whenever new tasks are added
        checkState(_tasks.size() == PostUpdate.DAEMON_POST_UPDATE_TASKS);
    }

    public void run() throws Exception {
        run(false);
    }

    static int firstValid()
    {
        return _tasks.lastIndexOf(TOO_OLD_TO_UPGRADE.class) + 1;
    }

    void run(boolean dryRun) throws Exception {
        // the zero value is required for oldest client to run all the tasks
        assert Key.DAEMON_POST_UPDATES.defaultValue().equals(Integer.toString(0));

        int current = _cfgDB.getInt(Key.DAEMON_POST_UPDATES);

        // N.B: no-op if current >= tasks.length
        for (int i = current; i < _tasks.size(); i++) {
            IDaemonPostUpdateTask task = _injector.getInstance(_tasks.get(i));
            if (task != null) {
                Loggers.getLogger(DaemonPostUpdateTasks.class).warn(task.getClass().getName());
                if (!dryRun) task.run();
            }

            // update db after every task so if later tasks fail earlier ones won't be run again
            // on the next launch
            _cfgDB.set(Key.DAEMON_POST_UPDATES, i + 1);
        }
    }
}
