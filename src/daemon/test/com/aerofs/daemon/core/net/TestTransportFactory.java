/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.net.TransportFactory.Transport;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class TestTransportFactory
{
    @Test
    public void shouldDefineTransportsInCorrectOrder()
    {
        // higher numbers are worse
        assertThat(Transport.ZEPHYR.getRank(), greaterThan(Transport.JINGLE.getRank()));
        assertThat(Transport.JINGLE.getRank(), greaterThan(Transport.LANTCP.getRank()));
    }
}
