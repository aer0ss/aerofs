/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.labeling;

import com.google.common.base.Objects;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicStringProperty;
import org.apache.commons.configuration.AbstractConfiguration;
import org.arrowfs.config.ArrowConfiguration;
import org.arrowfs.config.sources.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;

class PropertiesLabeling implements ILabeling
{

    final AbstractConfiguration config =
            PropertiesConfiguration.newInstance( newArrayList( "labeling.properties") );

    PropertiesLabeling() {
        LOGGER.debug(ArrowConfiguration.configurationAsMap( config ).toString());
    }

    @Override
    public boolean isStaging()
    {
        return config.getBoolean( "labeling.isStaging" );
    }

    @Override
    public boolean isMultiuser()
    {
        return config.getBoolean( "labeling.isMultiuser" );
    }

    @Override
    public String product()
    {
        return config.getString( "labeling.product" );
    }

    @Override
    public String productSpaceFreeName()
    {
        return config.getString( "labeling.productSpaceFreeName" );
    }

    @Override
    public String productUnixName()
    {
        return config.getString( "labeling.productUnixName" );
    }

    @Override
    public String rootAnchorName()
    {
        return config.getString( "labeling.rootAnchorName" );
    }

    @Override
    public int defaultPortbase()
    {
        return config.getInt( "labeling.defaultPortBase" );
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("isStaging", isStaging())
                .add("isMultiuser", isMultiuser())
                .add("product", product())
                .add("productSpaceFreeName", productSpaceFreeName())
                .add("productUnixName", productUnixName())
                .add("rootAnchorName", rootAnchorName())
                .add("defaultPortBase", defaultPortbase())
                .toString();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertiesLabeling.class);

}
