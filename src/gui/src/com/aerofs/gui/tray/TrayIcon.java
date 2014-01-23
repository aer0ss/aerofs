package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.tray.Progresses.ProgressUpdatedListener;
import com.aerofs.gui.tray.TrayIcon.TrayPosition.Orientation;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ritual_notification.RitualNotificationClient;
import com.aerofs.ritual_notification.RitualNotificationSystemConfiguration;
import com.aerofs.sv.client.SVClient;
import com.aerofs.swig.driver.Driver;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
import org.eclipse.swt.widgets.UbuntuTrayItem;
import org.eclipse.swt.widgets.Widget;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class TrayIcon implements ITrayMenuListener
{
    private static final Logger l = Loggers.getLogger(TrayIcon.class);

    private static final ClickEvent TRAY_ICON_DEFAULT_ACTION = new ClickEvent( Action.TRAY_ICON_DEFAULT_ACTION, Source.TASKBAR);
    private static final ClickEvent TRAY_ICON_CLICKED = new ClickEvent(Action.TRAY_ICON, Source.TASKBAR);

    private static final String TOOLTIP_PREFIX = L.product();
    private static final String DEFAULT_TOOLTIP = TOOLTIP_PREFIX + " " + Cfg.ver() +
            (OSUtil.isOSX() ? "" : "\nDouble click to open " + L.product() + "folder");

    private final SystemTray _st;
    private final TrayItem _ti;
    private final UbuntuTrayItem _uti;

    private boolean _isOnline;
    private String _tooltip;
    private String _iconName;

    private final ProgressListener _progressListener = new ProgressListener();

    // TrayIcon needs to have its own RNC to prevent a race condition between registering listener
    // and starting the client leading to the tray icon missing notifications.
    private RitualNotificationClient _rnc;

    TrayIcon(SystemTray st)
    {
        _st = st;
        Tray tray = GUI.get().disp().getSystemTray();
        if (tray == null) {
            SystemUtil.fatal("System tray not found");
        }

        l.debug("UbuntuTrayItem support: {}", UbuntuTrayItem.supported());
        if (UbuntuTrayItem.supported()) {
            _uti = new UbuntuTrayItem(tray, SWT.NONE, L.product());
            // We now ship the hicolor/ icon theme folder in the icons folder on Linux.
            // Note that the icon theme must reside in a folder named "icons" to work on KDE.
            // As a result, the name is kept as a separate string here.
            _uti.setIconPath(new File(AppRoot.abs(), LibParam.FDO_ICONS_DIR).getAbsolutePath());
            _uti.setStatus(UbuntuTrayItem.ACTIVE);
            _ti = null;
        } else {
            _uti = null;
            _ti = new TrayItem(tray, SWT.NONE);

            if (!OSUtil.isOSX()) {
                _ti.addListener(SWT.DefaultSelection, new AbstractListener(TRAY_ICON_DEFAULT_ACTION) {
                    @Override
                    public void handleEventImpl(Event event)
                    {
                        GUIUtil.launch(Cfg.absDefaultRootAnchor());
                    }
                });
            }

            Listener showMenu = new AbstractListener(TRAY_ICON_CLICKED) {
                @Override
                public void handleEventImpl(Event event)
                {
                    _st.setMenuVisible(true);
                }
            };

            _ti.addListener(SWT.MenuDetect, showMenu);

            // On non-OSX platforms, also show the menu the user left-click on the icon
            // This happen by default on OSX
            if (!OSUtil.isOSX()) _ti.addListener(SWT.Selection, showMenu);

            // On OSX, we need to set the "highlight image". This is just the regular tray icon
            // with its colors inverted.
            if (OSUtil.isOSX()) _ti.setHighlightImage(Images.getTrayIcon("tray_inverted"));
        }

        updateToolTipText();
        refreshTrayIconImage();

        addOnlineStatusListener();
        UIGlobals.progresses().addListener(_progressListener);
    }

    TrayItem getTrayItem()
    {
        return _ti;
    }

    private Widget iconImpl()
    {
        return Objects.firstNonNull(_uti, _ti);
    }

    public void dispose()
    {
        UIGlobals.progresses().removeListener(_progressListener);
        UIGlobals.progresses().removeAllProgresses();

        if (_ti != null) _ti.dispose();
        if (_uti != null) _uti.dispose();
        _rnc.stop();
    }

    public boolean isDisposed()
    {
        if (_ti != null) return _ti.isDisposed();
        return _uti.isDisposed();
    }

    public void updateToolTipText()
    {
        checkState(GUI.get().isUIThread());

        if (_ti != null) {
            String tooltip = getToolTipText();
            if (!tooltip.equals(_tooltip)) {
                _tooltip = tooltip;
                _ti.setToolTipText(_tooltip);
            }
        }
    }

    private String getToolTipText()
    {
        return !_isOnline ? S.SERVER_OFFLINE_TOOLTIP :
                !UIGlobals.progresses().getProgresses().isEmpty() ?
                        TOOLTIP_PREFIX + " | " + UIGlobals.progresses().getProgresses().get(0) :
                DEFAULT_TOOLTIP;
    }

    @Override
    public void onTrayMenuChange(Menu menu)
    {
        l.debug("onTrayMenuChange() - Menu is {}", menu);
        if (_uti != null) {
            _uti.setMenu(menu);
        }
    }

    public static enum NotificationReason
    {
        UPDATE,
        CONFLICT,
        UNSYNCABLE_FILE
    }

    private final Set<NotificationReason> _notificationReasons = Sets.newHashSet();

    public void clearNotifications()
    {
        _notificationReasons.clear();

        GUI.get().safeAsyncExec(iconImpl(), new Runnable() {
            @Override
            public void run()
            {
                refreshTrayIconImage();
            }
        });
    }

    public void showNotification(NotificationReason reason, boolean b)
    {
        if (b) {
            _notificationReasons.add(reason);
        } else {
            _notificationReasons.remove(reason);
        }

        GUI.get().safeAsyncExec(iconImpl(), new Runnable() {
            @Override
            public void run()
            {
                refreshTrayIconImage();
            }
        });
    }

    private void refreshTrayIconImage()
    {
        String iconName = Images.getTrayIconName(
                _isOnline,
                !_notificationReasons.isEmpty(),
                !UIGlobals.progresses().getProgresses().isEmpty(),
                false, // TODO: implement hooking into syncstat
                false, // TODO: implement detecting HDPI
                OSUtil.isWindows() && !OSUtil.isWindowsXP());

        /*
         * This optimization is necessary to prevent tray icon from flickering at start up.
         *
         * The root cause is a combination of 3 reasonble things that sums up to a SNAFU:
         * - we aggressive refresh tray icon whenever the icon state _can_ change.
         * - at start up, there's a burst of events that requires us to check if the tray icon
         *   has changed:
         *   * RNC trying to connect to daemon, who is not up yet, causing the channel to break
         *     immediately triggering a refresh.
         *   * when the connection is established, 2-3 notifications come in from initial connect
         *     casuing the tray icon to refresh.
         * - since we use asyncExec to refresh, we end up queuing 2-3 refresh requests in a short
         *   period of time. When the SWT event thread resumes, it refreshes many times in quick
         *   succession.
         *
         * SWT doesn't handle that well even though we are setting it to the same image most of
         * the times.
         */
        if (!iconName.equals(_iconName)) {
            _iconName = iconName;

            if (_uti != null) {
                _uti.setIcon(iconName, L.product());
            }
            if (_ti != null) {
                _ti.setImage(Images.getTrayIcon(iconName));
            }
        }
    }

    public static class TrayPosition
    {
        public enum Orientation {Top, Right, Bottom, Left}
        public int x;
        public int y;
        public Orientation orientation;

        public TrayPosition() {}

        public TrayPosition(int x, int y, Orientation o)
        {
            this.x = x;
            this.y = y;
            this.orientation = o;
        }
    }

    /**
     * @return the tray icon position and orientation, if supported by the platform, or null
     */
    public @Nullable TrayPosition getPosition()
    {
        if (OSUtil.isOSX()) {
            return getPositionOSX();
        } else if (OSUtil.isWindows()) {
            return getPositionWin();
        } else {
            return null;
        }
    }

    private TrayPosition getPositionOSX()
    {
        try {
            Method getLocation = _ti.getClass().getDeclaredMethod("getLocation");
            getLocation.setAccessible(true);
            Point pos = (Point)getLocation.invoke(_ti);
            return new TrayPosition(pos.x, pos.y, Orientation.Top);
        } catch (Exception e) {
            l.warn("Failed to get tray icon position: " + Util.e(e));
            SVClient.logSendDefectAsync(true, "failed to get tray icon position from SWT", e);
            return null;
        }
    }

    private TrayPosition getPositionWin()
    {
        com.aerofs.swig.driver.TrayPosition pos = Driver.getTrayPosition();

        TrayPosition tp = new TrayPosition();
        tp.x = pos.getX();
        tp.y = pos.getY();

        // If the coordinates are (0,0) the native call failed.
        if (tp.x == 0 && tp.y == 0) {
            SVClient.logSendDefectAsync(true, "failed to get tray icon position from driver");
            return null;
        }

        final int o = pos.getOrientation();
        // Java doesn't want a switch() here because those are not compile-time constants
        if (o == com.aerofs.swig.driver.TrayPosition.Top) { tp.orientation = Orientation.Top; }
        else if (o == com.aerofs.swig.driver.TrayPosition.Right) { tp.orientation = Orientation.Right; }
        else if (o == com.aerofs.swig.driver.TrayPosition.Bottom) { tp.orientation = Orientation.Bottom; }
        else if (o == com.aerofs.swig.driver.TrayPosition.Left) { tp.orientation = Orientation.Left; }

        return tp;
    }

    public void addOnlineStatusListener()
    {
        _rnc = new RitualNotificationClient(new RitualNotificationSystemConfiguration());
        _rnc.addListener(new IRitualNotificationListener()
        {
            @Override
            public void onNotificationReceived(PBNotification notification)
            {
                if (notification.getType() == Type.ONLINE_STATUS_CHANGED) {
                    checkArgument(notification.hasOnlineStatus());

                    updateOnlineStatus(notification.getOnlineStatus());
                }
            }

            @Override
            public void onNotificationChannelBroken()
            {
                updateOnlineStatus(false);
            }

            private void updateOnlineStatus(final boolean isOnline)
            {
                GUI.get().safeAsyncExec(iconImpl(), new Runnable()
                {
                    @Override
                    public void run()
                    {
                        _isOnline = isOnline;
                        updateToolTipText();
                        refreshTrayIconImage();
                    }
                });
            }
        });
        _rnc.start();
    }

    private class ProgressListener implements ProgressUpdatedListener
    {
        @Override
        public void onProgressAdded(Progresses progresses, String message)
        {
            if (isDisposed()) return;
            GUI.get().notify(MessageType.INFO, message + "...");
        }

        @Override
        public void onProgressChanged(Progresses progresses)
        {
            GUI.get().safeAsyncExec(iconImpl(), new Runnable()
            {
                @Override
                public void run()
                {
                    updateToolTipText();
                    refreshTrayIconImage();
                }
            });
        }
    }
}
