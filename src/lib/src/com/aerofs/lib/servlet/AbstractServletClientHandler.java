package com.aerofs.lib.servlet;

import com.aerofs.lib.Base64;
import com.aerofs.lib.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public abstract class AbstractServletClientHandler
{
    private final URL _url;
    private String _cookie;

    private String _postParamProtocol;
    private String _postParamData;
    private int _protocolVersion;

    protected AbstractServletClientHandler(URL url, String postParamProtocol, String postParamData,
            int protocolVersion)
    {
        this._url = url;
        this._postParamProtocol = postParamProtocol;
        this._postParamData = postParamData;
        this._protocolVersion = protocolVersion;
    }

    public ListenableFuture<byte[]> doRPC(byte[] data)
    {
        final UncancellableFuture<byte[]> future = UncancellableFuture.create();
        try {
            // Connect
            URLConnection c = _url.openConnection();
            c.setDoOutput(true);
            if (_cookie != null) c.setRequestProperty("Cookie", _cookie);
            c.connect();

            // Construct data
            final String charSet = "UTF-8";
            final String encodedData = URLEncoder.encode(_postParamProtocol, charSet)
                    + "=" + URLEncoder.encode(Integer.toString(_protocolVersion), charSet)
                    + "&" + URLEncoder.encode(_postParamData, charSet)
                    + "=" + URLEncoder.encode(Base64.encodeBytes(data), charSet);

            // Send call
            OutputStreamWriter wr = new OutputStreamWriter(c.getOutputStream());
            try {
                wr.write(encodedData);
                // TODO unsure whether this is necessary
                wr.flush();
            } finally {
                wr.close();
            }

            // Set cookie for the next call
            String setCookie = c.getHeaderField("Set-Cookie");
            if (setCookie != null) _cookie = setCookie.split(";")[0];

            if (c.getContentLength() < 0) {
                throw new IOException(
                        "cannot parse reply. content length = " + c.getContentLength());
            }

            InputStream is = c.getInputStream();
            DataInputStream dis = new DataInputStream(is);
            try {
                byte[] bs = new byte[c.getContentLength()];
                dis.readFully(bs);

                // Process reply
                future.set(Base64.decode(bs));
            } finally {
                is.close();
            }

        } catch (IOException e) {
            // If anything went wrong, dump the exception in the future and return to user.
            future.setException(e);
        }

        return future;
    }
}