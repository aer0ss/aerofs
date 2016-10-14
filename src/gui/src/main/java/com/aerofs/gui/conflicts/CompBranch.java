/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.conflicts;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.conflicts.ConflictsModel.Branch;
import com.aerofs.gui.conflicts.DlgConflicts.IConflictEventListener;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.gui.GUIUtil.makeBold;
import static com.aerofs.gui.GUIUtil.makeSubtitle;
import static com.google.common.base.Preconditions.checkState;

class CompBranch extends Composite
{
    private final static Logger l = Loggers.getLogger(CompBranch.class);

    private final @Nonnull Branch _branch;

    private IConflictEventListener _listener;

    public CompBranch(Composite parent, @Nonnull Branch branch)
    {
        super(parent, SWT.NONE);

        _branch = branch;

        boolean dummy = _branch.isDummy();

        Label lblVersion = createLabel(this, SWT.NONE);
        lblVersion.setText(_branch.formatVersion());
        lblVersion.setFont(makeBold(lblVersion.getFont()));

        Label lblMtime = createLabel(this, SWT.NONE);
        if (!dummy) lblMtime.setText(_branch.formatLastModified());
        lblMtime.setFont(makeSubtitle(lblMtime.getFont()));

        Label lblFilesize = createLabel(this, SWT.NONE);
        if (!dummy) lblFilesize.setText(_branch.formatFileSize());
        lblFilesize.setFont(makeSubtitle(lblFilesize.getFont()));

        Composite buttonsBar = GUIUtil.newButtonContainer(this, false);

        Button btnDelete = GUIUtil.createButton(buttonsBar, SWT.PUSH);
        btnDelete.setText("Delete");
        btnDelete.setToolTipText("Delete this conflicting version.");
        btnDelete.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                onDeleteBranch();
            }
        });

        // the button is created but hidden, this ensures that layout works as desired and the
        // button is not visible nor accessible.
        if (branch.isMaster()) btnDelete.setVisible(false);

        if (!dummy) {
            Button btnSaveAs = GUIUtil.createButton(buttonsBar, SWT.PUSH);
            btnSaveAs.setText("Save As...");
            btnSaveAs.setToolTipText("Save a copy of this version in a different location.");
            btnSaveAs.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    onSaveAs();
                }
            });

            Button btnOpen = GUIUtil.createButton(buttonsBar, SWT.PUSH);
            btnOpen.setText(_branch.isMaster() ? "Open" : "View");
            btnOpen.setToolTipText(_branch.isMaster()
                    ? "Open and edit the current version of this file."
                    : "Open and view a read-only copy of the conflicting version.");
            btnOpen.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    onOpen();
                }
            });
        }

        /**
         * Layout := 3x3 grid as follows to allow path and buttons to overlap
         *      [     path        ] [ mtime ]
         *      [       contributors        ]
         *      [ filesize ] [   buttons    ]
         */
        setLayout(new GridLayout(3, false));

        lblVersion.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false, 2, 1));
        lblMtime.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false));
        lblFilesize.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        buttonsBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
    }

    public void setConflictEventListener(IConflictEventListener listener)
    {
        checkState(GUI.get().isUIThread());
        _listener = listener;
    }

    private void notifyStatusChanged(boolean isBusy)
    {
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onStatusChanged(isBusy);
    }

    private void notifyVersionDataStale()
    {
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onVersionDataStale();
    }

    private void onOpen()
    {
        checkState(GUI.get().isUIThread());

        // don't notify busy state because we don't need to
        _branch.export(new GUIExecutor(this), new FutureCallback<String>()
        {
            @Override
            public void onSuccess(String filePath)
            {
                if (!GUIUtil.launch(filePath)) onFailure(new Exception("Unable to open the file."));
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                ErrorMessages.show(getShell(), throwable,
                        "Sorry, we encountered an error while opening the selected version.");
            }
        });
    }

    private void onSaveAs()
    {
        checkState(GUI.get().isUIThread());

        final String dest = promptSaveAsPathNullable();
        if (dest != null) {
            notifyStatusChanged(true);
            _branch.export(new GUIExecutor(this), new FutureCallback<String>() {
                @Override
                public void onSuccess(String filePath)
                {
                    try {
                        l.info("save conflict {} {} -> {}",
                                _branch._conflict._path, _branch._kidx, dest);
                        FileUtil.copy(new File(filePath), new File(dest), false, true);
                    } catch (IOException e) {
                        onFailure(e);
                    }

                    notifyStatusChanged(false);
                }

                @Override
                public void onFailure(Throwable throwable)
                {
                    ErrorMessages.show(getShell(), throwable,
                            "Sorry, we encountered an error while saving the file to disk.");

                    notifyStatusChanged(false);
                }
            });
        }
    }

    // null if the user cancelled the prompt
    private String promptSaveAsPathNullable()
    {
        checkState(GUI.get().isUIThread());

        Path path = _branch._conflict._path;

        FileDialog dialog = new FileDialog(getShell(), SWT.SHEET | SWT.SAVE);
        dialog.setFilterNames(new String[]{"All files"});
        dialog.setFilterExtensions(new String[]{"*.*"});

        String absParentPath = UIUtil.absPathNullable(path.removeLast());
        if (absParentPath != null) dialog.setFilterPath(absParentPath);

        dialog.setFileName(path.last());
        // The OS will show a warning if the user chooses an existing name
        dialog.setOverwrite(true);

        return dialog.open();
    }

    private void onDeleteBranch()
    {
        checkState(GUI.get().isUIThread());

        _branch.delete(new GUIExecutor(this), new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid)
            {
                notifyStatusChanged(false);
                notifyVersionDataStale();
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                ErrorMessages.show(getShell(), throwable,
                        "Sorry, we encountered an error while deleting this version.");
                notifyStatusChanged(false);
                notifyVersionDataStale();
            }
        });
    }
}
