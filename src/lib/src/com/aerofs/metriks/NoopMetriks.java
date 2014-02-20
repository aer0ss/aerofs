/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.metriks;

/**
 * A noop {@link IMetriks} implementation that does nothing.
 */
public class NoopMetriks implements IMetriks
{
    private static final IMetrik NOOP_METRIK = new IMetrik()
    {
        @Override
        public IMetrik addField(String fieldName, Object fieldValue)
        {
            return this;
        }

        @Override
        public void send()
        {
            // noop
        }
    };

    @Override
    public void start()
    {
        // noop
    }

    @Override
    public void stop()
    {
        // noop
    }

    @Override
    public IMetrik newMetrik(String topic)
    {
        return NOOP_METRIK;
    }
}
