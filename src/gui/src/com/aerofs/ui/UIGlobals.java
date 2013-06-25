package com.aerofs.ui;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.controller.ControllerClient;
import com.aerofs.gui.TransferState;
import com.aerofs.lib.analytics.DesktopAnalyticsProperties;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.ritual.RitualClient;
import com.aerofs.ritual.RitualClientProvider;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.aerofs.rocklog.RockLog;
import com.aerofs.ui.update.Updater;

/**
 * Global access points for all the singleton classes used in the Java UI.
 *
 * TODO (WW) The name of this class is not accurate, since AeroFS shell also has a UI (and uses the
 * IUI object) but it doesn't use this class.
 */
public final class UIGlobals
{
    private static RitualClientProvider _ritualProvider;

    public static void setRitualClientProvider(RitualClientProvider ritualProvider)
    {
        _ritualProvider = ritualProvider;
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

    public static Updater updater() { return s_updater; }

    public static RitualNotificationClient rnc() { return s_rnc; }

    public static TransferState ts() { return s_ts; }

    public static SanityPoller rap() { return s_rap; }

    public static UINotifier notifier() { return s_notifier; }

    public static ControllerClient controller()
    {
        if (s_controller == null) { s_controller = new ControllerClient(); }
        return s_controller;
    }

    public static IDaemonMonitor dm() { return IDaemonMonitor.Factory.get(); }

    public static UIScheduler scheduler() { return s_sched; }

    public static InfoCollector ic() { return s_ic; }

    public static IRitualClientProvider ritualClientProvider() { return _ritualProvider; }

    public static RitualBlockingClient ritual() { return _ritualProvider.getBlockingClient(); }

    public static RitualClient ritualNonBlocking() { return _ritualProvider.getNonBlockingClient(); }

    public static Analytics analytics() { return s_analytics; }

    public static RockLog rockLog() { return s_rockLog; }
}
