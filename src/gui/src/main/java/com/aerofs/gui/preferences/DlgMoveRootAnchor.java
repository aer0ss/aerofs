/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.preferences;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.S;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIParam;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.jboss.netty.channel.ChannelException;
import org.slf4j.Logger;

import java.io.IOException;

import static com.aerofs.gui.GUIUtil.createLabel;

/**
 * openDialog() returns whether if the operation succeeds
 */
public class DlgMoveRootAnchor extends AeroFSDialog implements ISWTWorker
{
    private static final Logger l = Loggers.getLogger(DlgMoveRootAnchor.class);

    private final String _absAnchorRoot;
    private CompSpin _compSpin;

    public DlgMoveRootAnchor(Shell parent, boolean sheet, String absAnchorRoot)
    {
        super(parent, "Moving " + S.ROOT_ANCHOR, sheet, false);
        _absAnchorRoot = absAnchorRoot;
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        GridLayout glShell = new GridLayout(2, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        glShell.verticalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(glShell);

        _compSpin = new CompSpin(shell, SWT.NONE);

        Label lblMovingTheAerofs = createLabel(shell, SWT.NONE);
        lblMovingTheAerofs.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        lblMovingTheAerofs.setText("Moving files and Sync History. Please do not quit " + L.product() + ".");

        getShell().addListener(SWT.Show, new Listener() {
            @Override
            public void handleEvent(Event arg0)
            {
                _compSpin.start();

                GUI.get().safeWork(getShell(), DlgMoveRootAnchor.this);
            }
        });
    }

    @Override
    public void run() throws Exception
    {
        try {
            UIGlobals.ritual().relocate(RootAnchorUtil.adjustRootAnchor(_absAnchorRoot, null), null);
        } catch (ChannelException e) {
            // ChannelException or IOException (depending on the OS) is thrown when the daemon exits
            // which is a result of a successful move.
            //
            // N.B. ignoring these exceptions may cause false positives, i.e. the operation fails
            // due to connection problems but the code thinks the operation succeeds. Although with
            // the current code logic, false positives don't lead to harmful effects, it might not
            // hold true in future implementations.
        } catch (IOException e) {
            // See above.
        }

        // update root anchor in the Cfg class right away
        Cfg.init_(Cfg.absRTRoot(), false);

        l.warn("wait for heartbeat");

        // wait until the daemon restarts
        while (true) {
            try {
                UIGlobals.ritual().heartbeat();
                break;
            } catch (Exception e) {
                ThreadUtil.sleepUninterruptable(UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
            }
        }
    }

    @Override
    public void okay()
    {
        _compSpin.stop();
        closeDialog(true);
    }

    @Override
    public void error(Exception e)
    {
        _compSpin.stop();

        l.warn("failed to relocate root anchor", e);

        // Convert exception messages to readable format
        StringBuilder msg = new StringBuilder(e.getMessage() == null || e.getMessage().isEmpty() ?
                "Could not move files to the new location" : e.getMessage());
        msg.append(".");
        msg.setCharAt(0, Character.toUpperCase(msg.charAt(0)));

        GUI.get().show(getShell(), MessageType.WARN, msg.toString());

        closeDialog(false);
    }
}