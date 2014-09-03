package com.aerofs.gui;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;

import org.eclipse.swt.widgets.Button;

import javax.annotation.Nullable;

import static com.aerofs.gui.GUIUtil.createUrlLaunchListener;

public class AeroFSMessageBox extends AeroFSJFaceDialog {

    public static enum IconType {
        INFO,
        WARN,
        ERROR,
        QUESTION
    }

    public static enum ButtonType {
        // Only show an OK button
        OKAY,

        // Show an OK button and a Cancel button
        OKAY_CANCEL,

        // Show an OK button and a Cancel button, with the default button being the Cancel button.
        // On OSX, the default button is placed on the left-most position in the dialog. Because the
        // user pressing ESC triggers the Cancel button, use this ButtonType if the ESC action
        // should be attached to the button on the left corner on OSX.
        OKAY_CANCEL_DEFAULT_ON_CANCEL,
    }

    public static final int OK_ID = IDialogConstants.OK_ID;
    public static final int CANCEL_ID = IDialogConstants.CANCEL_ID;

    private Link _lnkMessage;
    private final String _msg;
    private final IconType _it;
    private final ButtonType _bt;
    private Button _okayBtn, _cancelBtn;
    private final String _okayLabel, _cancelLabel;
    private final boolean _allowClose;

    /**
     * @param msg, see {@link #setMessage(String)}
     */
    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it)
    {
        this(parentShell, sheet, msg, it, ButtonType.OKAY);
    }

    /**
     * @param msg, see {@link #setMessage(String)}
     */
    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it,
            ButtonType bt)
    {
        this(parentShell, sheet, msg, it, bt, IDialogConstants.OK_LABEL,
                IDialogConstants.CANCEL_LABEL, true);
    }

    /**
     * @param msg, see {@link #setMessage(String)}
     */
    public AeroFSMessageBox(Shell parentShell, boolean sheet, String msg, IconType it,
            ButtonType bt, String okayLabel, String cancelLabel, boolean allowClose)
    {
        super(null, parentShell, sheet, false, true, allowClose);
        _it = it;
        _bt = bt;
        _okayLabel = okayLabel;
        _cancelLabel = cancelLabel;
        _msg = msg;
        _allowClose = allowClose;
    }

    /**
     * Create contents of the dialog.
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
            //noinspection fallthrough
        default:
            icon = SWT.ICON_INFORMATION;
        }

        CLabel lblIcon = new CLabel(container, SWT.NONE);
        lblIcon.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, true, 1, 1));
        lblIcon.setImage(getShell().getDisplay().getSystemImage(icon));

        _lnkMessage = new Link(container, SWT.WRAP);
        GridData gd__text = new GridData(SWT.FILL, SWT.CENTER, true, true, 1, 1);
        gd__text.widthHint = 360;
        _lnkMessage.setLayoutData(gd__text);
        _lnkMessage.setText(_msg);
        checkForLinks(_lnkMessage);

        if (!_allowClose) {
            getShell().addListener(SWT.Traverse, new Listener() {
                @Override
                public void handleEvent(Event e)
                {
                    if (e.detail == SWT.TRAVERSE_ESCAPE) {
                        e.doit = false;
                    }
                }
            });
        }

        return container;
    }

    /**
     * Create contents of the button bar.
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        switch (_bt) {
        case OKAY_CANCEL:
            _okayBtn = createButton(parent, OK_ID, _okayLabel, true);
            _cancelBtn = createButton(parent, CANCEL_ID, _cancelLabel, false);
            break;
        case OKAY_CANCEL_DEFAULT_ON_CANCEL:
            _okayBtn = createButton(parent, OK_ID, _okayLabel, false);
            _cancelBtn = createButton(parent, CANCEL_ID, _cancelLabel, true);
            break;
        default:
            _okayBtn = createButton(parent, OK_ID, IDialogConstants.OK_LABEL, true);
            _cancelBtn = null;
            break;
        }
    }

    /**
     * AeroFSMessageBox detects a pattern in the message and replaces it with a hyperlink.
     *
     * The exact pattern is:
     *      <a href=\"url\">label</a>
     *
     * The pattern _is_ white space sensitive and strict. It will allow the callers to embed one
     * hyperlink in the message and _all_ clicks on _any_ hyperlink direct end user's browser to the
     * url indicated in the first hyperlink.
     *
     * FIXME (AT): this should be improved so that markup and message boxes are bound in a more
     * cohesive way.
     */
    protected void setMessage(String message)
    {
        _lnkMessage.setText(message);
    }

    public Button getOkayBtn()
    {
        return _okayBtn;
    }

    /**
     * Null if ButtonType is not OKAY_CANCEL.
     */
    public @Nullable Button getCancelBtn()
    {
        return _cancelBtn;
    }

    /**
     * Checks for the presence of hyperlinks and add selection listener to open the first
     * hyperlink if any hyperlink is clicked.
     */
    private void checkForLinks(Link link)
    {
        String prefix = "<a href=\"";
        String text = link.getText();

        int index = text.indexOf(prefix);
        if (index >= 0) {
            index += prefix.length();
            int endIndex = text.indexOf("\"", index + 1);
            String url = text.substring(index, endIndex);
            link.addSelectionListener(createUrlLaunchListener(url));
        }
    }
}
