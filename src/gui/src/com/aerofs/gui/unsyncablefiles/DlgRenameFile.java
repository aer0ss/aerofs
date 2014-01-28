/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.unsyncablefiles;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import javax.annotation.Nonnull;

import static com.aerofs.gui.GUIUtil.makeSubtitle;
import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * This is an input dialog which handles the task of renaming a file.
 *
 * The dialog validates user input as the user enters it. The filename must be non-blank and a valid
 * filename for the current device's OS. The dialog also trims user input before renaming files.
 *
 * The dialog maintains a busy state:
 *   - while busy, the error message will say that renaming is in progress, the Cancel button is
 *     enabled, and the text input and the OK button is disabled.
 *   - while not busy, all controls are enabled as long as the input is valid.
 *   - if the input is not valid, a reason will be displayed and the OK button will be disabled.
 *
 * The dialog enters the busy state when the user triggers a renaming operation.
 *
 * When the user clicks OK, the dialog will block until the renaming operation succeed, at which
 * point the dialog closes with a non-null result.
 *
 * If an error is encountered while renaming, the user is notified and the dialog resumes. The user
 * now has the choice to change input, keep trying, or cancelling.
 *
 * If the user cancels, then a null result is returned.
 *
 * If the user clicks Cancel while renaming is in progress, it's now a race between the task
 * finished event and the cancel event. In either case, the dialog exits, but the task may or may
 * not have completed.
 */
public class DlgRenameFile extends AeroFSDialog
{
    private final Path _path;
    private final String _message;
    private final String _initialValue;

    public DlgRenameFile(Shell parentShell, Path path)
    {
        super(parentShell, "Rename", true, false, true);

        _path = path;
        _message = "Rename " + UIUtil.getPrintablePath(path.last()) + " to:";
        _initialValue = UIUtil.getPrintablePath(path.last());
    }

    @Override
    protected void open(Shell shell)
    {
        new CompRenameFile(shell);

        FillLayout layout = new FillLayout();
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        shell.setLayout(layout);
    }

    private class CompRenameFile extends Composite
    {
        private final Label         _lblMessage;
        private final Text          _txtInput;
        private final Composite     _compMessage;
        private final Label         _lblErrorMessage;
        private final CompSpin      _spinner;
        private final Composite     _buttonBar;
        private final Button        _btnOK;
        private final Button        _btnCancel;

        public CompRenameFile(Composite parent)
        {
            super(parent, SWT.NONE);

            _lblMessage = new Label(this, SWT.WRAP);
            _lblMessage.setText(_message);

            _txtInput = new Text(this, SWT.BORDER);
            _txtInput.setText(_initialValue);
            _txtInput.selectAll();
            _txtInput.addModifyListener(new ModifyListener()
            {
                @Override
                public void modifyText(ModifyEvent modifyEvent)
                {
                    validateInput();
                }
            });

            _compMessage = new Composite(this, SWT.NONE);

            _lblErrorMessage = new Label(_compMessage, SWT.WRAP);
            _lblErrorMessage.setText("");
            _lblErrorMessage.setForeground(SWTResourceManager.getColor(SWT.COLOR_RED));
            _lblErrorMessage.setFont(makeSubtitle(_lblErrorMessage.getFont()));

            _spinner = new CompSpin(_compMessage, SWT.NONE);

            _buttonBar = GUIUtil.newPackedButtonContainer(this);

            _btnOK = GUIUtil.createButton(_buttonBar, SWT.PUSH);
            _btnOK.setText(IDialogConstants.OK_LABEL);
            getShell().setDefaultButton(_btnOK);
            _btnOK.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    onCmdOK();
                }
            });

            _btnCancel = GUIUtil.createButton(_buttonBar, SWT.PUSH);
            _btnCancel.setText(IDialogConstants.CANCEL_LABEL);
            _btnCancel.addSelectionListener(new SelectionAdapter()
            {
                @Override
                public void widgetSelected(SelectionEvent e)
                {
                    closeDialog();
                }
            });

            GridLayout layout = new GridLayout(2, false);
            layout.marginWidth = 0;
            layout.marginHeight = 0;
            layout.verticalSpacing = GUIParam.VERTICAL_SPACING;
            setLayout(layout);

            _lblMessage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
            _txtInput.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
            _lblErrorMessage.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));

            _compMessage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            RowLayout errorMessageLayout = new RowLayout(SWT.HORIZONTAL);
            errorMessageLayout.marginTop = 0;
            errorMessageLayout.marginBottom = 0;
            errorMessageLayout.marginLeft = 0;
            errorMessageLayout.marginRight = 0;
            errorMessageLayout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
            errorMessageLayout.center = true;
            _compMessage.setLayout(errorMessageLayout);
            // this should allocate sufficient amount of space for the label so that we never have
            // to redo the layout due to text change
            _lblErrorMessage.setLayoutData(new RowData(240, SWT.DEFAULT));

            _buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
            _btnOK.setLayoutData(new RowData(GUIParam.AEROFS_MIN_BUTTON_WIDTH, SWT.DEFAULT));
            _btnCancel.setLayoutData(new RowData(GUIParam.AEROFS_MIN_BUTTON_WIDTH, SWT.DEFAULT));

            // validate the initial value
            validateInput();
        }

        /**
         * @pre must be called from the UI thread
         */
        private void setBusyState(boolean isBusy)
        {
            checkArgument(GUI.get().isUIThread());

            if (isBusy) {
                _btnOK.setEnabled(false);
                _txtInput.setEnabled(false);

                _lblErrorMessage.setText("Renaming in progress...");
                _spinner.start();
            } else {
                _btnOK.setEnabled(true);
                _txtInput.setEnabled(true);

                _lblErrorMessage.setText("");
                _spinner.stop();
            }
        }

        /**
         * @pre must be called from the UI thread
         */
        private void validateInput()
        {
            checkArgument(GUI.get().isUIThread());

            String message = getErrorMessageForInput(_txtInput.getText().trim());

            _btnOK.setEnabled(isEmpty(message));
            _lblErrorMessage.setText(message);
        }

        /**
         * @return "" if the input is valid, or an non-empty error message if the input is invalid
         */
        private @Nonnull String getErrorMessageForInput(String input)
        {
            return isBlank(input) ? "Please enter a new filename for this file." :
                    OSUtil.get().isInvalidFileName(input) ?
                            "This is not a valid filename on this computer." :
                            "";
        }

        /**
         * @pre must be called from the UI thread
         */
        private void onCmdOK()
        {
            checkArgument(GUI.get().isUIThread());

            final String filename = _txtInput.getText().trim();

            if (filename.equals(_initialValue)) {
                closeDialog();
                return;
            }

            setBusyState(true);

            GUI.get().safeWork(getShell(), new ISWTWorker()
            {
                @Override
                public void run()
                        throws Exception
                {
                    UIGlobals.ritual().moveObject(_path.toPB(),
                            _path.removeLast().append(filename).toPB());
                }

                @Override
                public void okay()
                {
                    // in practice, we could've returned anything as long as it's not null.
                    closeDialog(filename);
                }

                @Override
                public void error(Exception e)
                {
                    ErrorMessages.show(getShell(), e,
                            "Sorry, we encountered an error while renaming the file.",
                            new ErrorMessage(ExAlreadyExist.class,
                                    "A file with the same name already exists"));
                    setBusyState(false);
                }
            });
        }
    }
}
