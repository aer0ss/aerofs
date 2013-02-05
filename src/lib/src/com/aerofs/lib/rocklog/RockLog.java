/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.base.C;
import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RockLog
{
    private static final Logger l = Util.l(RockLog.class);
    private static final RockLog _instance = new RockLog();
    private final int SOCKET_TIMEOUT = (int) (10*C.SEC);
    private final InetSocketAddress ROCKLOG_SERVER = new InetSocketAddress("localhost", 8000);
    private RockLog() {} // prevent initialization

    /*
    TODO (GS)
        - do not resend automatic defects
        - zip and send logs
        - send Cfg DB in the defect
     */

    public static Defect newDefect(String name)
    {
        return new Defect(_instance, name);
    }

    public static Metric newMetric(String name)
    {
        return new Metric(_instance, name);
    }

    void sendAsync(final IRockLogMessage message)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                send(message);
            }
        },"rocklog-send").start();
    }

    void send(IRockLogMessage message)
    {
        try {
            l.info("Sending RockLog message...");
            rpc(message.getJSON().getBytes(), message.getURLPath());
        } catch (Throwable e) {
            l.warn("Could not send message to RockLog: " + Util.e(e, IOException.class));
        }
    }

    private void rpc(byte[] data, String urlPath) throws Exception
    {
        Socket s = send(data, urlPath);
        try {
            recv(s);
        } finally {
            if (!s.isClosed()) s.close();
        }
    }

    private Socket send(byte[] data, String urlPath) throws IOException
    {
        final Socket s = new Socket();
        s.connect(ROCKLOG_SERVER, SOCKET_TIMEOUT);
        s.setSoTimeout(SOCKET_TIMEOUT);

        final String header = "POST " + urlPath + " HTTP/1.0\r\n"
                + "Connection: close\r\n"
                + "Content-type: application/json\r\n"
                + "Content-Length: " + data.length + "\r\n\r\n";

        OutputStream os = s.getOutputStream();
        try {
            os.write(header.getBytes());
            os.write(data);
            return s;
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    private void recv(Socket s) throws IOException
    {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        try {
            final String expected = "HTTP/1.1 200";
            byte[] buf = new byte[expected.length()];
            dis.readFully(buf);
            String status = new String(buf);
            if (!status.equals(expected)) {
                throw new IOException("RockLog returned: " + status);
            }
        } finally {
            s.close();
        }
    }
}
