/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import com.aerofs.lib.obfuscate.ObfuscatingFormatter.FormattedMessage;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestObfuscatingFormatter extends AbstractTest
{
    private static class MockStringObfuscator implements IObfuscator<String>
    {
        @Override
        public String obfuscate(String object)
        {
            return "OBFUSCATED";
        }

        @Override
        public String plainText(String object)
        {
            return object;
        }
    }

    ObfuscatingFormatter<String> formatter;

    @Before
    public void setUp() throws Exception
    {
        formatter = new ObfuscatingFormatter<String>(new MockStringObfuscator());
    }

    @Test
    public void shouldKeepMessageUnchangedWithNoObjects() throws Exception
    {
        final String expected = "Hey there {} what's up?";
        FormattedMessage result = formatter.format("Hey there {} what's up?",
                ImmutableList.<String>of());
        assertEquals(expected, result._plainText);
        assertEquals(expected, result._obfuscated);
    }

    @Test
    public void shouldInterpolateMessageWithObject() throws Exception
    {
        final String expectedPlain = "Hey there Bob what's up?";
        final String expectedObfuscated = "Hey there OBFUSCATED what's up?";
        FormattedMessage result = formatter.format("Hey there {} what's up?",
                ImmutableList.of("Bob"));
        assertEquals(expectedPlain, result._plainText);
        assertEquals(expectedObfuscated, result._obfuscated);
    }

    @Test
    public void shouldInterpolateMessageWithManyObjects() throws Exception
    {
        final String expectedPlain = "Hey there Bob how is Alice doing?";
        final String expectedObfuscated = "Hey there OBFUSCATED how is OBFUSCATED doing?";
        FormattedMessage result = formatter.format("Hey there {} how is {} doing?",
                ImmutableList.of("Bob", "Alice"));
        assertEquals(expectedPlain, result._plainText);
        assertEquals(expectedObfuscated, result._obfuscated);
    }

    @Test
    public void shouldAppendObjectToMessageWhenMessageHasNoPlaceholderString() throws Exception
    {
        final String expectedPlain = "Hey what's up? Bob";
        final String expectedObfuscated = "Hey what's up? OBFUSCATED";
        FormattedMessage result = formatter.format("Hey what's up?", ImmutableList.of("Bob"));
        assertEquals(expectedPlain, result._plainText);
        assertEquals(expectedObfuscated, result._obfuscated);
    }

    @Test
    public void shouldAppendMultipleObjectsToMessageWithOnePlaceholderString() throws Exception
    {
        final String expectedPlain = "Hey Bob what's up? Alice";
        final String expectedObfuscated = "Hey OBFUSCATED what's up? OBFUSCATED";
        FormattedMessage result = formatter.format("Hey {} what's up?",
                ImmutableList.of("Bob", "Alice"));
        assertEquals(expectedPlain, result._plainText);
        assertEquals(expectedObfuscated, result._obfuscated);
    }
}
