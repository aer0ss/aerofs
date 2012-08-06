package com.aerofs.gui.netutil;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Label;

import com.aerofs.gui.GUI;
import com.aerofs.lib.Util;
import com.aerofs.ui.UIUtil;

public class CompBandwidthStatus extends Composite {
    private ProgressBar _progressBar;
    private Label _label;
    private StackLayout _sl = new StackLayout();

    /**
     * Create the composite.
     * @param parent
     * @param style
     */
    public CompBandwidthStatus(Composite parent, int style)
    {
        super(parent, style);

        setLayout(_sl);

        _progressBar = new ProgressBar(this, SWT.NONE);

        _label = new Label(this, SWT.NONE);
        _label.setText(Util.formatBandwidth(0, 0));
        _sl.topControl = _label;
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    void reset()
    {
        done(null, 0, 0);
    }

    /**
     * may be called from non-GUI threads
     */
    void init()
    {
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run() {
                _label.setText("Starting...");
                _sl.topControl = _label;
                layout();
            }
        });
    }


    /**
     * may be called from non-GUI threads
     * @param precent show 100% if greater than 100%
     */
    void progress(final int percent)
    {
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run() {
                _progressBar.setSelection(Math.min(percent, 100));
                _sl.topControl = _progressBar;
                layout();
            }
        });
    }

    /**
     * may be called from non-GUI threads
     */
    void collecting()
    {
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run() {
                _label.setText("Collecting results...");
                _sl.topControl = _label;
                layout();
            }
        });
    }

    /**
     * may be called from non-GUI threads
     *
     * @param bytes ignored if e != null
     * @param interval ignored if e != null
     */
    void done(final Exception e, final long bytes, final long interval)
    {
        GUI.get().safeAsyncExec(this, new Runnable() {
            @Override
            public void run() {
                if (e != null) {
                    _label.setText("Error " + UIUtil.e2msg(e));
                } else {
                    String txt = Util.formatBandwidth(bytes, interval);
                    _label.setText(txt);
                }
                _sl.topControl = _label;
                layout();
            }
        });
    }
}
