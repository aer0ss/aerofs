/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.http;

import com.aerofs.daemon.tng.base.http.HttpMessage.Method;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

public class HttpProxySocket extends Socket
{
    private final Proxy _proxy;

    public HttpProxySocket(Proxy proxy)
    {
        assert proxy.type() == Type.HTTP : "HttpProxySocket only accepts HTTP proxies";
        _proxy = proxy;
    }

    @Override
    public void connect(SocketAddress socketAddress)
            throws IOException
    {
        connect(socketAddress, 0);
    }

    @Override
    public void connect(SocketAddress socketAddress, int i)
            throws IOException
    {
        super.connect(_proxy.address(), i);
        try {
            handshake((InetSocketAddress) socketAddress);
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void handshake(SocketAddress socketAddress)
            throws IOException
    {
        if (!(socketAddress instanceof InetSocketAddress)) {
            throw new IllegalArgumentException("address must be an Inet address");
        }

        InetSocketAddress address = (InetSocketAddress) socketAddress;

        final String host;
        if (address.isUnresolved()) {
            // The proxy may wish to resolve this to a different IP address
            host = address.getHostName();
        } else {
            host = address.getAddress().getHostAddress();
        }

        try {
            // Send the CONNECT request
            URI uri = new URI(host + ":" + address.getPort());
            HttpMessage request = HttpMessage.createRequest(Method.CONNECT, uri,
                    Collections.<String, String>emptyMap());
            getOutputStream().write(request.toString().getBytes("UTF-8"));

            HttpMessage response = new HttpParser().parse(getInputStream());
            assert response != null : "the parser returned a null HttpMessage";

            if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
                throw new IOException("failed to negotiate with Proxy");
            }

            // Success
        } catch (URISyntaxException e) {
            throw new IOException(e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
