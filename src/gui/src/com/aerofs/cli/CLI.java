package com.aerofs.cli;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.ui.IUI;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIParam;
import com.aerofs.ui.UIUtil;

import java.io.IOError;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class CLI implements IUI {

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
        public long getDelay(TimeUnit arg0)
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
    private final LinkedList<ExecEntry> _execs = new LinkedList<ExecEntry>();
    private final PrintStream _out = System.out;
    private final DelayQueue<DelayedRunnable> _dq = new DelayQueue<DelayedRunnable>();
    private final String _rtRoot;
    /**
     * the caller thread will become the UI thread
     */
    public CLI(String rtRoot)
    {
        _rtRoot = rtRoot;
        _thd = Thread.currentThread();

        ThreadUtil.startDaemonThread("cli-timed-exec", new Runnable()
        {
            @Override
            public void run()
            {
                while (true) {
                    try {
                        asyncExec(_dq.take()._runnable);
                    } catch (InterruptedException e) {
                        SystemUtil.fatal(e);
                    }
                }
            }
        });

        // Schedule our launch() method to be called as soon as we enter the main loop
        asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                UIUtil.launch(_rtRoot, null, null);
            }
        });
    }


    public static CLI get()
    {
        return (CLI) UI.get();
    }

    @Override
    public void show(final MessageType mt, final String msg)
    {
        exec(new Runnable() {
            @Override
            public void run() {
                _out.println(mt2hdr(mt) + msg);
            }
        });
    }

    @Override
    public void confirm(final MessageType mt, final String msg) throws ExNoConsole
    {
        final OutArg<Boolean> noConsole = new OutArg<Boolean>(false);

        exec(new Runnable()
        {
            @Override
            public void run()
            {
                _out.println(mt2hdr(mt) + msg + "\n[Press ENTER to continue]");
                String line;
                line = readLine();
                if (line == null) noConsole.set(true);
            }
        });

        if (noConsole.get()) {
            show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        }
    }

    @Override
    public void showWithNoShowAgainCheckBox(MessageType mt, String msg,
            OutArg<Boolean> noShow)
    {
        noShow.set(false);
        show(mt, msg);
    }

    @Override
    public boolean ask(MessageType mt, String msg) throws ExNoConsole
    {
        return ask(mt, msg, "Yes", "No");
    }

    private static String getLabelString(String label, int key)
    {
        assert key < label.length();
        StringBuilder sb = new StringBuilder();
        sb.append(label.substring(0, key));
        sb.append('[');
        sb.append(label.charAt(key));
        sb.append(']');
        sb.append(label.substring(key + 1));
        return sb.toString();
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
        final OutArg<Boolean> ret = new OutArg<Boolean>();

        exec(new Runnable() {
            @Override
            public void run()
            {
                int yesKey = 0;
                int noKey = 0;
                while (noLabel.charAt(noKey) == yesLabel.charAt(yesKey)) { noKey++; }

                while (true) {
                    _out.print(mt2hdr(mt) + msg + " " +
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
            }
        });

        if (ret.get() == null) {
            _out.println();
            show(MessageType.WARN, S.NO_CONSOLE);
            throw new ExNoConsole();
        }
        return ret.get();
    }

    private static String mt2hdr(MessageType mt)
    {
        return mt == MessageType.INFO ? "" : mt + ": ";
    }

    public char[] askPasswd(final String msg) throws ExNoConsole
    {
        final OutArg<char[]> ret = new OutArg<char[]>();

        exec(new Runnable() {
            @Override
            public void run()
            {
                while (true) {
                    _out.print(msg + ": ");
                    ret.set(readPasswd());
                    if (ret.get() == null || ret.get().length > 0) {
                        break;
                    }
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
        final OutArg<String> ret = new OutArg<String>();

        exec(new Runnable() {
            @Override
            public void run()
            {
                while (true) {
                    _out.print(msg + (def != null ? " [" + def + "]" : "" ) + ": ");
                    ret.set(readLine());
                    if (ret.get() == null || !ret.get().isEmpty()) break;
                    if (def != null) {
                        ret.set(def);
                        break;
                    }
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

    public void enterMainLoop_()
    {
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
    public Object addProgress(String msg, boolean notify)
    {
        show(MessageType.INFO, msg + "...");
        return null;
    }

    @Override
    public void removeProgress(Object prog)
    {
        assert prog == null;
    }

    @Override
    public void removeAllProgresses()
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
    public void notify(MessageType mt, String msg, Runnable onClick)
    {
        notify(mt, null, msg, onClick);
    }

    @Override
    public void notify(MessageType mt, String title, String msg,
            Runnable onClick)
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
        _dq.add(new DelayedRunnable(System.currentTimeMillis() + delay, runnable));
    }

    @Override
    public void shutdown()
    {
        UI.dm().stopIgnoreException();
    }

    @Override
    public void retypePassword() throws ExNoConsole
    {
        String msg = S.SETUP_PASSWD;
        while (true) {
            String passwd = new String(askPasswd(msg));
            try {
                UI.controller().updateStoredPassword(Cfg.user().toString(), passwd);
                break;
            } catch (ExBadCredential ebc) {
                ThreadUtil.sleepUninterruptable(UIParam.LOGIN_PASSWD_RETRY_DELAY);
                show(MessageType.WARN, S.BAD_CREDENTIAL_CAP);
            } catch (Exception e) {
                show(MessageType.ERROR, S.PASSWORD_CHANGE_INTERNAL_ERROR + " " + UIUtil.e2msg(e));
            }
        }
    }

    @Override
    public void preSetupUpdateCheck_() throws Exception
    {
        new CLIPreSetupUpdateCheck().run();
    }
}
