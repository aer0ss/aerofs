/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.sp.server.AuditClient.AuditTopic;
import com.aerofs.sp.server.AuditClient.AuditableEvent;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 */
public class AuditClientTest extends AbstractTest
{
    @Mock VerkehrPublisher _publisher;

    private void prepareCaptors()
    {
        _topic = ArgumentCaptor.forClass(String.class);
        _payload = ArgumentCaptor.forClass(byte[].class);
        doNothing().when(_publisher).start();
    }

    private void captureEvent()
    {
        verify(_publisher).publish_(_topic.capture(), _payload.capture());
    }

    @Test
    public void shouldCallPublisher() throws Exception
    {
        prepareCaptors();
        new AuditClient(_publisher)
                .event(AuditTopic.DEVICE, "should_call_pub")
                .add("created", "device")
                .add("k2", "v2")
                .publish();
        captureEvent();
    }

    @Test
    public void shouldSetAuditTopic()
    {
        prepareCaptors();
        new AuditClient(_publisher)
                .event(AuditTopic.DEVICE, "should_set_topic")
                .add("created", "device")
                .add("k2", "v2")
                .publish();
        captureEvent();
        assertTrue(_topic.getValue().startsWith("audit/"));
        assertTrue(_topic.getValue().toLowerCase().contains("device"));
    }

    static class DummyData
    {
        public DummyData(String s) { _content = s; }
        public String toString() { return _content; }
        String _content;
    }

    @Test
    public void shouldEncodeEmbeds() throws UnsupportedEncodingException
    {
        Map<String, Object> testData = new HashMap<String, Object>();
        testData.put("k1", "v1");

        prepareCaptors();
        AuditableEvent event = new AuditClient(_publisher).event(AuditTopic.SHARING, "embeds");
        for (String key : testData.keySet()) {
            event.add(key, testData.get(key));
        }
        DummyData val1 = new DummyData("hi mom");
        event.embed("embed1", val1);
        event.embed("embed_arr", new DummyData[]{new DummyData("test 1"), new DummyData("test 2")});

        event.publish();
        captureEvent();

        Map res = _gson.fromJson(new String(_payload.getValue(), "UTF-8"), Map.class);

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
    public void shouldEncodeSimpleParams() throws UnsupportedEncodingException
    {
        Map<String, Object> testData = new HashMap<String, Object>();
        testData.put("k1", "v1");
        testData.put("k2", "v2");
        testData.put("k3", "v3");
        testData.put("k4", new Integer(1234));
        testData.put("k5", new Double(12.3456));
        testData.put("k6", new DummyData("dummy1"));
        testData.put("k7", new DummyData("dummy2"));

        prepareCaptors();
        AuditableEvent event = new AuditClient(_publisher).event(AuditTopic.SHARING, "params");
        for (String key : testData.keySet()) {
            event.add(key, testData.get(key));
        }

        event.publish();
        captureEvent();

        Map res = _gson.fromJson(new String(_payload.getValue(), "UTF-8"), Map.class);
        assertTrue(res.size() == testData.size() + 1); // + 1 for the implied "event" topic
        for (String k : testData.keySet()) {
            assertTrue(res.containsKey(k));
            assertEquals(res.get(k), String.valueOf(testData.get(k)));
        }
    }

    private ArgumentCaptor<String> _topic;
    private ArgumentCaptor<byte[]> _payload;
    Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
}
