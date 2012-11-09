package com.aerofs.gui.tray;

import com.aerofs.gui.tray.TrayIcon.TrayPosition.Orientation;
import com.aerofs.sv.client.SVClient;
import com.aerofs.swig.driver.Driver;
import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil.AbstractListener;
import com.aerofs.gui.Images;
import com.aerofs.l.L;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Sv;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

public class TrayIcon
{
    private final static Logger l = Util.l(TrayIcon.class);
    private final SystemTray _st;
    private final TrayItem _ti;
    private Thread _thdSpinning;
    private boolean _showNotification;
    private int _iconIndex;

    TrayIcon(SystemTray st)
    {
        _st = st;
        Tray tray = GUI.get().disp().getSystemTray();
        if (tray == null) {
            Util.fatal("System tray not found");
        }

        _ti = new TrayItem(tray, SWT.NONE);

        _ti.addListener(SWT.Show, new AbstractListener(null) {
            @Override
            public void handleEventImpl(Event event)
            {
            }
        });
        _ti.addListener(SWT.Hide, new AbstractListener(null) {
            @Override
            public void handleEventImpl(Event event)
            {
            }
        });

        if (!OSUtil.isOSX()) {
            _ti.addListener(SWT.DefaultSelection, new AbstractListener(Sv.PBSVEvent.Type.CLICKED_TASKBAR_DEFAULT_SELECTION) {
                @Override
                public void handleEventImpl(Event event)
                {
                    Program.launch(Cfg.absRootAnchor());
                }
            });
        }
        _ti.addListener(SWT.MenuDetect, new AbstractListener(Sv.PBSVEvent.Type.CLICKED_TASKBAR) {
            @Override
            public void handleEventImpl(Event event)
            {
                if (_st.getMenu() != null) _st.getMenu().setVisible(true);
            }
        });

        setSpin(false);
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

    private void refreshTrayIconImage()
    {
        _ti.setImage(Images.getTrayIcon(_showNotification, _iconIndex));
    }

    TrayItem getTrayItem()
    {
        return _ti;
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
                    Util.sleepUninterruptable(L.get().trayIconAnimationFrameInterval());

                    GUI.get().safeExec(_ti, new Runnable() {
                        @Override
                        public void run()
                        {
                            if (_thdSpinning != _self || _ti.isDisposed()) {
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
        _ti.dispose();
    }

    public boolean isDisposed()
    {
       return _ti.isDisposed();
    }

    public void setToolTipText(String str)
    {
        _ti.setToolTipText(str);
    }

    public void showNotification(boolean b)
    {
       _showNotification = b;

       GUI.get().safeAsyncExec(_ti, new Runnable() {
            @Override
            public void run()
            {
                refreshTrayIconImage();
            }
        });
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
}
