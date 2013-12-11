/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.verkehr.client.lib.publisher.VerkehrPublisher;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Client to create and publish auditable events, if auditing is enabled.
 *
 * Sample usage:<pre>_auditClient.event(AuditTopic.USER)
 .add("action", "signIn")
 .add("user", user)
 .publish();</pre>
 *
 * Embedded objects and arrays are supported.
 */
public class AuditClient
{
    /** The general topic this audit belongs to. */
    public enum AuditTopic
    {
        FILE,
        USER,
        SHARING,
        DEVICE
    }

    /**
     * Creates a dummy audit client - one with no capability to publish. The event-creation
     * methods are no-ops that JIT should have no trouble with.
     * TODO: consider encapsulating this distinction within this class - check audit configs
     */
    public AuditClient()
    {
        _publisher = null;
        _dummyAuditableEvent = new DummyAuditableEvent();
    }

    /**
     * Creates an audit-service client that delivers auditable events to a verkehr system.
     */
    public AuditClient(VerkehrPublisher publisher)
    {
        _publisher = publisher;
        _dummyAuditableEvent = null;
    }

    /**
     * Create an AuditableEvent that can accumulate key-value pairs and embedded structures,
     * and then publish via the audit client.
     * @param event Short text name of the event in question ; "signin", "signout", etc.
     * @param topic Topic the event belongs to.
     */
    public AuditableEvent event(AuditTopic topic, String event)
    {
        return (_dummyAuditableEvent != null) ? _dummyAuditableEvent : new JsonAuditableEvent(topic)
                .add("event", event);
    }

    // Note: this is organized in an abstract/impl relationship to provide a fast noop event
    // when auditing is disabled; it also   allows us to mock out testing reliably.
    // Also note, these non-static embedded classes can refer to the containing classes' publisher
    // directly.
    /**
     * Mechanism to build a simple or complex auditable event to deliver to downstream auditor.
     * @see AuditClient
     */
    public abstract class AuditableEvent
    {
        /**
         * Add the given string data to the event.
         */
        abstract AuditableEvent add(String name, String value);

        /**
         * Add the given object to the event, using the toString() implementation.
         */
        abstract AuditableEvent add(String name, Object value);

        /**
         * Use default marshalling to embed the given object in the auditable event.
         */
        abstract AuditableEvent embed(String name, Object value);
        // TODO: embed(AuditableEvent)

        /**
         * Marshall and submit the event data to the auditor.
         */
        abstract void publish();

        /**
         * Return the marshalled payload data without submitting to the auditor.
         */
        abstract byte[] getPayload();
    }

    // Implementation for publishable events
    class JsonAuditableEvent extends AuditableEvent
    {
        JsonAuditableEvent(AuditTopic topic) { _topic = topic; }
        AuditableEvent add(String name, String value) { _map.put(name, value); return this; }
        AuditableEvent add(String name, Object value) { _map.put(name, value.toString()); return this; }

        AuditableEvent embed(String name, Object value)
        {
            _map.put(name, value);
            return this;
        }

        void publish() { _publisher.publish_("audit/" + _topic.toString(), getPayload()); }
        byte[] getPayload() { return marshall(_map).getBytes(); }
        private String marshall(Object val) { return _gson.toJson(val); }

        private Map<String, Object> _map = new HashMap<String, Object>();
        private AuditTopic _topic;
    }

    // An instance of this dummy class is used to short-circuit the marshalling work if auditing
    // is disabled.
    class DummyAuditableEvent extends AuditableEvent
    {
        DummyAuditableEvent() { }
        DummyAuditableEvent(AuditTopic topic) { }
        AuditableEvent add(String name, String value) { return this; }
        AuditableEvent add(String name, Object value) { return this; }
        AuditableEvent embed(String name, Object value) { return this; }
        void publish() { }
        byte[] getPayload() { return new byte[0]; }
    }

    private VerkehrPublisher _publisher;
    private DummyAuditableEvent _dummyAuditableEvent = new DummyAuditableEvent();
    private static final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
}
