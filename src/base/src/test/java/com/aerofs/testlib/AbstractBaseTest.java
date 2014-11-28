/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.testlib;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.ConfigurationProperties;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

public class AbstractBaseTest
{
    static {
        Loggers.init();
        // Initialize ConfigurationProperties to avoid NullPointerException when using BaseParam
        // (for example when instantiating InvitationEmailers).
        ConfigurationProperties.setProperties(new Properties());
    }

    protected static final Logger l = Loggers.getLogger(AbstractBaseTest.class);

    @Rule
    public TestName _testName = new TestName();

    private Collection<Thread> _threads = Sets.newHashSet(getAllThreads());

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
        List<Thread> list = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) list.add(threads[i]);
        return list;
    }
}
