/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.obfuscate.ObfuscatingFormatter.FormattedMessage;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public final class ObfuscatingFormatters
{
    // The IObfuscator's that do the actual obfuscation
    private static final IObfuscator<File> _fileObjectObfuscator = new FileObjectObfuscator();
    private static final IObfuscator<Path> _pathObfuscator = new PathObfuscater(
            _fileObjectObfuscator);

    // The formatters that create FormattedMessage instances using IObfuscator's
    private static final ObfuscatingFormatter<File> _fileObjectFormatter =
            new ObfuscatingFormatter<File>(_fileObjectObfuscator);
    private static final ObfuscatingFormatter<Path> _pathFormatter =
            new ObfuscatingFormatter<Path>(_pathObfuscator);

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
        List<File> list = Arrays.asList(files);
        return appendFileAttributes(_fileObjectFormatter.format(message, list), list);
    }

    public static FormattedMessage formatFileMessage(String message, Iterable<File> files)
    {
        return appendFileAttributes(_fileObjectFormatter.format(message, files), files);
    }

    /**
     * Same as formatFileMessage() but deals with Path objects (which are relative to the root
     * anchor) and does not add extended file attributes to the message.
     *
     * @param message The message to format
     * @param paths The Path objects to obfuscate/format into the message
     * @return The obfuscated and plain text resultant message
     */
    public static FormattedMessage formatPathMessage(String message, Path... paths)
    {
        return _pathFormatter.format(message, Arrays.asList(paths));
    }

    private static FormattedMessage appendFileAttributes(FormattedMessage formattedMessage,
            Iterable<File> files)
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

    /**
     * Obfuscates a Path object. The direct use of this method is deprecated. Consider obfuscating
     * using the above formatPathMessage() method.
     *
     * @param path The path to obfuscate
     * @return The obfuscated path as a String
     */
    public static String obfuscatePath(Path path)
    {
        return _pathObfuscator.obfuscate(path);
    }

    /**
     * Obfuscates a String representing a path. The direct use of this method is deprecated.
     * Consider representing paths as File's or Path objects.
     *
     * @param path The path to obfuscate
     * @return The obfuscated path as a String
     */
    public static String obfuscatePath(String path)
    {
        return _fileObjectObfuscator.obfuscate(new File(path));
    }

    private ObfuscatingFormatters()
    {
        // Prevent instantiation
    }
}
