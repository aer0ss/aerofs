/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.proto.Ritual.GetDiagnosticsReply;
import com.aerofs.proto.Sp;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.protobuf.ByteString;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import java.util.Collections;
import java.util.List;

public class DlgTransportDiagnostics extends AeroFSDialog
{
    protected Composite _contents;

    public DlgTransportDiagnostics(Shell parent)
    {
        super(parent, "Network Diagnostics", false, true);
    }

    @Override
    protected void open(Shell shell)
    {
        _contents = new CompTransportDiagnostics(shell);
        Composite buttonsBar = createButtonsBar(shell);

        GridLayout layout = new GridLayout();
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        shell.setLayout(layout);

        _contents.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        buttonsBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));

        refreshData();
    }

    protected void refreshData()
    {
        new RefreshTask(GUI.get(), _contents, UIGlobals.ritualClientProvider()).run();
    }

    protected Composite createButtonsBar(Composite parent)
    {
        Composite buttonsBar = GUIUtil.newButtonContainer(parent, false);

        Button btnRefresh = GUIUtil.createButton(buttonsBar, SWT.PUSH);
        btnRefresh.setText("Refresh");
        btnRefresh.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                refreshData();
            }
        });

        Button btnClose = GUIUtil.createButton(buttonsBar, SWT.PUSH);
        btnClose.setText(IDialogConstants.CLOSE_LABEL);
        btnClose.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });

        return buttonsBar;
    }

    @Override
    protected void setShellSize()
    {
        getShell().setSize(840, 600);
    }

    private class RefreshTask implements FutureCallback<GetDiagnosticsReply>
    {
        private final GUI _gui;
        private final Widget _widget;
        private final IRitualClientProvider _provider;

        // N.B. it's necessary to use a IRitualClientProvider because a client doesn't
        //   maintain its connection
        public RefreshTask(GUI gui, Widget widget, IRitualClientProvider provider)
        {
            _gui = gui;
            _widget = widget;
            _provider = provider;
        }

        public void run()
        {
            Futures.addCallback(_provider.getNonBlockingClient().getDiagnostics(), this);
        }

        @Override
        public void onSuccess(final GetDiagnosticsReply reply)
        {

            _gui.safeAsyncExec(_widget, new Runnable()
            {
                @Override
                public void run()
                {
                    TransportDiagnosticsWithDeviceName td = new TransportDiagnosticsWithDeviceName(reply.getTransportDiagnostics());

                    try {
                        new DeviceNameLookupTask(td, InjectableSPBlockingClientFactory.newMutualAuthClientFactory().create().signInRemote(), _widget).dispatch();
                    } catch (Exception e) {
                        ErrorMessages.show(getShell(), e, S.ERR_GET_TRANSPORTS_INFO_FAILED);
                        _widget.setData(null);
                    }
                }
            });

        }

        @Override
        public void onFailure(final Throwable throwable)
        {
            _gui.safeAsyncExec(_widget, new Runnable()
            {
                @Override
                public void run()
                {
                    ErrorMessages.show(getShell(), throwable, S.ERR_GET_TRANSPORTS_INFO_FAILED);
                    _widget.setData(null);
                }
            });
        }
    }

    private class DeviceNameLookupTask implements GUI.ISWTWorker {
        private final SPBlockingClient _spClient;
        private final Widget _widget;
        private TransportDiagnosticsWithDeviceName _td;

        public DeviceNameLookupTask(TransportDiagnosticsWithDeviceName td, SPBlockingClient spClient, Widget widget) {
            _td = td;
            _spClient = spClient;
            _widget = widget;
        }

        public void dispatch() {
            GUI.get().safeWork(_widget, this);
        }

        @Override
        public void run() throws Exception {
            List<ByteString> tcpDids = Lists.newArrayList();
            List<ByteString> zephyrDids = Lists.newArrayList();
            List<ByteString> dids = Lists.newArrayList();

            _td.getTransportDiagnostics().getTcpDiagnostics().getReachableDevicesList().forEach(device -> tcpDids.add(device.getDid()));
            _td.getTransportDiagnostics().getZephyrDiagnostics().getReachableDevicesList().forEach(device -> zephyrDids.add(device.getDid()));
            dids.addAll(tcpDids);
            dids.addAll(zephyrDids);

            //Get device info with list of DIDs
            Sp.GetDeviceInfoReply reply = _spClient.getDeviceInfo(dids);

            //Check whether the number of devices returned is the same as the number passed in
            if (reply.getDeviceInfoCount() != dids.size()) {
                throw new ExProtocolError("server reply count mismatch (" +
                        reply.getDeviceInfoCount() + " != " + dids.size() + ")");
            }

            //Adding the device name in the same order as we parse the DID above.
            //Order is preserved when we call the SP client
            for (int i = 0; i < tcpDids.size(); i++) {
                Sp.GetDeviceInfoReply.PBDeviceInfo di = reply.getDeviceInfo(i);

                if(di.hasOwner() && di.hasDeviceName()) {
                    _td.addToTCPDeviceList(new TCPDeviceWithName(tcpDids.get(i),
                                    _td.getTransportDiagnostics().getTcpDiagnostics().getReachableDevices(i).getDeviceAddress().getHost(),
                                    di.getDeviceName(),
                                    di.getOwner().getUserEmail()));
                }
            }

            Collections.sort(_td.getTcpDevicesWithName());

            //Add Zephyr Devices. Don't need to check if there is an owner and device name because
            //only zephyr devices that is in the organization will be returned.
            for (int i = 0; i < zephyrDids.size(); i++){
                int offset = tcpDids.size() + i;
                Sp.GetDeviceInfoReply.PBDeviceInfo di = reply.getDeviceInfo(offset);

                _td.addToZephyrDeviceList(new ZephyrDeviceWithName(zephyrDids.get(i),
                        di.getDeviceName(),
                        di.getOwner().getUserEmail()));
            }

            Collections.sort(_td.getZephyrDevicesWithName());

        }

        @Override
        public void okay() {
            _widget.setData(_td);
        }

        @Override
        public void error(Exception e) {
            ErrorMessages.show(getShell(), e, S.ERR_GET_TRANSPORTS_INFO_FAILED);
            _widget.setData(null);
        }

    }
}
