package com.aerofs.testlib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.LogUtil;
import org.apache.log4j.BasicConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.testng.PowerMockTestCase;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public abstract class AbstractTest extends PowerMockTestCase
{
    private static boolean _loggingInited;

    @BeforeClass
    public static void initLog4J()
    {
        if (!_loggingInited) {
            String enableLoggingProperty = System.getProperty("com.aerofs.test.logging");
            if (isLoggingEnabled(enableLoggingProperty)) {
                BasicConfigurator.configure();
                LogUtil.setLevel("httpclient.wire", LogUtil.Level.INFO);
                LogUtil.setLevel("org.apache.commons.httpclient", LogUtil.Level.INFO);
                LogUtil.setLevel("com.amazonaws", LogUtil.Level.INFO);
            } else {
                LogUtil.disableLog4J();
            }

            _loggingInited = true;
        }
    }

    private static boolean isLoggingEnabled(String logging)
    {
        return !("no".equals(logging) || "false".equals(logging) || "off".equals(logging) || "0".equals(logging));
    }

    protected static final Logger l = Loggers.getLogger(AbstractTest.class);

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