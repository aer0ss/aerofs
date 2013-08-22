/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import com.aerofs.base.Loggers;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * Helper class to hold common functions for dealing with Properties objects.
 */
public class PropertiesHelper
{
    private static final Logger l = Loggers.getLogger(PropertiesHelper.class);

    /**
     * This function returns a Properties object that is the union of p1 and p2. It logs a warning
     * for each property set in both p1 and p2. p2 wins conflicts.
     *
     * @param p1
     * @param p2
     * @return Union of p1 and p2.
     */
    public Properties disjointUnionProperties(Properties p1, Properties p2)
    {
        Set<Object> intersection = Sets.intersection(p1.keySet(), p2.keySet());
        if (!intersection.isEmpty()) {
            for (Object sharedKey : intersection) {
                String key = (String) sharedKey;
                String val = p2.getProperty(key);
                l.warn("Key {} set multiple times in configuration files. Setting it to {}", key, val);
            }
        }
        Properties disjointUnion = new Properties();
        disjointUnion.putAll(p1);
        disjointUnion.putAll(p2);
        return disjointUnion;
    }

    /**
     * Convenience function to take the union of three Properties objects. In case of conflicts last
     * setter wins.
     *
     * @param p1 Properties object that loses all conflicts.
     * @param p2 Properties object that wins conflicts with p1, but loses to p3.
     * @param p3 Properties object that wins all conflicts.
     * @return Union of p1, p2 and p3.
     */
    public Properties disjointUnionOfThreeProperties(Properties p1, Properties p2, Properties p3)
    {
        return disjointUnionProperties(disjointUnionProperties(p1, p2), p3);
    }

    /**
     * Retrieves the boolean value corresponding to key from properties. If key has no entry in
     * properties, then returns defaultValue.
     */
    public Boolean getBooleanWithDefaultValueFromProperties(Properties properties, String key,
            Boolean defaultValue)
    {
        String val = properties.getProperty(key);
        return (val == null) ? defaultValue : Boolean.valueOf(val);
    }

    /**
     * Retrieves the integer value corresponding to key from properties. If key has no entry in
     * properties, then returns defaultValue.
     */
    public Integer getIntegerWithDefaultValueFromPropertiesObj(Properties properties, String key,
            Integer defaultValue)
    {
        String val = properties.getProperty(key);
        return (val == null) ? defaultValue : Integer.valueOf(val);
    }

    /**
     * Loads properties from file_name, first looking in the current folder, and if file_name isn't
     * there, looking in the classpath.
     *
     * When parsing the file, it uses parseProperties to parse the properties file. This allows some
     * very simple substitutions (see parseProperties documentation).
     *
     * @return Properties object parsed from the contents of file_name.
     */
    public Properties readPropertiesFromPwdOrClasspath(String file_name)
            throws Exception
    {
        Properties staticProperties = new Properties();
        InputStream propertyStream = null;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            try {
                propertyStream = new File(file_name).toURI().toURL().openStream();
            } catch (Exception e) {
                propertyStream = classLoader.getResourceAsStream(file_name);
            }

            staticProperties.load(propertyStream);
        } catch (Exception e) {
            throw new Exception("Couldn't read file: " + file_name, e);
        } finally {
            if (propertyStream != null) {
                try {
                    propertyStream.close();
                } catch (IOException e) {
                    throw new Exception("fail access: " + file_name);
                }
            }
        }
        return parseProperties(staticProperties);
    }

    /**
     * Prints properties to l with description description.
     */
    public void logProperties(Logger l, String description, Properties properties)
    {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            properties.store(byteArrayOutputStream, description);
            l.info(byteArrayOutputStream.toString("UTF-8"));
        } catch (Exception e) {
            l.warn("Failed to log server configuration with exception " + e.toString());
        }
    }

    /**
     * Read through properties, replacing any values of the form "${key}" with the value
     * corresponding to key in properties. Returns the result of this replacement. Ie
     *
     * labeling.product=AeroFS
     * labeling.rootAnchorName=${labeling.product}
     *
     * Becomes
     *
     * labeling.product=AeroFS
     * labeling.rootAnchorName=AeroFS
     *
     * NB: This parsing doesn't support chaining at all, it literally performs a find/replace.
     */
    public Properties parseProperties(Properties properties)
    {
        String value;
        for (Object keyObj : properties.keySet()) {
            String key = (String)keyObj;
            value = properties.getProperty(key);

            if (value.startsWith("${") && value.endsWith("}")) {
                String keyReferenced = value.substring(2, value.length()-1);
                String valueReferenced = properties.getProperty(keyReferenced);

                properties.setProperty(key, valueReferenced);
            }
        }
        return properties;
    }
}
