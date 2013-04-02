/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.ex;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.lib.Param;
import com.aerofs.lib.ProgramInformation;
import com.aerofs.lib.cfg.Cfg;
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

import static com.aerofs.lib.Util.join;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Singleton class to manage recent exceptions
 * We use this to avoid sending the same defects multiple times
 *
 * Note: this class will be used by both the daemon and the gui process to write to the same file.
 * There are potentially some concurrency issues
 */
public class RecentExceptions
{
    private static final Logger l = Loggers.getLogger(RecentExceptions.class);

    // How many recent exceptions we keep in the rex file. If you change this, make sure to update
    // the corresponding test.
    private static final int RECENT_EXCEPTIONS_COUNT = 10;

    // An exception seen in a shorter period of time than this interval will be considered recent:
    private static final long DEFAULT_INTERVAL = 6 * C.HOUR;

    private static RecentExceptions _instance;
    private final long _interval;
    private final File _rexFile;
    private final Map<String, Long> _exceptions = Maps.newHashMap(); // protected by 'this'

    public static RecentExceptions getInstance()
    {
        if (_instance == null) {
            // if we don't have a RT root yet, save the recent exceptions to the temp dir.
            String rtRoot = Cfg.absRTRoot();
            if (isNullOrEmpty(rtRoot)) rtRoot = System.getProperty("java.io.tmpdir");
            _instance = new RecentExceptions(rtRoot, DEFAULT_INTERVAL);
        }
        return _instance;
    }

    /**
     * For testing only
     * DO NOT CALL THIS FROM PRODUCTION CODE. USE THE SINGLETON INSTANCE.
     */
    protected RecentExceptions(String rtRoot, long interval)
    {
        _interval = interval;
        _rexFile = new File(join(rtRoot, getRexFilename()));
        try {
            if (!_rexFile.exists()) _rexFile.createNewFile();
            _exceptions.putAll(read(_rexFile));
        } catch (Throwable e) {
            l.warn("reading rex file failed: ", e);
        }
    }

    private String getRexFilename()
    {
        String programName = (ProgramInformation.get() != null)
                ? ProgramInformation.get().getProgramName() : "unknown-program";
        return programName +  "-" + Param.RECENT_EXCEPTIONS;
    }

    /**
     * Returns whether a given exception is a recent exception
     */
    public synchronized boolean isRecent(Throwable t)
    {
        try {
            String checksum = Exceptions.getChecksum(t);
            Long timestamp = _exceptions.get(checksum);
            return (timestamp != null && System.currentTimeMillis() - timestamp < _interval);
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
            _exceptions.put(Exceptions.getChecksum(t), System.currentTimeMillis());

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
