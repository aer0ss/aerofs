package com.aerofs.gui.setup;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.sp.client.SPClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply;
import com.aerofs.proto.Sp.ListPendingFolderInvitationsReply.PBFolderInvitation;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.apache.log4j.Logger;
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
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.ArrayList;
import java.util.List;

public class DlgJoinSharedFolders extends AeroFSDialog
{
    private static final Logger l = Util.l(DlgJoinSharedFolders.class);

    ListPendingFolderInvitationsReply _pendingFolders = null;
    CompSpin _compSpin;
    Label _lblStatus;
    TableViewer _tableViewer;
    Table _table;
    SPClient _sp;

    public DlgJoinSharedFolders(Shell parent)
    {
        super(parent, "Join Shared Folders", false, true);
        _sp = SPClientFactory.newClient(SP.URL,  Cfg.user());
        _sp.signInRemote();
    }

    public void showDialogIfNeeded()
    {
        Futures.addCallback(_sp.listPendingFolderInvitations(),
                new FutureCallback<ListPendingFolderInvitationsReply>()
                {
                    @Override
                    public void onSuccess(ListPendingFolderInvitationsReply reply)
                    {
                        _pendingFolders = reply;
                        if (_pendingFolders.getInvitationsCount() > 0) {
                            openDialog();
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        if (throwable instanceof ExNoPerm) {
                            l.info("join shared folders: needs email verification");

                            // TODO (GS): Deal with this case.
                            // - display a different dialog that says we must verify the email address
                            // - dialog has 3 buttons: "send email" and "continue", "cancel"
                            throw new NotImplementedException();

                        } else {
                            l.warn("list pending folders:" + Util.e(throwable));
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

        Label title = new Label(shell, SWT.NONE);
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
        _tableViewer = new TableViewer(composite, SWT.BORDER | SWT.FULL_SELECTION | SWT.CHECK);
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
            _lblStatus = new Label(composite, SWT.NONE);
        }

        // Create the join and cancel buttons
        {
            Composite composite = new Composite(shell, SWT.NONE);
            composite.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
            FillLayout fl = new FillLayout(SWT.HORIZONTAL);
            fl.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
            composite.setLayout(fl);

            Button btnJoin = new Button(composite, SWT.NONE);
            btnJoin.setText("Continue");
            btnJoin.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    // Create a list with the selected PBInvitations
                    ArrayList<PBFolderInvitation> invs = new ArrayList<PBFolderInvitation>();
                    TableItem[] items = _table.getItems();
                    for (int i = 0; i < items.length; i++) {
                        if (items[i].getChecked()) {
                            invs.add(_pendingFolders.getInvitations(i));
                        }
                    }

                    // Join the folders in a separate thread
                    final List<PBFolderInvitation> invitations = invs;
                    ThreadUtil.startDaemonThread("join-shared-folders", new Runnable()
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

        Object prog = UI.get().addProgress("Joining folders", true);
        RitualBlockingClient ritual  = RitualClientFactory.newBlockingClient();
        try {
            for (PBFolderInvitation inv : invitations) {
                try {
                    UIUtil.joinSharedFolder(ritual, new SID(inv.getShareId()), inv.getFolderName());
                } catch (Exception e) {
                    Util.l(this).warn("join folder " + inv.getFolderName() + Util.e(e));
                    UI.get().notify(MessageType.ERROR, "Couldn't join the folder "
                            + inv.getFolderName(), UIUtil.e2msgSentenceNoBracket(e), null);
                }
            }
            UI.get().notify(MessageType.INFO, "Successfully joined the shared folders", new Runnable() {
                @Override
                public void run()
                {
                    Program.launch(Cfg.absRootAnchor());
                }
            });
        } finally {
            ritual.close();
            UI.get().removeProgress(prog);
        }
    }



    private class ContentProvider implements IStructuredContentProvider
    {
        @Override
        public Object[] getElements(Object input)
        {
            ListPendingFolderInvitationsReply reply = (ListPendingFolderInvitationsReply) input;
            return reply.getInvitationsList().toArray();
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
