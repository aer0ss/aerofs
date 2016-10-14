package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.gui.notif.NotifMessage;
import com.aerofs.gui.notif.NotifService;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.TrayItem;
import org.eclipse.swt.widgets.Widget;

import javax.annotation.Nullable;

public class BalloonsImplSWT implements IBalloonsImpl
{
    private final TrayItem _ti;

    BalloonsImplSWT(TrayIcon icon)
    {
        _ti = icon.getTrayItem();
    }

    @Override
    public void add(MessageType mt, String title, String msg, NotifMessage onClick)
    {
        int icon;
        switch (mt) {
        case WARN:  icon = SWT.ICON_WARNING; break;
        case ERROR: icon = SWT.ICON_ERROR; break;
        default:    icon = SWT.ICON_INFORMATION; break;
        }

        final ToolTip tip = new ToolTip(GUI.get().sh(), SWT.BALLOON | icon);
        tip.setMessage(msg);
        tip.setText(title);

        if (onClick != null) {
            tip.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    NotifService.execNotifFunc(onClick.getType(), onClick.getPayload());
                }
            });
        }

        closeCurrent();
        open(tip);
    }

    @Override
    public boolean hasVisibleBalloon()
    {
        return isNonDisposed(_ti) && isNonDisposed(_ti.getToolTip()) && _ti.getToolTip().isVisible();
    }

    private void open(ToolTip tip)
    {
        if (isNonDisposed(_ti)) {
            _ti.setToolTip(tip);
            tip.setVisible(true);
        }
    }

    private void closeCurrent()
    {
        if (isNonDisposed(_ti) && isNonDisposed(_ti.getToolTip())) {
            ToolTip tt = _ti.getToolTip();
            _ti.setToolTip(null);
            tt.dispose();
        }
    }

    /**
     * Check if a widget is non-null and non-disposed
     */
    private boolean isNonDisposed(@Nullable Widget widget)
    {
        return widget != null && !widget.isDisposed();
    }

    @Override
    public void dispose()
    {
        closeCurrent();
    }
}
