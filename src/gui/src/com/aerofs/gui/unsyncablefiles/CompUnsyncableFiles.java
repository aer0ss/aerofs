/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.unsyncablefiles;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Ritual.ListNonRepresentableObjectsReply;
import com.aerofs.proto.Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;

import java.util.Set;

import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;
import static com.google.common.base.Preconditions.checkState;

/**
 * States that are maintained by this composite:
 *   - Busy := an operation is in progress, thus _table, _btnRefresh, _btnOpen, _btnRename,
 *     and _btnDeleted are disabled. The operation could be a refresh or a delete.
 *   - Not Busy := _table, _btnRefresh are enabled, there are two possibilities.
 *     - 0 rows selected: _btnOpen, _btnRename, _btnDeleted are disabled.
 *     - 1 row selected: all buttons enabled.
 *     - many rows selected: _btnRename is disabled.
 *
 * The busy / not busy state prevents concurrent operations and doing any operation while the
 * stale data is being refreshed, and selected state prevents an operation that doesn't have the
 * expected numbe of rows from starting.
 *
 * The busy state is set when a refresh or delete is in progress.
 *
 * It's necessary to check for selected state after the GUI enters the not busy state; this is done
 * when clearBusyState() is invoked.
 */
public class CompUnsyncableFiles extends Composite
{
    private final TblUnsyncableFiles    _table;
    private final Link                  _lnkHelp;
    private final Composite             _buttonBar;
    private final Button                _btnRefresh;
    private final CompSpin              _spinner;
    private final Button                _btnOpen;
    private final Button                _btnRename;
    private final Button                _btnDelete;

