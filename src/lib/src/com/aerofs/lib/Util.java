package com.aerofs.lib;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.*;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.ex.*;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.LogLevel;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.GeneratedMessageLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
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
                new ImmutableMap.Builder<Type, Class<? extends AbstractExWirable>>()
                        .put(Type.DEVICE_ID_ALREADY_EXISTS, ExDeviceIDAlreadyExists.class)
                        .put(Type.ALREADY_INVITED, ExAlreadyInvited.class)
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
                        .put(Type.CANNOT_INVITE_SELF, ExCannotInviteSelf.class)
                        .put(Type.INVITEE_LIST_EMPTY, ExInviteeListEmpty.class)

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
        l.warn("==== BEGIN STACKS ====\n{}\n==== END STACKS ====", getAllThreadStackTraces(ThreadUtil.getAllThreads()));
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

    // TODO (WW) make it private and clean up LogUtil.
    static boolean shouldPrintStackTrace(Throwable e, Class<?> ...suppressStackTrace)
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
     * Return the logging string of the exception. The string includes the stack trace for all the
     * exception classses except those defined in s_suppressStackTrace* (see above) and as the
     * suppressStackTrace parameter.
     *
     * @param suppressStackTrace the exception classes of which the stack trace should be
     * suppressed.
     *
     * N.B. Use this method for logging only. Use CLIUtil.e2msg() for UI messages
     */
    // avoid stack printing for non-error native exceptions
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
        SystemUtil.fatal("to be implemented: " + what);
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
     */
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
            return "\u221E";
        }

        if (!plural || v == 1) return v + " " + unit;
        else return v + " " + unit + 's';
    }

    public static <T> void addAll(List<T> dst, T[] src)
    {
        for (T t : src) dst.add(t);
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
    public static boolean isValidEmailAddressToken(String part)
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

    /**
     * If the caller hopes to throw an exception on invalid emails, use ExInvalidEmailAddress
     */
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
                    return arg1.endsWith(Param.HPROF_FILE_EXT);
                }
            });
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
            throw SystemUtil.fatalWithReturn(e);
        }
    }

    public static ByteArrayOutputStream writeDelimited(GeneratedMessageLite pb)
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream(
                pb.getSerializedSize() + Integer.SIZE / Byte.SIZE);
        try {
            pb.writeDelimitedTo(os);
        } catch (IOException e) {
            SystemUtil.fatal(e);
        }
        return os;
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
        ThreadUtil.startDaemonThread("expo.retry." + name, new Runnable()
        {
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
                ThreadUtil.sleepUninterruptable(interval);
                interval = Math.min(interval * 2, Param.EXP_RETRY_MAX_DEFAULT);
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

    public static void initDriver(String logFileName)
    {
        OSUtil.get().loadLibrary("aerofsd");

        Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (rootLogger.isTraceEnabled() || rootLogger.isDebugEnabled()) {
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LDEBUG);
        } else if (rootLogger.isInfoEnabled()) {
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LINFO);
        } else if (rootLogger.isWarnEnabled()) {
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LWARN);
        } else {
            Driver.initLogger_(Cfg.absRTRoot(), logFileName, LogLevel.LERROR);
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
    public static long copy(InputStream is, OutputStream os) throws IOException
    {
        byte[] buf = new byte[Param.FILE_BUF_SIZE];
        long total = 0;
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

    /**
     * Derive the name of a shared folder from its Path
     * This is necessary to handle external roots, whose Path are empty and whose name are dervied
     * from the physical folder they are linked too.
     *
     * Classes that need to be unit-tested cannot access Cfg directly...
     */
    public static String sharedFolderName(Path path, CfgAbsRoots absRoots)
    {
        return path.isEmpty() ? new File(absRoots.get(path.sid())).getName() : path.last();
    }
}
