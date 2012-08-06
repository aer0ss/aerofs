package com.aerofs.daemon.tng.xmpp.netty;

import org.jboss.netty.channel.*;

import java.net.InetSocketAddress;

/**
 * Event sink that delegates all event calls to a {@link MockSinkEventListener}. This listener is
 * where tests may be performed, or scenarios simulated, like a connect attempt
 * failing.
 *
 */
public class MockChannelEventSink extends AbstractChannelSink
{
    private final MockSinkEventListener _listener;

    public MockChannelEventSink(MockSinkEventListener listener)
    {
        _listener = listener;
    }

    @Override
    public void eventSunk(ChannelPipeline pipeline, ChannelEvent e)
        throws Exception
    {
        if (e instanceof MessageEvent) {
            _listener.writeRequested((MessageEvent)e);
        } else if (e instanceof ChannelStateEvent) {
            ChannelStateEvent se = (ChannelStateEvent) e;
            switch (se.getState()) {
            case OPEN:
                if (!Boolean.TRUE.equals(se.getValue())) {
                    _listener.closeRequested(se);
                }
                break;
            case BOUND:
                if (se.getValue() != null) {
                    ((MockChannel)se.getChannel()).setLocalAddress(
                            (InetSocketAddress)se.getValue());
                    _listener.bindRequested(se);
                } else {
                    ((MockChannel)se.getChannel()).setLocalAddress(null);
                    _listener.unbindRequested(se);
                }
                break;
            case CONNECTED:
                if (se.getValue() != null) {
                    ((MockChannel)se.getChannel()).setRemoteAddress(
                            (InetSocketAddress)se.getValue());
                    _listener.connectRequested(se);
                } else {
                    ((MockChannel)se.getChannel()).setRemoteAddress(null);
                    _listener.disconnectRequested(se);
                }
                break;
            case INTEREST_OPS:
                _listener.setInterestOpsRequested(se);
                break;
            default:
                break;
            }
        }
    }

}
