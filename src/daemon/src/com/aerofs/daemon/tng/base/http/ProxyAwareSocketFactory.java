/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.http;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.Socket;

public class ProxyAwareSocketFactory extends SocketFactory
{
    private final Proxy _proxy;

    public ProxyAwareSocketFactory(Proxy proxy)
    {
        assert proxy.type() == Type.DIRECT || proxy.type() == Type.HTTP :
                "Using unsupported proxy type: " + proxy.type();
        _proxy = proxy;
    }

    @Override
    public Socket createSocket(String host, int port)
            throws IOException
    {
        if (_proxy.type() == Type.DIRECT) {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        Socket socket = new HttpProxySocket(_proxy);
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException
    {
        if (_proxy.type() == Type.DIRECT) {
            return SocketFactory.getDefault().createSocket(host, port, localHost, localPort);
        }

        Socket socket = new HttpProxySocket(_proxy);
        socket.bind(new InetSocketAddress(localHost, localPort));
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port)
            throws IOException
    {
        if (_proxy.type() == Type.DIRECT) {
            return SocketFactory.getDefault().createSocket(host, port);
        }

        Socket socket = new HttpProxySocket(_proxy);
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress localHost, int localPort)
            throws IOException
    {
        if (_proxy.type() == Type.DIRECT) {
            return SocketFactory.getDefault().createSocket(host, port, localHost, localPort);
        }

        Socket socket = new HttpProxySocket(_proxy);
        socket.bind(new InetSocketAddress(localHost, localPort));
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }
}
