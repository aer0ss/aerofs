/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.conflicts;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.conflicts.ConflictsModel.Conflict;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.ui.UIGlobals;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.aerofs.gui.GUIUtil.createButton;
import static com.aerofs.gui.GUIUtil.newButtonContainer;
import static com.google.common.base.Preconditions.checkState;

/**
 * This dialog supports two display modes:
 *   one-pane: when the conflicted file is set, the dialog shows a list of branches for the given
 *             conflict.
 *   two-pane: when the conflicted file is not set, the dialog shows a list of all files with
 *             conflicts and a list of branches for the selected conflict from the list of
 *             conflicts.
 */
public class DlgConflicts extends AeroFSDialog
{
    private final ConflictsModel _model =
            new ConflictsModel(UIGlobals.ritualClientProvider(), new CfgLocalUser());

    private final @Nullable Path _path;

    public DlgConflicts(Shell parent)
    {
        this(parent, null);
    }

    public DlgConflicts(Shell parent, @Nullable Path path)
    {
        super(parent, "Resolve Conflicts", false, true);

        _path = path;
    }

    @Override
    protected void open(Shell shell)
    {
        final AbstractContentDelegate delegate = _path == null ?
                new TwoPaneDelegate() : new OnePaneDelegate();

        // it's important to separate construction with initialization because there's a control
        // here that depends on a control constructed later.
        Control     content     = delegate.createContent(shell);
        Link        lnkMessage  = new Link(shell, SWT.WRAP);
        CompSpin    spinner     = new CompSpin(shell, SWT.NONE);
        Composite   buttonBar   = newButtonContainer(shell, false);
        Button      btnRefresh  = createButton(buttonBar, SWT.PUSH);
        Button      btnClose    = createButton(buttonBar, SWT.PUSH);

        shell.setDefaultButton(btnRefresh);

        delegate.initializeContent(spinner);

        lnkMessage.setText("<a>Learn more about managing conflicts.</a>");
        lnkMessage.addSelectionListener(
                GUIUtil.createUrlLaunchListener("https://support.aerofs.com/hc/en-us/articles/201439064"));

        btnRefresh.setText("Refresh");
        btnRefresh.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                delegate.reload();
            }
        });

        btnClose.setText(IDialogConstants.CLOSE_LABEL);
        btnClose.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });

        GridLayout layout = new GridLayout(3, false); // 3 columns to deal with overlapping columns
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(layout);

        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
        lnkMessage.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
        spinner.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

        // N.B. this is necessary otherwise the cache data from GridLayout screws up the layout
        shell.pack();

        delegate.reload();
    }

    // encapsulates the differences between the two modes
    private abstract class AbstractContentDelegate
    {
        protected LstBranches _lstBranches;

        public abstract @Nonnull Control createContent(Composite parent);
        public abstract void initializeContent(CompSpin spinner);
        public abstract void reload();
    }

    private class OnePaneDelegate extends AbstractContentDelegate
    {
        @Override
        public @Nonnull Control createContent(Composite parent)
        {
            checkState(_lstBranches == null);
            _lstBranches = new LstBranches(parent);
            return _lstBranches;
        }

        @Override
        public void initializeContent(final CompSpin spinner)
        {
            checkState(_lstBranches != null);

            _lstBranches.setConflictEventListener(new IConflictEventListener()
            {
                @Override
                public void onStatusChanged(boolean isBusy)
                {
                    _lstBranches.getContent().setEnabled(!isBusy);

                    if (isBusy) spinner.start();
                    else spinner.stop();
                }

                @Override
                public void onVersionDataStale()
                {
                    reload();
                }

                @Override
                public void onConflictResolved()
                {
                    closeDialog();
                }
            });
        }

        @Override
        public void reload()
        {
            checkState(_lstBranches != null);
            _lstBranches.loadFrom(_model.createConflictFromPath(_path));
        }
    }

    private class TwoPaneDelegate extends AbstractContentDelegate
    {
        private SashForm        _form;
        private LstConflicts    _lstConflicts;

        @Override
        public @Nonnull Control createContent(Composite parent)
        {
            checkState(_form == null && _lstConflicts == null && _lstBranches == null);
            _form = new SashForm(parent, SWT.NONE);
            _lstConflicts = new LstConflicts(_form, _model);
            _lstBranches = new LstBranches(_form);
            return _form;
        }

        @Override
        public void initializeContent(final CompSpin spinner)
        {
            checkState(_form != null && _lstConflicts != null && _lstBranches != null);

            _form.setWeights(new int[]{1, 3});

            _form.setSashWidth(7);

            IConflictEventListener listener = new IConflictEventListener()
            {
                @Override
                public void onStatusChanged(boolean isBusy)
                {
                    _lstConflicts.setEnabled(!isBusy);
                    if (_lstConflicts.isEnabled()) _lstConflicts.setFocus();

                    _lstBranches.getContent().setEnabled(!isBusy);

                    if (isBusy) spinner.start();
                    else spinner.stop();
                }

                @Override
                public void onVersionDataStale()
                {
                    reloadBranches();
                }

                @Override
                public void onConflictResolved()
                {
                    reloadBranches();
                }
            };

            _lstConflicts.setConflictEventListener(listener);
            _lstConflicts.addPostSelectionChangedListener(new ISelectionChangedListener()
            {
                @Override
                public void selectionChanged(SelectionChangedEvent selectionChangedEvent)
                {
                    reloadBranches();
                }
            });

            _lstBranches.setConflictEventListener(listener);
        }

        private void reloadBranches()
        {
            Conflict conflict = _lstConflicts.getSelectedConflictNullable();

            if (conflict == null) {
                _lstBranches.clear();
            } else {
                _lstBranches.loadFrom(conflict);
            }
        }

        @Override
        public void reload()
        {
            checkState(_lstConflicts != null);
            _lstConflicts.reload();
        }
    }

    interface IConflictEventListener
    {
        // used to propagate busy state and block GUI while busy
        void onStatusChanged(boolean isBusy);
        void onConflictResolved();
        void onVersionDataStale();
    }
}
