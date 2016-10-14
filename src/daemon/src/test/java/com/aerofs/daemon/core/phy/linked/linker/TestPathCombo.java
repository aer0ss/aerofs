package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.ids.SID;
import com.aerofs.lib.Util;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

public class TestPathCombo extends AbstractTest
{
    String pathRoot = Util.join("a", "b", "c");
    final SID sid = SID.generate();

    @Before
    public void setup()
    {
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertIfGivenPathDoesntStartWithAbsRootAnchor()
    {
        new PathCombo(sid, pathRoot, Util.join("b", "c", "d"));
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertIfGivenPathIsShorterThanAbsRootAnchor()
    {
        new PathCombo(sid, pathRoot, Util.join("a", "b"));
    }

    @Test (expected = AssertionError.class)
    public void shouldAssertIfGivenPathDoesntExactlyMatchAbsRootAnchor()
    {
        new PathCombo(sid, pathRoot, Util.join("a", "b", "cd", "e"));
    }

    @Test
    public void shouldNotAssertIfGivenPathIsAbsRootAnchor()
    {
        new PathCombo(sid, pathRoot, pathRoot);
    }

    @Test
    public void shouldNotAssertIfGivenPathIsUnderAbsRootAnchor()
    {
        new PathCombo(sid, pathRoot, Util.join(pathRoot, "d"));
    }
}
