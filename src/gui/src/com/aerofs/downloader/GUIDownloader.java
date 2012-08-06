package com.aerofs.downloader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

import com.aerofs.gui.GUIParam;

public class GUIDownloader extends Shell implements IProgressIndicator {

    private final ProgressBar _prog;

    public GUIDownloader()
    {
        super((Display) null, SWT.DIALOG_TRIM);

        GridLayout gridLayout = new GridLayout(1, false);
        // same as DlgPreSetupUpdateCheck
        gridLayout.horizontalSpacing = 8;
        gridLayout.marginWidth = GUIParam.MARGIN;
        gridLayout.marginHeight = GUIParam.MARGIN;
        setLayout(gridLayout);

        Label label = new Label(this, SWT.NONE);
        label.setText(Main.TITLE);

        _prog = new ProgressBar(this, SWT.NONE);
        GridData gdProgressBar = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gdProgressBar.widthHint = 280;
        _prog.setLayoutData(gdProgressBar);

        Button button = new Button(this, SWT.NONE);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                close();
                System.exit(1);
            }
        });
        button.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
        button.setText("Cancel");
        createContents();
    }

    protected void createContents()
    {
        setText(Main.PRODUCT_NAME);
    }

    @Override
    protected void checkSubclass()
    {
        // Disable the check that prevents subclassing of SWT components
    }

    private void safeAsyncExec(final Runnable run)
    {
        if (!isDisposed()) {
            getDisplay().asyncExec(new Runnable() {
                @Override
                public void run()
                {
                    if (!isDisposed()) run.run();
                }
            });
        }
    }

    @Override
    public void setProgress(final int count)
    {
        safeAsyncExec(new Runnable() {
            @Override
            public void run()
            {
                _prog.setSelection(count);
            }
        });
    }

    @Override
    public void complete()
    {
        safeAsyncExec(new Runnable() {
            @Override
            public void run()
            {
                close();
                System.exit(0);
            }
        });
    }

    @Override
    public void run(final Runnable runnable)
    {
        Display display = getDisplay();
        layout();
        pack();
        centerShell(this);
        open();

        Thread thd = new Thread() {
            @Override
            public void run()
            {
                runnable.run();
            }
        };
        thd.setDaemon(true);
        thd.start();

        while (!isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
    }

    // copied from GUIUtil
    private static void centerShell(Shell shell)
    {
        Monitor primary = shell.getDisplay().getPrimaryMonitor();
        Rectangle bounds = primary.getBounds();
        Rectangle rect = shell.getBounds();

        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;

        shell.setLocation(x, y);
    }

    @Override
    public void setTotal(final int len)
    {
        safeAsyncExec(new Runnable() {
            @Override
            public void run()
            {
                _prog.setMinimum(0);
                _prog.setMaximum(len);
            }
        });
    }

    @Override
    public void error(final Throwable e)
    {
        safeAsyncExec(new Runnable() {
            @Override
            public void run()
            {
                MessageBox msg = new MessageBox(GUIDownloader.this,
                        SWT.SHEET | SWT.OK | SWT.ICON_ERROR);
                msg.setMessage(Main.getErrorMessage(e));
                msg.open();
                close();
                System.exit(1);
            }
        });
    }

}
