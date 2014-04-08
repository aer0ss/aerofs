/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.gui.sharing;

import com.aerofs.base.Loggers;
import com.aerofs.gui.AbstractSpinAnimator;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.TaskDialog;
import com.aerofs.gui.sharing.ShareNewFolderDialogs.IShareNewFolderCallback;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Ritual.ListSharedFoldersReply;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessages;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Map.Entry;

/**
 * The shared folder list in the Manage Shared Folder dialog
 */
class SharedFolderList extends Composite
{
    private static final Logger l = Loggers.getLogger(SharedFolderList.class);

    private final Table _d;
    private final Button _btnRemove;
    private final Button _btnOpen;
    private MemberList _memberList;

    private final AbstractSpinAnimator _animator = new AbstractSpinAnimator(this) {
        @Override
        protected void setImage(Image img)
        {
            _d.getItem(0).setImage(img);
        }
    };

    private static final String PATH_DATA = "Path";
    private static final String ABS_PATH_DATA = "AbsPath";

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

        Label lbl = new Label(this, SWT.NONE);
        lbl.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        lbl.setText("Shared Folders:");

        Composite c = DlgManageSharedFolders.newTableWrapper(this);
        c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        _d = new Table(c, SWT.SINGLE | SWT.BORDER | SWT.H_SCROLL | SWT.H_SCROLL);
        _d.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        _d.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                TableItem ti = (TableItem)e.item;
                Path path = ti != null ? (Path) ti.getData(PATH_DATA) : null;
                _memberList.setPath(path, false);
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                TableItem ti = (TableItem) e.item;
                if (ti == null) return;

                GUIUtil.launch((String) ti.getData(ABS_PATH_DATA));
            }
        });

        // tooltip is the absPath, which only make sense for Linked storage
        if (Cfg.storageType() == StorageType.LINKED) {
            new TableToolTip(_d, ABS_PATH_DATA);
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

                IShareNewFolderCallback callback = new IShareNewFolderCallback()
                {
                    @Override
                    public void onSuccess()
                    {
                        refresh();
                    }
                };

                boolean shift = Util.test(event.stateMask, SWT.SHIFT);
                new ShareNewFolderDialogs(getShell(), callback).open(shift);
            }
        });

        _btnRemove = GUIUtil.createButton(bar, SWT.PUSH);
        _btnRemove.setText("Leave");
        _btnRemove.setToolTipText("Leave the selected shared folder");
        _btnRemove.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                Path path = selectedPath();
                if (path != null) leave(path, selectedName());
            }
        });

        _btnOpen = GUIUtil.createButton(bar, SWT.PUSH);
        _btnOpen.setText("Open");
        _btnOpen.setToolTipText("Open the selected folder");
        _btnOpen.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                Path path = selectedPath();
                if (path == null) return;
                String absPath = UIUtil.absPathNullable(path);
                if (absPath != null) GUIUtil.launch(absPath);
            }
        });

        if (L.isMultiuser()) {
            // Team Server cannot leave folders. Keep the button in the dialog but hidden so
            // the dialog layout is consistent across different configurations.
            // TODO: use expulsion under the hood to filter out some shared folders?
            _btnRemove.setVisible(false);
        }

        if (Cfg.storageType() != StorageType.LINKED) {
            // Open and Add only makes sense for linked storage. Keep the button in the dialog but
            // hidden so the dialog layout is consistent across different configurations.
            _btnOpen.setVisible(false);
            btnAdd.setVisible(false);
        }
    }

    void setMemberList(MemberList memberList)
    {
        _memberList = memberList;
    }

    private @Nullable Path selectedPath()
    {
        TableItem[] items = _d.getSelection();
        return items.length != 1 ? null : (Path)items[0].getData(PATH_DATA);
    }

    private @Nullable String selectedName()
    {
        TableItem[] items = _d.getSelection();
        return items.length != 1 ? null : items[0].getText();
    }

    private void setLoading(boolean loading)
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

    void refresh()
    {
        _memberList.setPath(null, false);
        setLoading(true);

        Futures.addCallback(UIGlobals.ritualNonBlocking().listSharedFolders(),
                new FutureCallback<ListSharedFoldersReply>()
                {
                    @Override
                    public void onSuccess(@Nonnull final ListSharedFoldersReply reply)
                    {
                        GUI.get().safeAsyncExec(SharedFolderList.this, new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                setLoading(false);
                                fill(reply.getSharedFolderList());
                            }
                        });
                    }

                    @Override
                    public void onFailure(@Nonnull Throwable e)
                    {
                        setLoading(false);
                        ErrorMessages.show(getShell(), e, "Failed to retrieve shared folders.");
                    }
                }
        );
    }

    private void fill(List<PBSharedFolder> sharedFolders)
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
        if (_btnRemove != null) _btnRemove.setEnabled(notEmpty);
        if (_btnOpen != null) _btnOpen.setEnabled(notEmpty);

        _memberList.setPath(selectedPath(), false);
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
                refresh();
            }

            @Override
            public void error(Exception e)
            {
                super.error(e);
                refresh();
            }
        }.openDialog();
    }
}