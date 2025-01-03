/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.google.common.base.Preconditions;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

public class CompZephyr extends AbstractCompTransport
{
    private final Logger l = Loggers.getLogger(CompZephyr.class);

    protected StatusDecorator   _decStatus;
    protected StatusDecorator   _decZephyr;
    protected DevicesDecorator  _decDevices;

    public CompZephyr(Composite parent)
    {
        super(parent);
    }

    @Override
    protected @Nonnull Composite createContent(Composite parent)
    {
        Composite content = new Composite(parent, SWT.NONE);

        _decStatus = new StatusDecorator(content);
        _decStatus.setText("Status:");
        _decStatus.setStatus(S.TXT_COLLECTING_NETWORK_INFO);
        _decStatus.setDescription(S.LNK_ZEPHYR_DESC);
        _decStatus.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_TRANSPORTS_INFO));

        _decZephyr = new StatusDecorator(content);
        _decZephyr.setText("Relay Service:");
        _decZephyr.setStatus(S.TXT_COLLECTING_NETWORK_INFO);
        _decZephyr.setDescription(S.LBL_ZEPHYR_SERVER_DESC);

        _decDevices = new DevicesDecorator(content);

        content.setLayout(new GridLayout(2, false));

        return content;
    }

    @Override
    public void setData(Object data)
    {
        Preconditions.checkArgument(data == null || data instanceof TransportDiagnosticsWithDeviceName);
        super.setData(data);

        if (data != null) {
            TransportDiagnosticsWithDeviceName td = (TransportDiagnosticsWithDeviceName) data;
            _decStatus.setStatus("Enabled");
            _decZephyr.setStatus(formatServerStatus(td.getZephyrServer()));

            _decDevices.setData(td.getZephyrDevicesWithName());

            if (td.getZephyrServer().hasReachabilityError()) {
                l.error(td.getZephyrServer().getReachabilityError());
            }
        }
    }
}
