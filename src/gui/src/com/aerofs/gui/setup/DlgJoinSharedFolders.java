package com.aerofs.gui.setup;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.ex.ExIndexing;
import com.aerofs.proto.Common.PBFolderInvitation;
import com.aerofs.proto.Ritual.ListSharedFolderInvitationsReply;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;

import java.util.List;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.google.common.util.concurrent.Futures.addCallback;

public class DlgJoinSharedFolders extends AeroFSDialog
{
    private static final Logger l = Loggers.getLogger(DlgJoinSharedFolders.class);

    ListSharedFolderInvitationsReply _pendingFolders = null;
    CompSpin _compSpin;
    Label _lblStatus;
    TableViewer _tableViewer;
    Table _table;

    public DlgJoinSharedFolders(Shell parent)
    {
        super(parent, "Join Shared Folders", false, true);
    }

    public void showDialogIfNeeded()
    {
        showDialog(true);
    }

    // todo: spinner while waiting for list of invitations
    private void showDialog(final boolean silent)
    {
        // FIXME (AG): this would be way less verbose with the blocking client

        addCallback(UIGlobals.ritualNonBlocking().listSharedFolderInvitations(), new FutureCallback<ListSharedFolderInvitationsReply>()
        {
            @Override
            public void onSuccess(ListSharedFolderInvitationsReply reply)
            {
                _pendingFolders = reply; // FIXME (AG): I'm pretty sure this is wrong
                if (_pendingFolders.getInvitationCount() > 0) {
                    UI.get().asyncExec(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            openDialog();
                        }
                    });
                } else if (!silent) {
                    UI.get().show(MessageType.INFO, "No invitations to accept");
                }
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                if (!(throwable instanceof ExIndexing)) {
                    l.warn("list pending folders:" + Util.e(throwable));
                    UI.get().show(MessageType.ERROR, throwable.toString());
                } else {
                    UIGlobals.scheduler().schedule(new AbstractEBSelfHandling()
                    {
                        @Override
                        public void handle_()
                        {
                            showDialog(silent);
                        }
                    }, 1000);
                }
            }
        });
    }

    @Override
    protected void open(Shell shell)
    {
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        layout.marginHeight = GUIParam.MARGIN;
        layout.marginWidth = GUIParam.MARGIN;
        shell.setLayout(layout);

        Label title = createLabel(shell, SWT.NONE);
        title.setText("Which shared folders would you like to join?");

        createTableViewer(shell);
        createButtonsRow(shell);

        // Select all checkboxes
        for (TableItem item : _table.getItems()) {
            item.setChecked(true);
        }
    }

    // TODO (GS): Unused for the moment - remove later
//    private void setProgress(String msg)
//    {
//        if (msg != null) {
//            _compSpin.start();
//            _lblStatus.setText(msg);
//        } else {
//            _compSpin.stop();
//        }
//    }

    private void createTableViewer(Shell shell)
    {
        Composite composite = new Composite(shell, SWT.NONE);
        TableColumnLayout tableColumnLayout = new TableColumnLayout();
        composite.setLayout(tableColumnLayout);
        GridData gridData = new GridData(GridData.FILL_BOTH);
        gridData.horizontalSpan = 2;
        gridData.heightHint = 100;
        composite.setLayoutData(gridData);

        // Create the TableViewer
        _tableViewer = new TableViewer(composite, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL |
                SWT.FULL_SELECTION | SWT.CHECK);
        _table = _tableViewer.getTable();
        _table.setHeaderVisible(true);
        _table.setLinesVisible(true);

        // Add the first column
        TableViewerColumn tableViewerColumn_1 = new TableViewerColumn(_tableViewer, SWT.NONE);
        TableColumn tc1 = tableViewerColumn_1.getColumn();
        tableColumnLayout.setColumnData(tc1, new ColumnPixelData(120, true, true));
        tc1.setText("Folder name");

        // Add the second column
        TableViewerColumn tableViewerColumn_2 = new TableViewerColumn(_tableViewer, SWT.NONE);
        TableColumn tc2 = tableViewerColumn_2.getColumn();
        tableColumnLayout.setColumnData(tc2, new ColumnPixelData(150, true, true));
        tc2.setText("Shared by");

        // After the columns are set, set the data source
        _tableViewer.setLabelProvider(new LabelProvider());
        _tableViewer.setContentProvider(new ContentProvider());
        _tableViewer.setInput(_pendingFolders);
    }

    private void createButtonsRow(Shell shell)
    {
        // Create the status bar
        {
            Composite composite = new Composite(shell, SWT.NONE);
            composite.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
            composite.setLayout(new RowLayout(SWT.HORIZONTAL));

            _compSpin = new CompSpin(composite, SWT.NONE);
            _lblStatus = createLabel(composite, SWT.NONE);
        }

        // Create the join and cancel buttons
        {
            Composite composite = new Composite(shell, SWT.NONE);
            composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
            FillLayout fl = new FillLayout(SWT.HORIZONTAL);
            fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
            composite.setLayout(fl);

            Button btnJoin = GUIUtil.createButton(composite, SWT.NONE);
            btnJoin.setText("Continue");
            btnJoin.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    // Create a list with the selected PBInvitations
                    List<PBFolderInvitation> invs = Lists.newArrayList();
                    TableItem[] items = _table.getItems();
                    for (int i = 0; i < items.length; i++) {
                        if (items[i].getChecked()) {
                            invs.add(_pendingFolders.getInvitation(i));
                        }
                    }

                    // Join the folders in a separate thread
                    final List<PBFolderInvitation> invitations = invs;
                    ThreadUtil.startDaemonThread("gui-sf", new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            work(invitations);
                        }
                    });

                    closeDialog();
                }
            });
        }
    }

    private void work(List<PBFolderInvitation> invitations)
    {
        if (invitations.isEmpty()) {
            return;
        }

        String progress = "Joining folders";
        UI.get().addProgress(progress, true);
        try {
            for (PBFolderInvitation inv : invitations) {
                try {
                    UIGlobals.ritual().joinSharedFolder(inv.getShareId());
                } catch (Exception e) {
                    l.warn("join folder " + inv.getFolderName() + Util.e(e));
                    UI.get().notify(MessageType.ERROR, "Couldn't join the folder "
                            + inv.getFolderName(), ErrorMessages.e2msgSentenceNoBracketDeprecated(e), null);
                }
            }
        } finally {
            UI.get().removeProgress(progress);
        }
    }



    private class ContentProvider implements IStructuredContentProvider
    {
        @Override
        public Object[] getElements(Object input)
        {
            ListSharedFolderInvitationsReply reply = (ListSharedFolderInvitationsReply) input;
            return reply.getInvitationList().toArray();
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

        @Override
        public void dispose() {}
    }


    private class LabelProvider
            extends org.eclipse.jface.viewers.LabelProvider implements ITableLabelProvider
    {
        @Override
        public String getColumnText(Object element, int columnIndex)
        {
            PBFolderInvitation invitation = (PBFolderInvitation) element;

            switch (columnIndex) {
            case 0: return invitation.getFolderName();
            case 1: return invitation.getSharer();
            default: return "";
            }
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex)
        {
            return null;
        }

        @Override
        public void dispose() {}
    }

}
