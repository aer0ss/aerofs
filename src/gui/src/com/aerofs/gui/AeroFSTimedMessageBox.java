/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui;

import com.aerofs.base.C;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * A Timed dialog similar to AeroFSDialog except that after the time runs out
 * the dialog closes itself and sets its return code to OK.
 */
public class AeroFSTimedMessageBox extends AeroFSMessageBox
{
    private String _format;
    private volatile long _countdown;    ///< Number of seconds remaining until dialog closes.

    private final long refreshDelay = 1* C.SEC; ///< refresh the dialog every second.

    /**
     * Creates a Timed Dialog that will be open for {@code duration} seconds and then will
     * close itself while setting its returnCode to OK.
     * @param format String that needs to be formatted containing a "%d" to display the remaining
     * seconds on the timer to shut down the dialog.
     * @param duration Number of seconds that we want to leave this dialog open.
     */
    public AeroFSTimedMessageBox(Shell parentShell, boolean sheet, IconType it, String format,
            ButtonType bt, String okayLabel, String cancelLabel, long duration)
    {
        super(parentShell, sheet, String.format(format, duration), it, bt, okayLabel, cancelLabel,
                false);
        _format = format;
        _countdown = duration;
    }

    /**
     * Start the timer on this dialog we update the label every second, set the
     * return code to OK once the countdown hits 0 and close the dialog.
     */
    public void startTimer()
    {

        Display.getDefault().timerExec((int) refreshDelay, new Runnable()
        {
            @Override
            public void run()
            {
                addStopTimerListeners(this);
                if (getCountdown() > 0) {
                    // we have more time remaining display the remaining time and create
                    // a new thread to update the GUI again in {@code refreshDelay}
                    updateTime();
                    Display.getDefault().timerExec((int) refreshDelay, this);
                } else {
                    close();
                }
            }
        });
    }

    /**
     * Update the counter and label in the UI to display the number of seconds remaining
     * until the dialog closes. If the countdown hits 0 we set the return code to be OK.
     */
    private void updateTime()
    {
        _countdown--;
        if (_countdown <= 0) {
            setReturnCode(IDialogConstants.OK_ID);
        } else {
            setMessage(String.format(_format, _countdown));
        }
    }

    private long getCountdown()
    {
        return _countdown;
    }

    /**
     * In case a user clicks one of the buttons we need to stop the timer thread
     * to update the GUI otherwise an exception will occur.
     * @param runnable The timer runnable that updates the MessageBox.
     */
    private void addStopTimerListeners(final Runnable runnable)
    {
        getOkayBtn().addListener(SWT.Selection, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                // -1 to stop the timerExec for the runnable
                Display.getDefault().timerExec(-1, runnable);
            }
        });

        getCancelBtn().addListener(SWT.Selection, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                // -1 to stop the timerExec for the runnable
                Display.getDefault().timerExec(-1, runnable);
            }
        });

    }
}
