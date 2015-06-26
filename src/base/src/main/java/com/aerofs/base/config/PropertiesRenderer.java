/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.config;

import com.aerofs.base.ex.ExBadArgs;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to hold common functions for dealing with Properties objects.
 */
public class PropertiesRenderer
{
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
     * N.B. This rendering doesn't support chaining at all.
     *
     * @throws ExBadArgs when the input properties set is invalid.
     */
    public Properties renderProperties(Properties properties)
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
                String referencedValue = properties.getProperty(variable);

                if (referencedValue == null) {
                    throw new ExBadArgs(
                            "Bad reference variable=" + variable + " (" + key + "=" + value + ")");
                }

                value = value.replace(match, referencedValue);
            }

            properties.setProperty(key, value);
        }
        return properties;
    }
}
