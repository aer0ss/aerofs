package com.aerofs.gui.revision;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.AeroFSMessageBox;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Ritual.ExportRevisionReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.PBRevChild;
import com.aerofs.proto.Ritual.PBRevision;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Button;

import javax.annotation.Nullable;


public class DlgRevision extends AeroFSDialog
{
    private static final Logger l = Util.l(DlgRevision.class);

    private final Map<Program, Image> _iconCache = new HashMap<Program, Image>();
    private Tree _revTree;
    private Label _statusLabel;

    private class Rev
    {
        public Path path;
        public ByteString index;
        public String tmpFile;

        Rev(Path p, ByteString i)
        {
            path = p;
            index = i;
            tmpFile = null;
        }
    }

    private final RitualBlockingClient _ritual;
    private Table _revTable;

    public DlgRevision(Shell parent)
    {
        super(parent, "Version History", false, true);
        _ritual = RitualClientFactory.newBlockingClient();
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
        grid.horizontalSpacing = 4;
        grid.verticalSpacing = 4;
        shell.setMinimumSize(560, 400);
        shell.setSize(560, 400);
        shell.setLayout(grid);

        createRevisionTree(shell);

        Group group = new Group(sh, SWT.NONE);
        GridLayout groupLayout = new GridLayout(1, false);
        groupLayout.marginHeight = GUIParam.MARGIN;
        groupLayout.marginWidth = GUIParam.MARGIN;
        group.setLayout(groupLayout);
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));

        _statusLabel = new Label(group, SWT.NONE);
        GridData labelData = new GridData(GridData.GRAB_HORIZONTAL);
        labelData.horizontalAlignment = SWT.FILL;
        labelData.verticalAlignment = SWT.TOP;
        _statusLabel.setLayoutData(labelData);

        createRevisionTable(group);

        // Create a composite that will hold the buttons row
        Composite buttons = new Composite(shell, SWT.NONE);
        GridData gridData = new GridData(GridData.END, GridData.CENTER, false, false);
        gridData.horizontalSpan = 2;
        buttons.setLayoutData(gridData);
        buttons.setLayout(new RowLayout());

