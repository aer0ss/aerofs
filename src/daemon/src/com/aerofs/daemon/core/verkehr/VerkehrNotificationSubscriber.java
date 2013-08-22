/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.verkehr;

import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.serverstatus.AbstractConnectionStatusNotifier;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.proto.Common.Void;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriptionListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Map.Entry;

import static com.aerofs.lib.LibParam.Verkehr.VERKEHR_RETRY_INTERVAL;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * Single verkehr connection for all topics the daemon subscribes to
 */
public class VerkehrNotificationSubscriber extends AbstractConnectionStatusNotifier
{
    private static final Logger l = Loggers.getLogger(VerkehrNotificationSubscriber.class);

    private boolean _started = false;
    private final VerkehrSubscriber _subscriber;
    private final Map<String, IVerkehrListener> _subs;

    public interface IVerkehrListener extends IConnectionListener, ISubscriptionListener
    {
        public void onSubscribed();
    }

    @Inject
    public VerkehrNotificationSubscriber(ClientSocketChannelFactory clientSocketChannelFactory)
    {
        VerkehrListener listener = new VerkehrListener();
        ClientFactory factory = new ClientFactory(
                Verkehr.HOST,
                Short.parseShort(Verkehr.SUBSCRIBE_PORT),
                clientSocketChannelFactory,
                new CfgCACertificateProvider(),
                new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL,
                Cfg.db().getLong(Key.TIMEOUT),
                new HashedWheelTimer(),
                listener, listener, sameThreadExecutor());

        _subscriber = factory.create();
        _subs = Maps.newHashMap();
    }

    /**
     * Subscribe to a topic
     * @pre the notification subscriber must not be started
     */
    public void subscribe_(String topic, IVerkehrListener listener)
    {
        // the actual subscription is done in the isConnected callback and VerkehrSubscriber does
        // not offer an easy way to determine in which state it is so for simplicity we restrict
        // the window during which a subscription can be made.
        assert !_started;

        _subs.put(topic, listener);
    }

    public void start_()
    {
        l.info("start vk subscriber");
        _started = true;
        _subscriber.start();
    }

    private final class VerkehrListener implements IConnectionListener, ISubscriptionListener
    {
        VerkehrListener()
        {
        }

        @Override
        public void onConnected()
        {
            for (Entry<String, IVerkehrListener> sub : _subs.entrySet()) {
                final String topic = sub.getKey();
                final IVerkehrListener listener = sub.getValue();
                listener.onConnected();
                addCallback(_subscriber.subscribe_(topic), new FutureCallback<Void>()
                {
                    @Override
                    public void onSuccess(Void v)
                    {
                        listener.onSubscribed();
                    }

                    @Override
                    public void onFailure(Throwable t)
                    {
                        l.warn("fail subscribe vk t:" + topic);
                    }
                });
            }

            // no need for explicit synchronization: the onConnected() and onDisconnected() callback
            // cannot be called simultaneously
            notifyConnected_();
        }

        @Override
        public void onNotificationReceivedFromVerkehr(final String topic,
                @Nullable final byte[] payload)
        {
            IVerkehrListener listener = _subs.get(topic);
            assert listener != null;
            listener.onNotificationReceivedFromVerkehr(topic, payload);
        }

        @Override
        public void onDisconnected()
        {
            // no need for explicit synchronization: the onConnected() and onDisconnected() callback
            // cannot be called simultaneously
            notifyDisconnected_();

            for (Entry<String, IVerkehrListener> sub : _subs.entrySet()) {
                sub.getValue().onDisconnected();
            }
        }
    }
}
