/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.lib.os.OSUtil;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.Executor;

import static com.aerofs.defects.DefectUtils.*;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Maps.newHashMap;

/**
 * Represents a defect/metric where the defect data are reported to RockLog.
 *
 * Defects are essentially objects with some well-known fields (timestamp, name, message,
 * exception, etc...) and optionally additional custom fields
 *
 * All defects must have a name (set in the constructor). This name enables easy aggregation of
 * defects, so please be sure to pick a name specific to your defect. Feel free to use dots to
 * create namespaces.
 *
 * Example:
 *
 * try {
 *      ...
 * } catch (Exception e) {
 *      Defects.newMetric("mycomponent.foobar")
 *          .setMessage("Something failed")
 *          .setException(e)
 *          .sendAsync();
 * }
 */
public class Defect
{
    private static final Logger l = Loggers.getLogger(Defect.class);

    protected String    _defectID;
    protected String    _name;
    protected String    _message;
    protected Throwable _exception;
    protected String    _time;
    protected Priority  _priority;

    protected UserID    _userID;
    protected DID       _deviceID;

    private final Map<String, Object> _data = newHashMap();

    protected final InjectableCfg   _cfg;
    private final RockLog           _rockLog;
    private final Executor          _executor;

    public enum Priority {Auto, User, Command}

    // N.B. turns out passing in properties is necessary because querying System properties
    //   each time will cause defects to fail under heavy load
    public Defect(String name, InjectableCfg cfg, RockLog rockLog, Executor executor)
    {
        _cfg = cfg;
        _rockLog = rockLog;
        _executor = executor;

        // Set the timestamp field as early as possible
        _time = getTimeStamp();
        _defectID = newDefectID();
        _name = name;
        _message = "";
        // need the stacktrace at this point assuming it's not overwritten later
        _exception = new Exception();
        _priority = Priority.Auto;

        _userID = getCfgUser(_cfg);
        _deviceID = getCfgDID(_cfg);
    }

    public Defect setMessage(String message)
    {
        _message = firstNonNull(message, "");
        return this;
    }

    public Defect setException(@Nullable Throwable exception)
    {
        // do not update if exception is null
        _exception = firstNonNull(exception, _exception);
        return this;
    }

    public Defect addData(String key, Object value)
    {
        _data.put(key, value);
        return this;
    }

    public void sendSync()
            throws Exception
    {
        l.debug("build defect");

        // If we have any LinkageError (NoClassDefFoundError or UnsatisfiedLinkError) or
        // MissingResourceException, this probably indicates that our stripped-down version of
        // OpenJDK is missing something. Send a different RockLog defect to make sure we catch it.
        if (_exception instanceof LinkageError || _exception instanceof MissingResourceException) {
            _name = "system.classnotfound";
        }

        _rockLog.send("/defects", getData());
    }

    public void sendSyncIgnoreErrors()
    {
        try {
            sendSync();
        } catch (Throwable t) {
            // we don't want stack trace
            l.warn("Ignored. Failed to send defect: {}", t.toString());
        }
    }

    public void sendAsync()
    {
        _executor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                sendSyncIgnoreErrors();
            }
        });
    }

    /**
     * @return fully composed map of supplementary defect data + caller-supplied defect data
     */
    protected Map<String, Object> getData()
    {
        Map<String, Object> data = getDefectData();
        data.putAll(_data);
        return data;
    }

    /**
     * @return the auto-generated defect data, which may be overwritten by any caller-supplied data
     */
    protected Map<String, Object> getDefectData()
    {
        Map<String, Object> data = newHashMap();

        data.put("defect_id",           _defectID);
        data.put("name",                _name);
        data.put("@message",            _message);
        data.put("exception",           encodeException(_exception));
        // Note: some of our json fields start with a '@' to follow the logstash format
        // see: https://github.com/logstash/logstash/wiki/logstash%27s-internal-message-format
        // Kibana expects to find those fields (especially @timestamp)
        data.put("@timestamp",          _time);
        data.put("priority",            _priority.toString());

        data.put("version",             _cfg.ver());
        data.put("user_id",             _userID.getString());
        data.put("did",                 _deviceID.toStringFormal());

        if (OSUtil.get() != null) {
            data.put("os_name",         OSUtil.get().getFullOSName());
            data.put("os_family",       OSUtil.get().getOSFamily().getString());
            data.put("aerofs_arch",     OSUtil.getOSArch().toString());
        }

        return data;
    }
}
