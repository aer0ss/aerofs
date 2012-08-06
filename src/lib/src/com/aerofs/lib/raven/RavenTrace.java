package com.aerofs.lib.raven;

public class RavenTrace {

    RavenTraceElement[] _traceElements;
    String _message;
    String _user;
    String _deviceId;
    String _version;
    long _timestamp;

    public RavenTrace(RavenTraceElement[] traceElements, String message, String user, String deviceId, String version, long timestamp)
    {
        _traceElements = traceElements;
        _message = message;
        _user = user;
        _deviceId = deviceId;
        _version = version;
        _timestamp = timestamp;
    }

    public long getTimestamp() {
        return _timestamp;
    }
    public String getMessage() {
        return _message;
    }

    public String getUser() {
        return _user;
    }

    public String getDeviceId() {
        return _deviceId;
    }

    public String getVersion() {
        return _version;
    }

    public RavenTraceElement[] getTraceElements() {
        return _traceElements;
    }
}
