package com.aerofs.testlib;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

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
import org.powermock.modules.testng.PowerMockTestCase;

public abstract class AbstractTest extends PowerMockTestCase
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

    protected static final Logger l = Util.l(AbstractTest.class);

    @Rule
    public TestName _testName = new TestName();

    private Collection<Thread> _threads = new HashSet<Thread>(getAllThreads());

    @Before
    public void beforeAbstractTest()
    {
        l.info("running test " + _testName.getMethodName());
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
    }

    @Before
    public void initMocks()
    {
        MockitoAnnotations.initMocks(this);
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