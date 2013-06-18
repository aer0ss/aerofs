/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.error;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.IExObfuscated;
import com.aerofs.lib.JsonFormat.ParseException;
import com.aerofs.lib.ex.ExNoConsole;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * A utility class that displays error messages in the UI
 */
public class ErrorMessages
{
    public static void show(Throwable e, String defaultMessage, Map<Class<?>, String> error2message)
    {
        // TODO (WW)
    }

    ////////
    // All the following methods are deprecated. Do not use.

    /**
     * Deprecated. Use show() instead.
     */
    public static String e2msgDeprecated(Throwable e)
    {
        return '(' + e2msgNoBracketDeprecated(e) + ')';
    }

    /**
     * Deprecated. Use show() instead.
     */
    public static String e2msgSentenceNoBracketDeprecated(Throwable e)
    {
        String str = e2msgNoBracketDeprecated(e);

        if (str.isEmpty()) return "";

        str = Character.toUpperCase(str.charAt(0)) + str.substring(1);
        if (!str.endsWith(".")) str += ".";
        return str;
    }

    private static final String SERVER_ERROR = "Server returned HTTP response code: ";

    /**
     * Deprecated. Use show() instead.
     */
    public static String e2msgNoBracketDeprecated(Throwable e)
    {
        while (e.getCause() != null) { e = e.getCause(); }

        final String message;
        if (e instanceof IExObfuscated) {
            // Extract the plain text message if this is an obfuscated Exception
            message = ((IExObfuscated) e).getPlainTextMessage();
        } else {
            message = e.getMessage();
        }

        if (e instanceof AbstractExWirable) {
            String wireType = ((AbstractExWirable) e).getWireTypeString();
            return wireType.equals(message) ? wireType : wireType + ": " + message;
        } else if (e instanceof FileNotFoundException) {
            return message + " is not found";
        } else if (e instanceof SocketException) {
            return "connection failed";
        } else if (e instanceof UnknownHostException) {
            return "communication with the server failed";
        } else if (e instanceof ExNoConsole) {
            return "no console for user input";
        } else if (e instanceof EOFException) {
            return "connection failed or end of file";
        } else if (e instanceof ParseException) {
            return "parsing failed";

        // the following tests should go last
        } else if (message == null) {
            return e.getClass().getSimpleName();
        } else if (e instanceof IOException && message.startsWith(SERVER_ERROR)) {
            int start = SERVER_ERROR.length();
            String code = message.substring(start, start + 3);
            return "server error, code " + code;
        } else {
            return message;
        }
    }
}
