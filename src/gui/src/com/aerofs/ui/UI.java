package com.aerofs.ui;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.controller.ControllerClient;
import com.aerofs.gui.GUI;
import com.aerofs.gui.TransferState;
import com.aerofs.lib.analytics.DesktopAnalyticsProperties;
import com.aerofs.lib.ritual.IRitualClientProvider;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientProvider;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.ui.update.Updater;

/**
 * Global access points for all the singleton classes used in the Java UI
 *
 * This class should hold references to classes used only by the Java UI, but not by the
 * Controller part of the UI.
 *
 * ie: do not include something in this class if you answer "no" to the question: "Would we
 * still need it if we had only native UIs?"
 */
public final class UI
{
    private static IUI _ui;
    private static RitualClientProvider _ritualProvider;

    public static void init(IUI ui, RitualClientProvider ritualProvider)
    {
        _ui = ui;
        _ritualProvider = ritualProvider;
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
    private static final RitualNotificationClient s_rnc = new RitualNotificationClient();
    private static final TransferState s_ts = new TransferState(s_rnc);
    private static final SanityPoller s_rap = new SanityPoller();
    private static final InfoCollector s_ic = new InfoCollector();
    private static final UIScheduler s_sched = new UIScheduler();
    private static final Analytics s_analytics = new Analytics(new DesktopAnalyticsProperties());
    private static final RockLog s_rockLog = new RockLog();

    private static ControllerClient s_controller;

    // TODO (GS): Move to ControllerService
    public static Updater updater() { return s_updater; }

    // TODO (GS): Move to ControllerService
    public static RitualNotificationClient rnc() { return s_rnc; }

    public static TransferState ts() { return s_ts; }

    // TODO (WW): Move to ControllerService
    public static SanityPoller rap() { return s_rap; }

    public static UINotifier notifier() { return s_notifier; }

    public static ControllerClient controller()
    {
        if (s_controller == null) {s_controller = new ControllerClient();}
        return s_controller;
    }

    // TODO (GS): Move to ControllerService
    public static IDaemonMonitor dm() { return IDaemonMonitor.Factory.get(); }

    public static UIScheduler scheduler() { return s_sched; }

    public static InfoCollector ic() { return s_ic; }

    public static IRitualClientProvider ritualClientProvider() { return _ritualProvider; }

    public static RitualBlockingClient ritual() { return _ritualProvider.getBlockingClient(); }

    public static RitualClient ritualNonBlocking() { return _ritualProvider.getNonBlockingClient(); }

    public static Analytics analytics() { return s_analytics; }

    public static RockLog rockLog() { return s_rockLog; }
}
