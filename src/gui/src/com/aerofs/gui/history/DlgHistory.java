package com.aerofs.gui.history;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.AeroFSMessageBox;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.history.HistoryModel.IDecisionMaker;
import com.aerofs.gui.history.HistoryModel.ModelIndex;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.google.common.collect.Maps;
import org.apache.log4j.Logger;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Button;

import javax.annotation.Nullable;


public class DlgHistory extends AeroFSDialog
{
    private static final Logger l = Util.l(DlgHistory.class);

    private final Map<Program, Image> _iconCache = Maps.newHashMap();

    private Tree _revTree;
    private Label _statusLabel;
    private Table _revTable;

    private final Path _basePath;
    private final HistoryModel _model;

    public DlgHistory(Shell parent)
    {
        super(parent, "Version History", false, true);
        _model = new HistoryModel();
        _basePath = null;
    }

    public DlgHistory(Shell parent, Path path)
    {
        super(parent, "Version History", false, true);
        _model = new HistoryModel();
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

        createVersionTree(sashForm);

        Group group = new Group(sashForm, SWT.NONE);
        GridLayout groupLayout = new GridLayout(1, false);
        groupLayout.marginHeight = GUIParam.MARGIN;
        groupLayout.marginWidth = GUIParam.MARGIN;
        group.setLayout(groupLayout);
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        _statusLabel = new Label(group, SWT.NONE);
        _statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        createVersionTable(group);

        // the version table will only be shown when a file is selected
        hideVersionTable();

        // set default proportion between version tree and version table
        // NOTE: must be done AFTER children have been added to the SashForm
        sashForm.setWeights(new int[] {1, 2});

        // Create a composite that will hold the buttons row
        Composite buttons = new Composite(shell, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.END, SWT.BOTTOM, true, false, 2, 1));
        RowLayout buttonLayout = new RowLayout();
        buttonLayout.pack = false;
        buttonLayout.center = true;
        buttons.setLayout(buttonLayout);

        Button refreshBtn = new Button(buttons, SWT.NONE);
        refreshBtn.setText("Refresh");
        refreshBtn.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                Path path = null;
                TreeItem[] items = _revTree.getSelection();
                if (items != null && items.length == 1) {
                    path = _model.getPath(index(items[0]));
                }

                // clear the tree and the underlying model
                _revTree.setItemCount(0);
                _model.clear();

                // populate first level of tree
                _revTree.setItemCount(_model.rowCount(null));

