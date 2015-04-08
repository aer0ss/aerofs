/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.multiuser.setup;

import com.aerofs.gui.CompSpin;
import com.aerofs.gui.GUI;
import com.aerofs.gui.GUI.ISWTWorker;
import com.aerofs.lib.S;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.widgets.*;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.net.ConnectException;

/**
 * This class provides a template for performing asynchronous tasks on setup pages.
 */
public abstract class AbstractSetupWorkPage extends AbstractSetupPage
{
    protected AbstractSetupWorkPage(Composite parent, int style)
    {
        super(parent, style);

        getShell().addListener(SWT.Close, new Listener()
        {
            @Override
            public void handleEvent(Event event)
            {
                if (_inProgress) event.doit = false;
            }
        });
    }

    private boolean _inProgress;

    protected abstract @Nonnull Logger getLogger();

    // return the list of controls to disable while work is in progress
    //   the list may contain null elements.
    protected abstract @Nonnull Control[] getControls();

    protected abstract @Nonnull CompSpin getSpinner();

    // subclasses should call this method to initiate work
    protected final void doWork()
    {
        setProgress(true);

        writeToModel(_model);

        GUI.get().safeWork(getDefaultButton(), new ISWTWorker()
        {
            @Override
            public void run()
                    throws Exception
            {
                doWorkImpl();
            }

            @Override
            public void okay()
            {
                setProgress(false);
                traverse(SWT.TRAVERSE_PAGE_NEXT);
            }

            @Override
            public void error(Exception e)
            {
                onError(e);
            }
        });
    }

    // override to perform work on a non-UI thread
    protected abstract void doWorkImpl() throws Exception;

    private void setProgress(boolean inProgress)
    {
        _inProgress = inProgress;

        if (_inProgress) getSpinner().start();
        else getSpinner().stop();

        for (Control control : getControls()) {
            // skip null elements because getControls() tolerates null elements
            if (control != null) control.setEnabled(!_inProgress);
        }
    }

    protected void onError(Exception e)
    {
        getLogger().error("Setup error", e);

        ErrorMessage[] baseErrorMessages = new ErrorMessage[] {
                new ErrorMessage(ConnectException.class, S.SETUP_ERR_CONN),
                new ErrorMessage(ExUIMessage.class, e.getMessage())
        };

        ErrorMessages.show(getShell(), e, getDefaultErrorMessage(),
                (ErrorMessage[])ArrayUtils.addAll(baseErrorMessages, getErrorMessages(e)));

        setProgress(false);
    }

    protected abstract String getDefaultErrorMessage();

    protected abstract ErrorMessage[] getErrorMessages(Exception e);

    // an utility method to create a listener that invokes doWork when invoked
    protected final SelectionListener createListenerToDoWork()
    {
        return new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                doWork();
            }
        };
    }

    // an utility method to create a listener that'd validate user input when invoked
    protected final ModifyListener createListenerToValidateInput()
    {
        return new ModifyListener()
        {
            @Override
            public void modifyText(ModifyEvent modifyEvent)
            {
                validateInput();
            }
        };
    }

    // subclass should override to validate user input
    protected boolean isInputValid()
    {
        return true;
    }

    protected abstract @Nonnull
    Button getDefaultButton();

    // invoke to validate user input, which will update the state of the default button accordingly
    protected final void validateInput()
    {
        Button button = getDefaultButton();
        button.setEnabled(isInputValid());
        if (button.getEnabled()) getShell().setDefaultButton(button);
    }
}
