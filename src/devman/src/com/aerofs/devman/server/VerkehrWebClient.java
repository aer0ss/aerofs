/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.lib.Param;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.json.simple.JSONObject;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * Client for the vekrehr REST API.
 *
 * N.B. This client is aware of the semantic binding between the command channel topic name and the
 * device ID and makes use of this binding to get online devices and to map a given device ID to a
 * single IP address.
 */
public class VerkehrWebClient
{
    private final String _connectionsURL;

    public VerkehrWebClient(String verkehrHost, short verkehrAdminPort)
    {
        _connectionsURL = "http://" + verkehrHost + ":" + verkehrAdminPort + "/connections";
    }

    //
    // Private utils.
    //

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

    //
    // Public functions.
    //

    public Collection<OnlineDeviceInfo> getOnlineDevicesInfo()
            throws IOException, ExFormatError
    {
        Collection<OnlineDeviceInfo> result = Lists.newLinkedList();

        JSONObject topicsJsonObject = (JSONObject) readJsonFromUrl(_connectionsURL);
        JSONArray connections = (JSONArray) topicsJsonObject.get("connections");

        for (int i = 0; i < connections.size(); i++) {
            String connectionID = (String) connections.get(i);

            String connectionURL = _connectionsURL + "/" + connectionID;
            JSONObject connectionJsonObject = (JSONObject) readJsonFromUrl(connectionURL);

            String ipAddress = (String) connectionJsonObject.get("ip_address");
            JSONObject customJsonObject = (JSONObject) connectionJsonObject.get("custom");
            JSONArray topics = (JSONArray) customJsonObject.get("topics");

            if (topics == null) {
                // No topics set, so this must be an admin connection.
                continue;
            }

            for (int j = 0; j < topics.size(); j++) {
                String topic = (String) topics.get(j);

                if (topic.startsWith(Param.SSS_CHANNEL_TOPIC_PREFIX)) {
                    String[] split = topic.split(Param.TOPIC_SEPARATOR);

                    if (split.length != 2) {
                        throw new ExFormatError();
                    }

                    String did = split[1];
                    result.add(new OnlineDeviceInfo(new DID(did), InetAddress.getByName(ipAddress)));
                }
            }
        }

        return result;
    }

    //
    // Helper classes.
    //

    public static class OnlineDeviceInfo
    {
        private final DID _did;
        private final InetAddress _address;

        public OnlineDeviceInfo(DID did, InetAddress address)
        {
            _did = did;
            _address = address;
        }

        public DID getDevice()
        {
            return _did;
        }

        public InetAddress getAddress()
        {
            return _address;
        }
    }
}