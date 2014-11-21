package com.aerofs.ui;

import com.aerofs.LaunchArgs;
import com.aerofs.base.Lazy;
import com.aerofs.base.analytics.Analytics;
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
import com.aerofs.ui.IDaemonMonitor.Factory;
import com.aerofs.ui.update.Updater;

import static com.aerofs.lib.ChannelFactories.getClientChannelFactory;
import static com.aerofs.lib.ChannelFactories.getServerChannelFactory;

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
    private static RitualClientProvider s_ritualProvider =
            new RitualClientProvider(getClientChannelFactory());
    private static ShellextService s_shellext;

    // N.B. constructing RNC requires the Cfg port base file to be ready. Thus it is not constructed
    // when the class is initialized.
    private static final Lazy<RitualNotificationClient> s_rnc = new Lazy<>(() ->
                    new RitualNotificationClient(new RitualNotificationSystemConfiguration()));

    // N.B. depends on RNC, thus it's not constructed when the class is initialized
    // FIXME (AT): TransferState is meant for just GUI, not CLI
    // should be moved to TransferTrayMenuSection
    private static final Lazy<TransferState> s_ts = new Lazy<>(() -> new TransferState(rnc()));

    private static final Updater s_updater = Updater.getInstance_();
    private static final UINotifier s_notifier = new UINotifier();
    // TODO (AT): meant for both GUI and CLI, currently only used for GUI
    private static final Progresses s_progress = new Progresses();

    private static final SanityPoller s_rap = new SanityPoller();
    private static final UIScheduler s_sched = new UIScheduler();
    private static final Analytics s_analytics = new Analytics(new DesktopAnalyticsProperties());

    private static Factory _idm;

    public static void initialize_(boolean createShellextService, LaunchArgs launchArgs)
    {
        if (createShellextService) {
            s_shellext = new ShellextService(getServerChannelFactory(), s_ritualProvider);
        }
        _idm = new Factory(launchArgs);
    }

    public static ShellextService shellext() { return s_shellext; }

    public static boolean hasShellextService() { return s_shellext == null; }

    public static Updater updater() { return s_updater; }

    public static RitualNotificationClient rnc() { return s_rnc.get(); }

    public static TransferState ts() { return s_ts.get(); }

    public static Progresses progresses() { return s_progress; }

    public static SanityPoller rap() { return s_rap; }

    public static UINotifier notifier() { return s_notifier; }

    public static IDaemonMonitor dm() { return _idm.get(); }

    public static UIScheduler scheduler() { return s_sched; }

    public static IRitualClientProvider ritualClientProvider() { return s_ritualProvider; }

    public static RitualBlockingClient ritual() { return s_ritualProvider.getBlockingClient(); }

    public static RitualClient ritualNonBlocking() { return s_ritualProvider.getNonBlockingClient(); }

    public static Analytics analytics() { return s_analytics; }
}
