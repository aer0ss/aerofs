package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.lib.LibParam.PostUpdate;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.rocklog.RockLog;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class is structurally identical to UIPostUpdateTasks.
 * TODO (WW) use the Template Methods pattern.
 */
public class DaemonPostUpdateTasks
{
    private final CfgDatabase _cfgDB;
    private final IDaemonPostUpdateTask[] _tasks;

    @Inject
    public DaemonPostUpdateTasks(IOSUtil osutil, CfgDatabase cfgDB, CoreDBCW dbcw,
            CfgLocalUser cfgUser, InjectableDriver dr, RockLog rocklog)
    {
        _cfgDB = cfgDB;

        _tasks = new IDaemonPostUpdateTask[] {
            new DPUTOptimizeCSTableIndex(dbcw),
            null, // used to be DPUTUpdateEpochTable
            new DPUTCreateActivityLogTables(dbcw),
            null, // used to be DPUTUpdateSchemaForSyncStatus
            null, // used to be DPUTAddAggregateSyncColumn
            new DPUTMakeMTimesNaturalNumbersOnly(dbcw),
            new DPUTGetEncodingStats(dbcw),
            new DPUTMigrateRevisionSuffixToBase64(),
            null, // used to be DPUTResetSyncStatus (for redis migation issue)
            null, // used to be DPUTResetSyncStatus (account for change in vh computation)
            null, // used to be DPUTResetSyncStatus (account for aliasing related crash)
            new DPUTMorphStoreTables(dbcw),
            new DPUTMigrateS3Schema(dbcw, _cfgDB),
            null, // used to be DPUTBreakSyncStatActivityLogDependency with missing commit()
            null, // used to be DPUTBreakSyncStatActivityLogDependency
            null, // used to be DPUTResetSyncStatus (bug in AggregateSyncStatus.objectMoved_)
            new DPUTMigrateAuxRoot(),
            new DPUTUpdateSIDGeneration(cfgUser, dbcw),
            null,  // used to be DPUTMigrateDeadAnchorsAndEmigratedNames
            new DPUTMigrateDeadAnchorsAndEmigratedNames(dbcw),
            new DPUTMarkAuxRootAsHidden(),
            new DPUTCreateLeaveQueueTable(dbcw),
            null,  // used to be DPUTRenameTeamServerAutoExportFolders (obsoleted by linked TS)
            new DPUTSkipFirstLaunch(),
            new DPUTRenameRootDirs(dbcw),
            null,  // used to be DPUTFixExpelledAlias
            new DPUTFixExpelledAlias(dbcw),
            new DPUTPerPhyRootAuxRoot(),
            new DPUTCreateCAIndex(dbcw),
            new DPUTCreatePendingRootTable(dbcw),
            new DPUTAddStoreNameColumn(dbcw),
            new DPUTAddContributorsTable(dbcw),
            null,  // used to be DPUTCleanupGhostKML
            new DPUTCleanupGhostKML(dbcw),
            new DPUTRefreshBloomFilters(dbcw),
            new DPUTDeleteLargeLibjingleLog(),
            new DPUTAddTamperingDetectionTable(dbcw),
            new DPUTCaseSensitivityHellYeah(dbcw, dr),
            new DPUTMigrateHistoryToHex(),
            new DPUTUpdateACLFromDiscreteRolesToFlags(dbcw),
            new DPUTUpdateNROForAliasedAndMigrated(dbcw, rocklog),
            new DPUTFixNormalizationOSX(osutil, dbcw, dr, rocklog),
            new DPUTUpdateEpochTableAddAuditColumn(dbcw),
            new DPUTFixCNROsOnOSX(osutil, dbcw),
            null, // used to be DPUTResetSyncStatus
            new DPUTFixBlockHistory(dbcw),
            new DPUTUpdateSharedFoldersQueueTable(dbcw),
            new DPUTUpdateCAHash(dbcw),
            new DPUTClearSyncStatusColumns(dbcw),
            new DPUTFixStoreContributors(dbcw),
            // new tasks go here - also, update DAEMON_POST_UPDATE_TASKS counter value below!
        };

        // please update this macro whenever new tasks are added
        checkState(_tasks.length == PostUpdate.DAEMON_POST_UPDATE_TASKS);
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
