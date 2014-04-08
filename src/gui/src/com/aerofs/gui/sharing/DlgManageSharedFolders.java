/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.gui.AbstractSpinAnimator;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.TaskDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.sharing.ShareNewFolderDialogs.IShareNewFolderCallback;
import com.aerofs.gui.sharing.manage.CompUserList;
import com.aerofs.gui.sharing.manage.CompUserList.ILoadListener;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Ritual.ListSharedFoldersReply;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;

public class DlgManageSharedFolders extends AeroFSDialog
{
    private static final Logger l = Loggers.getLogger(DlgManageSharedFolders.class);

    private final @Nullable Path _path;

    private FolderList _folderList;
    private MemberList _memberList;

    public DlgManageSharedFolders(Shell parent)
    {
        super(parent, "Manage Shared Folders", false, true);
        _path = null;
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        final Shell shell = sh;
        GridLayout grid = new GridLayout(1, false);
        grid.marginHeight = GUIParam.MARGIN;
        grid.marginWidth = GUIParam.MARGIN;
        grid.horizontalSpacing = 4;
        grid.verticalSpacing = 4;
        shell.setLayout(grid);

        if (_path == null) {
            SashForm sashForm = new SashForm(shell, SWT.HORIZONTAL | SWT.SMOOTH);
            GridData sashData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
            sashData.heightHint = 300;
            sashForm.setLayoutData(sashData);
            sashForm.setSashWidth(7);

            _folderList = new FolderList(sashForm, SWT.NONE);
            _memberList = new MemberList(sashForm, SWT.NONE);

            // set default proportion as the golden split between version tree and version table
            // NOTE: must be done AFTER children have been added to the SashForm
            sashForm.setWeights(new int[] {382, 618});

            refreshFolderList();
        } else {
            _folderList = null;
            _memberList = new MemberList(shell, SWT.NONE);
            GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
            gd.widthHint = 250;
            gd.heightHint = 300;
            _memberList.setLayoutData(gd);

            refreshMemberList(_path, true);
        }
    }

    void refreshFolderList()
    {
        _folderList.setLoading(true);
        _memberList.setPath(null, false);

        Futures.addCallback(UIGlobals.ritualNonBlocking().listSharedFolders(),
                new FutureCallback<ListSharedFoldersReply>() {
            @Override
            public void onSuccess(@Nonnull final ListSharedFoldersReply reply)
            {
                GUI.get().safeAsyncExec(_folderList, new Runnable() {
                    @Override
                    public void run()
                    {
                        _folderList.setLoading(false);
                        _folderList.fill(reply.getSharedFolderList());
                    }
                });
            }

            @Override
            public void onFailure(@Nonnull Throwable e)
            {
                _folderList.setLoading(false);
                ErrorMessages.show(getShell(), e, "Failed to retrieve shared folders.");
            }
        });
    }

    private void refreshMemberList(Path path, boolean invite)
    {
        _memberList.setPath(path, invite);
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
                refreshFolderList();
            }

