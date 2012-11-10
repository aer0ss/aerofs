package com.aerofs.lib;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.varia.NullAppender;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.AbstractExWirable;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoAvailDevice;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.LogLevel;
import com.google.protobuf.GeneratedMessageLite;

import javax.annotation.Nonnull;

import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;

public abstract class Util
{
    public static final String REMOTE_STACKTRACE = "Remote stacktrace:";
    private static final String PROP_RTROOT = "aerofs.rtroot";
    private static final String PROP_APP = "aerofs.app";
    private static final String LOG4J_PROPERTIES_FILE = "aerofs-log4j.properties";
    private static final int INITIAL_ENUMERATION_ARRAY_SIZE = 30;

    static
    {
        //noinspection ConstantConditions
        assert INITIAL_ENUMERATION_ARRAY_SIZE > 0;
    }

    private static final Logger l = l(Util.class);

    private Util()
    {
        // private to enforce uninstantiability for this class
    }

    /**
     * Send a defect report and crash the daemon. Some callers need to throw the returned value
     * only to suppress compiler warnings, whereas other callers require this method to declare
     * "throws Error" for the same lame purpose.
     */
    public static Error fatal(final Throwable e) throws Error
    {
        l.fatal("FATAL:" + Util.e(e));
        SVClient.logSendDefectSyncNoLogsIgnoreErrors(true, "FATAL:", e);
        ExitCode.FATAL_ERROR.exit();
        throw new Error(e);
    }

    /**
     * See {@link Util#fatal(Throwable)}
     */
    public static Error fatal(String str) throws Error
    {
        return fatal(new Exception(str));
    }

    /**
     * Call fatal() if {@code e} is an unchecked exception. Alternatively, we can throw the same
     * {@code e} back to the caller. But unfortunately, some frameworks, noticeably netty, will not
     * crash the process.
     *
     * NOTICE: you should call this method whenever you do "catch (Exception e)" or
     * "catch (Throwable e)".
     */
    public static void fatalOnUncheckedException(Throwable e) throws Error
    {
        if (e instanceof RuntimeException || e instanceof Error) fatal(e);
    }

    public static <T> void addListener_(Set<T> ls, T l)
    {
        if (Param.STRICT_LISTENERS) Util.verify(ls.add(l));
        else ls.add(l);
    }

    public static <T> void removeListener_(Set<T> ls, T l)
    {
        if (Param.STRICT_LISTENERS) Util.verify(ls.remove(l));
        else ls.remove(l);
    }

    /**
     * @param app the log file will be named "<app>.log"
     */
    public static void initLog4J(String rtRoot, String app)
        throws IOException
    {
        System.setProperty(PROP_RTROOT, rtRoot);
        System.setProperty(PROP_APP, app);

        if (Cfg.staging()) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            URL url = cl.getResource(LOG4J_PROPERTIES_FILE);
            if (url != null) {
                PropertyConfigurator.configure(url);
                return;
            }
        }

        if (app.equals(C.SH_NAME)) {
            Logger.getRootLogger().addAppender(new NullAppender());
        } else {
            setupLog4JLayoutAndAppenders(rtRoot + File.separator + app + C.LOG_FILE_EXT,
                    Cfg.staging(), true);
        }

        String strDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        l.debug(app + " ========================================================\n" +
            Cfg.ver() + (Cfg.staging() ? " staging " : " ") +
            strDate + " " + AppRoot.abs() + " " + new File(rtRoot).getAbsolutePath());

        if (Cfg.useProfiler()) {
            l.debug("profiler: " + Cfg.profilerStartingThreshold());
        }

        Logger.getRootLogger().setLevel(Cfg.lotsOfLog(rtRoot) ? Level.DEBUG : Level.INFO);

