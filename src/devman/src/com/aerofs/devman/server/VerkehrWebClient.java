/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.devman.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.google.common.collect.Lists;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Collection;

import static com.aerofs.base.BaseParam.VerkehrTopics.CMD_CHANNEL_TOPIC_PREFIX;
import static com.aerofs.base.BaseParam.VerkehrTopics.TOPIC_SEPARATOR;

/**
 * Client for the vekrehr REST API.
 *
 * N.B. This client is aware of the semantic binding between the command channel topic name and the
 * device ID and makes use of this binding to get online devices and to map a given device ID to a
 * single IP address.
 */
public class VerkehrWebClient
{
    private static final Logger l = Loggers.getLogger(VerkehrWebClient.class);
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

    public static Object readJsonFromUrl(String urlString)
            throws IOException
    {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setReadTimeout(500);
        conn.setConnectTimeout(500);

        BufferedReader rd = null;

        try {
            rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String jsonText = readAll(rd);
            return JSONValue.parse(jsonText);
        } finally {
            if (rd != null) {
                rd.close();
            }

            conn.disconnect();
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

        for (Object connectionObject : connections) {
            String connection = (String) connectionObject;
            try {
                OnlineDeviceInfo odi = getOnlineDeviceInfo(connection);

                if (odi == null) {
                    continue;
                }

                result.add(odi);
            } catch (FileNotFoundException e) {
                l.warn("Connection " + connection + " not found (must have gone offline).");
            }
       }

        return result;
    }

    private @Nullable OnlineDeviceInfo getOnlineDeviceInfo(String connection)
            throws IOException, ExFormatError
    {
        l.debug("Get connection info for " + connection);

        String connectionURL = _connectionsURL + "/" + connection;
        JSONObject connectionJsonObject = (JSONObject) readJsonFromUrl(connectionURL);

        String ipAddress = (String) connectionJsonObject.get("ip_address");
        JSONObject customJsonObject = (JSONObject) connectionJsonObject.get("custom");
        JSONArray topics = (JSONArray) customJsonObject.get("topics");

        if (topics == null) {
            // No topics set, so this must be an admin connection.
            return null;
        }

        for (Object topicObject : topics) {
            String topic = (String) topicObject;

            // N.B. we must use a channel topic prefix that is unique per device. The command
            // channel prefix will suffice.
            if (topic.startsWith(CMD_CHANNEL_TOPIC_PREFIX)) {
                String[] split = topic.split(TOPIC_SEPARATOR);

                if (split.length != 2) {
                    throw new ExFormatError();
                }

                String did = split[1];
                return new OnlineDeviceInfo(new DID(did), InetAddress.getByName(ipAddress));
            }
        }

        return null;
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