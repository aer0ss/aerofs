package com.aerofs.gui.transfers;

import com.aerofs.base.C;
import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessages;
import com.aerofs.ui.IUI.MessageType;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import static com.aerofs.gui.GUIUtil.createLabel;
import static com.aerofs.lib.LibParam.Throttling.MIN_BANDWIDTH_UI;
import static com.aerofs.lib.LibParam.Throttling.UNLIMITED_BANDWIDTH;

public class DlgThrottling extends AeroFSDialog {

    private long _maxDownRate;
    private long _maxUpRate;
    private boolean _isDownRateUnlimited;
    private boolean _isUpRateUnlimited;

    private Text _txtDown;
    private Text _txtUp;
    private Button _btnDownKBS;
    private Button _btnDownMBS;
    private Button _btnUpKBS;
    private Button _btnUpMBS;
    private Button _btnUpUnlimited;
    private Button _btnUpLimit;
    private Button _btnDownLimit;
    private Button _btnDownUnlimited;
    private Button _btnCancel;
    private Composite composite_2;

    public DlgThrottling(Shell parent, boolean sheet)
    {
        super(parent, "Bandwidth Options", sheet, false);
    }

    @Override
    protected void open(Shell shell)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            shell = new Shell(getParent(), getStyle());

        GridLayout glShell = new GridLayout(1, false);
        glShell.marginHeight = GUIParam.MARGIN;
        glShell.marginWidth = GUIParam.MARGIN;
        glShell.verticalSpacing = GUIParam.MAJOR_SPACING;
        shell.setLayout(glShell);

        setupDownloadUi(shell);
        setupUploadUi(shell);

        composite_2 = new Composite(shell, SWT.NONE);
        composite_2.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        composite_2.setLayout(new FillLayout(SWT.HORIZONTAL));

        Button btnOK = GUIUtil.createButton(composite_2, SWT.NONE);
        btnOK.setText("  " + IDialogConstants.OK_LABEL + "  ");

