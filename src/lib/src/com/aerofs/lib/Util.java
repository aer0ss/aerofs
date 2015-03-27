package com.aerofs.lib;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.*;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.ex.*;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesError;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.LogLevel;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.GeneratedMessageLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static com.aerofs.lib.FileUtil.deleteOrOnExit;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;
import static com.google.common.collect.Sets.newHashSet;

public abstract class Util
{
    private static final Logger l = Loggers.getLogger(Util.class);

    private Util()
    {
        // private to enforce uninstantiability for this class
    }

    private static boolean s_exceptionsRegistered = false;

    /**
     * Register exception types from lib
     * Modules using the lib module should call this method in a static block in order to get
     * exceptions from the wire converted back to the appropriate types
     */
    public static void registerLibExceptions()
    {
        if (s_exceptionsRegistered) return;

        // Register exception types from lib
        Exceptions.registerExceptionTypes(
                new ImmutableMap.Builder<Type, Class<? extends AbstractExWirable>>().put(
                        Type.DEVICE_ID_ALREADY_EXISTS, ExDeviceIDAlreadyExists.class)
                        .put(Type.ALREADY_INVITED, ExAlreadyInvited.class)
                        .put(Type.UPDATING, ExUpdating.class)
                        .put(Type.INDEXING, ExIndexing.class)
                        .put(Type.NOT_SHARED, ExNotShared.class)
                        .put(Type.PARENT_ALREADY_SHARED, ExParentAlreadyShared.class)
                        .put(Type.CHILD_ALREADY_SHARED, ExChildAlreadyShared.class)
                        .put(Type.DEVICE_OFFLINE, ExDeviceOffline.class)
                        .put(Type.NOT_DIR, ExNotDir.class)
                        .put(Type.NOT_FILE, ExNotFile.class)
                        .put(Type.UI_MESSAGE, ExUIMessage.class)
                        .put(Type.NO_STRIPE_CUSTOMER_ID, ExNoStripeCustomerID.class)
                        .put(Type.NOT_AUTHENTICATED, ExNotAuthenticated.class)

                        // exceptions used by shared folder rules
                        .put(Type.SHARING_RULES_WARNINGS, ExSharingRulesWarning.class)
                        .put(Type.SHARING_RULES_ERROR, ExSharingRulesError.class)

                        // The following exceptions are consumed by Python clients only. No need to
                        // list them here for the time being.
                        /*
                        .put(Type.NO_ADMIN_OR_OWNER, ExNoAdminOrOwner.class)
                        .put(Type.INVALID_EMAIL_ADDRESS, ExInvalidEmailAddress.class)
                        */

                        .build());

        s_exceptionsRegistered = true;
    }

    private static String convertStackTraceToString(StackTraceElement[] stackTrace)
    {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement e : stackTrace) {
            if (e == null) break; // technically this should never happen

            builder.append("\tat ").append(e.toString());
            builder.append(System.getProperty("line.separator"));
        }

