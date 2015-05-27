/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.error;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.IExObfuscated;
import com.aerofs.gui.GUI;
import com.aerofs.gui.misc.DlgDefect;
import com.aerofs.labeling.L;
import com.aerofs.lib.JsonFormat.ParseException;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * A utility class that displays error messages in the UI.
 *
 * Requirements on Error Messages
 * --------
 * An error message passed into the methods of this class should be a complete, well structured
 * sentence:
 *
 * 1. The initial of the string must be capitalized.
 * 2. The string must end with a period, a question mark, or an exclamation mark.
 * 3. The message should be suitable for non-technical users and non-english speaker. Please use
 *    non-technical languages and simple words.
 */
public class ErrorMessages
{
    private final static Logger l = Loggers.getLogger(ErrorMessage.class);

    /**
     * Display the user an appropriate error message for a given exception. Also log the exception's
     * stack trace. If the exception matches one of the types specified in {@code messages}, the
     * string corresponding to the matched type is shown. Otherwise, the method searches a list of
     * predefined common error types for matching. If none of the types matches, {@code
     * defaultMessage} is shown.
     *
     * This method works for both GUI and CLI. In GUI, the method pops up a standalone dialog (use
     * the other show() method to show a SHEET style dialog). In CLI, the method prints text
     * messages.
     *
     * @param messages an array of <exception type, string> pairs. If {@code exception} is
     *      {@code instanceof} one of the exception types, the corresponding message is shown. If if
     *      it matches more than one entries in the array, the first match is used.
     * @param defaultMessage the message for exceptions that is not {@code instanceof} any exception
     *      types specified in {@code messages}. In most cases, the content of the message should be:
     *
     *      L.brand() + " couldn't {verb}."
     *
     *      where {verb} describes the attempted operation, such as "share the folder".
     */
    public static void show(@Nonnull Throwable exception, @Nonnull String defaultMessage,
            @Nonnull ErrorMessage ... messages)
    {
        showImpl(null, exception, defaultMessage, messages);
    }

    /**
     * Identical to the other show() method, except that it shows a SHEET style dialog attached to
     * {@code shell}. The {@code shell} parameter is ignored in CLI.
     */
    public static void show(@Nonnull Shell shell, @Nonnull Throwable exception,
            @Nonnull String defaultMessage, @Nonnull ErrorMessage ... messages)
    {
        showImpl(shell, exception, defaultMessage, messages);
    }

    private static void showImpl(@Nullable Shell shell, Throwable exception, String defaultMessage,
            ErrorMessage[] messages)
    {
        l.warn("error message for exception:", exception);

        String message = getMessageNullable(exception, messages);

        if (UI.isGUI()) showInGUI(shell, exception, defaultMessage, message);
        else showInCLI(defaultMessage, message);
    }

    /**
     * 1. Linear search is all right, kid.
     *
     * 2. All strings in this data structure must have capitalization and periods, since
     *    showUnnormalized() does not normalize them.
     */
    static private ErrorMessage[] _commonMessages = new ErrorMessage[] {
            new ErrorMessage(ExNoConsole.class, "No console is available for user input."),
    };

    /**
     * @return null if no message corresponding to the type is found, and the default message
     * should be used.
     */
    private static String getMessageNullable(Throwable exception, ErrorMessage... messages)
    {
        for (ErrorMessage em : messages) if (em._type.isInstance(exception)) return em._message;

        for (ErrorMessage em : _commonMessages) if (em._type.isInstance(exception)) return em._message;

        return null;
    }

    private static void showInGUI(Shell shell, Throwable exception, String defaultMessage,
            @Nullable String message)
    {
        if (message == null) message = defaultMessage + " Please try again later.";
        GUI.get().show(shell, MessageType.ERROR, message);
    }

    private static void showInCLI(String defaultMessage, String message)
    {
        /**
         * Do not customize messages as what we do for GUI for two reasons:
         *
         * 1. aerofs-sh does not specify type-specific error messages. If we customize messages,
         * all the messages would be suffixed with "please try again later", which is
         * inappropriate for input-induced errors.
         *
         * 2. CLI users are usually tech savvies and don't need much handholding, and having
         * aeofs-sh to specify type-specific error messages would be time consuming :)
         */
        if (message == null) message = defaultMessage;
        UI.get().show(MessageType.ERROR, message + " See log files for debugging information.");
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
            String wireType = ((AbstractExWirable) e).getWireTypeStringDeprecated();
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
