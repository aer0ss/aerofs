/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.audit.client;

import com.aerofs.base.NoObfuscation;
import com.aerofs.lib.log.LogUtil;
import com.google.common.collect.Maps;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Date;
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
// FIXME: I want to explicitly support add(String, User) but we can't see User from here
// (i hate that users say .add("user", user.id()) )
public class AuditClient
{
    public final static String HEADER_UID = "AeroFS-UserID";
    public final static String HEADER_DID = "AeroFS-DeviceID";

    Logger l = LoggerFactory.getLogger(AuditClient.class);

    /** The general topic this audit belongs to. */
    @NoObfuscation
    public enum AuditTopic
    {
        FILE,
        USER,
        SHARING,
        DEVICE,
        ORGANIZATION
    }

    /**
     * Explicitly configure the auditor client (used to transmit the AuditableEvent to the
     * Auditor server, which may deliver it to downstream collection points).
     * Note the client choice encapsulates implementation and address information.
     *
     * If auditorClient is null, or this method is not called,
     * the audit service will short-circuit all event creation and publishing.
     */
    // Commentary:
    // [SIGH], manual DI. I would rather do this with @Inject but it doesn't look like we
    // have that available for the lib or or client-side components.
    public AuditClient setAuditorClient(@Nullable IAuditorClient auditorClient)
    {
        l.info("Audit client set {}", auditorClient);
        _client = auditorClient;
        return this;
    }

    /**
     * Create an AuditableEvent that can accumulate key-value pairs and embedded structures,
     * and then publish via the audit client.
     * @param event Short text name of the event in question ; "signin", "signout", etc.
     * @param topic Topic the event belongs to.
     */
    public AuditableEvent event(AuditTopic topic, String event)
    {
        return (_client == null) ? _dummyAuditableEvent : new JsonAuditableEvent(topic, event);
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
        public abstract AuditableEvent add(String name, String value);

        /**
         * Add the given object to the event, using the toString() implementation.
         */
        public abstract AuditableEvent add(String name, Object value);

        /**
         * Use default marshalling to embed the given object in the auditable event.
         */
        public abstract AuditableEvent embed(String name, Object value);

        /**
         * Publish an event to the audit service. Suppress any connection or transmission errors.
         * Allow the implementation to reuse sockets.
         */
        public void publish()
        {
            try {
                publishBlocking();
            } catch (IOException ioe) {
                l.warn("audit publish error suppressed", LogUtil.suppress(ioe));
            }
        }

        /**
         * Marshall and submit the event data to the auditor.
         * If the event cannot be delivered, throw an exception.
         */
        public abstract void publishBlocking() throws IOException;

        /**
         * Return the marshalled payload data without submitting to the auditor.
         */
        public abstract String getPayload();
    }

    // Implementation for publishable events
    class JsonAuditableEvent extends AuditableEvent
    {
        private JsonAuditableEvent(AuditTopic topic, String event)
        {
            _map.put("topic", topic.toString());
            _map.put("timestamp", new Date());
            _map.put("event", event);
        }

        @Override
        public AuditableEvent add(String key, String val) { _map.put(key, val); return this; }

        @Override
        public AuditableEvent add(String key, Object val)
        {
            _map.put(key, val.toString());
            return this;
        }
        @Override
        public AuditableEvent embed(String key, Object val) { _map.put(key, val); return this; }

        @Override
        public void publishBlocking() throws IOException
        {
            String payload = getPayload();
            l.debug("pub {}", payload);
            _client.submit(payload);
        }

        @Override
        public String getPayload() { return _gson.toJson(_map); }

        private Map<String, Object> _map = Maps.newHashMap();
    }

    // An instance of this dummy class is used to short-circuit the marshalling work if auditing
    // is disabled.
    class DummyAuditableEvent extends AuditableEvent
    {
        private DummyAuditableEvent() { }
        @Override public AuditableEvent add(String name, String value) { return this; }
        @Override public AuditableEvent add(String name, Object value) { return this; }
        @Override public AuditableEvent embed(String name, Object value) { return this; }
        @Override public void publishBlocking() { }
        @Override public String getPayload() { return ""; }
    }

    IAuditorClient _client = null;
    private DummyAuditableEvent     _dummyAuditableEvent = new DummyAuditableEvent();

    private static final Gson       _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();
}