    public CompUnsyncableFiles(Composite parent)
    {
        super(parent, SWT.NONE);

        _table = new TblUnsyncableFiles(this);
        // open the selected files on double-click / enter pressed
        _table.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                onCmdOpen();
            }
        });
        // update the selected state
        _table.addPostSelectionChangedListener(new ISelectionChangedListener()
        {
            @Override
            public void selectionChanged(SelectionChangedEvent selectionChangedEvent)
            {
                onSelectionChanged();
            }
        });

        _lnkHelp = new Link(this, SWT.NONE);
        _lnkHelp.setText("<a>Learn more about unsyncable files.</a>");
        _lnkHelp.addSelectionListener(
                createUrlLaunchListener("https://support.aerofs.com/entries/23776990"));

        _spinner = new CompSpin(this, SWT.NONE);

        _buttonBar = GUIUtil.newButtonContainer(this, false);

        _btnRefresh = GUIUtil.createButton(_buttonBar, SWT.PUSH);
        _btnRefresh.setText("Refresh");
        _btnRefresh.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                onCmdRefresh();
            }
        });

        _btnOpen = GUIUtil.createButton(_buttonBar, SWT.PUSH);
        // This button's action is best known as "Show in Finder" in the OS X world. "Show in
        // Finder" is a common and well-understood term in OS X, but it makes no sense on other
        // platforms. Thus we are using platform-specific text.
        _btnOpen.setText(OSUtil.isOSX() ? "Show in Finder" : "Open Parent Folder");
        getShell().setDefaultButton(_btnOpen);
        _btnOpen.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                onCmdOpen();
            }
        });

        _btnRename = GUIUtil.createButton(_buttonBar, SWT.PUSH);
        _btnRename.setText("Rename...");
        _btnRename.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                onCmdRename();
            }
        });

        _btnDelete = GUIUtil.createButton(_buttonBar, SWT.PUSH);
        _btnDelete.setText("Delete...");
        _btnDelete.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                onCmdDelete();
            }
        });

        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = GUIParam.MAJOR_SPACING;
        setLayout(layout);

        GridData tableLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1);
        tableLayoutData.widthHint = 640;
        tableLayoutData.heightHint = 160;
        _table.setLayoutData(tableLayoutData);

        _lnkHelp.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        _spinner.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        _buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        // when the dialog is constructed, we'll be in the busy state while we are loading
        // data. After loading the data, the busy state will be set to false, which will set
        // selected / none selected state.
        onCmdRefresh();
    }

    private void setBusyState()
    {
        checkState(GUI.get().isUIThread());

        Control[] controls = new Control[]{ _btnRefresh, _table, _btnOpen, _btnRename, _btnDelete };
        for (Control control : controls) control.setEnabled(false);

        _spinner.start();
    }

    private void clearBusyState()
    {
        checkState(GUI.get().isUIThread());

        Control[] controls = new Control[] { _btnRefresh, _table, _btnOpen, _btnRename, _btnDelete };
        for (Control control : controls) control.setEnabled(true);

        _spinner.stop();

        onSelectionChanged();
    }

    /**
     * Called to enforce the selected state
     *
     * @pre called from UI thread.
     */
    private void onSelectionChanged()
    {
        checkState(GUI.get().isUIThread());

        int count = _table.getSelectedFilesCount();
        _btnOpen.setEnabled(count > 0);
        _btnRename.setEnabled(count == 1);
        _btnDelete.setEnabled(count > 0);
    }

    /**
     * @pre called from UI thread.
     */
    private void onCmdRefresh()
    {
        checkState(GUI.get().isUIThread());

        setBusyState();

        GUI.get().safeWork(getShell(), new ISWTWorker()
        {
            ImmutableCollection<UnsyncableFile> _files;

            @Override
            public void run() throws Exception
            {
                ListNonRepresentableObjectsReply reply
                        = UIGlobals.ritual().listNonRepresentableObjects();

                ImmutableList.Builder<UnsyncableFile> builder = ImmutableList.builder();

                for (PBNonRepresentableObject nro : reply.getObjectsList()) {
                    UnsyncableFile file;
                    try {
                        file = UnsyncableFile.fromPB(nro);
                    } catch (ExBadArgs e) {
                        // skip the objects for which we cannot resolve the path to its store
                        continue;
                    }

                    builder.add(file);
                }

                _files = builder.build();
            }

            @Override
            public void okay()
            {
                _table.setFiles(_files);
                clearBusyState();
            }

            @Override
            public void error(Exception e)
            {
                ErrorMessages.show(getShell(), e,
                        "Sorry, we encountered an error while loading the list of " +
                                "unsyncable files.");
                clearBusyState();
            }
        });
    }

    /**
     * @pre called from UI thread and at least one row in the table is selected
     */
    private void onCmdOpen()
    {
        checkState(GUI.get().isUIThread());
        checkState(_table.getSelectedFilesCount() > 0);

        // the following logic is necessary because launching the same path multiple times will
        // open up multiple windows in Unity (the default window manager in Ubuntu 12.04). In
        // general, we can't expect Linux windows manager to optimize this like Windows and OS X.
        Set<String> parentAbsPaths = Sets.newTreeSet();

        boolean hasWarning = false;

        // it's not necessary to set busy state here because launch should be async
        for (UnsyncableFile file : _table.getSelectedFiles()) {
            String parentAbsPath = UIUtil.absPathNullable(file._path.removeLast());

            if (parentAbsPath == null) {
                // This shouldn't happen. But since the data came from an external source, we
                // should guard against it.
                hasWarning = true;
            } else {
                parentAbsPaths.add(parentAbsPath);
            }
        }

        if (hasWarning) {
            String message = "Unable to browse some of the selected files.";
            GUI.get().show(getShell(), MessageType.ERROR, message);
        }

        for (String path : parentAbsPaths) GUIUtil.launch(path);
    }

    /**
     * @pre called from UI thread and exactly one row in the table is selected
     */
    private void onCmdRename()
    {
        checkState(GUI.get().isUIThread());
        checkState(_table.getSelectedFilesCount() == 1);

        // it's not necessary to set busy state here because the rename dialogs are blocking
        Path path = _table.getSelectedFiles().get(0)._path;
        // only refresh if the user renamed a file
        if (new DlgRenameFile(getShell(), path).openDialog() != null) onCmdRefresh();
    }

    /**
     * @pre called from UI thread and at least one row in the table is selected
     */
    private void onCmdDelete()
    {
        checkState(GUI.get().isUIThread());
        checkState(_table.getSelectedFilesCount() > 0);

        String objects = _table.getSelectedFilesCount() == 1 ? "this file" : "these files";
        String message = "This action will delete " + objects + " on this and other computers. " +
                "Are you sure you want to delete " + objects + "?";
        if (GUI.get().ask(getShell(), MessageType.WARN, message)) {
            setBusyState();

            final ImmutableList<UnsyncableFile> files = _table.getSelectedFiles();
            GUI.get().safeWork(getShell(), new ISWTWorker()
            {
                @Override
                public void run() throws Exception
                {
                    for (UnsyncableFile file : files) {
                        UIGlobals.ritual().deleteObject(file._path.toPB());
                    }
                }

                @Override
                public void okay()
                {
                    // it's not necessary to clearBusyState to false here because we will follow up
                    // with a refresh
                    onCmdRefresh();
                }

                @Override
                public void error(Exception e)
                {
                    ErrorMessages.show(getShell(), e,
                            "Sorry, we encountered an error while deleting the selected files.");

                    // it's not necessary to clearBusyState to false here because we will follow up
                    // with a refresh
                    onCmdRefresh();
                }
            });
        }
    }
}
