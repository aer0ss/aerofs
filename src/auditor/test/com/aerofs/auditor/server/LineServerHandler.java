/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.C;
import com.aerofs.lib.ThreadUtil;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
* A simple server handler that records line-delimited messages received for later checking
*/
class LineServerHandler extends SimpleChannelHandler
{
    static private Logger l = LoggerFactory.getLogger(LineServerHandler.class);
    static final long   DELAY = C.SEC / 10;
    static final long   MAX_ATTEMPTS = 450;

    Queue<String> recd  = new ConcurrentLinkedQueue<String>();

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
    {
        String msg = (String)e.getMessage();
        l.info("LSH R: {}", msg);
        if (msg.equals("bye\n")) {
            e.getChannel().disconnect();
        }
        recd.add(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        e.getCause().printStackTrace();
        Assert.fail();
    }

    /**
     * Block until the expected message has arrived, or 5 seconds, whichever is sooner.
     */
    public void waitForMessage(String expected)
    {
        int attempts = 0;
        while ((attempts < MAX_ATTEMPTS) && (recd.size() < 1)) {
            attempts++;
            ThreadUtil.sleepUninterruptable(DELAY);
        }
        Assert.assertEquals(1, recd.size());
        Assert.assertEquals(expected + '\n', recd.remove());
    }
}