        // print termination banner
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run()
            {
                DateFormat format = new SimpleDateFormat("yyyyMMdd");
                String strDate = format.format(new Date());
                l.debug("TERMINATED " + strDate);
            }
        }));
    }

    public static Layout getLogLayout(boolean fullyQualified)
    {
        LoggerRepository repo = LogManager.getLoggerRepository();
        if (repo instanceof ThrowableRendererSupport) {
            ThrowableRendererSupport trs = ((ThrowableRendererSupport)repo);
            trs.setThrowableRenderer(LogUtil.newThrowableRenderer());
        }

        String patternLayoutString = "%d{HHmmss.SSS}%-.1p %t ";
        patternLayoutString += fullyQualified ? "%c" : "%C{1}";
        patternLayoutString += ", %m%n";
        Layout layout = LogUtil.newPatternLayout(patternLayoutString);
        return layout;
    }

    public static void setupLog4JLayoutAndAppenders(String logfile, boolean logToConsole,
            boolean fullyQualified)
            throws IOException
    {
        Layout layout = getLogLayout(fullyQualified);

        List<Appender> appenders = new ArrayList<Appender>(2); // max number of appenders
        appenders.add(new DailyRollingFileAppender(layout, logfile, "'.'yyyyMMdd"));
        if (logToConsole) {
            appenders.add(new ConsoleAppender(layout));
        }

        for (Appender appender : appenders) {
            Logger.getRootLogger().addAppender(appender);
        }
    }

    public static void sleepUninterruptable(long ms)
    {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            fatal(e);
        }
    }

    public static void waitUninterruptable(Object obj, long ms)
    {
        try {
            obj.wait(ms);
        } catch (InterruptedException e) {
            fatal(e);
        }
    }

    public static void waitUninterruptable(Object obj)
    {
        waitUninterruptable(obj, 0);
    }

    public static String q(Object o)
    {
        return "\"" + o + "\"";
    }

    // these methods may have high overhead and is only suitable for occasional
    // uses. cache the objects for frequent logging
    //
    public static Logger l(Object o)
    {
        return l(o.getClass());
    }

    public static Logger l(Class<?> c)
    {
        return LogUtil.getLogger(c);
    }

    public static Thread startDaemonThread(String name, Runnable run)
    {
        if (l.isDebugEnabled()) l.debug("startDaemonThread: " + name);
        Thread thd = new Thread(run);
        thd.setName(name);
        thd.setDaemon(true);
        thd.start();
        return thd;
    }

    private static ThreadGroup getRootThreadGroup()
    {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while(group.getParent() != null) {
            group = group.getParent();
        }

        return group;
    }

    private static Thread[] getAllThreads()
    {
        return getAllChildThreads(getRootThreadGroup());
    }

    private static Thread[] getAllChildThreads(ThreadGroup group)
    {
        int enumeratedGroups;
        int arraySize = INITIAL_ENUMERATION_ARRAY_SIZE;
        Thread[] threads;
        do {

            //
            // we have to do this because of the contract provided by ThreadGroup.enumerate
            // basically, we can't know the number of threads a-priori so we can't appropriately
            // size the array. The best we can do is pick a number and use it. If the array is too
            // small ThreadGroup.enumerate will silently drop the remaining threads. The only way
            // to detect this is to check if the number of enumerated threads is _exactly_
            // equal to the size of the array
            //

            arraySize = arraySize * 2;
            threads = new Thread[arraySize];
            enumeratedGroups = group.enumerate(threads, true);
        } while (enumeratedGroups == arraySize);

        return threads;
    }

    private static String convertStackTraceToString(StackTraceElement[] stackTrace)
    {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement e : stackTrace) {
            if (e == null) break; // technically this should never happen

            builder.append("\t").append("at " + e.toString());
            builder.append(System.getProperty("line.separator"));
        }

        return builder.toString();
    }

    private static String getAllThreadStackTraces(Thread[] threads)
    {
        StringBuilder builder = new StringBuilder();

        for (Thread t : threads) {
            if (t == null) break;

            builder.append(t.getId() + ":" + t.getName() + " [" + t.getState() + "]");
            builder.append(System.getProperty("line.separator"));
            builder.append(convertStackTraceToString(t.getStackTrace()));
            builder.append(System.getProperty("line.separator"));
        }

        return builder.toString();
    }

    public static String getThreadStackTrace(Thread t)
    {
        return getAllThreadStackTraces(new Thread[] {t});
    }

    public static void logAllThreadStackTraces()
    {
        l().warn("==== BEGIN STACKS ====\n" +
                getAllThreadStackTraces(getAllThreads()) +
                "\n==== END STACKS ====");
    }

    public static ThreadFactory threadGroupFactory(ThreadGroup group)
    {
        return threadGroupFactory(group, Integer.MIN_VALUE);
    }

    public static ThreadFactory threadGroupFactory(final ThreadGroup group, final int priority)
    {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r,
                                      group.getName() + '-' + threadNumber.getAndIncrement(),
                                      0);
                if (priority != Integer.MIN_VALUE) t.setPriority(priority);
                return t;
            }
        };
        return factory;
    }

    public static <T extends Comparable<T>> int compare(T[] a, T[] b)
    {
        int min = Math.min(a.length, b.length);
        for (int i = 0; i < min; i++) {
            int comp = a[i].compareTo(b[i]);
            if (comp != 0) return comp;
        }

        return a.length - b.length;
    }

    /** Compare, where T is comparable to U (though not necessarily vice versa) */
    public static <U, T extends Comparable<? super U>> int compare(T a, U b)
    {
        if (a == null) {
            if (b == null) return 0;
            else return -1;
        } else {
            if (b == null) return 1;
            return a.compareTo(b);
        }
    }

    public static int compare(long a, long b)
    {
        if (a > b) return 1;
        else if (a == b) return 0;
        else return -1;
    }

    public static int compare(int a, int b)
    {
        if (a > b) return 1;
        else if (a == b) return 0;
        else return -1;
    }

    public static void verify(boolean exp)
    {
        boolean ret = exp;
        assert ret;
    }

    public static void verify(Object exp)
    {
        Object ret = exp;
        assert ret != null;
    }

    public static void unimplemented(String what)
    {
        fatal("to be implemented: " + what);
    }

    public static String format(long l)
    {
        return String.format("%1$,d", l);
    }

    public static String formatSize(long l)
    {
        if (l < C.KB) {
            return l == 1 ? "1 byte" : String.valueOf(l) + " bytes";
        } else if (l < C.MB) {
            return formatFrac(l, C.KB, " KB");
        } else if (l < C.GB) {
            return formatFrac(l, C.MB, " MB");
        } else {
            return formatFrac(l, C.GB, " GB");
        }
    }

    /**
     * Formats a byte transfer progress
     * @param done amount of bytes transfered
     * @param total total amount of bytes
     * @return something like "12.4/29.1 MB"
     */
    public static String formatProgress(long done, long total)
    {
        if (total < C.KB) {
            return done + "/" + total + (total == 1 ? " byte" : " bytes");
        } else if (total < C.MB) {
            return formatFrac(done, C.KB, "") + "/" + formatFrac(total, C.KB, " KB");
        } else if (total < C.GB) {
            return formatFrac(done, C.MB, "") + "/" + formatFrac(total, C.MB, " MB");
        } else {
            return formatFrac(done, C.GB, "") + "/" + formatFrac(total, C.GB, " GB");
        }
    }

    private static String formatFrac(long l, long base, String unit)
    {
        StringBuilder sb = new StringBuilder();
        sb.append(l / base);
        if (sb.length() < 3) {
            boolean one = sb.length() == 1;
            long frac = (l % base) * 1000 / base;
            frac /= one ? 10 : 100;
            if (frac != 0) {
                sb.append('.');
                sb.append(one ? String.format("%1$02d", frac) : frac);

                // remove trailing 0s
                int len = sb.length();
                while (sb.charAt(--len) == '0') sb.setLength(len);
            }
        }
        sb.append(unit);
        return sb.toString();
    }

    /**
     * @param bytes
     * @param interval in msec
     * @return "---" if interval == 0
     */
    public static String formatBandwidth(long bytes, long interval)
    {
        if (interval == 0) return "---";
        long l = bytes * C.SEC / interval;

        if (l < 1 * C.KB) return "~0 KB/s";

        return formatSize(l) + "/s";
    }

    public static String formatAbsoluteTime(long l)
    {
        long now = System.currentTimeMillis();
        long beginOfToday = (now / C.DAY) * C.DAY;
        long beginOfYesterday = beginOfToday - C.DAY;
        long beginOfThisYear = (now / C.YEAR) * C.YEAR;

        Date date = new Date(l);
        String strTime = new SimpleDateFormat("h:mm a").format(l);
        if (beginOfToday <= l && l < now) {
            return strTime;
        } else if (beginOfYesterday <= l && l < beginOfToday) {
            return "Yesterday " + strTime;
        } else if (beginOfThisYear <= l && l < beginOfYesterday) {
            return new SimpleDateFormat("MMM d").format(date) + " " + strTime;
        } else {
            return new SimpleDateFormat("MMM d, yyyy").format(date) + " " + strTime;
        }
    }

    public static String formatRelativeTimeHiRes(long l)
    {
        if (l < 1 * C.SEC) {
            return l + " ms";
        } else if (l < 1 * C.YEAR) {
            StringBuilder sb = new StringBuilder();
            sb.append(l);
            sb.insert(sb.length() - 3, '.');
            sb.append(" sec");
            return sb.toString();
        } else {
            return "\u221E";
        }
    }

    /**
     * @return the infinite sign if it's longer than a year
     */
    public static String formatRelativeTime(long l)
    {
        long v;
        boolean plural;
        String unit;
        if (l < 1 * C.SEC) {
            v = 1;
            unit = "sec";
            plural = false;
        } else if (l < 1 * C.MIN) {
            v = l / C.SEC;
            unit = "sec";
            plural = false;
        } else if (l < 1 * C.HOUR) {
            v = l / C.MIN;
            unit = "min";
            plural = false;
        } else if (l < 1 * C.DAY) {
            v = l / C.HOUR;
            unit = "hour";
            plural = true;
        } else if (l < 1 * C.WEEK) {
            v = l / C.DAY;
            unit = "day";
            plural = true;
        } else if (l < 1 * C.YEAR) {
            v = l / C.WEEK;
            unit = "week";
            plural = true;
        } else {
            return "\u221E";
        }

        if (!plural || v == 1) return v + " " + unit;
        else return v + " " + unit + 's';
    }

    public static <T> void addAll(List<T> dst, T[] src)
    {
        for (T t : src) dst.add(t);
    }

    private static Set<Class<?>> s_excludes = new HashSet<Class<?>>();
    static {
        s_excludes.add(ExTimeout.class);
        s_excludes.add(ExAborted.class);
        s_excludes.add(ExNoAvailDevice.class);
    }

    private static Class<?>[] s_excludeBases = new Class<?>[] {
        SocketException.class,
    };

    static boolean shouldPrintStackTrace(Throwable e, Class<?> ...excludes)
    {
        for (Class<?> exclude : excludes) {
            if (exclude.isInstance(e)) return false;
        }
        if (s_excludes.contains(e.getClass())) return false;
        for (Class<?> excludeBase : s_excludeBases) {
            if (excludeBase.isInstance(e)) return false;
        }
        return true;
    }

    // avoid stack printing for non-error native exceptions
    // this method is for logging only. Use CLIUtil.e2msg() for UI messages
    public static String e(Throwable e, Class<?> ... excludes)
    {
        if (shouldPrintStackTrace(e, excludes)) {
            return stackTrace2string(e);
        } else {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }



    public static String stackTrace2string(Throwable e)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, false);
        AbstractExWirable.printStackTrace(e, pw);
        return sw.toString();
    }

    public static byte[] toByteArray(long l)
    {
        return ByteBuffer.allocate(8).putLong(l).array();
    }

    public static byte[] toByteArray(int i)
    {
        return ByteBuffer.allocate(4).putInt(i).array();
    }

    private static final char[] VALID_EMAIL_CHARS =
        new char[] { '.', '!', '#', '$', '%', '&', '\'', '*', '+', '-', '/',
                     '=', '?', '^', '_', '`', '{', '|', '}', '~' };

    // the caller should throw ExInvalidCharacter if exception is needed
    private static boolean isValidEmailAddressToken(String part)
    {
        if (part.isEmpty()) return false;
        for (int i = 0; i < part.length(); i++) {
            char ch = part.charAt(i);
            if (ch >= 128) return false;    // must be ASCII
            if (Character.isLetterOrDigit(ch)) continue;
            boolean isValid = false;
            for (char valid : VALID_EMAIL_CHARS) {
                if (ch == valid) { isValid = true; break; }
            }
            if (!isValid) return false;
        }
        return true;
    }

    public static boolean isASCII(String str)
    {
        for (int i = 0; i < str.length(); i++) {
            if (!isASCII(str.charAt(i))) return false;
        }
        return true;
    }

    public static boolean isASCII(char ch)
    {
        return (ch >> 7) == 0;
    }

    // the caller should throw ExInvalidCharacter if exception is needed
    public static boolean isValidPassword(char[] passwd)
    {
        for (int i = 0; i < passwd.length; i++) {
            if (!isASCII(passwd[i])) return false;
        }

        return true;
    }

    public static boolean isValidEmailAddress(String email)
    {
        boolean hasAt = false;
        for (int i = 0; i < email.length(); i++) {
            if (email.charAt(i) == '@') {
                if (!hasAt) hasAt = true;
                else return false;
            }
        }
        if (!hasAt) return false;

        String[] tokens = email.split("@");
        if (tokens.length != 2) return false;
        if (!isValidEmailAddressToken(tokens[0])) return false;
        if (!isValidEmailAddressToken(tokens[1])) return false;

        // the domain name must have one dot or more
        String domain = tokens[1];
        boolean hasDot = false;
        for (int i = 0; i < domain.length(); i++) {
            if (domain.charAt(i) == '.') {
                hasDot = true;
                break;
            }
        }

        if (!hasDot || domain.charAt(0) == '.' ||
                domain.charAt(domain.length() - 1) == '.') {
            return false;
        }

        return true;
    }

    public static int execForeground(String ...cmds) throws IOException
    {
        return execForeground(null, cmds);
    }

    public static int execForegroundNoLogging(OutArg<String> output, String ... cmds)
            throws IOException
    {
        return execForeground(false, output, cmds);
    }

    public static int execForeground(OutArg<String> output, String ... cmds) throws IOException
    {
        return execForeground(true, output, cmds);
    }

    /**
     * @return the process's exit code
     */
    private static int execForeground(boolean logging, OutArg<String> output, String ... cmds)
            throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(cmds);
        if (logging) l.debug("execForeground: " + pb.command());
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        InputStream is = new BufferedInputStream(proc.getInputStream());
        StringBuilder sb = output == null ? null : new StringBuilder();
        byte[] bs = new byte[1024];
        while (true) {
            int read = is.read(bs);
            if (read == -1) break;
            String temp = new String(bs, 0, read);
            if (output != null) sb.append(temp);
            if (logging) l.debug("command '" + cmds[0] + "' output:\n" + temp);
        }

        if (output != null) output.set(sb.toString());

        try {
            return proc.waitFor();
        } catch (InterruptedException e) {
            Util.fatal(e);
            return 0;
        }
    }

    public static Process execBackground(String ... cmds) throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(cmds);
        l.debug("execBackground: " + pb.command());

        Process proc = pb.start();
        proc.getInputStream().close();
        proc.getOutputStream().close();
        proc.getErrorStream().close();
        return proc;
    }

    public static void checkPB(boolean has, Class<?> c) throws ExProtocolError
    {
        if (!has) throw new ExProtocolError(c);
    }

    public static void writeMessage(DataOutputStream os, int magic, byte[] bs)
        throws IOException
    {
        os.writeInt(magic);
        os.writeInt(bs.length);
        os.write(bs);
        os.flush();
    }

    // return the total size sent including headers
    public static int writeMessage(DataOutputStream os, int magic, byte[][] bss)
        throws IOException
    {
        int size = 0;
        for (byte[] bs : bss) size += bs.length;

        os.writeInt(magic);
        os.writeInt(size);
        // TODO: scatter/gather
        for (byte[] bs : bss) os.write(bs);
        os.flush();

        return size + Integer.SIZE * 2;
    }

    public static byte[] readMessage(DataInputStream is, int magic, int maxSize)
        throws IOException
    {
        int m = is.readInt();
        if (m != magic) {
            throw new IOException("Magic number doesn't match. Expect 0x" +
                    String.format("%1$08x", magic) + " recieved 0x" +
                    String.format("%1$08x", m));
        }
        int size = is.readInt();

        if (size > maxSize) {
            throw new IOException("Message too large (" + size + " > " +
                    maxSize + ")");
        }
        byte[] bs = new byte[size];
        is.readFully(bs);
        return bs;
    }

    /**
     * convert "\\n" and "\\r" in the string to "\n" and "\r"
     */
    public static String realizeControlChars(String input)
    {
        StringBuilder sb = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c != '\\' || i == len - 1) {
                sb.append(c);
            } else {
                char c2 = input.charAt(++i);
                if (c2 == 'n') {
                    sb.append('\n');
                } else if (c2 == 'r') {
                    sb.append('\r');
                } else if (c2 == 't') {
                    sb.append('\t');
                } else {
                    sb.append(c);
                    sb.append(c2);
                }
            }
        }

        return sb.toString();
    }

    public static void setDefaultUncaughtExceptionHandler()
    {
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e)
                    {
                        SVClient.logSendDefectSyncIgnoreErrors(true,
                                "uncaught exception from " + t.getName() +
                                        ". program exits now.", e);
                        // must abort the process as the abnormal thread can
                        // no longer run properly
                        fatal(e);
                    }
                }
            );
    }

    private static final String BRANCH_STR = "-ConflictBranch-";
    private static final char BRANCH_STR_UNIQUE_CHAR = '-';

    // Given a file name "abc.def", the returned value will be in the format of
    // abc-ConflictBranch-N.def, where N == kidx.getInt().
    // If the above name already exists, one or more '-'s will be appended after N.
    //
    public static String makeBranchConvenientName(String name, KIndex kidx,
            String[] sortedSiblings)
    {
        assert !kidx.equals(KIndex.MASTER);

        String first, second;
        int dotPos = name.lastIndexOf('.');
        if (dotPos <= 0) {
            // the dot doesn't exist or the last dot is the first char
            first = name;
            second = "";
        } else {
            first = name.substring(0, dotPos);
            second = name.substring(dotPos);
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (true) {
            sb.delete(0, Integer.MAX_VALUE);
            sb.append(first);
            sb.append(BRANCH_STR);
            sb.append(kidx.getInt());
            for (int i = 0; i < count; i++) sb.append(BRANCH_STR_UNIQUE_CHAR);
            sb.append(second);

            String str = sb.toString();
            if (Arrays.binarySearch(sortedSiblings, str) < 0) {
                return str;
            }

            count++;
        }
    }

    public static String hexEncode(byte[] bs, int offset, int length)
    {
        assert offset + length <= bs.length;

        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = offset; i < length; i++) {
            sb.append(String.format("%1$02x", bs[i]));
        }

        return sb.toString();
    }

    public static String hexEncode(byte[] bs)
    {
        return hexEncode(bs, 0, bs.length);
    }

    public static byte[] hexDecode(String str) throws ExFormatError
    {
        return hexDecode(str, 0, str.length());
    }

    public static byte[] hexDecode(String str, int start, int end)
        throws ExFormatError
    {
        int len = end - start;
        if (len % 2 != 0) throw new ExFormatError("wrong length");
        len >>= 1;

        byte[] bs = new byte[len];
        for (int i = 0; i < len; i++) {
            try {
                String pos = str.substring((i << 1) + start, (i << 1) + 2 + start);
                // Byte.parseByte() can't handle MSB==1
                bs[i] = (byte) Integer.parseInt(pos, 16);
            } catch (NumberFormatException e) {
                throw new ExFormatError(str + " contains invalid charactor");
            }
        }

        return bs;
    }

    // all case conversions throughout the system shall use this method
    public static String toEnglishLowerCase(String src)
    {
        return src.toLowerCase(Locale.ENGLISH);
    }

    public static int compare(InetSocketAddress a1, InetSocketAddress a2)
    {
        int comp = a1.getPort() - a2.getPort();
        if (comp != 0) return comp;
        byte[] b1 = a1.getAddress().getAddress();
        byte[] b2 = a2.getAddress().getAddress();
        for (int i = 0; i < b1.length; i++) {
            comp = b1[i] - b2[i];
            if (comp != 0) return comp;
        }
        return 0;
    }

    public static void deleteOldHeapDumps()
    {
        File[] heapDumps = new File(absRTRoot()).listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File arg0, String arg1) {
                    return arg1.endsWith(C.HPROF_FILE_EXT);
                }
            });
        if (heapDumps == null) {
            l().error("rtRoot not found.");
            return;
        }
        for (File heapDumpFile : heapDumps) {
            l().debug("Deleting old heap dump: " + heapDumpFile);
            deleteOrOnExit(heapDumpFile);
            heapDumpFile.delete();
        }
    }

    public static class FileName
    {
        public String base;
        public String extension;
    }
    /**
     * Splits a file name into name and extension
     * Ensures that the name part is not empty
     * This is important to correctly append information to the name
     * e.g: so that a file named ".test" gets renamed to ".test (2)" rather than " (2).test"
     *
     * Sample output:
     * given "abc.def", returns "abc" and ".def"
     * given "abcdef", returns "abcdef" and ""
     * given ".def", returns ".def" and ""
     */
    public static FileName splitFileName(String name)
    {
        FileName result = new FileName();

        int dot = name.lastIndexOf('.');
        if  (dot < 1) {
            result.base = name;
            result.extension = "";
        } else {
            result.base = name.substring(0, dot);
            result.extension = name.substring(dot);
        }

        return result;
    }

    private static final Pattern NEXT_NAME_PATTERN =
        Pattern.compile("(.*)\\(([0-9]+)\\)$");

    /**
     * Use to generate the next device name by incrementing the version number of name.
     * Given a name, it returns "<name>  (<next_ver>)"
     *
     * e.g.: given "macbook pro" -> "macbook pro (2)"
     *       given "myponey (3)" -> "myponey (4)"
     *
     * @param name String that needs to be incremented.
     */
    public static String newDeviceName(String name)
    {
        Matcher m = NEXT_NAME_PATTERN.matcher(name);
        String prefix;
        int num;
        if (m.find()) {
            prefix = m.group(1);
            try {
                num = Integer.valueOf(m.group(2)) + 1;
            } catch (NumberFormatException e) {
                prefix  = name + " ";
                num = 2;
            }
        } else {
            prefix  = name + " ";
            num = 2;
        }
        return prefix + "(" + num + ")";
    }

    /**
     * given "abc.def", returns "abc (2).def";
     * given "abc (5).def", returns "abc (6).def"
     * given ".def", returns ".def (2)"
     *
     * N.B. because AeroFS object names are case insensitive, caller must use
     * case insensitive comparison
     *
     * @param name the string to be inserted between the main part of
     *   file name and the index number
     *
     * TODO avoid path length overflow
     */
    public static String newNextFileName(String name)
    {
        FileName file = splitFileName(name);

        // find the pattern of "(N)" at the end of the main part
        Matcher m = NEXT_NAME_PATTERN.matcher(file.base);
        String prefix;
        int num;
        if (m.find()) {
            prefix = m.group(1);
            try {
                num = Integer.valueOf(m.group(2)) + 1;
            } catch (NumberFormatException e) {
                // If the number can't be parsed because it's too large, it's probably not us who
                // generated that number. In this case, add a new number after it.
                prefix = file.base + " ";
                num = 2;
            }
        } else {
            prefix = file.base + " ";
            num = 2;
        }

        return prefix + '(' + num + ')' + file.extension;
    }

    private static final Charset CHARSET_UTF = Charset.forName("UTF-8");

    public static byte[] string2utf(String str)
    {
        return str.getBytes(CHARSET_UTF);
    }

    public static String utf2string(byte[] utf)
    {
        return new String(utf, CHARSET_UTF);
    }

    /**
     * convert a C char array or wide char array to string, assuming the input
     * is null terminated and contains ASCII chars only.
     */
    public static String cstring2string(byte[] cstr, boolean wide)
    {
        int step = wide ? 2 : 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cstr.length; i += step) {
            if (cstr[i] == 0) break;
            sb.append((char) cstr[i]);
        }
        return sb.toString();
    }

    public static String urlEncode(String str)
    {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw fatal(e);
        }
    }

    public static ByteArrayOutputStream writeDelimited(GeneratedMessageLite pb)
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream(
                pb.getSerializedSize() + Integer.SIZE / Byte.SIZE);
        try {
            pb.writeDelimitedTo(os);
        } catch (IOException e) {
            fatal(e);
        }
        return os;
    }

    public static Category l()
    {
        return l;
    }

    public static void halt()
    {
        Integer i = new Integer(0);
        synchronized (i) { Util.waitUninterruptable(i); }
    }

    /**
     * TODO consider thread safety. May use thread local.
     */
    private static Random s_rand;
    public static Random rand()
    {
        if (s_rand == null) s_rand = new Random();
        return s_rand;
    }

    /**
     * the method runs ITry in a new thread
     */
    public static void exponentialRetryNewThread(final String name, final Callable<Void> call,
            final Class<?> ... excludes)
    {
        startDaemonThread("expo.retry." + name, new Runnable() {
            @Override
            public void run()
            {
                exponentialRetry(name, call, excludes);
            }
        });
    }

    /**
     * @param excludes exceptions for which stacktraces should not be printed
     */
    public static void exponentialRetry(String name, Callable<Void> call, Class<?> ... excludes)
    {
        long interval = Param.EXP_RETRY_MIN_DEFAULT;
        while (true) {
            try {
                call.call();
                break;

            } catch (RuntimeException e) {
                // we tolerate no runtime exceptions
                throw e;

            } catch (Exception e) {
                l.warn(name + ". expo wait " + interval + ": " + e(e, excludes));
                sleepUninterruptable(interval);
                interval = Math.min(interval * 2, Param.EXP_RETRY_MAX_DEFFAULT);
            }
        }
    }

    public static @Nonnull String crc32(@Nonnull String name)
    {
        return crc32(name.getBytes());
    }

    public static String crc32(byte[] bytes)
    {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return Long.toHexString(crc.getValue());
    }

    public static byte[] concatenate(byte[] b1, byte[] b2)
    {
        byte[] ret = new byte[b1.length + b2.length];
        System.arraycopy(b1, 0, ret, 0, b1.length);
        System.arraycopy(b2, 0, ret, b1.length, b2.length);
        return ret;
    }

    public static SID getRootSID(String user)
    {
        MessageDigest md = SecUtil.newMessageDigestMD5();
        md.update(Util.string2utf(user));
        return new SID(md.digest(C.ROOT_SID_SALT));
    }

    public static void initDriver(String logFileName)
    {
        // init driver
        OSUtil.get().loadLibrary("aerofsd");
        switch (Logger.getRootLogger().getLevel().toInt()) {
        case Level.ERROR_INT:
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LERROR);
            break;
        case Level.WARN_INT:
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LWARN);
            break;
        case Level.DEBUG_INT:
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LDEBUG);
            break;
        case Level.INFO_INT:
        default:
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LINFO);
            break;
        }
    }

    /**
     * Test bit values of {@code flags}
     */
    public static boolean test(int flags, int bits)
    {
        return (flags & bits) != 0;
    }

    /**
     * Set bits in {@code bits} to @{code flags}
     * @return the new flags
     */
    public static int set(int flags, int bits)
    {
        return flags | bits;
    }

    /**
     * Unset bits in {@code bits} from @{code flags}
     * @return the new flags
     */
    public static int unset(int flags, int bits)
    {
        return flags & ~bits;
    }

    /**
     * @return the total number of bytes read
     */
    public static int copy(InputStream is, OutputStream os) throws IOException
    {
        byte[] buf = new byte[Param.FILE_BUF_SIZE];
        int total = 0;
        int len;
        while ((len = is.read(buf)) > 0) {
            os.write(buf, 0, len);
            os.flush();
            total += len;
        }
        return total;
    }

    /**
     * Concatenate all the elements into one string, separated with File.separator
     */
    public static String join(String ... elems)
    {
        if (elems.length == 0) return "";
        StringBuilder sb = new StringBuilder(elems[0]);
        for (int i = 1; i < elems.length; i++) {
            sb.append(File.separatorChar);
            sb.append(elems[i]);
        }
        return sb.toString();
    }

    public static String removeTailingSeparator(String path)
    {
        if (path.length() > 1 && path.charAt(path.length() - 1) == File.separatorChar) {
            return path.substring(0, path.length() - 1);
        } else {
            return path;
        }
    }
}
