package com.aerofs.ui.update.uput;

import java.util.ArrayList;

import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam.PostUpdate;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.ui.UI;
import com.aerofs.ui.IUI.MessageType;

/**
 * This class is structurally identical to DaemonPostUpdateTasks.
 * TODO (WW) use the Template Methods pattern.
 */
public class UIPostUpdateTasks
{
    private final CfgDatabase _cfgDB;
    private final IUIPostUpdateTask[] _tasks;

    public UIPostUpdateTasks(CfgDatabase cfgDB)
    {
        _cfgDB = cfgDB;

        _tasks = new IUIPostUpdateTask[] {
            new UPUTSetDeviceOSFamilyAndName()
        };

        // please update this macro whenever new tasks are added
        assert _tasks.length == PostUpdate.UI_POST_UPDATE_TASKS;

        // the zero value is required for oldest client to run all the tasks
        assert Key.UI_POST_UPDATES.defaultValue().equals(Integer.toString(0));
    }

    /**
     * This is called in a non-UI thread
     * @return true to quit AeroFS
     */
    public boolean run() throws Exception
    {
        // N.B: no-op if current >= tasks.length
        int current = _cfgDB.getInt(Key.UI_POST_UPDATES);

        final ArrayList<String> msgs = new ArrayList<String>();
        boolean suggestReboot = false;
        boolean shutdown = false;
        for (int i = current; i < _tasks.length; i++) {
            IUIPostUpdateTask t = _tasks[i];
            if (t == null) continue;

            // run() must be called before getNotes()
            Loggers.getLogger(UIPostUpdateTasks.class).warn(t.getClass().getSimpleName());
            t.run();

            // update db after each task so the finished tasks won't be executed again on the next
            // launch if later tasks failed
            _cfgDB.set(Key.UI_POST_UPDATES, i + 1);

            // run() must be called *before* calling these methods
            if (!suggestReboot) suggestReboot = t.isRebootSuggested();
            if (!shutdown) shutdown = t.isShutdownRequired();
            if (t.getNotes() != null) for (String msg : t.getNotes()) msgs.add(msg);
        }

        if (!msgs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String msg : msgs) sb.append(msg + "\n\n");

            UI.get().show(MessageType.WARN,
                    "Important message from " + L.product() + ":\n\n" + sb);
        }

        if (suggestReboot) {
            UI.get().show(MessageType.INFO,
                    "A reboot of this computer is highly " +
                    "recommended for the latest " + L.product() +
                    " update to work properly.");
        }

        if (shutdown) {
            UI.dm().stopIgnoreException();
            UI.get().show(MessageType.INFO,
                    L.product() + " will have to shutdown to apply an update." +
                    " Please restart " + L.product() + " manually. Sorry" +
                    " for the inconvenience.");
        }

        return shutdown;
    }
}