        return builder.toString();
    }

    private static String getAllThreadStackTraces(Thread[] threads)
    {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        StringBuilder builder = new StringBuilder();

        // Remove all null values.
        threads = Arrays.stream(threads)
                .filter(t -> (t != null))
                .toArray(Thread[]::new);

        // This will sort threads in ascending order of CPU time used.
        Arrays.sort(threads, (Thread t1, Thread t2) ->
                Long.compare(threadBean.getThreadCpuTime(t1.getId()), threadBean.getThreadCpuTime(t2.getId())));

        for (Thread t : threads) {
            builder.append(t.getId()).append(":").append(t.getName())
                    .append(" [")
                    .append(t.getState())
                    .append("]")
                    .append(". CPU time (in nanosecs): ")
                    .append(threadBean.getThreadCpuTime(t.getId()));
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

    public static String getAllThreadStackTraces()
    {
        return getAllThreadStackTraces(ThreadUtil.getAllThreads());
    }

    public static void logAllThreadStackTraces()
    {
        l.error("==== BEGIN STACKS ====\n{}\n==== END STACKS ====", getAllThreadStackTraces());
    }

    private static Set<Class<?>> s_suppressStackTrace = newHashSet();
    static {
        s_suppressStackTrace.add(ExTimeout.class);
        s_suppressStackTrace.add(ExBadCredential.class);
    }

    private static Class<?>[] s_suppressStackTraceBaseClasses = new Class<?>[] {
        SocketException.class,
    };

    /**
     * Registered additional exception classes whose stack traces should not be printed on logs
     */
    public static void suppressStackTraces(Class<?>... classes)
    {
        Collections.addAll(s_suppressStackTrace, classes);
    }

    // TODO (jP): Remove this method along with Util.e.
    private static boolean shouldPrintStackTrace(Throwable e, Class<?> ...suppressStackTrace)
    {
        for (Class<?> suppress : suppressStackTrace) {
            if (suppress.isInstance(e)) return false;
        }
        if (s_suppressStackTrace.contains(e.getClass())) return false;
        for (Class<?> excludeBase : s_suppressStackTraceBaseClasses) {
            if (excludeBase.isInstance(e)) return false;
        }
        return true;
    }

    /**
     * FIXME(jP): This method should be removed. For logging, please use:
     *      logger.warn("This bad thing happened", e);
     * If you want to suppress a stack trace, use:
     *      logger.info("This ok thing happened", LogUtil.suppress(e));
     *
     * Return the logging string of the exception. The string includes the stack trace for all the
     * exception classses except those defined in s_suppressStackTrace* (see above) and as the
     * suppressStackTrace parameter.
     *
     * @param suppressStackTrace the exception classes of which the stack trace should be
     * suppressed.
     */
    public static String e(Throwable e, Class<?> ... suppressStackTrace)
    {
        if (shouldPrintStackTrace(e, suppressStackTrace)) {
            return Exceptions.getStackTraceAsString(e);
        } else {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    public static String quote(Object o)
    {
        return "\"" + o + "\"";
    }

    /**
     * This method is OBSOLETE. use Preconditions.checkState() instead.
     *
     * This is equivalent to assert, except the expression will always be evaluated
     * (If one was to replace "verify(foo)" with "assert foo" then the foo expression might not be
     * evaluated if assertions are disabled.
     */
    public static void verify(boolean exp)
    {
        assert exp;
    }

    /**
     * Formats a long with commas to group digits
     * e.g.: format(120000) -> 120,000
     */
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
     * @param interval in msec
     * @return "---" if interval == 0
     */
    public static String formatBandwidth(long bytes, long interval)
    {
        if (interval == 0) return "---";
        long l = bytes * C.SEC / interval;

        if (l < 1 * C.KB) return S.LBL_IDLE;

        return formatSize(l) + "/s";
    }

    /**
     * Formats a timestamp to be displayed to the user.
     *
     * Note: consider using:
     *
     *   private static final PrettyTime PRETTY_TIME = new PrettyTime();
     *   ...
     *   PRETTY_TIME.format(new Date(time))
     *
     * The code above results in a more user-friendly time, at the cost of precision (the user will
     * see "2 weeks ago", for example, rather than "Sep 19, 2012, 11:32 AM"
     *
     * N.B. For test purpose, override System.currentTimeMillis() to control the time and
     *   TimeZone.getDefault() to control the timezone
     */
    public static String formatAbsoluteTime(long l)
    {
        long now = System.currentTimeMillis();
        long offset = TimeZone.getDefault().getOffset(now);
        long beginOfToday = (((now + offset) / C.DAY) * C.DAY) - offset;
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
     *
     * Note: consider using the PrettyTime library instead:
     *   private static final PrettyTime PRETTY_TIME = new PrettyTime();
     *   ...
     *   PRETTY_TIME.format(new Date(time))
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
            return "\u221E";  // 'infinity' symbol
        }

        return (!plural || v == 1) ? v + " " + unit
                                   : v + " " + unit + 's';
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

    /**
     * If the caller hopes to throw an exception on invalid emails, use ExInvalidEmailAddress
     */
    public static boolean isValidEmailAddress(String email)
    {
        if (email.isEmpty()) return false;

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
        if (!BaseUtil.isValidEmailAddressToken(tokens[0])) return false;
        if (!BaseUtil.isValidEmailAddressToken(tokens[1])) return false;

        // the domain name must have one dot or more
        String domain = tokens[1];
        boolean hasDot = false;
        for (int i = 0; i < domain.length(); i++) {
            if (domain.charAt(i) == '.') {
                hasDot = true;
                break;
            }
        }

        return hasDot
                && domain.charAt(0) != '.'
                && domain.charAt(domain.length() - 1) != '.';
    }

    public static void checkPB(boolean has, Class<?> c) throws ExProtocolError
    {
        if (!has) throw new ExProtocolError(c);
    }

    public static void checkMatchingSizes(int... sz) throws ExProtocolError
    {
        Preconditions.checkState(sz.length > 1);
        int n = sz[0];
        for (int i = 1; i < sz.length; ++i) if (n != sz[i]) throw new ExProtocolError();
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
        File[] heapDumps = new File(absRTRoot()).listFiles(
                (arg0, arg1) -> arg1.endsWith(LibParam.HPROF_FILE_EXT));
        if (heapDumps == null) {
            l.error("rtRoot not found.");
            return;
        }
        for (File heapDumpFile : heapDumps) {
            l.debug("Deleting old heap dump: " + heapDumpFile);
            deleteOrOnExit(heapDumpFile);
            heapDumpFile.delete();
        }
    }

    private static final Pattern NEXT_NAME_PATTERN =
        Pattern.compile("(.*)\\(([0-9]+)\\)$");

    /**
     * Generate the next name for the object by incrementing its version number.
     * e.g.: given ("macbook pro", "") -> "macbook pro (2)"
     *       given ("abc", ".def") -> "abc (2).def"
     *
     * @param base String before the version number.
     * @param extension String after the version number.
     * @return String of the format "<base>  (<next_ver>)<extension>"
     */
    public static String nextName(String base, String extension)
    {
        // find the pattern of "(N)" at the end of the main part
        Matcher m = NEXT_NAME_PATTERN.matcher(base);
        String prefix;
        int num;
        if (m.find()) {
            prefix = m.group(1);
            try {
                num = Integer.valueOf(m.group(2)) + 1;
            } catch (NumberFormatException e) {
                // If the number can't be parsed because it's too large, it's probably not us who
                // generated that number. In this case, add a new number after it.
                prefix = base + " ";
                num = 2;
            }
        } else {
            prefix = base + " ";
            num = 2;
        }

        return prefix + '(' + num + ')' + extension;
    }

    /**
     * Split the file name into base and extension based on the position of the dot character,
     * and then return nextName(base, extension).
     */
    public static String nextFileName(String fileName)
    {
        FileName fn = FileName.fromBaseName(fileName);
        return nextName(fn.base, fn.extension);
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
            throw SystemUtil.fatal(e);
        }
    }

    public static ByteArrayOutputStream writeDelimited(GeneratedMessageLite pb)
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream(pb.getSerializedSize() + C.INTEGER_SIZE);
        try {
            pb.writeDelimitedTo(os);
        } catch (IOException e) {
            SystemUtil.fatal(e);
        }
        return os;
    }

    public static ThreadLocalRandom rand()
    {
        return ThreadLocalRandom.current();
    }

    /**
     * runs a retryable Callable in a new thread (allows you to specify all parameters)
     */
    public static void exponentialRetryNewThread(
            final String name,
            final long initialRetryInterval,
            final long maxRetryInterval,
            final Callable<Void> call,
            final Class<?> ... excludes)
    {
        ThreadUtil.startDaemonThread(name,
                () -> exponentialRetry(name, initialRetryInterval, maxRetryInterval, call, excludes));
    }

    public static void exponentialRetry(String name, Callable<Void> call, Class<?>... excludes)
    {
        exponentialRetry(name, LibParam.EXP_RETRY_MIN_DEFAULT, LibParam.EXP_RETRY_MAX_DEFAULT, call, excludes);
    }

    /**
     * @param excludes exceptions for which stacktraces should not be printed
     */
    public static void exponentialRetry(
            String name,
            long initialRetryInterval,
            long maxRetryInterval,
            Callable<Void> call,
            Class<?> ... excludes)
    {
        long retryInterval = initialRetryInterval;

        while (true) {
            try {
                call.call();
                break;

            } catch (RuntimeException e) {
                // we tolerate no runtime exceptions
                throw e;

            } catch (Exception e) {
                l.warn("{} expo wait:{} cause:{}", name, retryInterval, e(e, excludes));
                ThreadUtil.sleepUninterruptable(retryInterval);
                retryInterval = Math.min(retryInterval * 2, maxRetryInterval);
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
        // It's helpful for a CRC32 to actually describe 32 bits. It's very
        // easy to see a 7-char CRC and forget that there are leading zeros.
        return String.format("%08x", crc.getValue());
    }

    public static void initDriver(String logFileName, String rtRoot)
    {
        OSUtil.get().loadLibrary("aerofsd");
        Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        if (rootLogger.isTraceEnabled() || rootLogger.isDebugEnabled()) {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LDEBUG);
        } else if (rootLogger.isInfoEnabled()) {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LINFO);
        } else if (rootLogger.isWarnEnabled()) {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LWARN);
        } else {
            Driver.initLogger_(rtRoot, logFileName, LogLevel.LERROR);
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

    public static String returnNullIfEmpty(String value) {
        if (Strings.isNullOrEmpty(value)) {
            return null;
        } else {
            return value;
        }
    }
}
