/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.net;

import com.aerofs.base.Base64;
import com.aerofs.base.Loggers;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public abstract class AbstractHttpRpcClient
{
    private static final Logger LOGGER = Loggers.getLogger(AbstractHttpRpcClient.class);

    private final URL _url;
    private final String _postParamProtocol;
    private final String _postParamData;
    private final int _protocolVersion;
    private final IURLConnectionConfigurator _connectionConfigurator;

    private volatile String _cookie;

    protected AbstractHttpRpcClient(URL url, String postParamProtocol, String postParamData,
            int protocolVersion, IURLConnectionConfigurator connectionConfigurator)
    {
        _url = url;
        _postParamProtocol = postParamProtocol;
        _postParamData = postParamData;
        _protocolVersion = protocolVersion;
        _connectionConfigurator = connectionConfigurator;
    }

    public ListenableFuture<byte[]> doRPC(byte[] data)
    {
        try {
            // Connect
            URLConnection connection = _url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            if (_cookie != null) connection.setRequestProperty("Cookie", _cookie);

            _connectionConfigurator.configure(connection);

            connection.connect();

            // Construct data
            final String charSet = "UTF-8";
            final String encodedData = URLEncoder.encode(_postParamProtocol, charSet)
                    + "=" + URLEncoder.encode(Integer.toString(_protocolVersion), charSet)
                    + "&" + URLEncoder.encode(_postParamData, charSet)
                    + "=" + URLEncoder.encode(Base64.encodeBytes(data), charSet);

            // Send call
            try (OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream())) {
                wr.write(encodedData);
                // TODO unsure whether this is necessary
                wr.flush();
            }

            LOGGER.debug("url={} headers={}", _url, connection.getHeaderFields());

            // Set cookie for the next call
            String setCookie = connection.getHeaderField("Set-Cookie");
            if (setCookie != null) _cookie = setCookie.split(";")[0];

            if (connection.getContentLength() < 0) {
                throw new IOException(
                        "cannot parse reply. content length = " + connection.getContentLength());
            }

            InputStream is = connection.getInputStream();
            DataInputStream dis = new DataInputStream(is);
            try {
                byte[] bs = new byte[connection.getContentLength()];
                dis.readFully(bs);

                // Process reply
                return Futures.immediateFuture(Base64.decode(bs));
            } finally {
                is.close();
            }

        } catch (Throwable e) {
            // If anything went wrong, dump the exception in the future and return to user.
            return Futures.immediateFailedFuture(e);
        }
    }
}
