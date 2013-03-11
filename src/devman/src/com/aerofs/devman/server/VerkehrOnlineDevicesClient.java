package com.aerofs.devman.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.lib.Param;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collection;

public class VerkehrOnlineDevicesClient
{
    private static final Logger l = Loggers.getLogger(VerkehrOnlineDevicesClient.class);
    private final String _url;

    public VerkehrOnlineDevicesClient(String verkehrHost, short verkehrAdminPort)
    {
        _url = "http://" + verkehrHost + ":" + verkehrAdminPort + "/topics";
    }

    private static String readAll(Reader rd)
            throws IOException
    {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public static Object readJsonFromUrl(String url)
            throws IOException
    {
        InputStream is = new URL(url).openStream();

        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            return JSONValue.parse(jsonText);
        } finally {
            is.close();
        }
    }

    @SuppressWarnings("unchecked")
    public Collection<DID> getOnlineDevices()
            throws ExFormatError, IOException
    {
        Collection<DID> result = Lists.newLinkedList();

        JSONObject jo = (JSONObject) readJsonFromUrl(_url);
        JSONArray topics = (JSONArray) jo.get("topics");

        for (int i = 0; i < topics.size(); i++) {
            String topic = (String) topics.get(i);

            // The sync status channel only listens on device names.
            if (topic.startsWith(Param.SSS_CHANNEL_TOPIC_PREFIX)) {
                String[] split = topic.split(Param.TOPIC_SEPARATOR);

                if (split.length != 2) {
                    throw new ExFormatError();
                }

                String did = split[1];
                result.add(new DID(did));
            }
        }

        return result;
    }
}