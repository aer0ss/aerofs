package com.aerofs.ui.update.uput;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.cfg.CfgDatabase;

import static com.aerofs.lib.cfg.CfgDatabase.UI_POST_UPDATES;

/**
 * This class is similar to DaemonPostUpdateTasks.
 */
public class UIPostUpdateTasks
{
    private final CfgDatabase _cfgDB;
    private final IUIPostUpdateTask[] _tasks;

    public UIPostUpdateTasks(CfgDatabase cfgDB)
    {
        _cfgDB = cfgDB;

        _tasks = new IUIPostUpdateTask[] {
            new UPUTSetDeviceOSFamilyAndName(),
            new UPUTSetContactEmail()
        };

        // please update this macro whenever new tasks are added
        assert _tasks.length == ClientParam.PostUpdate.UI_POST_UPDATE_TASKS;

        // the zero value is required for oldest client to run all the tasks
        assert UI_POST_UPDATES.defaultValue().equals(Integer.toString(0));
    }

    /**
     * This is called in a non-UI thread.
     */
    public void run() throws Exception
    {
        // N.B: no-op if current >= tasks.length
        int current = _cfgDB.getInt(UI_POST_UPDATES);

        for (int i = current; i < _tasks.length; i++) {
            IUIPostUpdateTask t = _tasks[i];
            if (t == null) continue;

            // run() must be called before getNotes()
            Loggers.getLogger(UIPostUpdateTasks.class).warn(t.getClass().getSimpleName());
            t.run();

            // update db after each task so the finished tasks won't be executed again on the next
            // launch if later tasks failed
            _cfgDB.set(UI_POST_UPDATES, i + 1);
        }
    }
}
