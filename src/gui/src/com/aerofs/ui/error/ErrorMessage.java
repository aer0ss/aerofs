/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.error;

/**
 * This class is used by the users of the ErrorMessages class to specify an error message for a given
 * exception type.
 */
public class ErrorMessage
{
    final Class<? extends Throwable> _type;
    final String _message;

    /**
     * @param type an exception type
     * @param message the string to be shown for the given type of exceptions. Must be a complete
     * sentence. See the Error Message Guideline in ErrorMessage.java for more. PLEASE READ.
     */
    public ErrorMessage(Class<? extends Throwable> type, String message)
    {
        _type = type;
        _message = message;
    }
}
