/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.dryad;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import java.util.Set;

import static java.lang.String.format;

public abstract class Blacklist
{
    private static final Logger l = LoggerFactory.getLogger(Blacklist.class);

    private final String _source;
    private final Set<String> _items = Sets.newHashSet();

    protected Blacklist(String source)
    {
        _source = source;
    }

    public void load()
    {
        _items.clear();

        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(_source), "UTF-8");

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("#")) continue;
                _items.add(line);
            }
        } catch (FileNotFoundException e) {
            // warn, but move on assuming nothing is blacklisted
            l.warn("blacklist file not found: \"{}\"", _source);
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    public void throwIfBlacklisted(String value)
            throws BlacklistedException
    {
        if (_items.contains(value)) {
            throw new BlacklistedException(
                    format("%s is blacklisted in %s", value, _source));
        }
    }

    // FIXME (AT): using subtype to distinguish the two blacklists isn't a good idea
    //   Look into using name-based methods to inject these objects.
    @Singleton
    public static class UserBlacklist extends Blacklist
    {
        @Inject
        public UserBlacklist(DryadProperties properties)
        {
            super(properties.getProperty(DryadProperties.BLACKLIST_USERS, ""));
            load();
        }
    }

    @Singleton
    public static class DeviceBlacklist extends Blacklist
    {
        @Inject
        public DeviceBlacklist(DryadProperties properties)
        {
            super(properties.getProperty(DryadProperties.BLACKLIST_DEVICES, ""));
            load();
        }
    }

    public static class BlacklistedException extends Exception
    {
        private static final long serialVersionUID = 0L;

        public BlacklistedException(String message)
        {
            super(message);
        }
    }
}