                // reselect previously selected item, if possible
                if (path != null) selectPath(path);
            }
        });

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
                // Clear the icon
                for (Image img : _iconCache.values()) img.dispose();
                _iconCache.clear();
            }
        });

        // select base path, if any
        if (_basePath != null) selectPath(_basePath);
    }

    private void selectPath(Path path)
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
            _revTree.select(item);
            refreshVersionTable(item);
        }
    }

    private ModelIndex index(@Nullable TreeItem item)
    {
        return item == null ? null : (ModelIndex)item.getData();
    }

    private void createVersionTree(Composite parent)
    {
        _revTree = new Tree(parent, SWT.SINGLE | SWT.VIRTUAL | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        //GridData treeLayout = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        //treeLayout.widthHint = 150;
        //_revTree.setLayoutData(treeLayout);

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
                if (!(index.isDir && index.isDeleted)) return;

                Menu menu = new Menu(_revTree.getShell(), SWT.POP_UP);
                addMenuItem(menu, "Restore", new Listener()
                {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        recursivelyRestore(index);
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
    }

    private void refreshVersionTable(TreeItem item)
    {
        ModelIndex index = index(item);
        if (index != null && !index.isDir) {
            _revTable.setVisible(true);
            fillVersionTable(_revTable, index, _statusLabel);
        } else {
            hideVersionTable();
        }
    }

    private void hideVersionTable()
    {
        _revTable.removeAll();
        _statusLabel.setText("Select a file to view all locally known versions.");
        _revTable.setVisible(false);
    }

    /**
     * Creates the revision table (ie: the table on the right side of the dialog)
     * @param parent
     */
    private void createVersionTable(Composite parent)
    {
        Composite revTableWrap = new Composite(parent, SWT.NONE);
        revTableWrap.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        _revTable = new Table(revTableWrap, SWT.BORDER);
        _revTable.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, true, true, 1, 1));
        _revTable.setLinesVisible(true);
        _revTable.setHeaderVisible(true);

        final TableColumn revDateCol = new TableColumn(_revTable, SWT.NONE);
        revDateCol.setText("Date");
        revDateCol.setResizable(true);
        final TableColumn revSizeCol = new TableColumn(_revTable, SWT.NONE);
        revSizeCol.setText("Size");
        revSizeCol.setResizable(true);

        TableColumnLayout revTableLayout = new TableColumnLayout();
        revTableLayout.setColumnData(revDateCol,
                new ColumnWeightData(6, ColumnWeightData.MINIMUM_WIDTH));
        revTableLayout.setColumnData(revSizeCol,
                new ColumnWeightData(3, ColumnWeightData.MINIMUM_WIDTH));
        revTableWrap.setLayout(revTableLayout);

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
                        saveRevisionAs(version);
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
        _revTable.addSelectionListener(new SelectionAdapter()
        {
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

    private void fillVersionTable(Table revTable, ModelIndex index, Label status)
    {
        Path path = _model.getPath(index);
        status.setText("Previous versions of \"" + path.last() + "\":");

        List<HistoryModel.Version> versions = _model.versions(index);
        revTable.removeAll();
        if (versions == null) return;
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
    }

    private void openVersion(HistoryModel.Version version)
    {
        fetchTempFile(version);
        if (version.tmpFile == null) return;

        Program.launch(version.tmpFile);
    }

    private void saveRevisionAs(HistoryModel.Version version)
    {
        // Display a "Save As" dialog box
        FileDialog fDlg = new FileDialog(this.getShell(), SWT.SHEET | SWT.SAVE);
        fDlg.setFilterNames(new String[]{"All files"});
        fDlg.setFilterExtensions(new String[]{"*.*"});
        fDlg.setFilterPath(
                Util.join(Cfg.absRootAnchor(), Util.join(version.path.removeLast().elements())));
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

    private String fmt(ModelIndex index)
    {
        return (index.isDir ? "Folder " : "File ") + _model.getPath(index);
    }


    private void recursivelyRestore(ModelIndex index)
    {
        DirectoryDialog dDlg = new DirectoryDialog(getShell(), SWT.SHEET);
        dDlg.setFilterPath(
                Util.join(Cfg.absRootAnchor(),
                          Util.join(_model.getPath(index).removeLast().elements())));
        String path = dDlg.open();
        if (path == null) return;

        try {
            _model.restore(index, path, new IDecisionMaker()
            {
                @Override
                public Answer retry(ModelIndex a)
                {
                    MessageBox mb = new MessageBox(getShell(),
                            SWT.ICON_ERROR | SWT.ABORT | SWT.RETRY | SWT.IGNORE);
                    mb.setText("Failed to restore");
                    mb.setMessage("Failed to restore " + _model.getPath(a));
                    int ret = mb.open();
                    switch (ret) {
                    case SWT.ABORT:  return Answer.Abort;
                    case SWT.RETRY:  return Answer.Retry;
                    case SWT.IGNORE: return Answer.Retry;
                    default: throw new IllegalArgumentException();
                    }
                }

                @Override
                public ModelIndex resolve(ModelIndex a, ModelIndex b)
                {
                    // TODO(huguesb): improve this...
                    MessageBox mb = new MessageBox(getShell(),
                            SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
                    mb.setText("Name conflict");
                    mb.setMessage(fmt(a) + "\nconflicts with\n" +
                            fmt(b) + "\n\nRestore the first one?");
                    int ret = mb.open();
                    switch (ret) {
                    case SWT.YES:    return a;
                    case SWT.NO:     return b;
                    case SWT.CANCEL: return null;
                    default: throw new IllegalArgumentException();
                    }
                }
            });
        } catch (Exception e) {
            UI.get().show(MessageType.ERROR, e.getLocalizedMessage());
        }
    }
}
