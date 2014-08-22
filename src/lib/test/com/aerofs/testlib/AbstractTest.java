package com.aerofs.testlib;

import com.aerofs.defects.AutoDefect;
import com.aerofs.defects.DefectFactory;
import com.aerofs.defects.MockDefects;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import org.junit.Before;
import org.mockito.Mock;

public abstract class AbstractTest extends AbstractBaseTest
{
    static {
        // Change to DEBUG if you're writing a test, but keep at NONE otherwise.
        LogUtil.setLevel(Level.NONE);
    }

    @Mock protected DefectFactory _defectFactory;
    @Mock protected AutoDefect _defect;

    @Override
    @Before
    public void initMocks()
    {
        super.initMocks();

        MockDefects.init(_defectFactory, _defect);
    }

    @Before
    public void setupApproot()
    {
        // N.B. this is needed to run unit tests in IntelliJ. Otherwise, we may trip an assertion
        //   when initializing Cfg because AppRoot is not set.
        // Strangely enough, this doesn't happen when the tests are run from ant.
        //   I have no idea why.
        AppRoot.set("/dummy");
    }
}