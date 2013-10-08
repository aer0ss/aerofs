package com.aerofs.sp.server;

import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.testlib.AbstractTest;
import org.junit.Assert;
import org.junit.Test;

public class TestIdentitySessionManager extends AbstractTest
{
    @Test
    public void shouldThrowOnBogus() throws Exception
    {
        Exception caught = null;
        try {
            new IdentitySessionManager().getSession("this key won't exist...");
        } catch (Exception e) { caught = e; }
        Assert.assertNotNull(caught);
        Assert.assertTrue( caught instanceof ExExternalAuthFailure);

        try {
            new IdentitySessionManager().authenticateSession("this key won't exist...", 0, null);
        } catch (Exception e) { caught = e; }
        Assert.assertNotNull(caught);
        Assert.assertTrue( caught instanceof ExExternalAuthFailure);
    }

    @Test
    public void shouldBasicFlow() throws Exception
    {
        IdentitySessionManager m = new IdentitySessionManager();
        String sessionNonce= m.createSession(120);
        String delegate = m.createDelegate(sessionNonce, 120);

        Assert.assertNull(m.getSession(sessionNonce));
        Assert.assertNull(m.getSession(sessionNonce));
        Assert.assertNull(m.getSession(sessionNonce));

        m.authenticateSession(delegate, 10, new IdentitySessionAttributes("a", "b", "c"));

        IdentitySessionAttributes s = m.getSession(sessionNonce);
        Assert.assertNotNull(s);
        Assert.assertEquals(s.getEmail(), "a");
        Assert.assertEquals(s.getFirstName(), "b");
        Assert.assertEquals(s.getLastName(), "c");

        Exception caught = null;
        try {
            m.getSession(sessionNonce);
        } catch (Exception e) { caught = e; }
        Assert.assertNotNull(caught);
        Assert.assertTrue(caught instanceof ExExternalAuthFailure);

        try {
            m.authenticateSession(delegate, 100, new IdentitySessionAttributes("a","b","c"));
        } catch (Exception e) { caught = e; }
        Assert.assertNotNull(caught);
        Assert.assertTrue(caught instanceof ExExternalAuthFailure);
    }
}