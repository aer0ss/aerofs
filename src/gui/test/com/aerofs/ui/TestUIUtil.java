/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.ex.IExObfuscated;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.ui.error.ErrorMessages;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestUIUtil extends AbstractTest
{
    static class ExMockObfuscated extends Exception implements IExObfuscated
    {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString()
        {
            return "OBFUSCATED";
        }

        @Override
        public String getPlainTextMessage()
        {
            return "hello";
        }
    }

    @Test
    public void shouldShowPlainTextMessageOfObfuscatedException() throws Exception
    {
        Throwable ex = new ExMockObfuscated();
        String message = ErrorMessages.e2msgNoBracketDeprecated(ex);
        assertEquals("hello", message);
    }

    @Test
    public void shouldShowRegularMessageOfNormalException() throws Exception
    {
        Throwable ex = new Exception("woah");
        String message = ErrorMessages.e2msgNoBracketDeprecated(ex);
        assertEquals("woah", message);
    }
}
