/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;

import java.util.Iterator;

/**
 * Formats messages with objects and generates both obfuscated and plain text versions
 * of the formatted message.
 *
 * @param <T> The type of object to format along with the message
 */
public class ObfuscatingFormatter<T>
{
    private static final String PLACEHOLDER = "{}";
    private static final Splitter PLACEHOLDER_SPLITTER = Splitter.on(PLACEHOLDER);

    /**
     * Container for returning both obfuscated and plain text formatted messages.
     */
    public static class FormattedMessage {
        public final String _obfuscated;
        public final String _plainText;

        FormattedMessage(String obfuscated, String plainText)
        {
            this._obfuscated = obfuscated;
            this._plainText = plainText;
        }
    }

    private final IObfuscator<T> _obfuscator;

    public ObfuscatingFormatter(IObfuscator<T> obfuscator)
    {
        assert(obfuscator != null);
        _obfuscator = obfuscator;
    }

    /**
     * Formats the message by inserting the objects wherever the string '{}' is present in the
     * message. If no '{}' is found, objects are simply appended to the message.
     * Two messages are returned: one obfuscated and one plain text. The string
     * representations of the objects, both obfuscated and plain text, are generated using
     * the IObjectObfscator given upon construction.
     *
     * @param message The message to display, with '{}' denoting placeholders where the specified
     * objects will be inserted.
     * @param objects The objects to insert into the message
     * @return A tuple of obfuscated and plain text formatted messages
     */
    public FormattedMessage format(String message, T... objects)
    {
        Iterator<T> objectIter = Iterators.forArray(objects);
        StringBuilder obfuscatedBuilder = new StringBuilder();
        StringBuilder plainBuilder = new StringBuilder();

        Iterator<String> messageIter = PLACEHOLDER_SPLITTER.split(message).iterator();
        while (messageIter.hasNext()) {
            // Append the message part
            String messagePart = messageIter.next();
            obfuscatedBuilder.append(messagePart);
            plainBuilder.append(messagePart);

            // Append the object if there is more text after it. Otherwise we
            // append the remaining objects differently.
            if (messageIter.hasNext()) {

                // If there are more objects, append them.
                if (objectIter.hasNext()) {
                    T object = objectIter.next();
                    obfuscatedBuilder.append(_obfuscator.obfuscate(object));
                    plainBuilder.append(_obfuscator.plainText(object));
                } else {
                    // No more objects, so put back the placeholder that was here
                    obfuscatedBuilder.append(PLACEHOLDER);
                    plainBuilder.append(PLACEHOLDER);
                }
            }
        }

        // Append any remaining objects which do not correspond
        // to a {} in the message string
        if (objectIter.hasNext()) {
            obfuscatedBuilder.append(" ");
            plainBuilder.append(" ");

            while (objectIter.hasNext()) {
                T object = objectIter.next();
                obfuscatedBuilder.append(_obfuscator.obfuscate(object));
                plainBuilder.append(_obfuscator.plainText(object));
                if (objectIter.hasNext()) {
                    obfuscatedBuilder.append(", ");
                    plainBuilder.append(", ");
                }
            }
        }

        return new FormattedMessage(obfuscatedBuilder.toString(), plainBuilder.toString());
    }
}