        _btnCancel = GUIUtil.createButton(composite_2, SWT.NONE);
        _btnCancel.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });
        _btnCancel.setText(" " + IDialogConstants.CANCEL_LABEL + " ");
        btnOK.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                work();
            }
        });

        // Read the values from the config and refresh the UI
        CfgDatabase db = Cfg.db();
        setUnlimitedDownRate(db.getLong(Key.MAX_DOWN_RATE) == UNLIMITED_BANDWIDTH);
        setMaxDownRate(db.getLong(Key.MAX_DOWN_RATE_LIMITED), true);
        setUnlimitedUpRate(db.getLong(Key.MAX_UP_RATE) == UNLIMITED_BANDWIDTH);
        setMaxUpRate(db.getLong(Key.MAX_UP_RATE_LIMITED), true);
    }

    private void setupDownloadUi(Shell shell)
    {
        Composite composite = new Composite(shell, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        composite.setLayout(glComposite);

        Label lblDownloadRate = createLabel(composite, SWT.NONE);
        lblDownloadRate.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, true, 1, 1));
        lblDownloadRate.setText("Download rate:");

        _btnDownUnlimited = GUIUtil.createButton(composite, SWT.RADIO);
        _btnDownUnlimited.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setUnlimitedDownRate(_btnDownUnlimited.getSelection());
            }
        });
        _btnDownUnlimited.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, true, 2, 1));
        _btnDownUnlimited.setText("Unlimited");
        createLabel(composite, SWT.NONE);

        _btnDownLimit = GUIUtil.createButton(composite, SWT.RADIO);
        _btnDownLimit.setText("Limit to");
        _btnDownLimit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent) {
                setUnlimitedDownRate(!_btnDownLimit.getSelection());
            }
        });

        Composite composite_1 = new Composite(composite, SWT.NONE);
        GridLayout glComposite_1 = new GridLayout(3, false);
        glComposite_1.marginHeight = 0;
        glComposite_1.marginWidth = 0;
        composite_1.setLayout(glComposite_1);

        _txtDown = new Text(composite_1, SWT.BORDER | SWT.RIGHT);
        _txtDown.addVerifyListener(new VerifyListener() {
            @Override
            public void verifyText(VerifyEvent ev)
            {
                String txt = GUIUtil.getNewText(_txtDown, ev);
                if (txt.isEmpty()) return;
                try {
                    Long.valueOf(txt);
                } catch (NumberFormatException e) {
                    ev.doit = false;
                }
            }
        });
        _txtDown.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent focusEvent) {
                setMaxDownRate(computeMaxDownRate(), true);
            }
        });
        GridData gd_txtDown = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_txtDown.widthHint = 40;
        _txtDown.setLayoutData(gd_txtDown);

        _btnDownKBS = GUIUtil.createButton(composite_1, SWT.RADIO);
        _btnDownKBS.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!_btnDownKBS.getSelection()) return;
                setMaxDownRate(computeMaxDownRate(), false);
            }
        });
        _btnDownKBS.setSelection(true);
        _btnDownKBS.setText("KB/s");

        _btnDownMBS = GUIUtil.createButton(composite_1, SWT.RADIO);
        _btnDownMBS.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!_btnDownMBS.getSelection()) return;
                setMaxDownRate(computeMaxDownRate(), false);
            }
        });
        _btnDownMBS.setText("MB/s");
    }

    private void setupUploadUi(Shell shell)
    {
        Composite composite = new Composite(shell, SWT.NONE);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        GridLayout glComposite = new GridLayout(3, false);
        glComposite.marginWidth = 0;
        glComposite.marginHeight = 0;
        composite.setLayout(glComposite);

        Label lblUploadRate = createLabel(composite, SWT.NONE);
        lblUploadRate.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
        lblUploadRate.setText("Upload rate:");

        _btnUpUnlimited = GUIUtil.createButton(composite, SWT.RADIO);
        _btnUpUnlimited.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setUnlimitedUpRate(_btnUpUnlimited.getSelection());
            }
        });
        _btnUpUnlimited.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
        _btnUpUnlimited.setText("Unlimited");
        createLabel(composite, SWT.NONE);

        _btnUpLimit = GUIUtil.createButton(composite, SWT.RADIO);
        _btnUpLimit.setText("Limit to");
        _btnUpLimit.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent) {
                setUnlimitedUpRate(!_btnUpLimit.getSelection());
            }
        });

        Composite composite_1 = new Composite(composite, SWT.NONE);
        GridLayout glComposite_1 = new GridLayout(3, false);
        glComposite_1.marginHeight = 0;
        glComposite_1.marginWidth = 0;
        composite_1.setLayout(glComposite_1);

        _txtUp = new Text(composite_1, SWT.BORDER | SWT.RIGHT);
        _txtUp.addVerifyListener(new VerifyListener() {
            @Override
            public void verifyText(VerifyEvent ev)
            {
                String txt = GUIUtil.getNewText(_txtUp, ev);
                if (txt.isEmpty()) return;
                try {
                    Long.valueOf(txt);
                } catch (NumberFormatException e) {
                    ev.doit = false;
                }
            }
        });
        _txtUp.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent focusEvent) {
                setMaxUpRate(computeMaxUpRate(), true);
            }
        });
        GridData gd_txtUp = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_txtUp.widthHint = 40;
        _txtUp.setLayoutData(gd_txtUp);

        _btnUpKBS = GUIUtil.createButton(composite_1, SWT.RADIO);
        _btnUpKBS.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!_btnUpKBS.getSelection()) return;
                setMaxUpRate(computeMaxUpRate(), false);
            }
        });
        _btnUpKBS.setSelection(true);
        _btnUpKBS.setText("KB/s");

        _btnUpMBS = GUIUtil.createButton(composite_1, SWT.RADIO);
        _btnUpMBS.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                if (!_btnUpMBS.getSelection()) return;
                setMaxUpRate(computeMaxUpRate(), false);
            }
        });
        _btnUpMBS.setText("MB/s");
    }

    /**
     * Sets whether downloads are unlimitd or not and updates the UI accordingly
     * You should never set _isDownRateUnlimited directly.
     * @param unlimited: true if downloads are unlimited, false otherwise
     */
    private void setUnlimitedDownRate(boolean unlimited)
    {
        _isDownRateUnlimited = unlimited;

        // Update the UI to reflect the new state
        _btnDownUnlimited.setSelection(unlimited);
        _btnDownLimit.setSelection(!unlimited);
        _btnDownKBS.setEnabled(!unlimited);
        _btnDownMBS.setEnabled(!unlimited);
        _txtDown.setEnabled(!unlimited);

        selectDownText();
    }

    /**
     * @return the max download rate in bytes, or 0 if the text area is empty or invalid
     * This method has no side-effects
     */
    private long computeMaxDownRate()
    {
        long scale = _btnDownKBS.getSelection() ? C.KB : C.MB;
        try {
            return Long.valueOf(_txtDown.getText()) * scale;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Sets the max download rate and optionnally updates the UI.
     * @param rate: the max download rate in bytes
     * @param shouldUpdateUI: if true, the text area and the KB / MB buttons will be updated.
     * Note:
     * shouldUpdateUI should usually be true, except when the user has clicked on the MB or KB radio button.
     * In this case, updating the UI would mess with the user choice. (eg: the text is set to 1024 MB and the user clicks on
     * the KB button. Updating the UI would transform that to 1 MB, preventing the user to set a KB value.
     */
    private void setMaxDownRate(long rate, boolean shouldUpdateUI)
    {
        _maxDownRate = rate;
        if (_maxDownRate == 0) {
            setUnlimitedDownRate(true);
        } else if (_maxDownRate < MIN_BANDWIDTH_UI) {
            _maxDownRate = MIN_BANDWIDTH_UI;
            shouldUpdateUI = true;
        }

        if (shouldUpdateUI) {
            // Sets the text and the MB / KB buttons to the appriate values
            long displayValue = Math.round((float) _maxDownRate / C.KB);
            boolean isMB = false;
            if (_maxDownRate > C.MB) {
                displayValue = Math.round((float) _maxDownRate / C.MB);
                isMB = true;
            }
            _txtDown.setText(Long.toString(displayValue));
            _btnDownMBS.setSelection(isMB);
            _btnDownKBS.setSelection(!isMB);
        }

        selectDownText();
    }

    private void selectDownText()
    {
        if (!_isDownRateUnlimited) {
            _txtDown.setSelection(0, _txtDown.getText().length());
            _txtDown.setFocus();
        }
    }

    private void setUnlimitedUpRate(boolean unlimited)
    {
        _isUpRateUnlimited = unlimited;

        // Update the UI to reflect the new state
        _btnUpUnlimited.setSelection(unlimited);
        _btnUpLimit.setSelection(!unlimited);
        _btnUpKBS.setEnabled(!unlimited);
        _btnUpMBS.setEnabled(!unlimited);
        _txtUp.setEnabled(!unlimited);

        selectUpText();
    }

    private long computeMaxUpRate()
    {
        long scale = _btnUpKBS.getSelection() ? C.KB : C.MB;
        try {
            return Long.valueOf(_txtUp.getText()) * scale;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setMaxUpRate(long rate, boolean shouldUpdateUI)
    {
        _maxUpRate = rate;
        if (_maxUpRate == 0) {
            setUnlimitedUpRate(true);
        } else if (_maxUpRate < MIN_BANDWIDTH_UI) {
            _maxUpRate = MIN_BANDWIDTH_UI;
            shouldUpdateUI = true;
        }

        if (shouldUpdateUI) {
            // Sets the text and the MB / KB buttons to the appriate values
            long displayValue = Math.round((float) _maxUpRate / C.KB);
            boolean isMB = false;
            if (_maxUpRate > C.MB) {
                displayValue = Math.round((float) _maxUpRate / C.MB);
                isMB = true;
            }
            _txtUp.setText(Long.toString(displayValue));
            _btnUpMBS.setSelection(isMB);
            _btnUpKBS.setSelection(!isMB);
        }

        selectUpText();
    }

    private void selectUpText()
    {
        if (!_isUpRateUnlimited) {
            _txtUp.setSelection(0, _txtUp.getText().length());
            _txtUp.setFocus();
        }
    }

    private void work()
    {
        // Make sure we have the latest values
        setMaxDownRate(computeMaxDownRate(), true);
        setMaxUpRate(computeMaxUpRate(), true);

        // TODO: Update the backend to use a boolean to indicate if the rate is unlimited
        // Historically, instead of a boolean indicating if the rate is unlimited, we use 2 variables:
        // maxXXXRate and maxXXXRateLimited. The former is always equal to the later, unless the user
        // does not limit the bandwith. In this case maxXXXRate equals 0 (UNLIMITED_BANDWIDTH)
        long maxDownRate = _isDownRateUnlimited ? UNLIMITED_BANDWIDTH : _maxDownRate;
        long maxUpRate = _isUpRateUnlimited ? UNLIMITED_BANDWIDTH : _maxUpRate;

        try {
            CfgDatabase db = Cfg.db();
            db.set(Key.MAX_DOWN_RATE, maxDownRate);
            db.set(Key.MAX_UP_RATE, maxUpRate);
            db.set(Key.MAX_DOWN_RATE_LIMITED, _maxDownRate);
            db.set(Key.MAX_UP_RATE_LIMITED, _maxUpRate);

            UIGlobals.ritual().reloadConfig();

            closeDialog();
        } catch (Exception e) {
            GUI.get().show(getShell(), MessageType.ERROR, "Couldn't update" + " settings " +
                    ErrorMessages.e2msgDeprecated(e) + ".");
        }
    }
}
