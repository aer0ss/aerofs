package com.aerofs.testlib;

import com.aerofs.defects.Defect;
import com.aerofs.defects.DefectFactory;
import com.aerofs.defects.MockDefects;
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
    @Mock protected Defect _defect;

    @Override
    @Before
    public void initMocks()
    {
        super.initMocks();

        MockDefects.init(_defectFactory, _defect);
    }
}