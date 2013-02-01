/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class BaseMessage
{
    private HashMap<String, Object> _data = new HashMap<String, Object>();

    BaseMessage()
    {
        // Set the timestamp field as early as possible
        // Note: some of our json fields start with a '@' to follow the logstash format
        //       see: https://github.com/logstash/logstash/wiki/logstash%27s-internal-message-format
        //       Kibana expects to find those fields (especially @timestamp)
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        _data.put("@timestamp", sdf.format(new Date()));

        _data.put("version", Cfg.ver());

        if (Cfg.inited()) {
            // TODO (GS): add cfg DB
            _data.put("user", Cfg.user());
            _data.put("did", Cfg.did().toStringFormal());
        }

        if (OSUtil.get() != null) {
            _data.put("os_name", OSUtil.get().getFullOSName());
            _data.put("os_family", OSUtil.get().getOSFamily().toString());
            _data.put("aerofs_arch", OSUtil.getOSArch().toString());
        }
    }

    public HashMap<String, Object> getData()
    {
        return _data;
    }
}
