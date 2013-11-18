package com.aerofs.testlib;

import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;

public abstract class AbstractTest extends AbstractBaseTest
{
    static {
        // Change to DEBUG if you're writing a test, but keep at NONE otherwise.
        LogUtil.setLevel(Level.NONE);
    }
}