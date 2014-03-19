/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.rocklog;

import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.newHashMap;

/**
 * Represents a defect that will be sent to the RockLog server.
 *
 * Defects are essentially JSON objects with some well-known fields (timestamp, name, message,
 * exception, etc...) and optionally additional custom fields
 *
 * All defects must have a name (set in the constructor). This name enables easy aggregation of
 * defects, so please be sure to pick a name specific to your defect. Feel free to use dots to
 * create namespaces.
 *
 * Example:
 *
 * try {
 *     ...
 * } catch (Exception e) {
 *     RockLog.newDefect("mycomponent.foobar").setMessage("Something failed").setException(e).sendAsync();
 * }

 */
public class Defect
{
    private final HashMap<String, Object> _data = newHashMap();
    private final RockLog _rockLog;

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    private static final String ID_KEY = "defect_id";
    private static final String NAME_KEY = "name";
    private static final String MESSAGE_KEY = "@message";
    private static final String EXCEPTION_KEY = "exception";
    private static final String PRIORITY_KEY = "priority";

    public enum Priority {Auto, User}

    Defect(RockLog rockLog, InjectableCfg cfg, String name)
    {
        _rockLog = checkNotNull(rockLog);
        // TODO (GS): add cfg DB

        addId();
        addTimestamp();
        addVersion(cfg);
        addDeviceInfo(cfg);
        addOSInfo();
        setPriority(Priority.Auto);

        addData(NAME_KEY, name);
    }

    public Defect setMessage(String message)
    {
        addData(MESSAGE_KEY, message);
        return this;
    }

    public Defect setException(Throwable ex)
    {
        addData(EXCEPTION_KEY, encodeException(ex));
        return this;
    }

    public Defect setPriority(Priority priority)
    {
        addData(PRIORITY_KEY, priority.toString());
        return this;
    }

    /**
     * Send the message to RockLog asynchronously.
     */
    public void send()
    {
        _rockLog.send(this);
    }

    /**
     * Send the message to RockLog synchronously.
     * Be very careful when using this call, especially in an environment
     * where RockLog may not be available.
     */
    public void sendBlocking()
    {
        _rockLog.sendBlocking(this);
    }

    String getURLPath()
    {
        return "/defects";
    }

    public Defect addData(String key, Object value)
    {
        _data.put(key, value);
        return this;
    }

    String getJSON()
    {
        return new Gson().toJson(_data);
    }

    /**
     * Recursively converts a Throwable and its causes into JSON using the following format:
     *
     * {
     *   type: "java.io.IOException",
     *   message: "Something happened",
     *   stacktrace: [
     *     { class: "com.aerofs.Lol", method: "doWork", file: "Lol.java", line:32 },
     *     { class: "com.aerofs.Foo", method: "foo", file: "Foo.java", line:10 },
     *     ...
     *   ],
     *   cause: {
     *      type: "java.lang.NullPointerException"
     *      message: "Oops!"
     *      stacktrace: [...]
     *      cause: {}
     *   }
     * }
     */
    private HashMap<String, Object> encodeException(Throwable e)
    {
        if (e == null) return null;

        List<Object> frames = Lists.newArrayList();
        for (StackTraceElement f : e.getStackTrace()) {
            HashMap<String, Object> frame = Maps.newHashMap();
            frame.put("class", f.getClassName());
            frame.put("method", f.getMethodName());
            frame.put("file", f.getFileName());
            frame.put("line", f.getLineNumber());
            frames.add(frame);
        }

        HashMap<String, Object> result = Maps.newHashMap();
        result.put("type", e.getClass().getName());
        result.put("message", e.getMessage());
        result.put("stacktrace", frames);
        result.put("cause", encodeException(e.getCause()));

        return result;
    }

    private void addId()
    {
        addData(ID_KEY, UUID.randomUUID());
    }

    private void addTimestamp()
    {
        //
        // Set the timestamp field as early as possible
        // Note: some of our json fields start with a '@' to follow the logstash format
        // see: https://github.com/logstash/logstash/wiki/logstash%27s-internal-message-format
        // Kibana expects to find those fields (especially @timestamp)
        //

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        _data.put("@timestamp", sdf.format(new Date()));
    }

    private void addVersion(InjectableCfg cfg)
    {
        _data.put("version", cfg.ver());
    }

    private void addDeviceInfo(InjectableCfg cfg)
    {
        if (cfg.inited()) {
            _data.put("user_id", cfg.user().getString());
            _data.put("did", cfg.did().toStringFormal());
        }
    }

    private void addOSInfo()
    {
        if (OSUtil.get() != null) {
            _data.put("os_name", OSUtil.get().getFullOSName());
            _data.put("os_family", OSUtil.get().getOSFamily().getString());
            _data.put("aerofs_arch", OSUtil.getOSArch().toString());
        }
    }
}