//        Button refreshBtn = new Button(buttons, SWT.NONE);
//        refreshBtn.setText("Refresh");
//        refreshBtn.addSelectionListener(new SelectionAdapter()
//        {
//            @Override
//            public void widgetSelected(SelectionEvent selectionEvent)
//            {
//                // TODO (GS): Implement me
//            }
//        });

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
    }

    private void createRevisionTree(Composite parent)
    {
        _revTree = new Tree(parent, SWT.SINGLE | SWT.VIRTUAL | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        GridData treeLayout = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        treeLayout.widthHint = 150;
        _revTree.setLayoutData(treeLayout);

        // populate first level of revision tree
        createChildren(null);

        // incremental tree population
        _revTree.addListener(SWT.SetData, new Listener() {
            @SuppressWarnings("unchecked")
            @Override
            public void handleEvent(Event event) {
                TreeItem item = (TreeItem)event.item;
                TreeItem parent = item.getParentItem();
                List<PBRevChild> siblings;
                if (parent == null) {
                    siblings = (List<PBRevChild>) _revTree.getData();
                } else {
                    siblings = (List<PBRevChild>) parent.getData();
                }

                PBRevChild revChild = siblings.get(event.index);
                item.setText(revChild.getName());
                item.setData("rev", revChild);
                if (revChild.getIsDir()) {
                    item.setImage(Images.getFolderIcon());
                    createChildren(item);
                } else {
                    item.setImage(Images.getFileIcon(revChild.getName(), _iconCache));
                }
            }
        });

        // detect selection and update revision table as needed
        _revTree.addListener(SWT.Selection, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                TreeItem item = (TreeItem) event.item;
                if (item == null) return;
                PBRevChild revChild = (PBRevChild) item.getData("rev");
                if (!revChild.getIsDir()) {
                    fillRevTable(_revTable, getPathFromTreeItem(item), _statusLabel);
                } else {
                    _revTable.removeAll();
                    _statusLabel.setText("");
                }
            }
        });
    }

    /**
     * Creates the revision table (ie: the table on the right side of the dialog)
     * @param parent
     */
    private void createRevisionTable(Composite parent)
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

                final Rev rev = (Rev) item.getData();

                Menu menu = new Menu(_revTable.getShell(), SWT.POP_UP);

                addMenuItem(menu, "Open", new Listener()
                {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        openRevision(rev);
                    }
                });

                addMenuItem(menu, "Save As...", new Listener()
                {
                    @Override
                    public void handleEvent(Event ev)
                    {
                        saveRevisionAs(rev);
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
                final Rev rev = (Rev) item.getData();
                if (rev == null) return;
                openRevision(rev);
            }
        });
    }

    /**
     * For a given tree item, return the path that corresponds to it
     * If item is null, return a path to the root anchor
     */
    private Path getPathFromTreeItem(@Nullable TreeItem item)
    {
        List<String> elems = new ArrayList<String>();
        while (item != null) {
            elems.add(0, item.getText());
            item = item.getParentItem();
        }

        return new Path(elems);
    }

    /**
     * Given a tree item, queries ritual for its children and set them on the item
     * If item is null, it will use _revTree as the root item
     */
    private void createChildren(@Nullable TreeItem item)
    {
        try {
            ListRevChildrenReply reply = _ritual.listRevChildren(getPathFromTreeItem(item).toPB());
            List<PBRevChild> children = reply.getChildList();
            if (children == null) return;
            if (item != null) {
                item.setData(children);
                item.setItemCount(children.size());
            } else {
                _revTree.setData(children);
                _revTree.setItemCount(children.size());
            }
        } catch (Exception e) {
            l.warn(Util.e(e));
        }
    }

    private void fillRevTable(Table revTable, Path path, Label status)
    {
        status.setText("Previous versions of \"" + path.last() + "\":");

        List<PBRevision> revisions = null;
        try {
            ListRevHistoryReply reply = _ritual.listRevHistory(path.toPB());
            revisions = reply.getRevisionList();
        } catch (Exception e) {
            l.warn(Util.e(e));
        }
        revTable.removeAll();
        if (revisions == null) return;
        revTable.setItemCount(revisions.size());
        for (int i = 0; i < revisions.size(); ++i) {
            PBRevision rev = revisions.get(i);
            TableItem item = revTable.getItem(i);
            item.setText(0, Util.formatAbsoluteTime(rev.getMtime()));
            item.setText(1, Util.formatSize(rev.getLength()));
            item.setData(new Rev(path, rev.getIndex()));
        }
    }

    private void openRevision(Rev revision)
    {
        fetchTempFile(revision);
        if (revision.tmpFile == null) return;

        Program.launch(revision.tmpFile);
    }

    private void saveRevisionAs(Rev revision)
    {
        // Display a "Save As" dialog box
        FileDialog fDlg = new FileDialog(this.getShell(), SWT.SHEET | SWT.SAVE);
        fDlg.setFilterNames(new String[]{"All files"});
        fDlg.setFilterExtensions(new String[]{"*.*"});
        fDlg.setFilterPath(
                Util.join(Cfg.absRootAnchor(), Util.join(revision.path.removeLast().elements())));
        fDlg.setFileName(revision.path.last());
        fDlg.setOverwrite(true); // The OS will show a warning if the user chooses an existing name

        String dst = fDlg.open();
        if (dst == null) return; // User closed the save dialog

        fetchTempFile(revision);
        if (revision.tmpFile == null) return; // Ritual call failed

        try {
            FileUtil.moveInOrAcrossFileSystem(new File(revision.tmpFile), new File(dst));
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
    private void fetchTempFile(Rev revision)
    {
        if (revision.tmpFile == null) {
            try {
                ExportRevisionReply reply = _ritual.exportRevision(revision.path.toPB(), revision.index);
                revision.tmpFile = reply.getDest();
            } catch (Exception e) {
                l.warn("Fetching revision failed: " + Util.e(e));
                new AeroFSMessageBox(this.getShell(), false, e.getLocalizedMessage(),
                        AeroFSMessageBox.IconType.ERROR)
                        .open();
            }
        }
    }

    private MenuItem addMenuItem(Menu menu, String text, Listener listener)
    {
        MenuItem mi = new MenuItem(menu, SWT.PUSH);
        mi.setText(text);
        if (listener != null) mi.addListener(SWT.Selection, listener);
        return mi;
    }
}
