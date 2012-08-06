package com.aerofs.testlib;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.mockito.MockitoAnnotations;

import com.aerofs.lib.Util;

public abstract class AbstractTest
{
    private static boolean _log4JInited;

    @BeforeClass
    public static void initLog4J()
    {
        if (!_log4JInited) {
            String logging = System.getProperty("com.aerofs.test.logging");
            if ("no".equals(logging) || "false".equals(logging) ||
                    "off".equals(logging) || "0".equals(logging)) {
                Logger.getRootLogger().addAppender(new NullAppender());
            } else {
                BasicConfigurator.configure();
                Logger.getLogger("httpclient.wire").setLevel(Level.INFO);
                Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.INFO);
                Logger.getLogger("com.amazonaws").setLevel(Level.INFO);
            }
            _log4JInited = true;
        }
    }


    // TODO: deprecate this oddly named field, yo
    protected final Logger Log;
    protected final Logger l;

    @Rule
    public TestName _testName = new TestName();

    private Collection<Thread> _threads = new HashSet<Thread>(getAllThreads());

    public AbstractTest()
    {
        Log = l = Util.l(getClass());
    }

    @Before
    public void beforeAbstractTest()
    {
        l.info("running test " + _testName.getMethodName());
//        logThreads();
    }

    @After
    public void afterAbstractTest()
    {
        if (l.isDebugEnabled()) {
            List<Thread> threads = getAllThreads();
            for (Thread t : threads) {
                if (_threads.contains(t)) continue;
                l.debug("started thread: " + t);
            }
        }
//        logThreads();
    }

    @Before
    public void initMocks()
    {
        MockitoAnnotations.initMocks(this);
    }

    public static long getRandomSeed()
    {
        // TODO: get seed from system property?
        return 42;
    }


    public void logThreads()
    {
        if (l.isDebugEnabled()) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, false);
            logThreads(pw);
            pw.close();
            int len = sw.getBuffer().length();
            if (len > 0) sw.getBuffer().setLength(len - 1);
            l.debug("threads:\n" + sw);
        }
    }

    public static void logThreads(PrintWriter pw) {
        List<Thread> threads = getAllThreads();
        ThreadGroup root = Thread.currentThread().getThreadGroup();

        final Map<Thread, String> nameMap = new IdentityHashMap<Thread, String>(threads.size());

        Iterator<Thread> it = threads.iterator();
        while (it.hasNext()) {
            Thread t = it.next();
            ThreadGroup group = t.getThreadGroup();
            if (group == null) {
                it.remove();
                continue;
            }
            String name = "";
            while (group != null && group != root) {
                name = group.getName() + '.' + name;
                group = group.getParent();
            }
            name += t.getName();
            nameMap.put(t, name);
        }
        Collections.sort(threads, new Comparator<Thread>() {
            @Override
            public int compare(Thread o1, Thread o2)
            {
                String n1 = nameMap.get(o1);
                String n2 = nameMap.get(o2);
                return n1.compareTo(n2);
            }
        });
        for (Thread t : threads) {
            String name = nameMap.get(t);
            pw.print("  ");
            pw.print(name);
            pw.print('(');
            pw.print(t.getPriority());
            pw.print("): ");
            pw.print(t.getState());
            pw.println();
        }
    }

    private static List<Thread> getAllThreads()
    {
        Thread[] threads;
        int count;
        int guess = Thread.activeCount() + 2;
        while (true) {
            threads = new Thread[guess];
            count = Thread.enumerate(threads);
            if (count < threads.length) break;
            guess *= 2;
            if (guess <= 0) guess = Integer.MAX_VALUE;
        }
        List<Thread> list = new ArrayList<Thread>(count);
        for (int i = 0; i < count; ++i) list.add(threads[i]);
        return list;
    }
}
