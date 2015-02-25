/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Strings.isNullOrEmpty;

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
    private Properties unionProperties(Properties p1, Properties p2)
    {
        Set<Object> intersection = Sets.intersection(p1.keySet(), p2.keySet());
        if (!intersection.isEmpty()) {
            for (Object sharedKey : intersection) {
                String key = (String) sharedKey;
                String val = p2.getProperty(key);
                l.debug("Key {} set multiple times in configuration files. Setting it to {}", key, val);
            }
        }
        Properties disjointUnion = new Properties();
        disjointUnion.putAll(p1);
        disjointUnion.putAll(p2);
        return disjointUnion;
    }

    /**
     * Merges a list of Properties objects ordered from lowest precedence to highest.
     * @param propsArgs A series of Properties objects, ordered from lowest precedence to highest precedence.
     * @return A Properties object representing the merged value of all of the propsArgs.
     */
    public Properties mergeProperties(Properties... propsArgs)
    {
        Properties merged = new Properties();
        for (Properties props: propsArgs) {
            merged = unionProperties(merged, props);
        }
        return merged;
    }

    /**
     * Retrieves the boolean value corresponding to key from properties. If key has no entry in
     * properties, then returns defaultValue.
     */
    public Boolean getBooleanWithDefaultValueFromProperties(Properties properties, String key,
            Boolean defaultValue)
    {
        String val = properties.getProperty(key);
        return isNullOrEmpty(val) ? defaultValue : Boolean.valueOf(val);
    }

    /**
     * Retrieves the integer value corresponding to key from properties. If key has no entry in
     * properties, then returns defaultValue.
     */
    public Integer getIntegerWithDefaultValueFromPropertiesObj(Properties properties, String key,
            Integer defaultValue)
    {
        String val = properties.getProperty(key);
        return isNullOrEmpty(val) ? defaultValue : Integer.valueOf(val);
    }

    /**
     * Retrieves the integer value corresponding to key from properties. If key has no entry in
     * properties, then returns defaultValue.
     */
    public Long getLongWithDefaultValueFromPropertiesObj(Properties properties, String key,
            Long defaultValue)
    {
        String val = properties.getProperty(key);
        return isNullOrEmpty(val) ? defaultValue : Long.valueOf(val);
    }

    /**
     * Loads properties from filename, first looking in the current folder, and if filename isn't
     * there, looking in the classpath.
     *
     * When parsing the file, it uses parseProperties to parse the properties file. This allows some
     * very simple substitutions (see parseProperties documentation).
     *
     * @return Properties object parsed from the contents of filename.
     * @throws Exception when unable to load filename from both pwd and classpath.
     */
    public Properties readPropertiesFromPwdOrClasspath(String filename)
            throws Exception
    {
        Properties staticProperties = new Properties();
        InputStream propertyStream = null;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            try {
                propertyStream = new File(filename).toURI().toURL().openStream();
            } catch (Exception e) {
                propertyStream = classLoader.getResourceAsStream(filename);
            }

            staticProperties.load(propertyStream);
        } catch (Exception e) {
            throw new Exception("Couldn't read file: " + filename, e);
        } finally {
            if (propertyStream != null) {
                try {
                    propertyStream.close();
                } catch (IOException e) {
                    throw new Exception("fail access: " + filename);
                }
            }
        }

        // TODO (MP) yikes. loading properties should be separate from resolution.
        // This is fine for now since it is only used by labeling, which is going away. When
        // labeling is burned this should be burned as well.
        return parseProperties(staticProperties);
    }

    /**
     * Reads properties from a fully qualified filename. Exceptions are logged and life continues
     * on.
     */
    public static Properties readPropertiesFromFile(String fullyQualifiedFilename)
    {
        Properties staticProperties = new Properties();

        try {
            InputStream propertyStream = new File(fullyQualifiedFilename).toURI().toURL().openStream();
            try {
                staticProperties.load(propertyStream);
            } finally {
                if (propertyStream != null) {
                    propertyStream.close();
                }
            }
        } catch (IOException e) {
            l.warn("Couldn't read file {}: {}", fullyQualifiedFilename, e);
        }
        return staticProperties;
    }

    /**
     * Prints properties to l with description description.
     */
    public void logProperties(Logger l, String description, Properties properties)
    {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            properties.store(byteArrayOutputStream, description);
            l.debug(byteArrayOutputStream.toString("UTF-8"));
        } catch (Exception e) {
            l.error("Failed to log server configuration with exception " + e.toString());
        }
    }

    /**
     * Read through properties, replacing any values of the form "${key}" with the value
     * corresponding to key in properties. Returns the result of this replacement. Ie
     *
     * labeling.product=AeroFS
     * labeling.rootAnchorName=${labeling.product} Product
     *
     * Becomes
     *
     * labeling.product=AeroFS
     * labeling.rootAnchorName=AeroFS Product
     *
     * N.B. This parsing doesn't support chaining at all.
     *
     * @throws ExBadArgs when the input properties set is invalid.
     */
    public Properties parseProperties(Properties properties)
            throws ExBadArgs
    {
        String value;
        for (Object keyObject : properties.keySet()) {
            String key = (String) keyObject;
            value = properties.getProperty(key);

            Pattern pattern = Pattern.compile("\\$\\{([a-z,.]*)\\}");
            Matcher matcher = pattern.matcher(value);

            while (matcher.find()) {
                String match = matcher.group();

                // N.B. bounds are guaranteed here based on the regex used above. Assert to be safe.
                assert(match.length() >= 3);
                String variable = match.substring(2, match.length() - 1);

                // Sub out the variable, if the one it is referencing exists.
                String referenedValue = properties.getProperty(variable);

                if (referenedValue == null) {
                    throw new ExBadArgs(
                            "Bad reference variable=" + variable + " (" + key + "=" + value + ")");
                }

                value = value.replace(match, referenedValue);
            }

            properties.setProperty(key, value);
        }
        return properties;
    }
}
