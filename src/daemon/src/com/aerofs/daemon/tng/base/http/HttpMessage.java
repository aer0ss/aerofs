/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.http;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class HttpMessage
{
    public enum Method
    {
        HEAD,
        GET,
        POST,
        PUT,
        DELETE,
        TRACE,
        OPTIONS,
        CONNECT,
        PATCH,
    }

    final Map<String, String> _headers = new HashMap<String, String>();
    private final boolean _isRequest;
    private final Method _method;
    private final URI _uri;
    private final int _responseCode;

    public static HttpMessage createRequest(Method method, URI uri, Map<String, String> headers)
    {
        assert method != null : "No method specified";
        assert uri != null : "No URI specified";
        assert headers != null : "No Headers specified";
        return new HttpMessage(true, method, uri, -1, headers);
    }

    public static HttpMessage createResponse(int responseCode, Map<String, String> headers)
    {
        assert responseCode >= 100 : "Invalid response code " + responseCode;
        assert headers != null : "No headers specified";
        return new HttpMessage(false, null, null, responseCode, headers);
    }

    private HttpMessage(boolean request, Method method, URI uri, int responseCode,
            Map<String, String> headers)
    {
        _isRequest = request;
        _method = method;
        _uri = uri;
        _responseCode = responseCode;
        _headers.putAll(headers);
    }

    public boolean isRequest()
    {
        return _isRequest;
    }

    public Method getMethod()
    {
        if (_isRequest) {
            return _method;
        }

        return null;
    }

    public URI getUri()
    {
        if (_isRequest) {
            return _uri;
        }

        return null;
    }

    public int getResponseCode()
    {
        if (!_isRequest) {
            return _responseCode;
        }

        return -1;
    }

    public String getHeader(String header)
    {
        return _headers.get(header);
    }

    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        if (_isRequest) {
            builder.append(_method).append(" ").append(_uri).append(" HTTP/1.1\n");
        } else {
            builder.append("HTTP/1.1 ").append(_responseCode).append("\n");
        }

        for (Entry<String, String> header : _headers.entrySet()) {
            builder.append(header.getKey()).append(": ").append(header.getValue()).append("\n");
        }
        builder.append("\n");
        return builder.toString();
    }
}
