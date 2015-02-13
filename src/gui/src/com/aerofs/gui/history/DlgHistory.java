package com.aerofs.gui.history;

import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.AeroFSMessageBox;
import com.aerofs.gui.AeroFSMessageBox.IconType;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.TaskDialog;
import com.aerofs.gui.history.HistoryModel.IDecisionMaker;
import com.aerofs.gui.history.HistoryModel.ModelIndex;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.Maps;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.gui.GUIUtil.createLabel;

public class DlgHistory extends AeroFSDialog
{
    private static final Logger l = Loggers.getLogger(DlgHistory.class);

    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    private Tree _revTree;
    private Label _statusLabel;
    private Table _revTable;
    private Composite _revTableWrap;

    private Group _group;
    private Composite _actionButtons;
    private Button _restoreBtn;
    private Button _openBtn;
    private Button _saveBtn;
    private Button _deleteBtn;

    private final Path _basePath;
    private final HistoryModel _model;

    public DlgHistory(Shell parent)
    {
        this(parent, null);
    }

    public DlgHistory(Shell parent, Path path)
    {
        super(parent, "Sync History", false, true);

        _model = new HistoryModel(UIGlobals.ritualClientProvider());
        _basePath = path;
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        final Shell shell = sh;
        // setup dialog controls
        GridLayout grid = new GridLayout(2, false);
        grid.marginHeight = GUIParam.MARGIN;
        grid.marginWidth = GUIParam.MARGIN;
        grid.verticalSpacing = GUIParam.VERTICAL_SPACING;
        shell.setMinimumSize(600, 400);
        shell.setSize(600, 400);
        shell.setLayout(grid);

        // Use a SashFrom to allow the user to adjust the relative width of version tree and table
        SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL | SWT.SMOOTH);
        GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        sashData.widthHint = 600;
        sashData.heightHint = 350;
        sashForm.setLayoutData(sashData);
        sashForm.setSashWidth(7);

        createVersionTree(sashForm);

        _group = new Group(sashForm, SWT.NONE);
        GridLayout groupLayout = new GridLayout(1, false);
        groupLayout.marginHeight = 8;
        groupLayout.marginWidth = 8;
        groupLayout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        _group.setLayout(groupLayout);
        _group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        _statusLabel = createLabel(_group, SWT.WRAP);
        _statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        createVersionTable(_group);

        _actionButtons = GUIUtil.newPackedButtonContainer(_group);
        _actionButtons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

