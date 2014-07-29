/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.labeling;

import com.aerofs.base.config.PropertiesHelper;
import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/*
 * Sources properties from labeling.properties on the classpath.  Make sure that you update
 * values in labeling*.properties in resource/client/common/ if you edit this file.
 */
class PropertiesLabeling implements ILabeling
{
    private PropertiesHelper _propertiesHelper;
    private Properties properties;

    PropertiesLabeling() {
        _propertiesHelper = new PropertiesHelper();

        try {
            properties = _propertiesHelper.readPropertiesFromPwdOrClasspath("labeling.properties");
        } catch (Exception e) {
            // TODO (MP) yuck. hate this. normal code path should not rely on exceptions for flow control.
            properties = new Properties();
        }

        _propertiesHelper.logProperties(LOGGER, "Labeling properties", properties);
    }

    // TESTING only
    PropertiesLabeling(Properties prop)
    {
        properties = prop;
        _propertiesHelper = new PropertiesHelper();
    }

    @Override
    public boolean isMultiuser()
    {
        return _propertiesHelper.getBooleanWithDefaultValueFromProperties(properties,
                "labeling.isMultiuser", false);
    }

    @Override
    public String brand()
    {
        return properties.getProperty("labeling.brand", "AeroFS");
    }

    @Override
    public String product()
    {
        return properties.getProperty("labeling.product", "AeroFS");
    }

    @Override
    public String productSpaceFreeName()
    {
        return properties.getProperty("labeling.productSpaceFreeName", "AeroFS");
    }

    @Override
    public String productUnixName()
    {
        return properties.getProperty("labeling.productUnixName", "aerofs");
    }

    @Override
    public String rootAnchorName()
    {
        return properties.getProperty("labeling.rootAnchorName", "AeroFS");
    }

    @Override
    public int defaultPortbase()
    {
        return _propertiesHelper.getIntegerWithDefaultValueFromPropertiesObj(properties,
                "labeling.defaultPortBase", 50193);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("brand", brand())
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
