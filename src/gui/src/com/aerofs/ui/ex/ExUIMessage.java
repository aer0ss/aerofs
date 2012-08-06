package com.aerofs.ui.ex;

/**
 * these exceptions are used in the places where the UI should display only the
 * message preset in the exception.
 */
public class ExUIMessage extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExUIMessage(String msg)
    {
        super(msg);
    }
}
