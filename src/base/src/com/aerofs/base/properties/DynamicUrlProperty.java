/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.properties;

import com.netflix.config.DynamicStringProperty;
import org.arrowfs.config.properties.DynamicProperty;

import java.net.MalformedURLException;
import java.net.URL;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang.StringUtils.isNotBlank;

public class DynamicUrlProperty implements DynamicProperty<URL>
{
    private URL cachedValue;

    private final String defaultValue;
    private final DynamicStringProperty delegate;
    private final Runnable callback = new Runnable() {
        @Override
        public void run()
        {
            load();
            propertyChanged();
        }
    };

    public DynamicUrlProperty( final String name, final String defaultValue ) {
        this.delegate = new DynamicStringProperty( name, null );
        this.defaultValue = defaultValue;
        load();
        this.delegate.addCallback( callback );
    }

    private void load() {
        final String propertySource = delegate.getValue();
        if ( propertySource == null ) {
            cachedValue = parse(defaultValue);
            return;
        }

        cachedValue = parse(propertySource);
    }

    private URL parse( final String propertySource ) {
        checkArgument( isNotBlank(propertySource), "propertySource cannot be null or blank" );
        try {
            return new URL(propertySource);
        } catch ( final MalformedURLException e ) {
            throw new IllegalStateException("The value of SP.URL could not be read as a URL, " + this, e);
        }
    }

    protected void propertyChanged() {
    }

    @Override
    public String getName()
    {
        return delegate.getName();
    }

    @Override
    public URL getValue()
    {
        return cachedValue;
    }

    @Override
    public URL get()
    {
        return getValue();
    }

    @Override
    public long getChangedTimestamp()
    {
        return delegate.getChangedTimestamp();
    }

    @Override
    public void addCallback( final Runnable callback )
    {
        if ( callback != null ) {
            delegate.addCallback( callback );
        }
    }
}
