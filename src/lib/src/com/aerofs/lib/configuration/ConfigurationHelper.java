/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.configuration;

import com.aerofs.base.Loggers;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.util.Properties;
import java.util.Set;

/**
 * Helper class to hold common functions for dealing with configuration files.
 */
public class ConfigurationHelper
{
    private static final Logger l = Loggers.getLogger(ConfigurationHelper.class);

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
}