        if (Cfg.storageType() == StorageType.LINKED) {
            _restoreBtn = GUIUtil.createButton(_actionButtons, SWT.NONE);
            _restoreBtn.setText("Restore Deleted Files...");
            _restoreBtn.setToolTipText("Recursively restore deleted files and folders to their "
                    + "most recent version");
            _restoreBtn.setLayoutData(new RowData());
            _restoreBtn.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    TreeItem[] items = _revTree.getSelection();
                    assert items.length == 1;

                    ModelIndex index = index(items[0]);

                    recursivelyRestore(index);
                }
            });
        } else {
            // TODO: enable restore for non-linked storage
            _restoreBtn = null;
        }

        _openBtn = GUIUtil.createButton(_actionButtons, SWT.NONE);
        _openBtn.setText("Open");
        _openBtn.setToolTipText("Open a read-only copy of the selected version");
        _openBtn.setLayoutData(new RowData());
        _openBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                TableItem[] items = _revTable.getSelection();
                assert items.length == 1;

                HistoryModel.Version version = (HistoryModel.Version) items[0].getData();
                openVersion(version);
            }
        });

        _saveBtn = GUIUtil.createButton(_actionButtons, SWT.NONE);
        _saveBtn.setText("Save as...");
        _saveBtn.setToolTipText("Save a copy of the selected version in a different location");
        _saveBtn.setLayoutData(new RowData());
        _saveBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                TableItem[] items = _revTable.getSelection();
                assert items.length == 1;

                HistoryModel.Version version = (HistoryModel.Version) items[0].getData();
                saveVersionAs(version);
            }
        });

        _deleteBtn = GUIUtil.createButton(_actionButtons, SWT.NONE);
        _deleteBtn.setText("Delete");
        _deleteBtn.setToolTipText("Delete the selected version");
        _deleteBtn.setLayoutData(new RowData());
        _deleteBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                ModelIndex index = index(_revTree.getSelection()[0]);

                if (index.isDir) {
                    deleteAllVersionsUnder(index);
                } else {
                    TableItem[] items = _revTable.getSelection();
                    assert items.length == 1;

                    HistoryModel.Version version = (HistoryModel.Version) items[0].getData();
                    deleteVersion(version);
                }
            }
        });

        // the version table will only be shown when a file is selected
        refreshVersionTable(null);

        // set default proportion between version tree and version table
        // NOTE: must be done AFTER children have been added to the SashForm
        sashForm.setWeights(new int[] {1, 2});

        Link lnkHistory = new Link(shell, SWT.NONE);
        lnkHistory.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        lnkHistory.setText("<a>Learn more about Sync History</a>");
        lnkHistory.addSelectionListener(
                GUIUtil.createUrlLaunchListener("https://support.aerofs.com/entries/23753136"));

        // Create a composite that will hold the buttons row
        Composite buttons = GUIUtil.newPackedButtonContainer(shell);
        buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

        Button refreshBtn = GUIUtil.createButton(buttons, SWT.NONE);
        refreshBtn.setText("Refresh");
        refreshBtn.setToolTipText("Refresh the content of the version tree to reflect the latest " +
                "state of the file system");
        refreshBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                refreshVersionTree();
            }
        });

        Button doneBtn = GUIUtil.createButton(buttons, SWT.NONE);
        doneBtn.setText("Close");
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
                // Clear the icon
                for (Image img : _iconCache.values()) img.dispose();
                _iconCache.clear();
            }
        });

        // select base path, if any
        if (_basePath != null) selectPath(_basePath, true);
    }

    private void selectPath(Path path, boolean expanded)
    {
        TreeItem item = null, parent = null;

        // for multiroot, need to select the appropriate root first...
        for (int i = 0; i < _revTree.getItemCount(); ++i) {
            TreeItem it = _revTree.getItem(i);
            // NOTE: getText() must be called first to ensure population of the virtual tree
            it.getText();
            ModelIndex idx = (ModelIndex)it.getData();
            Path p = _model.getPath(idx);

            // if the top level items are not roots we're done
            if (!p.isEmpty()) break;

            if (p.sid().equals(path.sid())) {
                parent = item = it;
                break;
            }
        }

        for (String e : path.elements()) {
            item = null;
            int n = parent == null ? _revTree.getItemCount() : parent.getItemCount();
            for (int i = 0; i < n; ++i) {
                TreeItem child = parent == null ? _revTree.getItem(i) : parent.getItem(i);
                // NOTE: getText() must be called before any attempt to access children
                // as it triggers the SetData event that incrementally populates the tree
                if (e.equals(child.getText())) {
                    item = child;
                    break;
                }
            }
            if (item == null)
                break;
            item.setExpanded(true);
            parent = item;
        }
        if (item != null) {
            if (_model.getPath(index(item)).equals(path)) {
                item.setExpanded(expanded);
            }
            _revTree.select(item);
            refreshVersionTable(item);
        } else {
            if (parent != null) _revTree.select(parent);
            refreshVersionTable(parent);
        }
    }

    private ModelIndex index(@Nullable TreeItem item)
    {
        return item == null ? null : (ModelIndex)item.getData();
    }

    private void createVersionTree(Composite parent)
    {
        Composite alignmentWorkaround = GUIUtil.createGroupAligningContainer(parent);

        _revTree = new Tree(alignmentWorkaround,
                SWT.SINGLE | SWT.VIRTUAL | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        _revTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        // populate first level of version tree
        _revTree.setItemCount(_model.rowCount(null));

        // incremental tree population
        _revTree.addListener(SWT.SetData, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                TreeItem item = (TreeItem) event.item;
                ModelIndex index = _model.index(index(item.getParentItem()), event.index);
                item.setData(index);
                item.setText(index.name());
                if (index.isDeleted) {
                    item.setForeground(Display.getCurrent()
                            .getSystemColor(SWT.COLOR_TITLE_INACTIVE_FOREGROUND));
                }
                if (index.isDir) {
                    item.setImage(Images.getFolderIcon());
                    item.setItemCount(_model.rowCount(index));
                } else {
                    item.setImage(Images.getFileIcon(index.name(), _iconCache));
                }
            }
        });

        // detect selection and update revision table as needed
        _revTree.addListener(SWT.Selection, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                refreshVersionTable((TreeItem) event.item);
            }
        });
    }

    private void refreshVersionTree()
    {
        Path path = null;
        TreeItem[] items = _revTree.getSelection();
        boolean expanded = false;
        if (items != null && items.length == 1) {
            path = _model.getPath(index(items[0]));
            expanded = items[0].getExpanded();
        }


        // clear the tree and the underlying model
        _revTree.setItemCount(0);
        _model.clear();

        // populate first level of tree
        _revTree.setItemCount(_model.rowCount(null));

        // reselect previously selected item, if possible
        if (path != null) selectPath(path, expanded);
    }

    /**
     * Helper functions to work around stupid SWT layouts...
     */
    private void setButtonVisible(Button b, boolean visible)
    {
        b.setVisible(visible);
        ((RowData)b.getLayoutData()).exclude = !visible;
    }

    private void setRevTableVisible(boolean visible)
    {
        _revTableWrap.setVisible(visible);
        ((GridData)_revTableWrap.getLayoutData()).exclude = !visible;
    }

    private void refreshVersionTable(final TreeItem item)
    {
        ModelIndex index = index(item);
        if (index != null && !index.isDir) {
            boolean ok = fillVersionTable(_revTable, index, _statusLabel);
            if (!ok) {
                _actionButtons.setVisible(false);
                setRevTableVisible(false);
                _statusLabel.setText("No old versions available for this file");
                _group.layout(true, true);
                return;
            }
            _actionButtons.setVisible(ok);
            _deleteBtn.setText("Delete");
            if (_restoreBtn != null) {
                _restoreBtn.setText("Restore...");
                setButtonVisible(_restoreBtn, index.isDeleted);
            }
            setButtonVisible(_openBtn, ok);
            setButtonVisible(_saveBtn, ok);
            setButtonVisible(_deleteBtn, ok);
            ((GridData)_actionButtons.getLayoutData()).horizontalAlignment = SWT.RIGHT;
            setRevTableVisible(true);
            _group.layout(true, true);
        } else {
            if (_restoreBtn != null) _restoreBtn.setText("Restore Deleted Files...");
            _deleteBtn.setText("Delete Old Versions");
            _deleteBtn.setEnabled(true);
            if (index == null) {
                _statusLabel.setText(
                        L.product() + " keeps previous versions of a file when receiving new" +
                        " updates from remote devices. When disk space runs low, old versions may" +
                        " be deleted to save space.\n\n" +
                        "Select a file in the left column to view all the versions stored on this" +
                        " computer.");
                _actionButtons.setVisible(false);
            } else {
                _statusLabel.setText(
                        "You can restore files and folders that have been deleted under the" +
                        " selected folder. Only their latest versions will be restored." +
                        " Restoring a large folder may take some time.\n \n" +
                        "You can also delete all old versions under the selected folder. This" +
                        // have an extra space so Windows will not ignore the trailing line breaks.
                        " will save disk space but cannot be undone, so proceed with caution.\n ");
                _actionButtons.setVisible(true);
                if (_restoreBtn != null) setButtonVisible(_restoreBtn, true);
                setButtonVisible(_openBtn, false);
                setButtonVisible(_saveBtn, false);
                setButtonVisible(_deleteBtn, true);
                ((GridData)_actionButtons.getLayoutData()).horizontalAlignment = SWT.LEFT;
            }
            _revTable.removeAll();
            setRevTableVisible(false);
            _group.layout(true, true);
        }
    }

    /**
     * Creates the revision table (ie: the table on the right side of the dialog)
     */
    private void createVersionTable(Composite parent)
    {
        _revTableWrap = new Composite(parent, SWT.NONE);
        _revTableWrap.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        _revTable = new Table(_revTableWrap, SWT.SINGLE | SWT.BORDER);
        _revTable.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, true, true, 1, 1));
        _revTable.setLinesVisible(true);
        _revTable.setHeaderVisible(true);

        final TableColumn revDateCol = new TableColumn(_revTable, SWT.NONE);
        revDateCol.setText("Last Modified");
        revDateCol.setResizable(true);
        final TableColumn revSizeCol = new TableColumn(_revTable, SWT.NONE);
        revSizeCol.setText("Size");
        revSizeCol.setAlignment(SWT.RIGHT);
        revSizeCol.setResizable(true);

        TableColumnLayout revTableLayout = new TableColumnLayout();
        revTableLayout.setColumnData(revDateCol,
                new ColumnWeightData(6, ColumnWeightData.MINIMUM_WIDTH));
        revTableLayout.setColumnData(revSizeCol,
                new ColumnWeightData(3, ColumnWeightData.MINIMUM_WIDTH));
        _revTableWrap.setLayout(revTableLayout);

        // Open the revision on double click
        _revTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event)
            {
                final TableItem item = (TableItem)event.item;
                if (item == null) return;
                final HistoryModel.Version version = (HistoryModel.Version) item.getData();
                if (version == null) return;
                _deleteBtn.setEnabled(version.index != null);
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event)
            {
                final TableItem item = (TableItem)event.item;
                if (item == null) return;
                final HistoryModel.Version version = (HistoryModel.Version) item.getData();
                if (version == null) return;
                openVersion(version);
            }
        });
    }

    private boolean fillVersionTable(Table revTable, ModelIndex index, Label status)
    {
        status.setText("Sync history of " + Util.quote(index.name()));

        revTable.removeAll();

        List<HistoryModel.Version> versions;
        try {
            versions = _model.versions(index);
        } catch (Exception e) {
            ErrorMessages.show(getShell(), e, L.product() + " failed to retrieve version list.");
            return false;
        }
        if (versions.isEmpty()) {
            // NB: theoretically this can only happen if a file has lost version since the tree
            // was populated so a refresh should be enough to take care of that. In practice
            // we've seen weird conditions where a refresh does not work...
            return false;
        }
        revTable.setItemCount(versions.size());

        for (int i = 0; i < versions.size(); ++i) {
            // version list returned by model is sorted from oldest to newest and we want to
            // display versions sorted from newest to oldest
            HistoryModel.Version version = versions.get(versions.size() - 1 - i);
            TableItem item = revTable.getItem(i);
            String text = Util.formatAbsoluteTime(version.mtime);
            item.setText(0, text + (i == 0 && !index.isDeleted ? " (current version)" : ""));
            item.setText(1, Util.formatSize(version.size));
            item.setData(version);
        }

        // sigh, swt does not call selection listener when changing the selection programmatically
        _deleteBtn.setEnabled(index.isDeleted);
        revTable.setSelection(0);
        return true;
    }

    private void openVersion(HistoryModel.Version version)
    {
        fetchTempFile(version);
        if (version.tmpFile == null) return;

        GUIUtil.launch(version.tmpFile);
    }

    private void saveVersionAs(HistoryModel.Version version)
    {
        // Display a "Save As" dialog box
        FileDialog fDlg = new FileDialog(this.getShell(), SWT.SHEET | SWT.SAVE);
        fDlg.setFilterNames(new String[]{"All files"});
        fDlg.setFilterExtensions(new String[]{"*.*"});
        if (Cfg.storageType() == StorageType.LINKED) {
            String absParentPath = UIUtil.absPathNullable(version.path.removeLast());
            if (absParentPath != null) fDlg.setFilterPath(absParentPath);
        }
        fDlg.setFileName(version.path.last());
        fDlg.setOverwrite(true); // The OS will show a warning if the user chooses an existing name

        String dst = fDlg.open();
        if (dst == null) return; // User closed the save dialog

        fetchTempFile(version);
        if (version.tmpFile == null) return; // Ritual call failed

        try {
            // override existing file because the user has implicitly consent it by choosing to
            // save as the file in the file dialog.
            FileUtil.copy(new File(version.tmpFile), new File(dst), false, true);
        } catch (Exception e) {
            ErrorMessages.show(getShell(), e, L.product() + " failed to save the selected version.");
        }

        // refresh the version tree if we saved under the root anchor
        // don't do it otherwise because :
        //   * it might be time consuming
        //   * it can be terribly annoying because it discards to expansion state of tree items
        //   outside of the selected path
        if (isUnderAnyRoot(dst)) {
            // TODO: optimize refresh when restoring a file to a previous version of itself
            refreshVersionTree();
        }
    }

    boolean isUnderAnyRoot(String absPath)
    {
        if (Cfg.storageType() != StorageType.LINKED) return false;

        try {
            for (Entry<SID, String> e : Cfg.getRoots().entrySet()) {
                if (Path.isUnder(e.getValue(), absPath)) return true;
            }
        } catch (SQLException e) {
            l.error("ignored exception", e);
        }

        return false;
    }

    private void deleteVersion(HistoryModel.Version version)
    {
        if (!GUI.get().ask(getShell(), MessageType.INFO,
                "Permanently delete this old version of \"" + version.path.last() + "\"?")) {
            return;
        }

        try {
            _model.delete(version);
        } catch (Exception e) {
            ErrorMessages.show(getShell(), e, L.product() + " failed to delete the selected version.");
        }

        TreeItem[] items = _revTree.getSelection();
        if (items != null && items.length == 1) {
            refreshVersionTable(items[0]);
        }
    }

    private void deleteAllVersionsUnder(final ModelIndex index)
    {
        Path p = _model.getPath(index);
        new HistoryTaskDialog(getShell(), "Deleting...",
                "Permanently delete all previous versions under \"" + index.name() + "\" ?\n ",
                "Deleting old versions under " + p.toStringRelative()) {
            @Override
            public void run() throws Exception {
                _model.delete(index);
            }
        }.openDialog();
    }

    private void recursivelyRestore(final ModelIndex index)
    {
        // FIXME: this only works for LINKED storage...
        String absPath = UIUtil.absPathNullable(_model.getPath(index));
        DirectoryDialog dDlg = new DirectoryDialog(getShell(), SWT.SHEET);
        dDlg.setMessage("Select destination folder in which deleted files from the source folder " +
                "will be restored.");
        if (absPath != null) dDlg.setFilterPath(new File(absPath).getParent());

        final String dest = dDlg.open();
        if (dest == null) return;

        File root = new File(dest);
        if (!root.isDirectory()) {
            // NOTE: DirectoryDialog allows you to pick a file from a directory dialog if the filter
            // path points to a file...
            new AeroFSMessageBox(getShell(), true, "Please select a folder, not a file.",
                    IconType.ERROR).open();
        } else {
            // the actual restore operation is started in a separate thread by the feedback dialog
            final boolean inPlace = Util.join(dest, index.name()).equals(absPath);

            String label = "Restoring " + Util.quote(index.name()) +
                    (inPlace ? "" : "\nto " + Util.quote(dest));
            new HistoryTaskDialog(getShell(), "Restoring...", null, label) {
                @Override
                public void run() throws Exception
                {
                    _model.restore(index, dest, new IDecisionMaker()
                    {
                        @Override
                        public Answer retry(ModelIndex a)
                        {
                            final ModelIndex idx = a;
                            final OutArg<Answer> reply = new OutArg<Answer>();
                            GUI.get().exec(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    _compSpin.stop(Images.get(Images.ICON_ERROR));
                                    MessageBox mb = new MessageBox(getShell(),
                                            SWT.ICON_ERROR | SWT.ABORT | SWT.RETRY | SWT.IGNORE);
                                    mb.setText("Failed to restore");
                                    mb.setMessage("Failed to restore "
                                            + _model.getPath(idx).toStringRelative());
                                    int ret = mb.open();
                                    switch (ret) {
                                    case SWT.ABORT:  reply.set(Answer.Abort); break;
                                    case SWT.RETRY:  reply.set(Answer.Retry); break;
                                    case SWT.IGNORE: reply.set(Answer.Retry); break;
                                    default: break;
                                    }
                                    _compSpin.start();
                                }
                            });
                            if (reply.get() == null) throw new IllegalArgumentException();
                            return reply.get();
                        }
                    });

                    // TODO: wait for daemon to pick up restored files before closing dialog?
                    // -> this would avoid having to manually refresh...
                }
            }.openDialog();
        }
    }

    /**
     * Uses ritual to retrieve a revision and save it locally as a temp file
     * No-op if the revision has already been retrieved
     */
    private void fetchTempFile(HistoryModel.Version version)
    {
        try {
            _model.export(version);
        } catch (Exception e) {
            ErrorMessages.show(getShell(), e, L.product() + " failed to fetch the selected version.");
        }
    }

    private abstract class HistoryTaskDialog extends TaskDialog
    {
        HistoryTaskDialog(Shell parent, String title, String confirm, String label)
        {
            super(parent, title, confirm, label);
        }

        @Override
        public void okay()
        {
            super.okay();
            refreshVersionTree();
        }

        @Override
        public void error(Exception e)
        {
            super.error(e);
            refreshVersionTree();
        }
    }
}
