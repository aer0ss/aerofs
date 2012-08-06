package com.aerofs.gui;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.layout.GridData;

import org.eclipse.swt.widgets.Button;

public class AeroFSMessageBox extends AeroFSJFaceDialog {

    public static enum IconType {
        INFO,
        WARN,
        ERROR,
        QUESTION
    }

    public static enum ButtonType {
        OKAY,
        OKAY_CANCEL
    }

    public static final int OK_ID = IDialogConstants.OK_ID;
    public static final int CANCEL_ID = IDialogConstants.CANCEL_ID;

    private Label _lbl;
    private CLabel _icon;
    private final String _msg;
    private final IconType _it;
    private final ButtonType _bt;
    private final String _checkBoxText;
    private boolean _checked;
    private final String _okayLabel, _cancelLabel;

    /**
     * @wbp.parser.constructor
     */
    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it)
    {
        this(parentShell, sheet, msg, it, ButtonType.OKAY);
    }

    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it,
            ButtonType bt)
    {
        this(parentShell, sheet, msg, it, bt, null);
    }

    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it,
            ButtonType bt, String checkBoxText)
    {
        this(parentShell, sheet, msg, it, bt, IDialogConstants.OK_LABEL,
                IDialogConstants.CANCEL_LABEL, checkBoxText);
    }

    /**
     * @param checkBoxText null to not show the check box
     */
    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it,
            ButtonType bt, String okayLabel, String cancelLabel, String checkBoxText)
    {
        super(null, parentShell, sheet, false, true, true);
        _checkBoxText = checkBoxText;
        _it = it;
        _bt = bt;
        _okayLabel = okayLabel;
        _cancelLabel = cancelLabel;
        _msg = msg;
    }

    /**
     * Create contents of the dialog.
     * @param parent
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayout gl_container = new GridLayout(2, false);
        gl_container.verticalSpacing = GUIParam.MAJOR_SPACING;
        gl_container.horizontalSpacing = GUIParam.MAJOR_SPACING;
        gl_container.marginWidth = GUIParam.MARGIN;
        gl_container.marginHeight = GUIParam.MARGIN;
        container.setLayout(gl_container);

        int icon;
        switch (_it) {
        case WARN:
            icon = SWT.ICON_WARNING;
            break;
        case ERROR:
            icon = SWT.ICON_ERROR;
            break;
        case QUESTION:
            icon = SWT.ICON_QUESTION;
            break;
        case INFO:
        default:
            icon = SWT.ICON_INFORMATION;
        }

        _icon = new CLabel(container, SWT.NONE);
        _icon.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, true, 1, 1));
        _icon.setImage(getShell().getDisplay().getSystemImage(icon));

        _lbl = new Label(container, SWT.WRAP);
        GridData gd__text = new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1);
        gd__text.widthHint = 360;
        _lbl.setLayoutData(gd__text);
        _lbl.setText(_msg);

        if (_checkBoxText != null) {
            new Label(container, SWT.NONE);
            final Button button = new Button(container, SWT.CHECK);
            button.setText(_checkBoxText);
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent arg0)
                {
                    _checked = button.getSelection();
                }
            });
        }

        return container;
    }

    /**
     * Create contents of the button bar.
     * @param parent
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        switch (_bt) {
        case OKAY_CANCEL:
            createButton(parent, OK_ID, _okayLabel, true);
            createButton(parent, CANCEL_ID, _cancelLabel, false);
            break;
        default:
            createButton(parent, OK_ID, IDialogConstants.OK_LABEL, true);
            break;
        }
    }

    public boolean isChecked()
    {
        assert _checkBoxText != null;
        return _checked;
    }
}
