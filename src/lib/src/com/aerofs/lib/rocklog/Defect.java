/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

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
 *     RockLog.newDefect("mycomponent.foobar").setMsg("Something failed").setEx(e).sendAsync();
 * }

 */
@SuppressWarnings("unchecked")
public class Defect
{
    public enum Priority { Info, Warning, Fatal }

    private final String _name;
    private final RockLog _rocklog;
    private JSONObject _json = new JSONObject();

    Defect(RockLog rocklog, String name)
    {
        // Set the timestamp field as early as possible
        // Note: some of our json fields start with a '@' to follow the logstash format
        //       see: https://github.com/logstash/logstash/wiki/logstash%27s-internal-message-format
        //       Kibana expects to find those fields (especially @timestamp)
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        _json.put("@timestamp", sdf.format(new Date()));

        // Defects have the highest priority by default
        setPriority(Priority.Fatal);

        _rocklog = rocklog;
        _name = name;
    }

    public Defect setMsg(String message)
    {
        _json.put("@message", message);
        return this;
    }

    public Defect setEx(Throwable ex)
    {
        _json.put("exception", encodeException(ex));
        return this;
    }

    public Defect setPriority(Priority priority)
    {
        _json.put("priority", priority.toString());
        return this;
    }

    public Defect tag(String... tags)
    {
        _json.put("@tags", Arrays.asList(tags));
        return this;
    }

    /**
     * Add custom structured data to the defect
     *
     * @param key a key identifying this field. It is recommended that the name have some unique
     * prefix identifying the part of the code that is using it, like "linker.foobar".
     *
     * @param value anything that can be parsed as JSON by json-simple. (String, Number, Boolean,
     * List, Map, or null).
     */
    public Defect addData(String key, Object value)
    {
        _json.put(key, value);
        return this;
    }

    public void send()
    {
        _rocklog.send(this);
    }

    public void sendAsync()
    {
        _rocklog.sendAsync(this);
    }

    String getJSON()
    {
        _json.put("defect_name", _name);
        _json.put("version", Cfg.ver());

        if (Cfg.inited()) {
            // TODO (GS): add cfg DB
            _json.put("user", Cfg.user());
            _json.put("did", Cfg.did().toHex());
        }

        if (OSUtil.get() != null) {
            _json.put("os_name", OSUtil.get().getFullOSName());
            _json.put("os_family", OSUtil.get().getOSFamily().toString());
            _json.put("aerofs_arch", OSUtil.getOSArch().toString());
        }

        return _json.toJSONString();
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
    private JSONObject encodeException(Throwable e)
    {
        JSONObject result = new JSONObject();
        if (e == null) return result;

        JSONArray stacktrace = new JSONArray();
        StackTraceElement[] javaStack = e.getStackTrace();
        for (StackTraceElement javaFrame : javaStack) {
            JSONObject frame = new JSONObject();
            frame.put("class", javaFrame.getClassName());
            frame.put("method", javaFrame.getMethodName());
            frame.put("file", javaFrame.getFileName());
            frame.put("line", javaFrame.getLineNumber());
            stacktrace.add(frame);
        }

        result.put("type", e.getClass().getName());
        result.put("message", e.getMessage());
        result.put("stacktrace", stacktrace);
        result.put("cause", encodeException(e.getCause()));
        return result;
    }
}
