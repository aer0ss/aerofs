package com.aerofs.cli;

import com.aerofs.base.Loggers;
import com.aerofs.gui.notif.NotifMessage;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.ui.IUI;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.update.Updater;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOError;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CLI implements IUI {
    private static final Logger l = Loggers.getLogger(CLI.class);

    private static class ExecEntry {
        final Runnable _runnable;
        final boolean _sync;
        boolean _done;

        ExecEntry(Runnable runnable, boolean sync)
        {
            _runnable = runnable;
            _sync = sync;
        }
    }

    private static class DelayedRunnable implements Delayed {

        final long _abs;
        final Runnable _runnable;

        DelayedRunnable(long abs, Runnable runnable)
        {
            _abs = abs;
            _runnable = runnable;
        }

        @Override
        public int compareTo(Delayed arg0)
        {
            return (int) (_abs - ((DelayedRunnable) arg0)._abs);
        }

        @Override
        public long getDelay(@Nonnull TimeUnit arg0)
        {
            long ms = _abs - System.currentTimeMillis();
            switch (arg0) {
            case MICROSECONDS: return ms * 1000;
            case MILLISECONDS: return ms;
            case NANOSECONDS: return ms * 1000 * 1000;
            default:
                assert arg0 == TimeUnit.SECONDS;
                return ms / 1000;
            }
        }
    }

    private final Thread _thd;
    private final LinkedList<ExecEntry> _execs = new LinkedList<>();
    private final PrintStream _out = System.out;
    private final DelayQueue<DelayedRunnable> _dq = new DelayQueue<>();
    private final AtomicBoolean _timedExecThreadStarted = new AtomicBoolean(false);

    /**
     * the caller thread will become the UI thread
     */
    public CLI()
    {
        _thd = Thread.currentThread();
    }

    public static CLI get()
    {
        return (CLI) UI.get();
    }

    @Override
    public void show(final MessageType mt, final String msg)
    {
        exec(() -> _out.println(mt2hdr(mt) + msg));
    }

    @Override
    public void confirm(final MessageType mt, final String msg) throws ExNoConsole
    {
        final OutArg<Boolean> noConsole = new OutArg<>(false);

        exec(() -> {
            l.debug("waiting for confirmation: {}", msg);
            _out.println(mt2hdr(mt) + msg);
            _out.println("[Press ENTER to continue]");
            String line;
            line = readLine();
            if (line == null) noConsole.set(true);
        });

        if (noConsole.get()) {
            show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        }
    }

    @Override
    public IWaiter showWait(final String title, String msg)
    {
        show(MessageType.INFO, msg);
        return () -> show(MessageType.INFO, title + " done");
    }

    @Override
    public boolean ask(MessageType mt, String msg) throws ExNoConsole
    {
        return ask(mt, msg, "Yes", "No");
    }

    private static String getLabelString(String label, int key)
    {
        assert key < label.length();
        return label.substring(0, key) + '[' + label.charAt(key) + ']' + label.substring(key + 1);
    }

    /**
     * @return null on errors (e.g. console is closed)
     */
    private String readLine()
    {
        assert isUIThread();
        try {
            return System.console() == null ? null : System.console().readLine();
        } catch (IOError e) {
            return null;
        }
    }

    /**
     * @return null on errors (e.g. console is closed)
     */
    private char[] readPasswd()
    {
        assert isUIThread();
        try {
            return System.console() == null ? null : System.console().readPassword();
        } catch (IOError e) {
            return null;
        }
    }

    @Override
    public boolean ask(final MessageType mt, final String msg, final String yesLabel,
            final String noLabel) throws ExNoConsole
    {
        final OutArg<Boolean> ret = new OutArg<>();

        exec(() -> {
            l.debug("waiting for [{} / {}] choice: {}", yesLabel, noLabel, msg);
            int yesKey = 0;
            int noKey = 0;
            while (noLabel.charAt(noKey) == yesLabel.charAt(yesKey)) { noKey++; }

            while (true) {
                _out.print(mt2hdr(mt) + msg + "\n" +
                        getLabelString(yesLabel, yesKey) + " / " +
                        getLabelString(noLabel, noKey) + ": ");

                String line = readLine();
                if (line == null) {
                    // leave ret as null
                    return;
                }

                if (line.length() != 1) continue;
                char ch = Character.toLowerCase(line.charAt(0));
                char y = Character.toLowerCase(yesLabel.charAt(yesKey));
                char n = Character.toLowerCase(noLabel.charAt(noKey));
                if (ch == y) {
                    ret.set(true);
                    break;
                } else if (ch == n) {
                    ret.set(false);
                    break;
                }
            }
        });

        if (ret.get() == null) {
            _out.println();
            show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        }
        return ret.get();
    }

    @Override
    public boolean askNoDismiss(final MessageType mt, final String msg, final String yesLabel,
            final String noLabel) throws ExNoConsole
    {
        return ask(mt, msg, yesLabel, noLabel);
    }

    private static String mt2hdr(MessageType mt)
    {
        return mt == MessageType.INFO ? "" : mt + ": ";
    }

    public char[] askPasswd(final String msg) throws ExNoConsole
    {
        final OutArg<char[]> ret = new OutArg<>();

        exec(() -> {
            l.debug("waiting for password: {}", msg);
            while (true) {
                _out.print(msg + ": ");
                ret.set(readPasswd());
                if (ret.get() == null || ret.get().length > 0) {
                    break;
                }
            }
        });

        if (ret.get() == null) {
            _out.println();
            show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        }
        return ret.get();
    }

    /**
     * @param def the default text. inputting empty text is now allowed if def == null
     */
    public String askText(final String msg, final String def)
        throws ExNoConsole
    {
        final OutArg<String> ret = new OutArg<>();

        exec(() -> {
            l.debug("waiting for data: {}", msg);
            while (true) {
                _out.print(msg + (def != null ? " [" + def + "]" : "" ) + ": ");
                ret.set(readLine());
                if (ret.get() == null || !ret.get().isEmpty()) break;
                if (def != null) {
                    ret.set(def);
                    break;
                }
            }
        });

        if (ret.get() == null) {
            _out.println();
            show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        }
        return ret.get();
    }

    @Override
    public void setup_(String rtRoot) throws Exception
    {
        new CLISetup(this, rtRoot);
    }

    public void scheduleLaunch(final String rtRoot)
    {
        asyncExec(() -> UIUtil.launch(rtRoot, null, null));
    }

    public void enterMainLoop_()
    {
        l.debug("enter cli loop");
        while (true) {
            ExecEntry ee;
            synchronized (_execs) {
                while (_execs.isEmpty()) { ThreadUtil.waitUninterruptable(_execs); }
                ee = _execs.removeFirst();
            }

            ee._runnable.run();

            if (ee._sync) {
                synchronized (ee) {
                    ee._done = true;
                    ee.notify();
                }
            }
        }
    }

    @Override
    public boolean isUIThread()
    {
        return _thd == Thread.currentThread();
    }

    @Override
    public void exec(Runnable runnable)
    {
        if (isUIThread()) {
            runnable.run();
        } else {
            ExecEntry ee = new ExecEntry(runnable, true);
            synchronized (_execs) {
                _execs.addLast(ee);
                _execs.notify();
            }

            synchronized (ee) {
                while (!ee._done) { ThreadUtil.waitUninterruptable(ee); }
            }
        }
    }

    @Override
    public void asyncExec(Runnable runnable)
    {
        ExecEntry ee = new ExecEntry(runnable, false);
        synchronized (_execs) {
            _execs.addLast(ee);
            _execs.notify();
        }
    }

    PrintStream out()
    {
        assert isUIThread();
        return _out;
    }

    /**
     * @param msg the method always suffix "..." to the message. The initial
     * should be capitalized.
     */
    public void progress(String msg)
    {
        show(MessageType.INFO, msg + "...");
    }

    @Override
    public void addProgress(String message, boolean notify)
    {
        show(MessageType.INFO, message + "...");
    }

    @Override
    public void removeProgress(String message)
    {

    }

    @Override
    public boolean areNotificationsClickable()
    {
        return false;
    }

    @Override
    public void notify(MessageType mt, String msg)
    {
        notify(mt, msg, null);
    }

    @Override
    public void notify(MessageType mt, String msg, NotifMessage onClick)
    {
        notify(mt, null, msg, onClick);
    }

    @Override
    public void notify(MessageType mt, String title, String msg,
            NotifMessage onClick)
    {
        show(mt, (title == null ? "" : title + " | ") + msg);
    }

    @Override
    public boolean hasVisibleNotifications()
    {
        return false;
    }

    @Override
    public void timerExec(long delay, Runnable runnable)
    {
        // lazy start the thread so we don't waste time on launching
        startTimedExecThreadAsNeeded();
        _dq.add(new DelayedRunnable(System.currentTimeMillis() + delay, runnable));
    }

    private void startTimedExecThreadAsNeeded()
    {
        if (_timedExecThreadStarted.compareAndSet(false, true)) return;

        ThreadUtil.startDaemonThread("cli-exec", () -> {
            while (true) {
                try {
                    asyncExec(_dq.take()._runnable);
                } catch (InterruptedException e) {
                    SystemUtil.fatal(e);
                }
            }
        });
    }

    @Override
    public void shutdown()
    {
        l.info("shutting down.");
        UIGlobals.dm().stopIgnoreException();
        ExitCode.NORMAL_EXIT.exit();
    }

    @Override
    public void preSetupUpdateCheck_() throws Exception
    {
        // N.B. if the updater is disabled, the pre-setup update check is skipped for GUI as well
        //   the logic here to duplicated due to its dependency on updater.
        if (UIGlobals.updater().getUpdateStatus() != Updater.Status.DISABLED) {
            new CLIPreSetupUpdateCheck().run();
        }
    }
}
