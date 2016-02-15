/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.formatted;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.google.common.base.Splitter;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class MessageFormatters
{
    /**
     * Formats the message with the specified File objects and generates an obfuscated and
     * plain text formatted message. The internal version of the message includes extended
     * information, such as file attributes.
     *
     * @param message The message to format
     * @param files The File objects to formatted/format into the message
     * @return The internal and user-friendly resultant message
     */
    public static FormattedMessage formatFileMessage(String message, File... files)
    {
        List<File> list = Arrays.asList(files);
        return appendFileAttributes(format(message, list), list);
    }

    public static FormattedMessage formatFileMessage(String message, Iterable<File> files)
    {
        return appendFileAttributes(format(message, files), files);
    }

    /**
     * Same as formatFileMessage() but deals with Path objects (which are relative to the root
     * anchor) and does not add extended file attributes to the message.
     *
     * @param message The message to format
     * @param paths The Path objects to formatted/format into the message
     * @return The obfuscated and plain text resultant message
     */
    public static FormattedMessage formatPathMessage(String message, Path... paths)
    {
        return format(message, Arrays.asList(paths));
    }

    private static FormattedMessage appendFileAttributes(FormattedMessage formattedMessage,
            Iterable<File> files)
    {
        StringBuilder internalBuilder = new StringBuilder(formattedMessage._plainText);

        // Add file attributes to the obfuscated version (user won't see this)
        for (File f : files) {
            internalBuilder.append(" (attrs:")
                    .append(FileUtil.getDebuggingAttributesString(f.getParentFile()))
                    .append(",")
                    .append(FileUtil.getDebuggingAttributesString(f))
                    .append(")");
        }
        return new FormattedMessage(formattedMessage._plainText, internalBuilder.toString());
    }

    private static final String PLACEHOLDER = "{}";
    private static final Splitter PLACEHOLDER_SPLITTER = Splitter.on(PLACEHOLDER);

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
    public static <T> FormattedMessage format(String message, Iterable<T> objects)
    {
        Iterator<T> objectIter = objects.iterator();
        StringBuilder plainBuilder = new StringBuilder();

        Iterator<String> messageIter = PLACEHOLDER_SPLITTER.split(message).iterator();
        while (messageIter.hasNext()) {
            // Append the message part
            String messagePart = messageIter.next();
            plainBuilder.append(messagePart);

            // Append the object if there is more text after it. Otherwise we
            // append the remaining objects differently.
            if (messageIter.hasNext()) {

                // If there are more objects, append them.
                if (objectIter.hasNext()) {
                    T object = objectIter.next();
                    plainBuilder.append(object.toString());
                } else {
                    // No more objects, so put back the placeholder that was here
                    plainBuilder.append(PLACEHOLDER);
                }
            }
        }

        // Append any remaining objects which do not correspond
        // to a {} in the message string
        if (objectIter.hasNext()) {
            plainBuilder.append(" ");

            while (objectIter.hasNext()) {
                T object = objectIter.next();
                plainBuilder.append(object.toString());
                if (objectIter.hasNext()) {
                    plainBuilder.append(", ");
                }
            }
        }

        return new FormattedMessage(plainBuilder.toString());
    }

    private MessageFormatters()
    {
        // Prevent instantiation
    }
}
