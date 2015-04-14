package com.aerofs.ui;

import com.aerofs.lib.ex.ExNoConsole;

/**
 * methods with a trailing underscore is always called from the ui thread
 */
public interface IUI {

    enum MessageType {
        INFO,
        WARN,
        ERROR,
        QUESTION
    }

    void show(MessageType mt, String msg);

    interface IWaiter
    {
        void done();
    }

    /**
     * Similar to show() except that the message cannot be dismissed by the user.
     * Specifically, in GUI mode the dialog is not closable and ESC is intercepted.
     *
     * The message will disapppear when the task finishes, i.e. when IWaiter.done()
     * is called on the returned value.
     */
    IWaiter showWait(String title, String msg);

    /**
     * Similar to show(), but requires explicit confirmation from the user.
     * @throws ExNoConsole if no console is found. the method show()s an error
     * message before throwing.
     */
    void confirm(MessageType mt, String msg) throws ExNoConsole;

    /**
     * this method uses "Yes" for yesLabel and "No" for noLabel
     * @throws ExNoConsole if no console is found. the method show()s an error
     * message before throwing.
     */
    boolean ask(MessageType mt, String msg) throws ExNoConsole;

    /**
     * @return true if the user answered yes, false otherwise
     * @throws ExNoConsole if no console is found. the method show()s an error
     * message before throwing.
     */
    boolean ask(MessageType mt, String msg, String yesLabel, String noLabel)
            throws ExNoConsole;

    /**
     * @return true if the user answered yes, false otherwise
     * @throws ExNoConsole if no console is found. the method show()s an error
     * message before throwing.
     */
    boolean askNoDismiss(MessageType mt, String msg, String yesLabel, String noLabel)
            throws ExNoConsole;

    boolean isUIThread();

    void exec(Runnable runnable);

    void asyncExec(Runnable runnable);

    void timerExec(long delay, Runnable runnable);

    /**
     * Add a progress status.
     *
     * The CLI always prints out the message.
     *
     * The GUI shows a balloon with the message if {@paramref notify} is true.
     *
     * It also affects the GUI tray icon tooltip.
     *
     * @param message the method adds "..." to the end of the message
     * @param notify show notification with the same massage
     */
    void addProgress(String message, boolean notify);

    /**
     * @param message the corresponding message used in addProgress().
     */
    void removeProgress(String message);

    boolean areNotificationsClickable();

    void notify(MessageType mt, String msg);

    /**
     * NB onClick may or may not run in the GUI thread
     */
    void notify(MessageType mt, String msg, Runnable onClick);

    /**
     * NB onClick may or may not run in the GUI thread
     */
    void notify(MessageType mt, String title, String msg, Runnable onClick);

    boolean hasVisibleNotifications();

    void preSetupUpdateCheck_() throws Exception;

    /**
     * Do the setup process
     * @throws com.aerofs.controller.ExLaunchAborted if the user canceled the setup
     */
    void setup_(String rtRoot) throws Exception;

    /**
     * Stop daemon (ignoring errors), dispose all UI components, and quit the current process
     */
    void shutdown();

    /**
     * @throws ExNoConsole if no console is found. the method show()s an error
     * message before throwing.
     */
    void retypePassword() throws ExNoConsole;
}
