/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.labeling;

import com.google.common.base.Objects;
import org.apache.commons.configuration.AbstractConfiguration;
import org.arrowfs.config.ArrowConfiguration;
import org.arrowfs.config.sources.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Lists.newArrayList;

/*
 * Sources properties from labeling.properties on the classpath.  Make sure that you update
 * values in labeling*.properties in resource/client/common/ if you edit this file.
 */
class PropertiesLabeling implements ILabeling
{

    final AbstractConfiguration config =
            PropertiesConfiguration.newInstance( newArrayList( "resources/labeling.properties") );

    PropertiesLabeling() {
        LOGGER.debug(ArrowConfiguration.configurationAsMap( config ).toString());
    }

    @Override
    public boolean isStaging()
    {
        return config.getBoolean( "labeling.isStaging", false );
    }

    @Override
    public boolean isMultiuser()
    {
        return config.getBoolean( "labeling.isMultiuser", false );
    }

    @Override
    public String brand()
    {
        return config.getString( "labeling.brand", "AeroFS" );
    }

    @Override
    public String product()
    {
        return config.getString( "labeling.product", "AeroFS" );
    }

    @Override
    public String productSpaceFreeName()
    {
        return config.getString( "labeling.productSpaceFreeName", "AeroFS" );
    }

    @Override
    public String productUnixName()
    {
        return config.getString( "labeling.productUnixName", "aerofs" );
    }

    @Override
    public String rootAnchorName()
    {
        return config.getString( "labeling.rootAnchorName", "AeroFS" );
    }

    @Override
    public int defaultPortbase()
    {
        return config.getInt( "labeling.defaultPortBase", 50193 );
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("brand", brand())
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
