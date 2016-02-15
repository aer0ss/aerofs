package com.aerofs.audit.client;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Map;

// used in unit tests to capture and verify audit events
public class MockAuditClient extends AuditClient
{
    private final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private AuditableEvent _auditEvent;

    // workaround for new MockAuditClient().setAuditorClient() returning AuditClient
    public MockAuditClient(IAuditorClient auditorClient)
    {
        setAuditorClient(auditorClient);
    }

    @Override
    public AuditableEvent event(AuditTopic topic, String event)
    {
        return _auditEvent = super.event(topic, event);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLastEventPayloadAndReset()
    {
        Map<String, Object> result = _gson.fromJson(_auditEvent.getPayload(), Map.class);
        _auditEvent = null;
        return result;
    }
}
