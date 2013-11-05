package com.aerofs.gui.tray;

import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Action;
import com.aerofs.base.analytics.AnalyticsEvents.ClickEvent.Source;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.gui.tray.TrayIcon.TrayPosition.Orientation;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.sv.client.SVClient;
import com.aerofs.swig.driver.Driver;
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

public class TrayIcon implements ITrayMenuListener
{
    private static final Logger l = Loggers.getLogger(TrayIcon.class);

    // milliseconds between frames. in testing, libappindicator was unable to
    // handle anything faster than ~8fps, so give it a longer interval.
    private final static long ANIMATION_INTERVAL = UbuntuTrayItem.supported() ? 200 : 80;
    private static final ClickEvent TRAY_ICON_DEFAULT_ACTION = new ClickEvent( Action.TRAY_ICON_DEFAULT_ACTION, Source.TASKBAR);
    private static final ClickEvent TRAY_ICON_CLICKED = new ClickEvent(Action.TRAY_ICON, Source.TASKBAR);

    private final SystemTray _st;
    private final TrayItem _ti;
    private final UbuntuTrayItem _uti;
    private Thread _thdSpinning;
    private int _iconIndex;

    // stores what the tooltip would be if the device is online, this is needed because when the
    // device is offline, the offline message takes precedence over other messages.
    private String _tooltip;
    private boolean _isOnline;
    private String _iconName;

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
            _uti.setIcon("tray0", L.product());
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
        }

        setSpin(false);

        addOnlineStatusListener();
    }

    public void setSpin(boolean spin)
    {
        assert GUI.get().isUIThread();

        if (!spin) {
            _thdSpinning = null;
            _iconIndex = 0;
            refreshTrayIconImage();
        } else if (_thdSpinning == null) {
            _iconIndex = 0;
            refreshTrayIconImage();
            startAnimation();
        }
    }

    TrayItem getTrayItem()
    {
        return _ti;
    }

    private Widget iconImpl()
    {
        return Objects.firstNonNull(_uti, _ti);
    }

    private void startAnimation()
    {
        _thdSpinning = new Thread() {
            boolean _done = false;
            Thread _self = this;

            @Override
            public void run()
            {
                while (!_done) {
                    ThreadUtil.sleepUninterruptable(ANIMATION_INTERVAL);

                    GUI.get().safeExec(iconImpl(), new Runnable() {
                        @Override
                        public void run()
                        {
                            if (_thdSpinning != _self || iconImpl().isDisposed()) {
                                _done = true;
                            } else {
                                _iconIndex++;
                                refreshTrayIconImage();
                            }
                        }
                    });
                }
            }
        };

        _thdSpinning.setDaemon(true);
        _thdSpinning.start();
    }

    public void dispose()
    {
        if (_ti != null) _ti.dispose();
        if (_uti != null) _uti.dispose();
    }

    public boolean isDisposed()
    {
        if (_ti != null) return _ti.isDisposed();
        return _uti.isDisposed();
    }

    /**
     * HACK ALERT (AT): the intended tooltip text logic is to use the server offline tooltip
     *   if the server status is offline and to use the previously set tooltip otherwise.
     *   The method to achieve this is whenever tooltip is set, it checks the server status.
     *   And whenever server status change, it calls setToolTipText(_tooltip).
     */
    public void setToolTipText(String str)
    {
        if (_ti != null) {
            _tooltip = str;
            _ti.setToolTipText(_isOnline ? _tooltip : S.SERVER_OFFLINE_TOOLTIP);
        }
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
        CONFLICT
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
        String iconName = Images.getTrayIconName(_isOnline, !_notificationReasons.isEmpty(),
                _iconIndex);

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
        UIGlobals.rnc().addListener(new IRitualNotificationListener() {
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
                        setToolTipText(_tooltip);
                        refreshTrayIconImage();
                    }
                });
            }
        });
    }
}
