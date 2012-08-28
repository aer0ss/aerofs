package com.aerofs.gui.tray;

import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.TrayItem;

import com.aerofs.gui.GUI;
import com.aerofs.lib.Util;
import com.aerofs.ui.IUI.MessageType;

public class BalloonsImplSWT implements IBalloonsImpl {
    private static final Logger l = Util.l(Balloons.class);

    private final TrayItem _ti;

    BalloonsImplSWT(TrayIcon icon)
    {
        _ti = icon.getTrayItem();
    }

    @Override
    public void add(MessageType mt, String title, String msg, final Runnable onClick)
    {
        l.warn("add BLN " + title);

        l.info("add balloon \"" + title + ": " + msg + "\"");

        int icon;
        switch (mt) {
        case WARN: icon = SWT.ICON_WARNING; break;
        case ERROR: icon = SWT.ICON_ERROR; break;
        default: icon = SWT.ICON_INFORMATION; break;
        }

        final ToolTip tip = new ToolTip(GUI.get().sh(), SWT.BALLOON | icon);
        tip.setMessage(msg);
        tip.setText(title);

        if (onClick != null) {
            tip.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    onClick.run();
                }
            });
        }

        // tip.addListener(SWT.Hide, new Listener() {
        // @Override
        // public void handleEvent(Event event)
        // {
        // // this causes exceptions
        // // TODO BUGBUG will the tool tip disposes itself?
        // //_ti.setToolTip(null);
        // //tip.dispose();
        // Util.verify(tip == _q.remove());
        // if (!_q.isEmpty()) open(_q.peek());
        // }
        // });
        //
        closeCurrent();
        open(tip);
    }

    @Override
    public boolean hasVisibleBalloon()
    {
        return !_ti.isDisposed() && _ti.getToolTip() != null
                && !_ti.getToolTip().isDisposed()
                && _ti.getToolTip().isVisible();
    }

    private void open(ToolTip tip)
    {
        if (!_ti.isDisposed()) {
            Util.l().warn("open BLN " + _ti);

            _ti.setToolTip(tip);
            tip.setVisible(true);
        }
    }

    private void closeCurrent()
    {
        if (!_ti.isDisposed() && _ti.getToolTip() != null
                && !_ti.getToolTip().isDisposed()) {
            ToolTip tt = _ti.getToolTip();
            _ti.setToolTip(null);
            tt.dispose();
        }
    }

    @Override
    public void dispose()
    {
        closeCurrent();
    }

}
