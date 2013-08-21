package com.aerofs.testlib;

import com.aerofs.base.Loggers;
import com.aerofs.config.ConfigurationProperties;
import com.aerofs.config.DynamicConfiguration;
import com.aerofs.lib.LibParam.EnterpriseConfig;
import com.aerofs.lib.log.LogUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.mockito.MockitoAnnotations;
import org.powermock.modules.testng.PowerMockTestCase;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import static com.aerofs.lib.configuration.ClientConfigurationLoader.PROPERTY_IS_ENTERPRISE_DEPLOYMENT;
import static org.junit.Assert.assertEquals;

public abstract class AbstractTest extends PowerMockTestCase
{
    protected static final Logger l = Loggers.getLogger(AbstractTest.class);

    static {
        LogUtil.enableConsoleLogging();
        // Change to DEBUG if you're writing a test, but keep at NONE otherwise.
        LogUtil.setLevel(LogUtil.Level.NONE);

        DynamicConfiguration.initialize(DynamicConfiguration.builder().build());

        // Initialize ConfigurationProperties to avoid NullPointerException when using BaseParam
        // (for example when instantiating InvitationEmailers).
        ConfigurationProperties.setProperties(new Properties());
    }

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

    /**
     * Helper method to set whether we are in enterprise deployment
     * IMPORTANT: This is a global setting. However, it will be reset to false before each test
     * method.
     */
    protected void setEnterpriseDeployment(boolean value)
    {
        DynamicConfiguration.getInstance().setProperty(PROPERTY_IS_ENTERPRISE_DEPLOYMENT, value);
        assertEquals(value, EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT.get());
    }

    @Before
    public void resetEnterpriseDeployment()
    {
        setEnterpriseDeployment(false);
    }
}