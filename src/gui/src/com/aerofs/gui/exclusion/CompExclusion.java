package com.aerofs.gui.exclusion;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkState;

public class CompExclusion extends Composite
{
    private Label               _lblHeader;
    private Composite           _compBody;
    private CompExclusionList   _lstExclusion;
    private Composite           _buttonBar;
    private CompSpin            _spinner;

    // the enabled state of this button is used for signalling and to prevent multiple concurrent
    // tasks.
    private Button              _btnOK;
    private Button              _btnCancel;

    private final String _strMessage = "Only checked folders will sync to this computer.";
    private final String _strWarning = "Unchecked folders outside your AeroFS folder will no longer " +
            "be synced to this device. They will not be deleted until done so explicitly.\n" +
            "Unchecked folders within your AeroFS folder will be deleted from this computer. " +
            "They will not be deleted from other computers. Do you still want to continue?";

    public CompExclusion(Composite parent)
    {
        super(parent, SWT.NONE);

        _lblHeader = new Label(this, SWT.NONE);
        _lblHeader.setText(_strMessage);

        _compBody = new Composite(this, SWT.NONE);

        _lstExclusion = new CompExclusionList(_compBody);

        _buttonBar = GUIUtil.newPackedButtonContainer(_compBody);

        _spinner = new CompSpin(_buttonBar, SWT.NONE);

        _btnOK = GUIUtil.createButton(_buttonBar, SWT.PUSH);
        _btnOK.setText(IDialogConstants.OK_LABEL);
        _btnOK.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                SelectivelySyncedFolders ssf = _lstExclusion.computeSelectivelySyncedFolders();

                // Find reasons to exit early so we don't set busy state nor spawn a new thread to
                // do no works. Reasons to exit early include no changes, or the user cancels
                // after we show a warning.

                // no changes
                if (ssf._newlyIncludedFolders.isEmpty() && ssf._newlyExcludedFolders.isEmpty()) {
                    getShell().close();
                    return;
                }

                // warn user before we exclude folders, and exits if the user cancels
                if (!ssf._newlyExcludedFolders.isEmpty() &&
                        !GUI.get().ask(getShell(), MessageType.WARN, _strWarning)) return;

                setBusyState(true);
                GUI.get().safeWork(_lstExclusion,
                        new SetExclusionTask(UIGlobals.ritualClientProvider(), ssf));
            }
        });

        _btnCancel = GUIUtil.createButton(_buttonBar, SWT.PUSH);
        _btnCancel.setText(IDialogConstants.CANCEL_LABEL);
        _btnCancel.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                getShell().close();
            }
        });

        // composite layout section
        GridLayout layout = new GridLayout();
        layout.marginWidth = GUIParam.MARGIN;
        layout.marginHeight = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.MAJOR_SPACING;
        setLayout(layout);

        _lblHeader.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
        _compBody.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        GridLayout bodyLayout = new GridLayout();
        bodyLayout.marginWidth = 0;
        bodyLayout.marginHeight = 0;
        bodyLayout.verticalSpacing = GUIParam.VERTICAL_SPACING;
        _compBody.setLayout(bodyLayout);

        GridData listLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
        listLayoutData.widthHint = 400;
        listLayoutData.heightHint = 200;
        _lstExclusion.setLayoutData(listLayoutData);

        _buttonBar.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, false));
        _btnOK.setLayoutData(new RowData(GUIParam.AEROFS_MIN_BUTTON_WIDTH, SWT.DEFAULT));
        _btnCancel.setLayoutData(new RowData(GUIParam.AEROFS_MIN_BUTTON_WIDTH, SWT.DEFAULT));
    }

    /**
     * @pre called from UI thread
     *
     * this is used as a signalling mechanism to prevent the user from running multiple tasks
     * simultaneiously.
     */
    private void setBusyState(boolean isBusy)
    {
        checkState(GUI.get().isUIThread());

        if (isBusy) {
            _lstExclusion.setEnabled(false);
            _btnOK.setEnabled(false);
            _btnCancel.setEnabled(false);
            _spinner.start();
        } else {
            _lstExclusion.setEnabled(true);
            _btnOK.setEnabled(true);
            _btnCancel.setEnabled(true);
            _spinner.stop();
        }
    }

    // N.B. chaining channel futures could work, but it's a lot simpler to make blocking calls
    // on a separate thread
    private class SetExclusionTask implements ISWTWorker
    {
        // N.B. it's important to have a client provider because the ritual channel may connect
        // or disconnect in between calls, and if the channel is not disconnected, we'll just
        // get the cached client anyway.
        private final IRitualClientProvider _ritual;
        private final SelectivelySyncedFolders _ssf;

        public SetExclusionTask(IRitualClientProvider ritual, SelectivelySyncedFolders ssf)
        {
            _ritual = ritual;
            _ssf = ssf;
        }

        @Override
        public void run()
                throws Exception
        {
           // N.B. per GUI.safeWork()'s contract, this method is called on a new task thread
            for (Path path : _ssf._newlyExcludedFolders.keySet()) {
                if (_ssf._newlyExcludedFolders.get(path)._isInternal) {
                    _ritual.getBlockingClient().excludeFolder(path.toPB());
                } else {
                    _ritual.getBlockingClient().unlinkRoot(path.sid());
               }
            }
            for (Entry<Path, FolderData> included : _ssf._newlyIncludedFolders.entrySet()) {
                if(included.getValue()._isInternal) {
                    _ritual.getBlockingClient().includeFolder(included.getKey().toPB());
                }
                else {
                    _ritual.getBlockingClient().linkRoot(included.getValue()._absPath,
                            included.getKey().toPB().getSid());
                }
            }
        }

        @Override
        public void okay()
        {
            // N.B. per GUI.safeWork()'s contract, this method is called on the UI thread
            setBusyState(false);
            getShell().close();
        }

        @Override
        public void error(Exception e)
        {
            // N.B. per GUI.safeWork()'s contract, this method is called on the UI thread
            ErrorMessages.show(getShell(), e,
                    "An error has occurred while updating the folders you chose to sync.",
                    new ErrorMessage(ExChildAlreadyShared.class, S.CHILD_ALREADY_SHARED),
                    new ErrorMessage(ExParentAlreadyShared.class, S.PARENT_ALREADY_SHARED));
            setBusyState(false);
        }
    }
}