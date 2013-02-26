/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.lib.os.OSUtil;
import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import static com.google.common.collect.Maps.newHashMap;

abstract class RockLogMessage
{
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private final HashMap<String, Object> _data = newHashMap();
    private final RockLog _rockLog;

    RockLogMessage(RockLog rockLog, InjectableCfg cfg)
    {
        this._rockLog = rockLog;

        addTimestamp();
        addVersion(cfg);
        addDeviceInfo(cfg);
        addOSInfo();
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
            _data.put("os_family", OSUtil.get().getOSFamily().toString());
            _data.put("aerofs_arch", OSUtil.getOSArch().toString());
        }
    }

    public void send()
    {
        _rockLog.send(this);
    }

    public void sendAsync()
    {
        _rockLog.sendAsync(this);
    }

    RockLogMessage addData(String key, Object value)
    {
        _data.put(key, value);

        return this;
    }

    String getJSON()
    {
        return new Gson().toJson(_data);
    }

    protected RockLog getRockLog()
    {
        return _rockLog;
    }

    abstract String getURLPath();
}
