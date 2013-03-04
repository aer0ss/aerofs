/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser;

import com.aerofs.gui.GUIParam;
import com.aerofs.gui.setup.AbstractDlgSetupAdvanced;
import com.aerofs.gui.setup.CompLocalStorage;
import com.aerofs.lib.S;
import com.aerofs.ui.PasswordVerifier;
import com.aerofs.ui.PasswordVerifier.PasswordVerifierResult;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import javax.annotation.Nullable;
import java.util.List;

public class MultiuserDlgSetupAdvanced extends AbstractDlgSetupAdvanced
{
    private Text _txtS3BucketId;
    private Text _txtS3AccessKey;
    private Text _txtS3SecretKey;
    private Text _txtS3Passphrase;
    private Text _txtS3Passphrase2;

    private S3Config _s3Config;

    protected class S3Config {
        public String s3BucketId;
        public String s3AccessKey;
        public String s3SecretKey;
        public String s3Passphrase;
    }

    final private int WIDTH_HINT = 400;

    final static public int LOCAL_STORAGE_OPTION = 0;
    final static public int S3_STORAGE_OPTION = 1;

    final private String LOCAL_STORAGE_OPTION_TEXT = "Local Storage";
    final private String S3_STORAGE_OPTION_TEXT = "S3 Storage";
    final private String LOCAL_STORAGE_EXPLANATION =
            "This option stores files on the local computer.";
    final private String S3_STORAGE_EXPLANATION =
            "This option stores files in Amazon S3.";
    final private String S3_PASSWORD_EXPLANATION = S.SETUP_S3_ENCRYPTION_PASSWORD;


    private int _storageChoice = LOCAL_STORAGE_OPTION;

    private Composite _container;
    private CompLocalStorage _compLocalStorage;
    private final List<Control> _s3Controls = Lists.newArrayList();

    private Label _lblS3Error;

    protected MultiuserDlgSetupAdvanced(Shell parentShell, String deviceName, String absRootAnchor,
            S3Config s3Config, int storageChoice)
    {
        super(parentShell, deviceName, absRootAnchor);
        _s3Config = s3Config;
        _storageChoice = storageChoice;
    }

