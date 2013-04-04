package com.aerofs.gui.history;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.AeroFSMessageBox;
import com.aerofs.gui.AeroFSMessageBox.IconType;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.history.HistoryModel.IDecisionMaker;
import com.aerofs.gui.history.HistoryModel.ModelIndex;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
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
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DlgHistory extends AeroFSDialog
{
    private static final Logger l = Loggers.getLogger(DlgHistory.class);

    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    private Tree _revTree;
    private Label _statusLabel;
    private Table _revTable;
    private Composite _revTableWrap;

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
        super(parent, "Version History", false, true);

        _model = new HistoryModel(UI.ritualClientProvider());
        _basePath = path;
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        final Shell shell = sh;
        // setup dialog controls
        GridLayout grid = new GridLayout(1, false);
        grid.marginHeight = GUIParam.MARGIN;
        grid.marginWidth = GUIParam.MARGIN;
        grid.horizontalSpacing = 4;
        grid.verticalSpacing = 4;
        shell.setMinimumSize(600, 400);
        shell.setSize(600, 400);
        shell.setLayout(grid);

        // Use a SashFrom to allow the user to adjust the relative width of version tree and table
        SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL | SWT.SMOOTH);
        GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        sashData.widthHint = 600;
        sashData.heightHint = 350;
        sashForm.setLayoutData(sashData);
        sashForm.setSashWidth(7);

        createVersionTree(sashForm);

        Group group = new Group(sashForm, SWT.NONE);
        GridLayout groupLayout = new GridLayout(1, false);
        groupLayout.marginHeight = GUIParam.MARGIN;
        groupLayout.marginWidth = GUIParam.MARGIN;
        group.setLayout(groupLayout);
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        _statusLabel = new Label(group, SWT.WRAP);
        _statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        createVersionTable(group);

        _actionButtons = newPackedButtonContainer(group);
        _actionButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        _restoreBtn = new Button(_actionButtons, SWT.NONE);
        _restoreBtn.setText("Restore Deleted Files...");
        _restoreBtn.setToolTipText("Recursively restore deleted files and folders to their most "
                + "recent version");
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

        _openBtn = new Button(_actionButtons, SWT.NONE);
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

        _saveBtn = new Button(_actionButtons, SWT.NONE);
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

        _deleteBtn = new Button(_actionButtons, SWT.NONE);
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

        // Create a composite that will hold the buttons row
        Composite buttons = newPackedButtonContainer(shell);
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 2, 1));

        Button refreshBtn = new Button(buttons, SWT.NONE);
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

        Button doneBtn = new Button(buttons, SWT.NONE);
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
        Composite alignmentWorkaround = new Composite(parent, SWT.FILL);
        GridLayout layoutAlignmentWorkaround = new GridLayout();
        layoutAlignmentWorkaround.marginWidth = 0;
        if (OSUtil.isOSX()) {
            layoutAlignmentWorkaround.marginHeight = 3;
        } else if (OSUtil.isWindows()) {
            layoutAlignmentWorkaround.marginHeight = 0;
            layoutAlignmentWorkaround.marginTop = 7;
            layoutAlignmentWorkaround.marginBottom = 2;
        } else {
            layoutAlignmentWorkaround.marginHeight = 0;
        }
        alignmentWorkaround.setLayout(layoutAlignmentWorkaround);

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
                item.setText(index.name);
                if (index.isDeleted) {
                    item.setForeground(Display.getCurrent()
                            .getSystemColor(SWT.COLOR_TITLE_INACTIVE_FOREGROUND));
                }
                if (index.isDir) {
                    item.setImage(Images.getFolderIcon());
                    item.setItemCount(_model.rowCount(index));
                } else {
                    item.setImage(Images.getFileIcon(index.name, _iconCache));
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

        // Add context menu
        _revTree.addListener(SWT.MenuDetect, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                TreeItem item = _revTree.getItem(_revTree.toControl(event.x, event.y));
                if (item == null) return;

                final ModelIndex index = index(item);

                Menu menu = new Menu(_revTree.getShell(), SWT.POP_UP);
                addMenuItem(menu, "Restore Deleted File" + (index.isDir ? "s" : "") + "...",
                            new Listener() {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        recursivelyRestore(index);
                    }
                }).setEnabled(index.isDir || index.isDeleted);

                menu.setLocation(event.x, event.y);
                menu.setVisible(true);
                while (!menu.isDisposed() && menu.isVisible()) {
                    if (!menu.getDisplay().readAndDispatch()) {
                        menu.getDisplay().sleep();
                    }
                }
                menu.dispose();
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

    private void setCompositeVisible(Composite c, boolean visible)
    {
        c.setVisible(visible);
        ((GridData)c.getLayoutData()).exclude = !visible;
    }

    private void refreshVersionTable(TreeItem item)
    {
        ModelIndex index = index(item);
        if (index != null && !index.isDir) {
            boolean ok = fillVersionTable(_revTable, index, _statusLabel);
            if (!ok) {
                _actionButtons.setVisible(false);
                _actionButtons.layout();
                setCompositeVisible(_revTableWrap, false);
                _statusLabel.getParent().layout();

                GUI.get().safeAsyncExec(_revTable, new Runnable() {
                    @Override
                    public void run() {
                        refreshVersionTree();
                    }
                });
                return;
            }
            _actionButtons.setVisible(ok);
            _restoreBtn.setText("Restore...");
            _deleteBtn.setText("Delete");
            setButtonVisible(_restoreBtn, index.isDeleted);
            setButtonVisible(_openBtn, ok);
            setButtonVisible(_saveBtn, ok);
            setButtonVisible(_deleteBtn, ok);
            ((GridData)_actionButtons.getLayoutData()).horizontalAlignment = SWT.RIGHT;
            _actionButtons.layout();
            setCompositeVisible(_revTableWrap, true);
            _statusLabel.getParent().layout();
        } else {
            _restoreBtn.setText("Restore Deleted Files...");
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
                        " Restoring a large folder may take a few moments.\n \n" +
                        "You can also delete all old versions under the selected folder. This" +
                        // have an extra space so Windows will not ignore the trailing line breaks.
                        " will save disk space but cannot be undone, so proceed with caution.\n ");
                _actionButtons.setVisible(true);
                setButtonVisible(_restoreBtn, true);
                setButtonVisible(_openBtn, false);
                setButtonVisible(_saveBtn, false);
                setButtonVisible(_deleteBtn, true);
                ((GridData)_actionButtons.getLayoutData()).horizontalAlignment = SWT.LEFT;
                _actionButtons.layout();
            }
            _revTable.removeAll();
            setCompositeVisible(_revTableWrap, false);
            _statusLabel.getParent().layout();
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

        // Add context menu
        _revTable.addListener(SWT.MenuDetect, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                final TableItem item = _revTable.getItem(_revTable.toControl(event.x, event.y));
                if (item == null) return;

                final HistoryModel.Version version = (HistoryModel.Version) item.getData();

                Menu menu = new Menu(_revTable.getShell(), SWT.POP_UP);

                addMenuItem(menu, "Open", new Listener()
                {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        openVersion(version);
                    }
                });

                addMenuItem(menu, "Save As...", new Listener()
                {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        saveVersionAs(version);
                    }
                });

                addMenuItem(menu, "Delete", new Listener()
                {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        deleteVersion(version);
                    }
                });

                menu.setLocation(event.x, event.y);
                menu.setVisible(true);
                while (!menu.isDisposed() && menu.isVisible()) {
                    if (!menu.getDisplay().readAndDispatch()) {
                        menu.getDisplay().sleep();
                    }
                }
                menu.dispose();
            }
        });

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
        Path path = _model.getPath(index);
        status.setText("Version history of " + Util.quote(path.last()));

        List<HistoryModel.Version> versions = _model.versions(index);
        revTable.removeAll();
        if (versions == null || versions.isEmpty()) return false;
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
        fDlg.setFilterPath(
                Util.join(Cfg.absDefaultRootAnchor(), Util.join(version.path.removeLast().elements())));
        fDlg.setFileName(version.path.last());
        fDlg.setOverwrite(true); // The OS will show a warning if the user chooses an existing name

        String dst = fDlg.open();
        if (dst == null) return; // User closed the save dialog

        fetchTempFile(version);
        if (version.tmpFile == null) return; // Ritual call failed

        try {
            FileUtil.moveInOrAcrossFileSystem(new File(version.tmpFile), new File(dst));
        } catch (IOException e) {
            l.warn("Saving revision failed: " + Util.e(e));
            new AeroFSMessageBox(this.getShell(), false, e.getLocalizedMessage(),
                    AeroFSMessageBox.IconType.ERROR)
                    .open();
        }

        // refresh the version tree if we saved under the root anchor
        // don't do it otherwise because :
        //   * it might be time consuming
        //   * it can be terribly annoying because it discards to expansion state of tree items
        //   outside of the selected path
        if (Path.isUnder(Cfg.absDefaultRootAnchor(), dst)) {
            // TODO: optimize refresh when restoring a file to a previous version of itself
            refreshVersionTree();
        }
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
            l.warn("Deleting revision failed: " + Util.e(e));
            new AeroFSMessageBox(this.getShell(), false, e.getLocalizedMessage(),
                    AeroFSMessageBox.IconType.ERROR)
                    .open();
        }

        TreeItem[] items = _revTree.getSelection();
        if (items != null && items.length == 1) {
            refreshVersionTable(items[0]);
        }
    }

    private void deleteAllVersionsUnder(final ModelIndex index)
    {
        Path p = _model.getPath(index);
        new FeedbackDialog(getShell(), "Deleting...",
                "Permanently delete all previous versions under \"" + p.last() + "\" ?\n ",
                "Deleting old versions under " + p) {
            @Override
            public void run() throws Exception {
                _model.delete(index);
            }
        }.openDialog();
    }

    private void recursivelyRestore(final ModelIndex index)
    {
        DirectoryDialog dDlg = new DirectoryDialog(getShell(), SWT.SHEET);
        dDlg.setMessage("Select destination folder in which deleted files from the source folder " +
                "will be restored.");
        dDlg.setFilterPath(Util.join(Cfg.absDefaultRootAnchor(),
                Util.join(_model.getPath(index).removeLast().elements())));
        final String path = dDlg.open();
        if (path == null) return;

        File root = new File(path);
        if (!root.isDirectory()) {
            // NOTE: DirectoryDialog allows you to pick a file from a directory dialog if the filter
            // path points to a file...
            new AeroFSMessageBox(getShell(), true, "Please select a folder, not a file.",
                    IconType.ERROR).open();
        } else {
            // the actual restore operation is started in a separate thread by the feedback dialog
            final boolean inPlace = Util.join(path, index.name).equals(
                    _model.getPath(index).toAbsoluteString(Cfg.absDefaultRootAnchor()));
            String label = "Restoring " + Util.quote(_model.getPath(index).last()) +
                    (inPlace ? "" : "\nto " + Util.quote(path));
            new FeedbackDialog(getShell(), "Restoring...", null, label) {
                @Override
                public void run() throws Exception
                {
                    _model.restore(index, path, new IDecisionMaker()
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
                                    mb.setMessage("Failed to restore " + _model.getPath(idx));
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
                    // -> this would aovid having to manually refresh...
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
            l.warn("Fetching revision failed: " + Util.e(e));
            new AeroFSMessageBox(this.getShell(), false, e.getLocalizedMessage(),
                    AeroFSMessageBox.IconType.ERROR)
                    .open();
        }
    }

    private MenuItem addMenuItem(Menu menu, String text, Listener listener)
    {
        MenuItem mi = new MenuItem(menu, SWT.PUSH);
        mi.setText(text);
        if (listener != null) mi.addListener(SWT.Selection, listener);
        return mi;
    }

    private Composite newPackedButtonContainer(Composite parent)
    {
        return newButtonContainer(parent, true);
    }

    private Composite newButtonContainer(Composite parent, boolean pack)
    {
        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
        RowLayout buttonLayout = new RowLayout();
        buttonLayout.pack = pack;
        buttonLayout.wrap = false;
        buttonLayout.fill = true;
        buttonLayout.center = true;
        buttonLayout.marginBottom = 0;
        buttonLayout.marginTop = 0;
        buttonLayout.marginHeight = 0;
        buttonLayout.marginLeft = 0;
        buttonLayout.marginRight = 0;
        buttonLayout.marginWidth = 0;
        buttonLayout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        if (OSUtil.isOSX()) {
            // workaround broken margins on OSX
            buttonLayout.marginLeft = -4;
            buttonLayout.marginRight = -4;
            buttonLayout.marginTop = 4;
            buttonLayout.marginBottom = -6;
        }
        buttons.setLayout(buttonLayout);
        return buttons;
    }

    /**
     * Simple sheet dialog controlling a long running operation
     */
    private abstract class FeedbackDialog extends AeroFSDialog implements ISWTWorker
    {
        protected CompSpin _compSpin;
        private final String _confirm;
        private final String _label;

        FeedbackDialog(Shell parent, String title, String confirm, String label)
        {
            super(parent, title, true, false);
            _confirm = confirm;
            _label = label;
        }

        @Override
        protected void open(Shell shell)
        {
            if (GUIUtil.isWindowBuilderPro()) // $hide$
                shell = new Shell(getParent(), getStyle());

            GridLayout glShell = new GridLayout(2, false);
            glShell.marginHeight = GUIParam.MARGIN;
            glShell.marginWidth = GUIParam.MARGIN;
            glShell.verticalSpacing = 0;
            shell.setLayout(glShell);

            _compSpin = new CompSpin(shell, SWT.NONE);

            final Label label = new Label(shell, SWT.WRAP);
            label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
            if (_confirm != null && !_confirm.isEmpty()) {
                label.setText(_confirm);

                final Composite c = newButtonContainer(shell, false);
                c.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false, 2, 1));

                Button bYes = new Button(c, SWT.NONE);
                bYes.setText("   Yes   ");
                bYes.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent selectionEvent)
                    {
                        label.setText(_label);
                        c.setVisible(false);
                        ((GridData)c.getLayoutData()).exclude = true;
                        getShell().layout();
                        getShell().pack();

                        start();
                    }
                });

                Button bNo = new Button(c, SWT.NONE);
                bNo.setText("   No   ");
                bNo.addSelectionListener(new SelectionAdapter()
                {
                    @Override
                    public void widgetSelected(SelectionEvent selectionEvent)
                    {
                        closeDialog(true);
                    }
                });

                shell.setDefaultButton(bYes);
            } else {
                label.setText(_label);

                shell.addListener(SWT.Show, new Listener()
                {
                    @Override
                    public void handleEvent(Event arg0)
                    {
                        start();
                    }
                });
            }
        }

        protected void start()
        {
            Shell shell = getShell();

            // prevent user from closing by pressing ESC
            shell.addListener(SWT.Traverse, new Listener() {
                @Override
                public void handleEvent(Event e) {
                    if (e.detail == SWT.TRAVERSE_ESCAPE) {
                        e.doit = false;
                    }
                }
            });

            _compSpin.start();

            // perform actual work
            GUI.get().safeWork(shell, FeedbackDialog.this);
        }

        @Override
        public void okay()
        {
            _compSpin.stop();

            refreshVersionTree();

            closeDialog(true);
        }

        @Override
        public void error(Exception e)
        {
            _compSpin.stop();

            new AeroFSMessageBox(getShell(), true, e.getLocalizedMessage(), IconType.ERROR)
                    .open();

            // refresh in case of partial restore
            refreshVersionTree();

            closeDialog(false);
        }
    }
}
