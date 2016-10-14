/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AbstractSpinAnimator;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.TaskDialog;
import com.aerofs.gui.common.BackgroundRenderer;
import com.aerofs.gui.exclusion.DlgExclusion;
import com.aerofs.gui.sharing.AddSharedFolderDialogs.IShareNewFolderCallback;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Ritual.ListSharedFoldersReply;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The shared folder list in the Manage Shared Folder dialog
 */
class SharedFolderList extends Composite
{
    private static final Logger l = Loggers.getLogger(SharedFolderList.class);

    private final Table _table;
    private final Button _btnLeave;
    private final Button _btnOpen;
    private MemberList _memberList;

    private final AbstractSpinAnimator _animator = new AbstractSpinAnimator(this) {
        @Override
        protected void setImage(Image img)
        {
            _table.getItem(0).setImage(img);
        }
    };

    private static final String PATH_DATA = "Path";
    private static final String ABS_PATH_DATA = "AbsPath";

    private static final String OPEN_UNLINKED_FOLDERS_MSG = "This folder cannot be opened because " +
            "it is not currently syncing to this device. You can still share this folder or " +
            " change its permissions without syncing.";

    SharedFolderList(Composite composite, int style)
    {
        super(composite, style);

        GridLayout l = new GridLayout(1, false);
        l.marginBottom = 0;
        l.marginTop = 0;
        l.marginRight = 0;
        l.marginLeft = 0;
        l.marginHeight = 0;
        l.marginWidth = 0;
        setLayout(l);

        Label lbl = createLabel(this, SWT.NONE);
        lbl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lbl.setText("Shared Folders:");

        Composite c = DlgManageSharedFolders.newTableWrapper(this, 1);
        c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        _table = new Table(c, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        _table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        _table.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                TableItem ti = (TableItem)e.item;
                Path path = ti != null ? (Path)ti.getData(PATH_DATA) : null;
                _btnOpen.setEnabled(path != null);
                _btnLeave.setEnabled(path != null);
                _memberList.setFolder(path, ti.getText());
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                openSelectedFolder();
            }
        });
        // Listener to draw the separator. A simple setBackground will not work on Windows. So we
        // have to use GC to draw the background for the separator. Have to pass in the composite
        // here so that the gray background is drawn according to width of sash.
        _table.addListener(SWT.EraseItem, new BackgroundRenderer(composite, PATH_DATA, false));
        if (!OSUtil.isLinux()) {
            // Not attaching this listener on linux because it makes the scroll bars disappear and
            // they only come back after resizing the sashForm. There is prolly some better way to
            // to do this without using the if. TODO: Abhishek
            _table.addListener(SWT.MeasureItem, new Listener()
            {
                public void handleEvent(Event event)
                {
                    event.width = _table.getClientArea().width;
                }
            });
        }
        // tooltip is the absPath, which only make sense for Linked storage
        if (Cfg.storageType() == StorageType.LINKED) {
            new TableToolTip(_table, ABS_PATH_DATA);
        }

        Composite bar = GUIUtil.newPackedButtonContainer(c);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        Button btnAdd = GUIUtil.createButton(bar, SWT.PUSH);
        btnAdd.setText("Add...");
        btnAdd.setToolTipText("Share a folder");
        btnAdd.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent event)
            {
                boolean shift = Util.test(event.stateMask, SWT.SHIFT);

                addSharedFolder(L.isMultiuser() || shift);
            }
        });

        // Place the Open button before Leave so when the storage type is not linked, the Leave
        // button is placed to the right of the button bar rather than in the middle.
        _btnOpen = GUIUtil.createButton(bar, SWT.PUSH);
        _btnOpen.setText("Open");
        _btnOpen.setToolTipText("Open the selected folder");
        _btnOpen.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                openSelectedFolder();
            }
        });

        _btnLeave = GUIUtil.createButton(bar, SWT.PUSH);
        _btnLeave.setText("Leave");
        _btnLeave.setToolTipText("Leave the selected shared folder");
        _btnLeave.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                Path path = selectedPath();
                if (path != null) leave(path, selectedName());
            }
        });

        if (L.isMultiuser()) {
            // Team Server cannot leave folders. Keep the button in the dialog but hidden so
            // the dialog layout is consistent across different configurations.
            // TODO: use expulsion under the hood to filter out some shared folders?
            _btnLeave.setVisible(false);
        }

        if (Cfg.storageType() != StorageType.LINKED) {
            // Open and Add only makes sense for linked storage. Keep the button in the dialog but
            // hidden so the dialog layout is consistent across different configurations.
            _btnOpen.setVisible(false);
            btnAdd.setVisible(false);
        }
    }

    private void openSelectedFolder() {
        Path path = selectedPath();
        if (path == null) return;
        // We can only open a folder as long they are linked. Expelled folders and Pending roots
        // cannot be opened. Linked Folders will have an absolute path where'as the other two won't.
        if (selectedAbsPath() != null) {
            GUIUtil.launch(UIUtil.absPathNullable(path));
        } else {
            addFolderToSync();
        }
    }

    /**
     * This function displays the Selective Sync dialog. It is invoked when the user tries to open
     * or double clicks on a pending or expelled folder.
     */
    private void addFolderToSync()
    {
        try {
            final Path path = selectedPath();
            GUI askToSyncGui = new GUI();

            // We want to display this only if the shared folder is not at the top level of folders
            // within AeroFS. For example this message should be displayed if the shared folder is
            // AeroFS/foo/bar but shouldn't be displayed if the shared folder is AeroFS/foo. When
            // the user tries to open bar, we let him know that he needs to sync foo for that to
            // happen.
            String pathHelpInfo = "\n\nTo sync this folder on this device, please select " +
                    (path.toPB().getElemCount() > 1 ? (path.toPB().getElem(0) + " ") : "it ") +
                    "in the Selective Sync dialog. You can sync this folder at anytime by clicking " +
                    "the AeroFS icon and going to Preferences... > Advanced... > Selective Sync...";
            String askMsg = OPEN_UNLINKED_FOLDERS_MSG + pathHelpInfo;

            if (askToSyncGui.ask(getShell(), MessageType.INFO, askMsg, "Open Selective Sync Dialog",
                    "Not Now")) {
                new DlgExclusion(getShell()).openDialog();
                refreshAsync(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        // This makes sure that the same folder is selected in the table
                        // after refreshing it.
                        select(path);
                        _memberList.setFolder(path, selectedName());
                    }
                });
            }
        } catch (IOException e) {
            l.warn("ignored exception", e);
        }
    }

    private void addSharedFolder(boolean allowExternalFolder)
    {
        IShareNewFolderCallback callback = new IShareNewFolderCallback() {
        @Override
        public void onSuccess(@Nonnull final Path path)
        {
            checkNotNull(path);
            refreshAsync(new Runnable() {
                @Override
                public void run() {
                    select(path);
                    _memberList.showInvitationDialog();
                }
            });
        }
    };

        new AddSharedFolderDialogs(getShell(), callback).open(allowExternalFolder);
    }

    void setMemberList(MemberList memberList)
    {
        _memberList = memberList;
    }

    private @Nullable Path selectedPath()
    {
        TableItem[] items = _table.getSelection();
        return items.length != 1 ? null : (Path)items[0].getData(PATH_DATA);
    }

    private @Nullable String selectedAbsPath()
    {
        TableItem[] items = _table.getSelection();
        return items.length != 1 ? null : (String)items[0].getData(ABS_PATH_DATA);
    }

    private @Nullable String selectedName()
    {
        TableItem[] items = _table.getSelection();
        return items.length != 1 ? null : items[0].getText();
    }

    private void setLoading(boolean loading)
    {
        _table.removeAll();

        if (loading) {
            _table.setItemCount(1);

            TableItem item = _table.getItem(0);
            item.setText(0, S.GUI_LOADING);
            _animator.start();
        } else {
            _animator.stop();
        }
    }

    void refreshAsync()
    {
        refreshAsync(null);
    }

    /**
     * Refresh the folder list asynchronously.
     *
     * @param callback the callback to be invoked on the UI thread when the refresh completes.
     */
    private void refreshAsync(@Nullable final Runnable callback)
    {
        _memberList.setFolder(null, null);
        setLoading(true);

        Futures.addCallback(UIGlobals.ritualNonBlocking().listSharedFolders(),
                new FutureCallback<ListSharedFoldersReply>()
                {
                    @Override
                    public void onSuccess(@Nonnull final ListSharedFoldersReply reply)
                    {
                        setLoading(false);
                        fill(reply.getSharedFolderList());
                        if (callback != null) callback.run();
                    }

                    @Override
                    public void onFailure(@Nonnull Throwable e)
                    {
                        setLoading(false);
                        ErrorMessages.show(getShell(), e, "Failed to retrieve shared folders.");
                    }
                }, new GUIExecutor(SharedFolderList.this));
    }

    /**
     * Select the item that matches the path and scroll to that item. Nop if no matching items.
     *
     * It also set the current folder for the member list.
     */
    private void select(@Nonnull Path path)
    {
        // A linear search to find the matching item
        for (TableItem ti : _table.getItems()) {
            if (path.equals(ti.getData(PATH_DATA))) {
                _table.deselectAll();
                _table.setSelection(ti);
                _table.showSelection();
                _memberList.setFolder(path, ti.getText());
                break;
            }
        }
    }

    private void addSharedFolderToDisplayTable(TableItem ti,
            PBSharedFolder folder)
    {
        Path path = Path.fromPB(folder.getPath());
        ti.setData(PATH_DATA, path);
        ti.setImage(Images.getSharedFolderIcon());
        ti.setText(0, folder.getName());

        // absPath only available for Linked storage
        if (Cfg.storageType() == StorageType.LINKED) {
            String root = null;
            try {
                root = Cfg.getRootPathNullable(path.sid());
            } catch (SQLException e) {
                l.error("ignored exception", e);
            }
            if (root == null) {
                l.warn("unknown root " + path.sid());
                return;
            }
            ti.setData(ABS_PATH_DATA, folder.getAdmittedOrLinked()
                ? path.toAbsoluteString(root)
                : null);
        }
    }

    private void addSeparatorToDisplayTable(TableItem ti, String separatorText)
    {
        ti.setText(separatorText);
        // Setting ABS_PATH_DATA to empty string because the TableToolTip shouldn't display anything
        // for separators.
        ti.setData(ABS_PATH_DATA, null);
        ti.setData(PATH_DATA, null);
        // Make the text bold.
        ti.setFont(GUIUtil.makeBold(ti.getFont()));
    }

    private List<Object> getTableContent(Collection<PBSharedFolder> internalStores,
            Collection<PBSharedFolder> externalStores)
    {
        boolean needSeparator = externalStores.size() > 0;
        List<Object> tableContents = Lists.newArrayList();

        for(PBSharedFolder folder: internalStores) {
            tableContents.add(folder);
        }
        if (needSeparator) {
            if (!L.isMultiuser()) {
                tableContents.add("Folders outside your AeroFS folder");
            }
            for(PBSharedFolder folder: externalStores) {
                tableContents.add(folder);
            }
        }
        return tableContents;
    }

    /**
     * Populate the view with the given shared folder list.
     * We want the shared folder list to contain all internal folders first (sorted alphabetically),
     * then a separator(to separate internal and external folders) followed by external folders
     * (sorted alphabetically).
     */
    private void fill(List<PBSharedFolder> sharedFolders)
    {
        // Split the shared folders into internal and external shared folders
        Collection<PBSharedFolder> internalStores = UIUtil.filterStoresIntoInternalOrExternal(
                sharedFolders, true);
        Collection<PBSharedFolder> externalStores = UIUtil.filterStoresIntoInternalOrExternal(
                sharedFolders, false);

        List<Object> tableContent = getTableContent(internalStores, externalStores);

        for (Object elem: tableContent) {
            TableItem ti = new TableItem(_table, SWT.NONE);
            if (elem instanceof PBSharedFolder) {
                addSharedFolderToDisplayTable(ti, (PBSharedFolder)elem);
            } else if (elem instanceof String) {
                addSeparatorToDisplayTable(ti, (String)elem);
            }
        }

        boolean notEmpty = !sharedFolders.isEmpty();

        /* If we have internal folders, the default selection should be the first table item.
           If we have no internal folders, then default selection should be the second table item.
           In this case, the first table item is the External folders separator.
        */
        if (notEmpty) _table.select(internalStores.isEmpty() ? 1 : 0);
        if (_btnLeave != null) _btnLeave.setEnabled(notEmpty);
        if (_btnOpen != null) _btnOpen.setEnabled(notEmpty);

        _memberList.setFolder(selectedPath(), selectedName());
    }

    private void leave(final Path path, String defaultName)
    {
        String name = UIUtil.sharedFolderName(path, defaultName);
        new TaskDialog(getShell(), "Leave Shared Folder",
                "Leaving a shared folder will delete its content on all your devices "
                        + "but will not affect other members.\n\n"
                        + "Are you sure you want to leave \"" + name + "\" ?\n ",
                "Leaving " + name) {
            @Override
            public void run() throws Exception
            {
                UIGlobals.ritual().leaveSharedFolder(path.toPB());
            }

            @Override
            public void okay()
            {
                super.okay();
                refreshAsync();
            }

            @Override
            public void error(Exception e)
            {
                super.error(e);
                refreshAsync();
            }
        }.openDialog();
    }
}