            @Override
            public void error(Exception e)
            {
                super.error(e);
                refreshFolderList();
            }
        }.openDialog();
    }

    private class FolderList extends Composite
    {
        private final Table _d;
        private final Button _btnPlus;
        private final Button _btnMinus;
        private final Button _btnOpen;

        private final AbstractSpinAnimator _animator = new AbstractSpinAnimator(this) {
            @Override
            protected void setImage(Image img)
            {
                _d.getItem(0).setImage(img);
            }
        };

        private static final String PATH_DATA = "Path";
        private static final String ABS_PATH_DATA = "AbsPath";

        public FolderList(Composite composite, int i)
        {
            super(composite, i);

            GridLayout l = new GridLayout(1, false);
            l.marginBottom = 0;
            l.marginTop = 0;
            l.marginRight = 0;
            l.marginLeft = 0;
            l.marginHeight = 0;
            l.marginWidth = 0;
            setLayout(l);

            Label lbl = new Label(this, SWT.NONE);
            lbl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            lbl.setText("Shared Folders:");

            Composite c = newTableWrapper(this);
            c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            _d = new Table(c, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.H_SCROLL);
            _d.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
            _d.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    TableItem i = (TableItem)e.item;
                    Path path = i != null ? (Path)i.getData(PATH_DATA) : null;
                    refreshMemberList(path, false);
                }

                @Override
                public void widgetDefaultSelected(SelectionEvent e)
                {
                    TableItem i = (TableItem)e.item;
                    if (i == null) return;

                    GUIUtil.launch((String)i.getData(ABS_PATH_DATA));
                }
            });

            // tooltip is the absPath, which only make sense for Linked storage
            if (Cfg.storageType() == StorageType.LINKED) {
                new TableToolTip(_d, ABS_PATH_DATA);
            }

            Composite bar = GUIUtil.newPackedButtonContainer(c);
            bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

            if (Cfg.storageType() == StorageType.LINKED) {
                _btnPlus = GUIUtil.createButton(bar, SWT.PUSH);
                _btnPlus.setText("Add...");
                _btnPlus.setToolTipText("Share a folder");
                _btnPlus.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent event)
                    {

                        IShareNewFolderCallback callback = new IShareNewFolderCallback() {
                            @Override
                            public void onSuccess()
                            {
                                refreshFolderList();
                            }
                        };

                        boolean shift = Util.test(event.stateMask, SWT.SHIFT);
                        new ShareNewFolderDialogs(getShell(), callback).open(shift);
                    }
                });
            } else {
                _btnPlus = null;
            }

            _btnMinus = GUIUtil.createButton(bar, SWT.PUSH);
            _btnMinus.setText("Leave");
            _btnMinus.setToolTipText("Leave the selected shared folder");
            _btnMinus.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    Path path = _folderList.selectedPath();
                    if (path != null) leave(path, _folderList.selectedName());
                }
            });

            if (L.isMultiuser()) {
                // Team Server cannot leave folders. Keep the button in the dialog but hidden so
                // the dialog layout is consistent across different configurations.
                // TODO: use expulsion under the hood to filter out some shared folders?
                _btnMinus.setVisible(false);
            }

            _btnOpen = GUIUtil.createButton(bar, SWT.PUSH);
            _btnOpen.setText("Open");
            _btnOpen.setToolTipText("Open the selected folder");
            _btnOpen.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    Path path = _folderList.selectedPath();
                    if (path == null) return;
                    String absPath = UIUtil.absPathNullable(path);
                    if (absPath != null) GUIUtil.launch(absPath);
                }
            });

            if (Cfg.storageType() != StorageType.LINKED) {
                // Open only makes sense for linked storage. Keep the button in the dialog but
                // hidden so the dialog layout is consistent across different configurations.
                _btnOpen.setVisible(false);
            }

            // add an invisible button if the button bar is empty to preserve control alignment
            if (bar.getChildren().length == 0) {
                GUIUtil.createButton(bar, SWT.PUSH).setVisible(false);
            }
        }

        void fill(List<PBSharedFolder> sharedFolders)
        {
            _d.setItemCount(sharedFolders.size());
            int i = 0;
            for (Entry<String, Path> entry : UIUtil.getPathsSortedByName(sharedFolders)) {
                String name = entry.getKey();
                Path path = entry.getValue();

                TableItem item = _d.getItem(i++);
                item.setText(0, name);
                item.setData(PATH_DATA, path);

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
                        continue;
                    }
                    item.setData(ABS_PATH_DATA, path.toAbsoluteString(root));
                }
            }

            boolean notEmpty = !sharedFolders.isEmpty();

            if (notEmpty) _d.select(0);
            if (_btnMinus != null) _btnMinus.setEnabled(notEmpty);
            if (_btnOpen != null) _btnOpen.setEnabled(notEmpty);

            refreshMemberList(selectedPath(), false);
        }

        @Nullable Path selectedPath()
        {
            TableItem[] items = _d.getSelection();
            return items.length != 1 ? null : (Path)items[0].getData(PATH_DATA);
        }

        @Nullable String selectedName()
        {
            TableItem[] items = _d.getSelection();
            return items.length != 1 ? null : items[0].getText();
        }

        public void setLoading(boolean loading)
        {
            _d.removeAll();

            if (loading) {
                _d.setItemCount(1);

                TableItem item = _d.getItem(0);
                item.setText(0, S.GUI_LOADING);
                _animator.start();
            } else {
                _animator.stop();
            }
        }
    }

    private class MemberList extends Composite
    {
        private final Label _lbl;
        private final CompUserList _userList;
        private final Composite _btnBar;
        private final Button _btnPlus;
        private final CompSpin _compSpin;

        public MemberList(Composite composite, int i)
        {
            super(composite, i);

            GridLayout l = new GridLayout(1, false);
            l.marginBottom = 0;
            l.marginTop = 0;
            l.marginRight = 0;
            l.marginLeft = 0;
            l.marginHeight = 0;
            l.marginWidth = 0;
            setLayout(l);

            _lbl = new Label(this, SWT.NONE);
            _lbl.setText("Members:");
            _lbl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

            Composite c = newTableWrapper(this);
            c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            _userList = new CompUserList(c);
            _userList.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            _btnBar = GUIUtil.newPackedButtonContainer(c);
            _btnBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));

            _compSpin = new CompSpin(_btnBar, SWT.NONE);
            _userList.setSpinner(_compSpin);

            _btnPlus = GUIUtil.createButton(_btnBar, SWT.PUSH);
            _btnPlus.setText("Invite Others...");
            _btnPlus.setToolTipText("Invite another user to join the shared folder");
            _btnPlus.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    if (_path == null) {
                        new DlgInvite(getShell(), _folderList.selectedPath(),
                                _folderList.selectedName()).openDialog();
                    } else {
                        new DlgInvite(getShell(), _path, null).openDialog();
                    }
                }
            });
        }

        private void setVisible(Control c, boolean visible)
        {
            c.setVisible(visible);
            ((GridData)c.getLayoutData()).exclude = !visible;
        }

        void setPath(@Nullable Path path, final boolean invite)
        {
            boolean valid = path != null;
            setVisible(_userList, valid);
            setVisible(_btnBar, valid);
            _btnPlus.setGrayed(true);
            if (valid) {
                _userList.setLoadListener(new ILoadListener() {
                    @Override
                    public void loaded(int membersCount, @Nullable Permissions localUserPermissions)
                    {
                        // gray out invite button when not admin, except on Team Server where
                        // the ACL check is slightly more complicated...
                        // FIXME: TS needs effective ACL
                        boolean admin = L.isMultiuser() ||
                                (localUserPermissions != null
                                        && localUserPermissions.covers(Permission.MANAGE));
                        _btnPlus.setGrayed(!admin);
                        _btnPlus.setEnabled(admin);
                        if (invite) {
                            if (admin) {
                                _btnPlus.notifyListeners(SWT.Selection, new Event());
                            }
                        }
                    }
                });
                _userList.load(path);
            }

            layout(true, true);
        }
    }

    private Composite newTableWrapper(Composite parent)
    {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayout lc = new GridLayout(1, false);
        lc.marginBottom = 0;
        lc.marginTop = 0;
        lc.marginRight = 0;
        lc.marginLeft = 0;
        lc.marginHeight = 0;
        lc.marginWidth = 0;
        lc.horizontalSpacing = 0;
        lc.verticalSpacing = GUIParam.VERTICAL_SPACING;
        c.setLayout(lc);
        return c;
    }

    private static class DlgInvite extends AeroFSDialog
    {
        private final Path _path;

        public DlgInvite(Shell parent, Path path, String defaultName)
        {
            super(parent, "Invite Members to "
                    + Util.quote(UIUtil.sharedFolderName(path, defaultName)), true, false);
            _path = path;
        }

        @Override
        protected void open(Shell shell)
        {
            if (GUIUtil.isWindowBuilderPro()) {
                shell = new Shell(getParent(), getStyle());
            }

            shell.setLayout(new FillLayout(SWT.HORIZONTAL));

            CompInviteUsers.createForExistingSharedFolder(shell, _path);
        }
    }

    /**
     * Helper class to add custom dynamic tooltip to each item of a Table
     */
    private static class TableToolTip implements Listener
    {
        private Shell _tip = null;
        private Label _label = null;
        private final Table _table;
        private final String _toolTipData;

        TableToolTip(Table table, String toolTipData)
        {
            _table = table;
            _toolTipData = toolTipData;

            _table.setToolTipText("");
            _table.addListener(SWT.Dispose, this);
            _table.addListener(SWT.KeyDown, this);
            _table.addListener(SWT.MouseMove, this);
            _table.addListener(SWT.MouseHover, this);
        }

        @Override
        public void handleEvent(Event event) {
            switch (event.type) {
            case SWT.Dispose:
            case SWT.KeyDown:
            case SWT.MouseMove:
                if (_tip == null) break;
                _tip.dispose();
                _tip = null;
                _label = null;
                break;
            case SWT.MouseHover:
                Display display = _table.getDisplay();
                TableItem item = _table.getItem(new Point(event.x, event.y));
                if (item != null) {
                    if (_tip != null  && !_tip.isDisposed ()) _tip.dispose();
                    _tip = new Shell(_table.getShell(), SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
                    _tip.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));

                    FillLayout layout = new FillLayout();
                    layout.marginWidth = 2;
                    _tip.setLayout(layout);

                    _label = new Label(_tip, SWT.NONE);
                    _label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
                    _label.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                    _label.setData("_TABLEITEM", item);
                    _label.setText((String)item.getData(_toolTipData));
                    _label.addListener(SWT.MouseExit, labelListener);
                    _label.addListener(SWT.MouseDown, labelListener);

                    Point size = _tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                    Rectangle rect = item.getBounds(0);
                    Point pt = _table.toDisplay(rect.x, rect.y);
                    _tip.setBounds(pt.x, pt.y, size.x, size.y);
                    _tip.setVisible(true);
                }
                break;
            default:
                break;
            }
        }

        private final Listener labelListener = new Listener () {
            @Override
            public void handleEvent(Event event) {
                Label label = (Label)event.widget;
                switch (event.type) {
                case SWT.MouseDown:
                    Event e = new Event();
                    e.item = (TableItem)label.getData("_TABLEITEM");
                    // Assuming table is single select, set the selection as if
                    // the mouse down event went through to the table
                    _table.setSelection(new TableItem [] {(TableItem) e.item});
                    _table.notifyListeners(SWT.Selection, e);
                    _tip.dispose();
                    _table.setFocus();
                    break;
                case SWT.MouseExit:
                    _tip.dispose();
                    break;
                }
            }
        };
    }
}
