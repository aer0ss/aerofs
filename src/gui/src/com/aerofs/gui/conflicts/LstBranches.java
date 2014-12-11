/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.conflicts;

import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIExecutor;
import com.aerofs.gui.conflicts.ConflictsModel.Branch;
import com.aerofs.gui.conflicts.ConflictsModel.Conflict;
import com.aerofs.gui.conflicts.DlgConflicts.IConflictEventListener;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.util.concurrent.FutureCallback;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.google.common.base.Preconditions.checkState;

class LstBranches extends ScrolledComposite
{
    private final Composite _content;
    private IConflictEventListener _listener;

    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 240;

    public LstBranches(Composite parent)
    {
        super(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);

        setExpandHorizontal(true);
        setExpandVertical(true);

        _content = new Composite(this, SWT.NONE);
        setContent(_content);

        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        _content.setLayout(layout);

        // determines the initial size when there's no content
        setMinSize(MIN_WIDTH, MIN_HEIGHT);
    }

    private void refreshLayout()
    {
        _content.layout();

        // N.B. we may over-allocate vertical space if the current width > MIN_WIDTH, and that's ok.
        int preferredHeight = _content.computeSize(MIN_WIDTH, SWT.DEFAULT).y;
        setMinSize(MIN_WIDTH, preferredHeight);
    }

    public void setConflictEventListener(IConflictEventListener listener)
    {
        checkState(GUI.get().isUIThread());
        _listener = listener;

        for (Control control : _content.getChildren()) {
            if (control instanceof CompBranch) {
                ((CompBranch)control).setConflictEventListener(_listener);
            }
        }
    }

    private void notifyStatusChanged(boolean isBusy)
    {
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onStatusChanged(isBusy);
    }

    private void notifyConflictResolved()
    {
        checkState(GUI.get().isUIThread());
        if (_listener != null) _listener.onConflictResolved();
    }

    public void clear()
    {
        checkState(GUI.get().isUIThread());
        for (Control control : _content.getChildren()) control.dispose();
    }

    public void loadFrom(@Nonnull Conflict conflict)
    {
        checkState(GUI.get().isUIThread());
        notifyStatusChanged(true);
        conflict.listBranches(new GUIExecutor(_content), new FutureCallback<Collection<Branch>>()
        {
            @Override
            public void onSuccess(Collection<Branch> branches)
            {
                clear();

                if (branches.size() > 1) {
                    try {
                        setInput(branches);
                    } catch (ExMasterBranchNotFound e) {
                        onFailure(e);
                        return;
                    }
                }

                notifyStatusChanged(false);

                if (branches.size() <= 1) notifyConflictResolved();
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                ErrorMessages.show(getShell(), throwable,
                        "Sorry, we encountered an error while looking up the versions for this " +
                                "file." +
                        new ErrorMessage(ExMasterBranchNotFound.class,
                                "Sorry, we cannot find the current version of this file."));

                notifyStatusChanged(false);
            }
        });
    }

    /**
     * @throws ExMasterBranchNotFound, if a master branch was not found in {@paramref branches}.
     *   In which case, the content will be cleared.
     */
    private void setInput(Collection<Branch> branches) throws ExMasterBranchNotFound
    {
        checkState(GUI.get().isUIThread());

        Branch master = findMasterBranchThrows(branches);

        // always put master branch first
        createChildFor(master);
        for (Branch branch : branches) if (!branch.isMaster()) createChildFor(branch);

        refreshLayout();
    }

    /**
     * @throws ExMasterBranchNotFound, if a master branch was not found in {@paramref branches}
     */
    private Branch findMasterBranchThrows(Collection<Branch> branches)
            throws ExMasterBranchNotFound
    {
        for (Branch branch : branches) if (branch.isMaster()) return branch;
        throw new ExMasterBranchNotFound();
    }

    private void createChildFor(Branch branch)
    {
        checkState(GUI.get().isUIThread());

        CompBranch compBranch = new CompBranch(_content, branch);
        compBranch.setConflictEventListener(_listener);
        compBranch.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label separator = createLabel(_content, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
    }

    public class ExMasterBranchNotFound extends Exception
    {
        private static final long serialVersionUID = 0L;
    }
}
