package com.aerofs.daemon.core.update;

import javax.inject.Inject;

import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Param.PostUpdate;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.injectable.InjectableDriver;

/**
 * This class is structurally identical to UIPostUpdateTasks.
 * TODO (WW) use the Template Methods pattern.
 */
public class DaemonPostUpdateTasks
{
    private final CfgDatabase _cfgDB;
    private final CoreDBCW _dbcw;
    private final TransManager _tm;
    private final InjectableDriver _dr;
    private final IStoreDatabase _sdb;
    private final IMetaDatabase _mdb;
    private final MapSIndex2DeviceBitMap _sidx2dbm;
    private final CfgAbsRootAnchor _absRootAnchor;

    @Inject
    public DaemonPostUpdateTasks(CfgDatabase cfgDB, CoreDBCW dbcw, InjectableDriver dr,
            TransManager tm, IStoreDatabase sdb, IMetaDatabase mdb, MapSIndex2DeviceBitMap sidx2dbm,
            CfgAbsRootAnchor absRootAnchor)
    {
        _cfgDB = cfgDB;
        _dbcw = dbcw;
        _dr = dr;
        _tm = tm;
        _sdb = sdb;
        _mdb = mdb;
        _sidx2dbm = sidx2dbm;
        _absRootAnchor = absRootAnchor;
    }

    public void run() throws Exception
    {
        // do not use a static member to avoid permanently occupying the memory
        final IDaemonPostUpdateTask[] tasks = new IDaemonPostUpdateTask[] {
                new DPUTOptimizeCSTableIndex(_dbcw),
                new DPUTUpdateEpochTable(_dbcw),
                new DPUTCreateActivityLogTables(_dbcw, _dr),
                new DPUTUpdateSchemaForSyncStatus(_dbcw),
                new DPUTAddAggregateSyncColumn(_dbcw, _tm, _sdb, _mdb, _sidx2dbm),
                new DPUTMakeMTimesNaturalNumbersOnly(_dbcw),
                new DPUTGetEncodingStats(_dbcw),
                new DPUTMigrateRevisionSuffixToBase64(_absRootAnchor)
                // new tasks go here
        };

        // please update this macro whenever new tasks are added
        assert tasks.length == PostUpdate.DAEMON_POST_UPDATE_TASKS;

        // the zero value is required for oldest client to run all the tasks
        assert Key.DAEMON_POST_UPDATES.defaultValue().equals(Integer.toString(0));

        int current = _cfgDB.getInt(Key.DAEMON_POST_UPDATES);

        // N.B: no-op if current >= tasks.length
        for (int i = current; i < tasks.length; i++) {
            IDaemonPostUpdateTask task = tasks[i];
            if (task != null) {
                Util.l(DaemonPostUpdateTasks.class).warn(task.getClass().getName());
                task.run();
            }

            // update db after every task so if later tasks fail earlier ones won't be run again
            // on the next launch
            _cfgDB.set(Key.DAEMON_POST_UPDATES, i + 1);
        }
    }
}
