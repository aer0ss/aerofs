/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server.db;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.DID;
import com.aerofs.devman.server.db.IPAddressDatabase.IPAddress;
import com.aerofs.servlets.lib.db.AbstractJedisTest;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;

public class TestIPAddressDatabase extends AbstractJedisTest
{
    // IP Address Database (ipad... haha).
    private final IPAddressDatabase _ipad = new IPAddressDatabase((getTransaction()));
    private final DID _d = DID.generate();

    private InetAddress _localhost;

    @Before
    public void setupTestIPAddressDatabase()
            throws Exception
    {
        _localhost = InetAddress.getByName("127.0.0.1");
    }

    @Test
    public void testSetIPAddress()
            throws Exception
    {
        getTransaction().begin();
        _ipad.setIPAddress(_d, _localhost);
        IPAddress address = _ipad.getIPAddress(_d);
        getTransaction().commit();

        Assert.assertTrue(address.exists());
        Assert.assertEquals(_localhost, address.get());
    }

    @Test (expected = ExNotFound.class)
    public void testIPAddressNotFound()
            throws Exception
    {
        getTransaction().begin();
        IPAddress address = _ipad.getIPAddress(DID.generate());
        getTransaction().commit();

        Assert.assertFalse(address.exists());

        // Expect this to throw.
        address.get();
    }
}