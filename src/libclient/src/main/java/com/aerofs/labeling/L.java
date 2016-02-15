/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.labeling;

import com.aerofs.base.Loggers;
import com.aerofs.base.config.BaseProperties;
import com.aerofs.base.config.PropertiesLoader;
import com.aerofs.base.config.PropertiesRenderer;

import java.util.Properties;

/**
 * L stands for "Labeling". We use a single letter as class name for convenience, as many classes
 * refer to it.
 *
 * TODO: merge this in with ConfigurationProperties someday
 *
 * Warning: this class depends on how the desktop clients are provisioned, so _do_not_ use this
 *   class on any server. If you need L.brand(), use SPParam.BRAND instead.
 */
public class L
{
    private static final String DEFAULT_LABELING_FILE = "labeling.properties";

    private static final String PROP_IS_MULTIUSER = "labeling.isMultiuser";
    private static final String PROP_BRAND = "labeling.brand";
    private static final String PROP_PRODUCT = "labeling.product";
    private static final String PROP_PRODUCT_SPACE_FREE_NAME = "labeling.productSpaceFreeName";
    private static final String PROP_PRODUCT_UNIX_NAME = "labeling.productUnixName";
    private static final String PROP_ROOT_ANCHOR_NAME = "labeling.rootAnchorName";
    private static final String PROP_DEFAULT_PORTBASE = "labeling.defaultPortBase";

    // FIXME this is done for the time being for the sake of incremental update.
    //   we should take the static initializer out and make initialization explicit in the end.
    static
    {
        Properties properties = new Properties();
        try {
            properties = new PropertiesLoader()
                    .loadPropertiesFromPwdOrClasspath(DEFAULT_LABELING_FILE);
            properties = new PropertiesRenderer().renderProperties(properties);
        } catch (Exception e) {
            // note that this is expected to occur on servers.
            Loggers.getLogger(L.class).warn("Failed to load labeling properties from {}.",
                    DEFAULT_LABELING_FILE);
        }
        set(properties);
    }

    private static BaseProperties _properties;

    public static void set(Properties properties)
    {
        _properties = new BaseProperties(properties);
    }

    public static boolean isMultiuser() {
        return _properties.getBooleanProperty(PROP_IS_MULTIUSER, false);
    }

    public static String brand() {
        return _properties.getStringProperty(PROP_BRAND, "AeroFS");
    }

    public static String product() {
        return _properties.getStringProperty(PROP_PRODUCT, "AeroFS");
    }

    public static String productSpaceFreeName() {
        return _properties.getStringProperty(PROP_PRODUCT_SPACE_FREE_NAME, "AeroFS");
    }

    public static String productUnixName() {
        return _properties.getStringProperty(PROP_PRODUCT_UNIX_NAME, "aerofs");
    }

    public static String rootAnchorName() {
        return _properties.getStringProperty(PROP_ROOT_ANCHOR_NAME, "AeroFS");
    }

    public static int defaultPortbase() {
        return _properties.getIntProperty(PROP_DEFAULT_PORTBASE, 50193);
    }
}
