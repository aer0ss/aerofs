package com.aerofs.gui.tray;

import org.eclipse.swt.SWT;
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

public class TrayIcon
{
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

}
