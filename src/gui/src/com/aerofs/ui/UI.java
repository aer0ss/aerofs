package com.aerofs.ui;

import com.aerofs.controller.ControllerClient;
import com.aerofs.ui.update.Updater;
import com.aerofs.gui.GUI;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableJNotify;

/**
 * Global access points for all the singleton classes used in the Java UI
 *
 * This class should hold references to classes used only by the Java UI, but not by the
 * Controller part of the UI.
 *
 * ie: do not include something in this class if you answer "no" to the question: "Would we
 * still need it if we had only native UIs?"
 */
public class UI
{
    private static IUI _ui;

    public static void set(IUI ui)
    {
        _ui = ui;
    }

    public static IUI get()
    {
        return _ui;
    }

    public static boolean isGUI()
    {
        return _ui instanceof GUI;
    }

    private static final Updater s_updater = Updater.getInstance_();
    private static final UINotifier s_notifier = new UINotifier();
    private static final IDaemonMonitor s_dm = IDaemonMonitor.Factory.create();
    private static final RitualNotificationClient s_rnc = new RitualNotificationClient();
    private static final RootAnchorWatch s_raw = new RootAnchorWatch(new InjectableFile.Factory(),
            new InjectableJNotify());

    private static ControllerClient s_controller;

    // TODO (GS): Move to ControllerService
    public static Updater updater() { return s_updater; }

    // TODO (GS): Move to ControllerService
    public static IDaemonMonitor dm() { return s_dm; }

    // TODO (GS): Move to ControllerService
    public static RitualNotificationClient rnc() { return s_rnc; }

    // TODO (WW): Move to ControllerService
    public static RootAnchorWatch raw() { return s_raw; }

    public static UINotifier notifier() { return s_notifier; }

    public static ControllerClient controller()
    {
        if (s_controller == null) {s_controller = new ControllerClient();}
        return s_controller;
    }
}
