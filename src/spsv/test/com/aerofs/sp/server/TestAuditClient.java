/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.audit.client.AuditClient.AuditTopic;
import com.aerofs.audit.client.AuditClient.AuditableEvent;
import com.aerofs.audit.client.IAuditorClient;
import com.aerofs.base.BaseParam.Audit;
import com.aerofs.testlib.AbstractTest;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 */
@SuppressWarnings("rawtypes")
public class TestAuditClient extends AbstractTest
{
    @Mock IAuditorClient _httpMock;
    AuditClient             _client;

    @Before
    public void setUp() throws IOException
    {
        Audit.AUDIT_ENABLED = true;
        _payload = ArgumentCaptor.forClass(String.class);
        doNothing().when(_httpMock).submit(anyString());

        _client = new AuditClient().setAuditorClient(_httpMock);
    }

    @After
    public void tearDown()
    {
        Audit.AUDIT_ENABLED = false;
    }


    private void captureEvent() throws Exception
    {
        verify(_httpMock).submit(_payload.capture());
    }

    @Test
    public void shouldCallPublisher() throws Exception
    {
        _client.event(AuditTopic.DEVICE, "should_call_pub")
                .add("created", "device")
                .add("k2", "v2")
                .publish();
        captureEvent();
    }

    @Test
    public void shouldSetAuditTopic() throws Exception
    {
        _client.event(AuditTopic.DEVICE, "should_set_topic")
                .add("created", "device")
                .add("k2", "v2")
                .publish();
        captureEvent();

        Map json = _gson.fromJson(_payload.getValue(), Map.class);
        assertTrue(json.containsKey("topic"));
        assertTrue(json.containsKey("event"));

        String topic = json.get("topic").toString();
        assertEquals(topic.toLowerCase(), AuditTopic.DEVICE.toString().toLowerCase());
    }

    static class DummyData
    {
        public DummyData(String s) { _content = s; }
        public String toString() { return _content; }
        String _content;
    }

    @Test
    public void shouldEncodeEmbeds() throws Exception
    {
        Map<String, Object> testData = new HashMap<String, Object>();
        testData.put("k1", "v1");

        AuditableEvent event = _client.event(AuditTopic.SHARING, "embeds");
        for (String key : testData.keySet()) {
            event.add(key, testData.get(key));
        }
        DummyData val1 = new DummyData("hi mom");
        event.embed("embed1", val1);
        event.embed("embed_arr", new DummyData[]{new DummyData("test 1"), new DummyData("test 2")});

        event.publish();
        captureEvent();

        Map res = _gson.fromJson(_payload.getValue(), Map.class);

        // check simple embed:
        Map embed1 = (Map)res.get("embed1");
        assertTrue(embed1.containsKey("_content"));
        assertEquals(embed1.get("_content"), val1.toString());

        // check array of embedded objects: right number, object, contain the right strings
        List embed_arr  = (List)res.get("embed_arr");
        assertEquals(2, embed_arr.size());
        for (Object obj : embed_arr) {
            Map dummy = (Map)obj;
            assertTrue(dummy.containsKey("_content"));
            assertTrue(((String)dummy.get("_content")).startsWith("test "));
        }
    }

    @Test
    public void shouldEncodeSimpleParams() throws Exception
    {
        Map<String, Object> testData = new HashMap<String, Object>();
        testData.put("k1", "v1");
        testData.put("k2", "v2");
        testData.put("k3", "v3");
        testData.put("k4", 1234);
        testData.put("k5", 12.3456);
        testData.put("k6", new DummyData("dummy1"));
        testData.put("k7", new DummyData("dummy2"));

        AuditableEvent event = _client.event(AuditTopic.SHARING, "params");
        for (String key : testData.keySet()) {
            event.add(key, testData.get(key));
        }

        event.publish();
        captureEvent();

        Map res = _gson.fromJson(_payload.getValue(), Map.class);
        assertEquals(testData.size() + 3, res.size()); // + 1 for the implied "event" topic
        for (String k : testData.keySet()) {
            assertTrue(res.containsKey(k));
            assertEquals(res.get(k), String.valueOf(testData.get(k)));
        }
    }

    private ArgumentCaptor<String> _payload;
    Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
}
