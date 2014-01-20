package com.aerofs.ui;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.controller.Setup;
import com.aerofs.gui.TransferState;
import com.aerofs.gui.shellext.ShellextService;
import com.aerofs.gui.tray.Progresses;
import com.aerofs.lib.analytics.DesktopAnalyticsProperties;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.ritual.RitualClient;
import com.aerofs.ritual.RitualClientProvider;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.aerofs.ritual_notification.RitualNotificationSystemConfiguration;
import com.aerofs.rocklog.RockLog;
import com.aerofs.ui.update.Updater;

/**
 * Global access points for all the singleton classes used in the Java UI. (both GUI & CLI)
 *
 * TODO (AT) right now some of the objects here are only used for GUI and not CLI.
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

    // explicitly set to null because hasShellextService() expects it to be null if unset.
    private static ShellextService _shellext = null;

    public static ShellextService shellext()
    {
        return _shellext;
    }

    public static void setShellextService(ShellextService shellext)
    {
        _shellext = shellext;
    }

    public static boolean hasShellextService()
    {
        return _shellext == null;
    }

    public static void initSetup_(String rtRoot) { s_setup = new Setup(rtRoot); }

    private static final Updater s_updater = Updater.getInstance_();
    private static Setup s_setup;

    private static final UINotifier s_notifier = new UINotifier();
    private static final RitualNotificationClient s_rnc = new RitualNotificationClient(
            new RitualNotificationSystemConfiguration());
    // FIXME (AT): TransferState is meant for just GUI, not CLI
    private static final TransferState s_ts = new TransferState(s_rnc);
    // TODO (AT): meant for both GUI and CLI, currently on used for GUI
    private static final Progresses s_progress = new Progresses();

    private static final SanityPoller s_rap = new SanityPoller();
    private static final InfoCollector s_ic = new InfoCollector();
    private static final UIScheduler s_sched = new UIScheduler();
    private static final Analytics s_analytics = new Analytics(new DesktopAnalyticsProperties());
    private static final RockLog s_rockLog = new RockLog();

    public static Updater updater() { return s_updater; }

    public static RitualNotificationClient rnc() { return s_rnc; }

    public static TransferState ts() { return s_ts; }

    public static Progresses progresses() { return s_progress; }

    public static SanityPoller rap() { return s_rap; }

    public static UINotifier notifier() { return s_notifier; }

    // TODO (hugues): this is indicative of design issues in the setup logic
    // a refactoring of that code would be nice but I don't have time for this now
    public static Setup setup() { return s_setup; }

    public static IDaemonMonitor dm() { return IDaemonMonitor.Factory.get(); }

    public static UIScheduler scheduler() { return s_sched; }

    public static InfoCollector ic() { return s_ic; }

    public static IRitualClientProvider ritualClientProvider() { return _ritualProvider; }

    public static RitualBlockingClient ritual() { return _ritualProvider.getBlockingClient(); }

    public static RitualClient ritualNonBlocking() { return _ritualProvider.getNonBlockingClient(); }

    public static Analytics analytics() { return s_analytics; }

    public static RockLog rockLog() { return s_rockLog; }
}
