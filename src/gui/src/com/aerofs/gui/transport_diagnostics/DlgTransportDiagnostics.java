/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.transport_diagnostics;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.S;
import com.aerofs.proto.Ritual.GetDiagnosticsReply;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
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
                    // we only support transport diagnostics
                    // if the transport diagnostics don't exist, then
                    // getTransportDiagnostics() will return null, which is
                    // handled properly by the underlying component
                    _widget.setData(reply.getTransportDiagnostics());
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
}
