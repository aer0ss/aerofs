package com.aerofs.daemon.core.linker;

import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.Util;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.*;

public class TestPathCombo extends AbstractTest
{
    @Mock CfgAbsRootAnchor cfgAbsRootAnchor;

    String pathRoot = Util.join("a", "b", "c");

    @Before
    public void setup()
    {
        when(cfgAbsRootAnchor.get()).thenReturn(pathRoot);
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertIfGivenPathDoesntStartWithAbsRootAnchor()
    {
        new PathCombo(cfgAbsRootAnchor, Util.join("b", "c", "d"));
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertIfGivenPathIsShorterThanAbsRootAnchor()
    {
        new PathCombo(cfgAbsRootAnchor, Util.join("a", "b"));
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertIfGivenPathDoesntExactlyMatchAbsRootAnchor()
    {
        new PathCombo(cfgAbsRootAnchor, Util.join("a", "b", "cd", "e"));
    }

    @Test
    public void shouldNotAssertIfGivenPathExactlyMatchesAbsRootAnchor()
    {
        new PathCombo(cfgAbsRootAnchor, Util.join(pathRoot, "d"));
    }
}
