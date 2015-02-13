/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.proto.Diagnostics.JingleDiagnostics;
import com.google.common.base.Preconditions;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

public class CompJingle extends AbstractCompTransport
{
    private final Logger l = Loggers.getLogger(CompJingle.class);

    protected StatusDecorator   _decStatus;
    protected StatusDecorator   _decXmpp;
    protected DevicesDecorator  _decDevices;

    public CompJingle(Composite parent)
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
        _decStatus.setDescription(S.LNK_JINGLE_DESC);
        _decStatus.addSelectionListener(GUIUtil.createUrlLaunchListener(S.URL_TRANSPORTS_INFO));

        _decXmpp = new StatusDecorator(content);
        _decXmpp.setText("Presence Service:");
        _decXmpp.setStatus(S.TXT_COLLECTING_NETWORK_INFO);
        _decXmpp.setDescription(S.LBL_XMPP_DESC);

        _decDevices = new DevicesDecorator(content);

        content.setLayout(new GridLayout(2, false));

        return content;
    }

    @Override
    public void setData(Object data)
    {
        Preconditions.checkArgument(data == null || data instanceof JingleDiagnostics);

        super.setData(data);

        if (data != null) {
            JingleDiagnostics d = (JingleDiagnostics) data;

            Preconditions.checkArgument(d.getXmppServer().hasReachable());

            _decStatus.setStatus("Enabled");
            _decXmpp.setStatus(formatServerStatus(d.getXmppServer()));

            String[] deviceIDs = new String[d.getReachableDevicesCount()];
            for (int i = 0; i < deviceIDs.length; i++) {
                deviceIDs[i] = new DID(BaseUtil.fromPB(d.getReachableDevices(i).getDid())).toStringFormal();
            }
            _decDevices.setItems(deviceIDs);

            // logging error messages
            if (d.getXmppServer().hasReachabilityError()) {
                l.error(d.getXmppServer().getReachabilityError());
            }

            if (d.getStunServer().hasReachabilityError()) {
                l.error(d.getStunServer().getReachabilityError());
            }
        }
    }
}
