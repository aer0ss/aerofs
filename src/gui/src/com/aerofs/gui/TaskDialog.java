/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui;

import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * Simple sheet dialog controlling a long running operation
 *
 * Offers optional confirmation before performing the task and displays a spinner while the
 * operation is in progress
 */
public abstract class TaskDialog extends AeroFSDialog implements ISWTWorker
{
    protected CompSpin _compSpin;
    private final String _confirm;
    private final String _label;

    protected TaskDialog(Shell parent, String title, String confirm, String label)
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
        glShell.verticalSpacing = GUIParam.VERTICAL_SPACING;
        shell.setLayout(glShell);


        _compSpin = new CompSpin(shell, SWT.NONE);

        final Label label = new Label(shell, SWT.WRAP);
        final GridData gd = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
        label.setLayoutData(gd);
        if (_confirm != null && !_confirm.isEmpty()) {
            gd.widthHint = 300;
            label.setText(_confirm);

            final Composite c = GUIUtil.newButtonContainer(shell, false);
            c.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false, 2, 1));

            Button bYes = GUIUtil.createButton(c, SWT.NONE);
            bYes.setText("   Yes   ");
            bYes.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent selectionEvent)
                {
                    label.setText(_label);
                    gd.widthHint = SWT.DEFAULT;

                    c.setVisible(false);
                    ((GridData)c.getLayoutData()).exclude = true;
                    getShell().layout();
                    getShell().pack();

                    start();
                }
            });

            Button bNo = GUIUtil.createButton(c, SWT.NONE);
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
        GUI.get().safeWork(shell, TaskDialog.this);
    }

    @Override
    public void okay()
    {
        _compSpin.stop();
        closeDialog(true);
    }

    /**
     * By default, error() shows an error message and then closes the dialog. Override error() and
     * call errorWithNoErrorMessage() to avoid the message.
     */
    @Override
    public void error(Exception e)
    {
        GUI.get().show(getShell(), MessageType.ERROR, e.getLocalizedMessage());

        errorWithNoErrorMessage();
    }

    /**
     * See error().
     */
    protected void errorWithNoErrorMessage()
    {
        _compSpin.stop();
        closeDialog(false);
    }
}