    protected void createStorageArea(Composite container)
    {
        _container = container;

        Label lblStorageSelector = new Label(_container, SWT.NONE);
        lblStorageSelector.setText("Storage Option:");

        final Combo storageSelector = new Combo(_container, SWT.DROP_DOWN | SWT.READ_ONLY);
        storageSelector.add(LOCAL_STORAGE_OPTION_TEXT, LOCAL_STORAGE_OPTION);
        storageSelector.add(S3_STORAGE_OPTION_TEXT, S3_STORAGE_OPTION);

        _compLocalStorage = new CompLocalStorage(_container, getAbsoluteRootAnchor(),
                LOCAL_STORAGE_EXPLANATION);

        createS3StorageArea(_container);

        updateStorageArea();

        storageSelector.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent selectionEvent)
            {
                _storageChoice = storageSelector.getSelectionIndex();
                updateStorageArea();
                updateOkButtonState();
            }
        });

        getShell().addListener(SWT.Show, new Listener() {

            @Override
            public void handleEvent(Event event)
            {
                if (_s3Config != null) {
                    _txtS3BucketId.setText(_s3Config.s3BucketId);
                    _txtS3AccessKey.setText(_s3Config.s3AccessKey);
                    _txtS3SecretKey.setText(_s3Config.s3SecretKey);
                    _txtS3Passphrase.setText(_s3Config.s3Passphrase);
                    _txtS3Passphrase2.setText(_s3Config.s3Passphrase);
                }
                _compLocalStorage.setAbsRootAnchor(getAbsoluteRootAnchor());
                storageSelector.select(_storageChoice);
                updateStorageArea();
                updateOkButtonState();
            }
        });
    }

    private void updateOkButtonState()
    {
        switch (_storageChoice) {
            case LOCAL_STORAGE_OPTION:
                getButton(IDialogConstants.OK_ID).setEnabled(true);
                return;
            case S3_STORAGE_OPTION:
                PasswordVerifierResult result = verifyPasswords();
                getButton(IDialogConstants.OK_ID).setEnabled(result == PasswordVerifierResult.OK);
                _lblS3Error.setText(result.getMsg());
                _container.layout();
                return;
            default:
                Preconditions.checkState(_storageChoice == LOCAL_STORAGE_OPTION ||
                        _storageChoice == S3_STORAGE_OPTION, "Unimplemented Storage Option");
                break;
        }
    }

    private void updateStorageArea()
    {
        if (_storageChoice == LOCAL_STORAGE_OPTION) {
            setS3Visible(false);
            _compLocalStorage.setVisible(true);
        } else {
            _compLocalStorage.setVisible(false);
            setS3Visible(true);
        }
        _container.getShell().layout();
        _container.getShell().pack();
    }

    public void setS3Visible(boolean visible)
    {
        for (Control c : _s3Controls) {
            c.setVisible(visible);
            ((GridData)c.getLayoutData()).exclude = !visible;
        }
    }

    private void createS3StorageArea(Composite container)
    {
        addPlaceholder(container, _s3Controls);

        Label lblExplanation = new Label(container, SWT.WRAP);
        GridData gd = new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1);
        lblExplanation.setLayoutData(gd);
        lblExplanation.setText(S3_STORAGE_EXPLANATION);
        _s3Controls.add(lblExplanation);

        addPlaceholder(container, _s3Controls);

        Label lblS3BucketId = new Label(container, SWT.NONE);
        lblS3BucketId.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lblS3BucketId.setText(S.SETUP_S3_BUCKET_NAME + ":");
        _s3Controls.add(lblS3BucketId);

        _txtS3BucketId = new Text(container, SWT.BORDER);
        _txtS3BucketId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _s3Controls.add(_txtS3BucketId);

        Label lblS3AccessKey = new Label(container, SWT.NONE);
        lblS3AccessKey.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lblS3AccessKey.setText(S.SETUP_S3_ACCESS_KEY + ":");
        _s3Controls.add(lblS3AccessKey);

        _txtS3AccessKey = new Text(container, SWT.BORDER);
        _txtS3AccessKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _s3Controls.add(_txtS3AccessKey);

        Label lblS3SecretKey = new Label(container, SWT.NONE);
        lblS3SecretKey.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lblS3SecretKey.setText(S.SETUP_S3_SECRET_KEY + ":");
        _s3Controls.add(lblS3SecretKey);

        _txtS3SecretKey = new Text(container, SWT.BORDER);
        _txtS3SecretKey.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _s3Controls.add(_txtS3SecretKey);

        addPlaceholder(container, _s3Controls);

        Label lblPassphrase = new Label(container, SWT.WRAP);
        GridData gdPassphrase = new GridData(SWT.LEFT, SWT.TOP, false, false, 2, 1);
        lblPassphrase.setLayoutData(gdPassphrase);
        lblPassphrase.setText(S3_PASSWORD_EXPLANATION);
        _s3Controls.add(lblPassphrase);

        addPlaceholder(container, _s3Controls);

        Label lblS3Passphrase = new Label(container, SWT.NONE);
        lblS3Passphrase.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lblS3Passphrase.setText("Passphrase:");
        _s3Controls.add(lblS3Passphrase);

        _txtS3Passphrase = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtS3Passphrase.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _s3Controls.add(_txtS3Passphrase);

        Label lblS3Passphrase2 = new Label(container, SWT.NONE);
        lblS3Passphrase2.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false, 1, 1));
        lblS3Passphrase2.setText("Confirm Passphrase:");
        _s3Controls.add(lblS3Passphrase2);

        _txtS3Passphrase2 = new Text(container, SWT.BORDER | SWT.PASSWORD);
        _txtS3Passphrase2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        _s3Controls.add(_txtS3Passphrase2);

        _lblS3Error= new Label(container, SWT.WRAP);
        GridData gdError = new GridData(SWT.RIGHT, SWT.TOP, false, false, 2, 1);
        gdPassphrase.widthHint = 300;
        _lblS3Error.setLayoutData(gdError);
        _s3Controls.add(_lblS3Error);

        _txtS3Passphrase.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                updateOkButtonState();
            }
        });

        _txtS3Passphrase2.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                updateOkButtonState();
            }
        });
    }

    private void addPlaceholder(Composite container, List<Control> controls)
    {
        Label l = new Label(container, SWT.NONE);
        l.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        controls.add(l);
    }

    private PasswordVerifierResult verifyPasswords()
    {
        PasswordVerifier verifier = new PasswordVerifier();
        PasswordVerifierResult result = verifier.verifyAndConfirmPasswords(
                _txtS3Passphrase.getText().toCharArray(),
                _txtS3Passphrase2.getText().toCharArray());
        return result;
    }

    @Override
    protected String getAbsRootAnchorFromTxtField()
    {
        return _compLocalStorage.getAbsRootAnchor();
    }

    protected @Nullable S3Config getS3Config()
    {
        return _s3Config;
    }

    protected int getStorageChoice()
    {
        return _storageChoice;
    }

    @Override
    protected void buttonPressed(int buttonId)
    {
        _s3Config = new S3Config();
        _s3Config.s3BucketId = _txtS3BucketId.getText();
        _s3Config.s3AccessKey = _txtS3AccessKey.getText();
        _s3Config.s3SecretKey = _txtS3SecretKey.getText();
        _s3Config.s3Passphrase = _txtS3Passphrase.getText();
        super.buttonPressed(buttonId);
    }
}
