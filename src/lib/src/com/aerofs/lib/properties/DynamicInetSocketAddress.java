/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.properties;

import com.aerofs.base.params.IProperty;
import com.aerofs.config.properties.DynamicProperty;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.netflix.config.DynamicStringProperty;

import java.net.InetSocketAddress;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/*
 * Format: "<host>:<port>"
 *
 * Constructs and caches an InetSocketAddress from a dynamic configuration source.
 */
public class DynamicInetSocketAddress implements DynamicProperty<InetSocketAddress>, IProperty<InetSocketAddress>
{
    private InetSocketAddress cachedValue;

    private final InetSocketAddress defaultValue;
    private final DynamicStringProperty delegate;
    private final Runnable callback = new Runnable() {
        @Override
        public void run()
        {
            load();
            propertyChanged();
        }
    };

    public DynamicInetSocketAddress(final String name, final InetSocketAddress defaultValue)
    {
        this.delegate = new DynamicStringProperty(name, null);
        this.defaultValue = defaultValue;
        load();
        this.delegate.addCallback(callback);
    }

    private void load()
    {
        final String propertySource = delegate.getValue();
        if (propertySource == null) {
            cachedValue = defaultValue;
            return;
        }

        cachedValue = parse(propertySource);
    }

    private InetSocketAddress parse(final String propertySource)
    {
        checkArgument(isNotBlank(propertySource), "propertySource cannot be null or blank");
        final Iterable<String> parts = Splitter.on(":").split(propertySource);
        checkState(Iterables.size(parts) == 2,
                "propertySource in wrong format, <host>:<port> expected");

        final String host = Iterables.get(parts, 0);
        final String portSource = Iterables.get(parts, 1);
        final int port = Integer.parseInt(portSource);

        return InetSocketAddress.createUnresolved(host, port);
    }

    protected void propertyChanged()
    {
    }

    @Override
    public String getName()
    {
        return delegate.getName();
    }

    @Override
    public InetSocketAddress getValue()
    {
        return cachedValue;
    }

    /**
     * This always return an unresolved InetSocketAddress
     */
    @Override
    public InetSocketAddress get()
    {
        return getValue();
    }

    @Override
    public long getChangedTimestamp()
    {
        return delegate.getChangedTimestamp();
    }

    @Override
    public void addCallback(final Runnable callback)
    {
        if (callback != null) {
            delegate.addCallback(callback);
        }
    }
}
