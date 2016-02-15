/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.injectable.TimeSource;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import static com.aerofs.lib.FileUtil.createNewFile;

class RecentExceptions
{
    private static final Logger l = Loggers.getLogger(RecentExceptions.class);

    // How many recent exceptions we keep in the rex file. If you change this, make sure to update
    // the corresponding test.
    private static final int RECENT_EXCEPTIONS_COUNT = 10;

    // An exception seen in a shorter period of time than this interval will be considered recent:
    public static final long DEFAULT_INTERVAL = 6 * C.HOUR;

    private final long _interval;
    private final File _rexFile;
    private final Map<String, Long> _exceptions = Maps.newHashMap(); // protected by 'this'
    private final TimeSource _timeSource;

    public RecentExceptions(String programName, String rtroot, long interval, TimeSource timeSource)
    {
        _rexFile = new File(rtroot, programName + "-" + ClientParam.RECENT_EXCEPTIONS);
        _interval = interval;
        _timeSource = timeSource;

        loadFromFiles();
    }

    private void loadFromFiles()
    {
        try {
            if (!_rexFile.exists()) {
                createNewFile(_rexFile);
            }

            _exceptions.putAll(read(_rexFile));
        } catch (Throwable e) {
            l.warn("reading rex file failed: ", e);
        }
    }

    /**
     * Returns whether a given exception is a recent exception
     */
    public synchronized boolean isRecent(Throwable t)
    {
        try {
            String checksum = Exceptions.getChecksum(t);
            Long timestamp = _exceptions.get(checksum);
            return (timestamp != null && _timeSource.getTime() - timestamp < _interval);
        } catch (Throwable e) {
            l.warn("rex is recent failed: ", e);
            return false;
        }
    }

    /**
     * Adds an exception to the list of recent exceptions
     */
    public synchronized void add(Throwable t)
    {
        try {
            _exceptions.put(Exceptions.getChecksum(t), _timeSource.getTime());

            // Remove older entries
            Set<Entry<String, Long>> entries = _exceptions.entrySet();
            while (_exceptions.size() > RECENT_EXCEPTIONS_COUNT) {
                entries.remove(Collections.min(entries, _comparator));
            }

            write(_exceptions, _rexFile);

        } catch (Throwable e) {
            l.warn("rex add failed: ", e);
        }
    }

    /**
     * Clear the exceptions. Mostly useful for testing purposes
     */
    public synchronized void clear() throws IOException
    {
        _exceptions.clear();
        write(_exceptions, _rexFile);
    }

    /**
     * Compares the entries in the exception map according to their timestamps
     */
    private final Comparator<Entry<String, Long>> _comparator = new Comparator<Entry<String, Long>>()
    {
        @Override
        public int compare(Entry<String, Long> a, Entry<String, Long> b)
        {
            return a.getValue().compareTo(b.getValue());
        }
    };

    /**
     * Reads a map of exception timestamps -> checksums from a file
     * Reads at most RECENT_EXCEPTION_COUNT exceptions
     */
    private static Map<String, Long> read(File file) throws IOException
    {
        // maps exception checksums -> timestamps
        Map<String, Long> exceptions = Maps.newHashMap();

        Properties properties = new Properties();
        properties.load(new FileInputStream(file));
        for (String key : properties.stringPropertyNames()) {
            try {
                exceptions.put(key, Long.parseLong(properties.get(key).toString()));
            } catch (Throwable e) {
                // ignore malformed lines
            }
        }

        return exceptions;
    }

    /**
     * Writes a map of exception checksums -> timestamps to a file.
     * Any previous file content is cleared
     */
    private static void write(Map<String, Long> exceptions, File file) throws IOException
    {
        Properties properties = new Properties();
        for (Entry<String, Long> entry : exceptions.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue().toString());
        }
        properties.store(new FileOutputStream(file), null);
    }
}
