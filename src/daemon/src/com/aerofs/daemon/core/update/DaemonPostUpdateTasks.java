package com.aerofs.daemon.core.update;

import javax.inject.Inject;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.Param.PostUpdate;
import com.aerofs.lib.cfg.CfgAbsAutoExportFolder;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgLocalUser;

/**
 * This class is structurally identical to UIPostUpdateTasks.
 * TODO (WW) use the Template Methods pattern.
 */
public class DaemonPostUpdateTasks
{
    private final CfgDatabase _cfgDB;
    private final IDaemonPostUpdateTask[] _tasks;

    @Inject
    public DaemonPostUpdateTasks(CfgDatabase cfgDB, CoreDBCW dbcw, CfgAbsAuxRoot absAuxRoot,
            CfgLocalUser cfgUser, CfgAbsAutoExportFolder autoExportFolder)
    {
        _cfgDB = cfgDB;

        _tasks = new IDaemonPostUpdateTask[] {
            new DPUTOptimizeCSTableIndex(dbcw),
            new DPUTUpdateEpochTable(dbcw),
            new DPUTCreateActivityLogTables(dbcw),
            new DPUTUpdateSchemaForSyncStatus(dbcw),
            new DPUTAddAggregateSyncColumn(dbcw),
            new DPUTMakeMTimesNaturalNumbersOnly(dbcw),
            new DPUTGetEncodingStats(dbcw),
            new DPUTMigrateRevisionSuffixToBase64(absAuxRoot),
            null, // used to be DPUTResetSyncStatus (for redis migation issue)
            null, // used to be DPUTRestSyncStatus (account for change in vh computation)
            null, // used to be DPUTRestSyncStatus (account for aliasing related crash)
            new DPUTMorphStoreTables(dbcw),
            new DPUTMigrateS3Schema(dbcw, _cfgDB),
            null, // used to be DPUTBreakSyncStatActivityLogDependency with missing commit()
            new DPUTBreakSyncStatActivityLogDependency(dbcw),
            new DPUTResetSyncStatus(dbcw), // bug in AggregateSyncStatus.objectMoved_
            new DPUTMigrateAuxRoot(absAuxRoot),
            new DPUTUpdateSIDGeneration(cfgUser, dbcw),
            null,  // used to be DPUTMigrateDeadAnchorsAndEmigratedNames
            new DPUTMigrateDeadAnchorsAndEmigratedNames(dbcw),
            new DPUTMarkAuxRootAsHidden(absAuxRoot),
            new DPUTCreateLeaveQueueTable(dbcw),
            new DPUTRenameTeamServerAutoExportFolders(autoExportFolder),
            new DPUTSkipFirstLaunch(),
            new DPUTRenameRootDirs(dbcw),
            new DPUTFixExpelledAlias(dbcw)
            // new tasks go here
        };

        // please update this macro whenever new tasks are added
        assert _tasks.length == PostUpdate.DAEMON_POST_UPDATE_TASKS;
    }

    public void run() throws Exception
    {
        // the zero value is required for oldest client to run all the tasks
        assert Key.DAEMON_POST_UPDATES.defaultValue().equals(Integer.toString(0));

        int current = _cfgDB.getInt(Key.DAEMON_POST_UPDATES);

        // N.B: no-op if current >= tasks.length
        for (int i = current; i < _tasks.length; i++) {
            IDaemonPostUpdateTask task = _tasks[i];
            if (task != null) {
                Loggers.getLogger(DaemonPostUpdateTasks.class).warn(task.getClass().getName());
                task.run();
            }

            // update db after every task so if later tasks fail earlier ones won't be run again
            // on the next launch
            _cfgDB.set(Key.DAEMON_POST_UPDATES, i + 1);
        }
    }
}
