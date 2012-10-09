/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.setup;

import com.aerofs.gui.AeroFSDialog;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUIParam;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.gui.tray.BigArrow;
import com.aerofs.gui.tray.TrayIcon.TrayPosition;
import com.aerofs.lib.S;
import com.aerofs.lib.os.OSUtil;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import javax.annotation.Nullable;

public class DlgTutorial extends AeroFSDialog
{
    private int _currentStep;
    private Button _btnPrev;
    private Button _btnNext;
    private Label _image;
    private Label _title;
    private Label _description;
    private Label _lblStep;
    private Button _btnSkip;
    private Shell _shell;

    private BigArrow _arrow;
    private final Runnable _showArrow = new Runnable()
    {
        @Override
        public void run()
        {
            TrayPosition pos = GUI.get().st().getIcon().getPosition();
            _arrow = new BigArrow(pos);
            _arrow.open();
            // The arrow will take the focus from our dialog, so we take it back
            _shell.setActive();
        }
    };

    private final Runnable _closeArrow = new Runnable()
    {
        @Override
        public void run()
        {
            _arrow.close();
        }
    };

    private static class StepData
    {
        public String title;
        public String description;
        public String image;
        public Runnable preStep;
        public Runnable postStep;

        public StepData(String title, String description, String image,
                @Nullable Runnable preStep, @Nullable Runnable postStep)
        {
            this.title = title;
            this.description = description;
            this.image = image;
            this.preStep = preStep;
            this.postStep = postStep;
        }
    }

    private StepData[] _steps = {
            // Step 1
            new StepData("Welcome to AeroFS!",
                    "AeroFS creates a special folder on your computer. All the files you" +
                            " put there will be synced with all your other computers.",
                    "tutorial1.png", null, null),

            // Step 2
            new StepData("The AeroFS Icon",
                    "The blue arrow on your screen shows the locations of the AeroFS icon." +
                            " Click the icon to open your AeroFS folder and manage your preferences.",
                    "tutorial2.png", _showArrow, _closeArrow),

            // Step 3
            new StepData("Share folders with people you know",
                    "You can share any folder in AeroFS with friends or colleagues, " +
                            "even if they're not using AeroFS yet.",
                    "tutorial3.png", null, null)
    };

    public DlgTutorial(Shell parentShell)
    {
        super(parentShell, S.PRODUCT, false, false);
    }

    private void setStep(int step)
    {
        // Run the post step runnable on the previous step
        if (_steps[_currentStep].postStep != null) _steps[_currentStep].postStep.run();

        if (step == _steps.length) {
            closeDialog();
            return;
        }

        // Set up everything
        _btnPrev.setEnabled(step != 0);
        _btnNext.setText((step < _steps.length - 1) ? "Continue" : "Finish");
        _btnSkip.setVisible(step != (_steps.length - 1));
        _title.setText(_steps[step].title);
        _description.setText(_steps[step].description);
        String fileName = _steps[step].image;
        if (OSUtil.isWindowsXP()) fileName = fileName.replaceFirst(".png", "xp.png");
        _image.setImage(Images.get(fileName));
        _lblStep.setText("Part " + (step + 1) + " of " + _steps.length);

        // Run the pre step runnable
        if (_steps[step].preStep != null) _steps[step].preStep.run();

        _currentStep = step;
    }

    @Override
    protected void open(Shell sh)
    {
        if (GUIUtil.isWindowBuilderPro()) // $hide$
            sh = new Shell(getParent(), getStyle());

        _shell = sh;

        GridData gd;
        GridLayout layout = new GridLayout(2, false);
        layout.marginHeight = GUIParam.MARGIN;
        layout.marginWidth = GUIParam.MARGIN;
        layout.verticalSpacing = GUIParam.MAJOR_SPACING;
        _shell.setLayout(layout);

        _title = new Label(_shell, SWT.NONE);
        gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.horizontalSpan = 2;
        _title.setLayoutData(gd);
        FontData fd = _title.getFont().getFontData()[0];
        _title.setFont(SWTResourceManager.getFont(fd.getName(), (int)Math.round(fd.getHeight()*1.2),
                SWT.BOLD));

        _description = new Label(_shell, SWT.WRAP);
        gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.horizontalSpan = 2;
        gd.widthHint = 500;
        _description.setLayoutData(gd);

        _image = new Label(_shell, SWT.NONE);
        gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.horizontalSpan = 2;
        _image.setLayoutData(gd);

        _lblStep = new Label(_shell, SWT.NONE);

        Composite compButtons = new Composite(_shell, SWT.NONE);
        compButtons.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));

        FillLayout btnLayout = new FillLayout();
        btnLayout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;
        compButtons.setLayout(btnLayout);

        _btnSkip = new Button(compButtons, SWT.NONE);
        _btnSkip.setText("Skip Tour");
        _btnSkip.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                closeDialog();
            }
        });

        _btnPrev = new Button(compButtons, SWT.NONE);
        _btnPrev.setText("Go Back");
        _btnPrev.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setStep(_currentStep - 1);
            }
        });

        _btnNext = new Button(compButtons, SWT.NONE);
        _btnNext.addSelectionListener(new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setStep(_currentStep + 1);
            }
        });

        _shell.setDefaultButton(_btnNext);
        _btnNext.setFocus();

        _shell.pack();
        setStep(0);
    }
}
