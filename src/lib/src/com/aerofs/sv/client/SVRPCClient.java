/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.client;

import com.aerofs.base.BaseParam.SV;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Sv.PBSVCall;
import com.aerofs.proto.Sv.PBSVReply;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import static com.aerofs.lib.LibParam.FILE_BUF_SIZE;
import static java.net.HttpURLConnection.HTTP_OK;

final class SVRPCClient
{
    private static final Logger l = Loggers.getLogger(SVRPCClient.class);

    private final String _svurl;

    SVRPCClient(String svurl)
    {
        l.debug("set sv:{}", svurl);

        this._svurl = svurl;
    }

    /**
     * Make an RPC call to SV
     * @param svpb PB message to send to SV
     * @param file a file whose contents will be read and transferred as part of this RPC's
     * request content
     *
     * <strong>IMPORTANT:</strong> you can do multiple {@code doRPC} calls simultaneously
     */
    final void doRPC(PBSVCall svpb, @Nullable File file)
        throws IOException, AbstractExWirable
    {
        int contentLength = (int) (determineDelimitedSerializedSize(svpb) + (file == null ? 0 : file.length()));

        HttpsURLConnection conn = null;
        try {
            l.debug("start sv rpc conlen:{}", contentLength);

            conn = newSVConnection(_svurl,  contentLength);
            sendSVRequest(conn, svpb, file);
            readSVResponse(conn);

            l.debug("finish sv rpc");
        } catch (IOException e) {
            l.warn("fail send err", LogUtil.suppress(e));
            throw e;
        } catch (AbstractExWirable e) {
            l.warn("fail send remote err", LogUtil.suppress(e));
            throw e;
        } finally {
            if (conn != null) {
                l.warn("diconnect from sv");
                conn.disconnect();
            }
        }
    }

    private static HttpsURLConnection newSVConnection(String svurl, int contentLength)
            throws IOException
    {
        assert contentLength > 0 : ("invalid content length:" + contentLength);

        URL sv = new URL(svurl);
        HttpsURLConnection conn = (HttpsURLConnection) sv.openConnection();

        conn.setUseCaches(false);
        conn.setConnectTimeout((int) SV.CONNECT_TIMEOUT);
        conn.setReadTimeout((int) SV.READ_TIMEOUT);
        conn.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.FORM_DATA.toString());
        conn.setFixedLengthStreamingMode(contentLength);
        conn.setDoOutput(true);
        conn.connect();

        return conn;
    }

    private static void sendSVRequest(HttpsURLConnection conn, PBSVCall svpb, @Nullable File file)
            throws IOException
    {
        OutputStream httpStream = null;
        try {
            httpStream = conn.getOutputStream();
            writeSVPBToHttpStream(httpStream, svpb);
            if (file != null) writeFileToHttpStream(httpStream, file);
        } finally {
            if (httpStream != null) httpStream.close();
        }
    }

    private static void readSVResponse(HttpsURLConnection conn)
            throws IOException, AbstractExWirable
    {
        l.debug("read response");

        int code = conn.getResponseCode();
        if (code != HTTP_OK) {
            l.warn("fail sv call code {}", code);
            throw new IOException("fail sv call code:" + code);
        }

        InputStream responseStream = null;
        DataInputStream dis = null;
        try {
            responseStream =  conn.getInputStream();

            dis =  new DataInputStream(responseStream);
            byte[] responseBytes = new byte[conn.getContentLength()];
            dis.readFully(responseBytes);

            PBSVReply reply = PBSVReply.parseFrom(responseBytes);
            if (reply.hasException()) throw Exceptions.fromPB(reply.getException());
        } finally {
            if (responseStream != null) closeSilently(responseStream);
            if (dis != null) closeSilently(dis);
        }
    }

    private static int determineDelimitedSerializedSize(PBSVCall svpb)
            throws IOException
    {
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            svpb.writeDelimitedTo(baos);
            return baos.size();
        } finally {
            if (baos != null) baos.close();
        }
    }

    private static void writeSVPBToHttpStream(OutputStream httpStream, PBSVCall svpb)
            throws IOException
    {
        ByteArrayOutputStream baos = null;
        try {
            baos = new ByteArrayOutputStream();
            svpb.writeDelimitedTo(baos);
            baos.writeTo(httpStream);
        } finally {
            if (baos != null) baos.close();
        }
    }

    private static void writeFileToHttpStream(OutputStream httpStream, File file)
            throws IOException
    {
        long bytes = 0;

        FileInputStream fileStream = null;
        try {
            fileStream = new FileInputStream(file);
            byte[] buf = new byte[FILE_BUF_SIZE];
            while (true) {
                int read = fileStream.read(buf);
                if (read == -1) break;

                bytes += read;

                l.trace("read total: {}", bytes);
                httpStream.write(buf, 0, read);
                l.trace("send total: {}", bytes);
            }
        } finally {
            if (fileStream != null) fileStream.close();
        }
    }

    private static void closeSilently(InputStream stream)
    {
        try {
            stream.close();
        } catch (IOException e) {
            // ignored
        }
    }
}
