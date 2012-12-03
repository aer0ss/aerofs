/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Ritual.GetSyncStatusReply;
import com.aerofs.proto.Ritual.PBSyncStatus;
import com.aerofs.proto.Ritual.PBSyncStatus.Status;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;

import java.io.File;
import java.util.Map;

public class DlgSyncStatus extends AeroFSDialog
{
    private static final Logger l = Util.l(DlgSyncStatus.class);
    private final Path _path;
    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    public DlgSyncStatus(Shell parent, Path path)
    {
        super(parent, "Sync status", false, true);
        _path = path;
    }

    Image getPathIcon(Path p)
    {
        File f = new File(p.toAbsoluteString(Cfg.absRootAnchor()));
        if (f.isDirectory()) return Images.getFolderIcon();
        return Images.getFileIcon(_path.last(), _iconCache);
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        final Shell shell = sh;

        GridLayout grid = new GridLayout(3, false);
        grid.marginHeight = GUIParam.MARGIN;
        grid.marginWidth = GUIParam.MARGIN;
        grid.horizontalSpacing = 4;
        grid.verticalSpacing = 4;
        shell.setMinimumSize(300, 250);
        shell.setSize(300, 250);
        shell.setLayout(grid);

        addLabelPreImage(_path.toStringFormal(), getPathIcon(_path), shell);

        ScrolledComposite sc = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.BORDER);
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);
        Composite scc = new Composite(sc, SWT.NONE);
        sc.setContent(scc);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        populateSyncStatusList(scc);
        sc.setMinSize(scc.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        Composite buttons = new Composite(shell, SWT.NONE);
        buttons.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false, 3, 1));
        buttons.setLayout(new RowLayout());

        Button doneBtn = new Button(buttons, SWT.NONE);
        doneBtn.setText("Done");
        doneBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                shell.close();
            }
        });

        shell.addListener(SWT.Close, new Listener() {
            @Override
            public void handleEvent(Event event)
            {
                // Clear the icon cache
                for (Image img : _iconCache.values()) img.dispose();
                _iconCache.clear();
            }
        });
    }

    private void addLabelPreImage(String text, Image img, Composite c)
    {
        Label limg = new Label(c, SWT.LEFT | SWT.HORIZONTAL);
        limg.setImage(img);
        limg.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 1, 1));

        Label ltxt = new Label(c, SWT.LEFT | SWT.HORIZONTAL);
        ltxt.setText(text);
        GridData lbldata = new GridData(GridData.BEGINNING, GridData.CENTER, true, false, 2, 1);
        ltxt.setLayoutData(lbldata);
    }

    private void addLabelWithStatusIcon(String text, PBSyncStatus.Status status, int indent,
            Composite c)
    {
        Label ltxt = new Label(c, SWT.LEFT | SWT.HORIZONTAL);
        ltxt.setText(text);
        GridData lbldata = new GridData(GridData.BEGINNING, GridData.CENTER, true, false, 2, 1);
        lbldata.horizontalIndent = indent;
        ltxt.setLayoutData(lbldata);

        Label limg = new Label(c, SWT.LEFT | SWT.HORIZONTAL);
        limg.setImage(Images.get(statusIconName(status)));
        limg.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 1, 1));

        // add tooltip text to explain icon meaning
        final ToolTip tip = new ToolTip(getShell(), SWT.BALLOON);
        tip.setMessage(statusTooltip(status));
        limg.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent focusEvent)
            {
                tip.setVisible(false);
            }

            @Override
            public void focusLost(FocusEvent focusEvent)
            {
                Label l = (Label)focusEvent.widget;
                tip.setLocation(l.toDisplay(l.getLocation()));
                tip.setVisible(true);
            }
        });
    }

    private String statusIconName(PBSyncStatus.Status status)
    {
        switch (status.getNumber()) {
        case Status.IN_SYNC_VALUE:     return Images.SS_IN_SYNC;
        case Status.IN_PROGRESS_VALUE: return Images.SS_IN_PROGRESS;
        case Status.OFFLINE_VALUE:     return Images.SS_OFFLINE_NOSYNC;
        default:                       return Images.ICON_DOUBLE_QUESTION;
        }
    }

    private String statusTooltip(PBSyncStatus.Status status)
    {
        switch (status.getNumber()) {
        case Status.IN_SYNC_VALUE:     return S.SS_IN_SYNC_TOOLTIP;
        case Status.IN_PROGRESS_VALUE: return S.SS_IN_PROGRESS_TOOLTIP;
        case Status.OFFLINE_VALUE:     return S.SS_OFFLINE_TOOLTIP;
        default:                       return "?";
        }
    }

    private void populateSyncStatusList(Composite c)
    {
        Layout listLayout = new GridLayout(3, false);
        c.setLayout(listLayout);

        GetSyncStatusReply reply = null;
        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            reply = ritual.getSyncStatus(_path.toPB());
        } catch (Exception e) {
            l.warn(e);
        } finally {
            ritual.close();
        }

        // do not show sync status when servers are known to be down or when daemon is dead
        if (reply == null || !reply.getIsServerUp()) {
            addLabelPreImage(S.SYNC_STATUS_DOWN, Images.get(Images.ICON_ERROR), c);
            return;
        }

        if (reply.getStatusCount() == 0) {
            addLabelPreImage(S.SYNC_STATUS_LOCAL, Images.get(Images.ICON_WARNING), c);
            return;
        }

        boolean myDev = false, otherUsers = false;
        for (PBSyncStatus pbs : reply.getStatusList()) {
            if (pbs.hasDeviceName()) {
                if (!myDev) {
                    myDev = true;
                    addLabelPreImage("My devices", Images.get(Images.ICON_HOME), c);
                }
                addLabelWithStatusIcon(pbs.getDeviceName(), pbs.getStatus(), 10, c);
            } else {
                if (!otherUsers) {
                    otherUsers = true;
                    addLabelPreImage("Other users", Images.get(Images.ICON_USER), c);
                }
                addLabelWithStatusIcon(pbs.getUserName(), pbs.getStatus(), 10, c);
            }
        }
    }
}
