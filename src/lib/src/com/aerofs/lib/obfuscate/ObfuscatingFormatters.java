/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.obfuscate.ObfuscatingFormatter.FormattedMessage;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.LinkedList;
import java.util.Set;

public final class ObfuscatingFormatters
{
    private static final ObfuscatingFormatter<File> _fileObjectFormatter =
            new ObfuscatingFormatter<File>(new FileObjectObfuscator());

    /**
     * Formats the message with the specified File objects and generates an obfuscated and
     * plain text formatted message. The obfuscated version of the message includes extended
     * information, such as file attributes.
     *
     * @param message The message to format
     * @param files The File objects to obfuscate/format into the message
     * @return The obfuscated and plain text resultant message
     */
    public static FormattedMessage formatFileMessage(String message, File... files)
    {
        return appendFileAttributes(_fileObjectFormatter.format(message, files), files);
    }

    private static FormattedMessage appendFileAttributes(FormattedMessage formattedMessage,
            File[] files)
    {
        StringBuilder obfuscatedBuilder = new StringBuilder(formattedMessage._obfuscated);

        // Add file attributes to the obfuscated version (user won't see this)
        for (File f : files) {
            obfuscatedBuilder.append(" (attrs:")
                    .append(FileUtil.getDebuggingAttributesString(f.getParentFile()))
                    .append(",")
                    .append(FileUtil.getDebuggingAttributesString(f))
                    .append(")");
        }
        return new FormattedMessage(obfuscatedBuilder.toString(), formattedMessage._plainText);
    }

    public static String obfuscatePath(Path path)
    {
        return obfuscatePath(path.elements());
    }

    public static String obfuscatePath(String path)
    {
        LinkedList<String> names = Lists.newLinkedList();
        File f = new File(path);

        // Root directory has an empty filename
        while (f != null && !f.getName().isEmpty()) {
            names.addFirst(f.getName());
            f = f.getParentFile();
        }

        String[] elements = new String[names.size()];
        names.toArray(elements);

        return obfuscatePath(elements);
    }

    public static String obfuscatePaths(Set<String> paths)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("[");
        String separator = "";
        for (String path : paths) {
            sb.append(separator);
            separator = ", ";
            sb.append(obfuscatePath(path));
        }
        sb.append("]");

        return sb.toString();
    }

    public static String obfuscatePath(String[] pathElements)
    {
        StringBuilder sb = new StringBuilder();

        if (pathElements.length == 0) {
            sb.append("/");
        }

        for (String name : pathElements) {
            sb.append('/');
            sb.append(Util.crc32(name));
        }

        return sb.toString();
    }

    private ObfuscatingFormatters()
    {
        // Prevent instantiation
    }
}
