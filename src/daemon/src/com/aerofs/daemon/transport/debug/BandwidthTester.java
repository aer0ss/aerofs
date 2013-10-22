/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

public final class BandwidthTester
{
    public enum Mode
    {
        CLIENT,
        SERVER
    }

    public interface BandwidthReportListener
    {
        void onBandwidthCalculated(long kilobytesPerSecond);
    }

    //----------------------------------------------------------------------------------------//

    private static final Logger l = Loggers.getLogger(BandwidthTester.class);

    private static final long BYTES_TO_SEND_COUNT = 10 * C.MB;

    private static final BandwidthReportListener DEFAULT_BANDWIDTH_LISTENER = new BandwidthReportListener()
    {
        @Override
        public void onBandwidthCalculated(long kilobytesPerSecond)
        {
            l.info("received {} kb at a rate of {} kb/sec", kilobytesPerSecond);
        }
    };

    private final class BandwidthTesterClientHandler extends SimpleChannelHandler
    {

    }

    private final class BandwidthTesterServerHandler extends SimpleChannelHandler
    {
        private final Timer _timer;
        private final BandwidthReportListener _listener;

        private long _channelConnectedTime;
        private long _allBytesReceivedTime;
        private long _bytesReceived;

        private BandwidthTesterServerHandler(Timer timer, BandwidthReportListener listener)
        {
            _timer = timer;
            _listener = listener;
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception
        {
            final Channel channel = ctx.getChannel();

            _channelConnectedTime = System.currentTimeMillis();
            _timer.newTimeout(new TimerTask()
            {
                @Override
                public void run(Timeout timeout)
                        throws Exception
                {
                    channel.close();

                    if (_allBytesReceivedTime == 0) {
                        _allBytesReceivedTime = System.currentTimeMillis();
                    }
                }
            }, 10 * C.SEC, TimeUnit.MILLISECONDS);

            super.channelConnected(ctx, e);
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception
        {
            _bytesReceived += ((ChannelBuffer) e.getMessage()).readableBytes();

            if (_bytesReceived == BYTES_TO_SEND_COUNT) {
                _allBytesReceivedTime = System.currentTimeMillis();
            }
        }

        private double getBandwidth()
        {
            checkState(_allBytesReceivedTime != 0);

            return ((_bytesReceived / (double) (_allBytesReceivedTime - _channelConnectedTime)) / 1024);
        }
    }

    public BandwidthTester(Mode mode)
    {
    }

    public void start()
    {

    }
}