/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.rocklog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

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
public class Defect extends RockLogMessage
{
    private static final String NAME_KEY = "name";
    private static final String MESSAGE_KEY = "@message";
    private static final String EXCEPTION_KEY = "exception";
    private static final String PRIORITY_KEY = "priority";
    private static final String TAGS_KEY = "@tags";

    public enum Priority { Info, Warning, Fatal }

    Defect(RockLog rockLog, String name)
    {
        super(rockLog);

        // TODO (GS): add cfg DB

        setPriority(Priority.Fatal); // Defects have the highest priority by default

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

    public Defect tag(String... tags)
    {
        addData(TAGS_KEY, Arrays.asList(tags));
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
    @Override
    public Defect addData(String key, Object value)
    {
        super.addData(key, value);

        return this;
    }

    @Override
    String getURLPath()
    {
        return "/defects";
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
    private JsonObject encodeException(Throwable e)
    {
        JsonObject result = new JsonObject();
        if (e == null) return result;

        JsonArray stacktrace = new JsonArray();
        StackTraceElement[] javaStack = e.getStackTrace();
        for (StackTraceElement javaFrame : javaStack) {
            JsonObject frame = new JsonObject();
            frame.addProperty("class", javaFrame.getClassName());
            frame.addProperty("method", javaFrame.getMethodName());
            frame.addProperty("file", javaFrame.getFileName());
            frame.addProperty("line", javaFrame.getLineNumber());
            stacktrace.add(frame);
        }

        result.addProperty("type", e.getClass().getName());
        result.addProperty("message", e.getMessage());
        result.add("stacktrace", stacktrace);
        result.add("cause", encodeException(e.getCause()));
        return result;
    }
}
