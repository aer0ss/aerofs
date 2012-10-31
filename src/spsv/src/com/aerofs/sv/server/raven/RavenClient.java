package com.aerofs.sv.server.raven;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * User: ken cochrane
 * Date: 2/6/12
 * Time: 11:59 AM
 */

@SuppressWarnings("unchecked")
public class RavenClient {

    private static final String RAVEN_JAVA_VERSION = "Raven-Java 0.6";
    private RavenConfig config;
    private String sentryDSN;
    private String lastID;
    private MessageSender messageSender;

    public RavenClient() {
        this.sentryDSN = System.getenv("SENTRY_DSN");
        if (this.sentryDSN == null || this.sentryDSN.length() == 0) {
            throw new RuntimeException("You must provide a DSN to RavenClient");
        }
        setConfig(new RavenConfig(this.sentryDSN));
    }

    public RavenClient(String sentryDSN) {
        this.sentryDSN = sentryDSN;
        setConfig(new RavenConfig(sentryDSN));
    }

    public RavenClient(String sentryDSN, String proxy, boolean naiveSsl) {
        this.sentryDSN = sentryDSN;
        setConfig(new RavenConfig(sentryDSN, proxy, naiveSsl));
    }

    public RavenConfig getConfig() {
        return config;
    }

    public void setConfig(RavenConfig config) {
        this.config = config;
        try {
            String protocol = config.getProtocol();
            if ("udp".equals(protocol)) {
                messageSender = new UdpMessageSender(config, null);
            } else {
                URL endpoint = new URL(config.getSentryURL());
                if (config.isNaiveSsl() && "https".equals(protocol)) {
                    messageSender = new NaiveHttpsMessageSender(config, endpoint);
                } else {
                    messageSender = new MessageSender(config, endpoint);
                }
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Sentry URL is malformed", e);
        }
    }

    public String getSentryDSN() {
        return sentryDSN;
    }

    public void setSentryDSN(String sentryDSN) {
        this.sentryDSN = sentryDSN;
    }

    public void setLastID(String lastID) {
        this.lastID = lastID;
    }

    public String getLastID() {
        return lastID;
    }


    private String buildJSON(RavenTrace rt)
    {
        JSONObject obj = new JSONObject();
        JSONObject jsonStack = buildStacktrace(rt);
        buildJSONCommon(obj, rt.getMessage(), RavenUtils.getTimestampString(rt.getTimestamp()), "logger", 50, rt.getUser(), rt.getDeviceId(), rt.getVersion());
        obj.put("culprit", determineCulprit(rt));
        obj.put("checksum", RavenUtils.calculateChecksum(jsonStack.toJSONString()));
        obj.put("sentry.interfaces.Exception", buildException(rt));
        obj.put("sentry.interfaces.Stacktrace", jsonStack);

        return obj.toJSONString();
    }

    private String buildJSONCommon(JSONObject obj, String message, String timestamp, String loggerClass, int logLevel, String user, String deviceId, String version)
    {
        String lastID = RavenUtils.getRandomUUID();
        obj.put("event_id", lastID); //Hexadecimal string representing a uuid4 value.
        obj.put("timestamp", timestamp);
        obj.put("message", message);
        obj.put("project", getConfig().getProjectId());
        obj.put("level", logLevel);
        obj.put("logger", loggerClass);
        obj.put("server_name", RavenUtils.getHostname());
        obj.put("sentry.interfaces.User", buildUser(user,deviceId, version));
        setLastID(lastID);
        return obj.toJSONString();
    }

    private String determineCulprit(RavenTrace rt)
    {
        return rt.getTraceElements()[0].getClassName() + "." + rt.getTraceElements()[0].getMethodName();
    }

    private JSONObject buildUser(String user, String deviceId, String version) {
        JSONObject json = new JSONObject();
        json.put("is_authenticated", true);
        json.put("id", version);
        json.put("username", deviceId);
        json.put("email", user);

        return json;
    }

    private JSONObject buildException(RavenTrace rt) {
        JSONObject json = new JSONObject();
        json.put("type", rt.getTraceElements()[0].getClassName());
        json.put("value", rt.getMessage());
        //yuris: module is optional, skipping for now

        return json;
    }


    private JSONObject buildStacktrace(RavenTrace rt) {
        JSONArray array = new JSONArray();
        JSONObject frame = new JSONObject();
        RavenTraceElement[] rte = rt.getTraceElements();

        for (int i = 0; i < rte.length; i++)
        {
            frame = new JSONObject();
            frame.put("filename", rte[i].getClassName());
            frame.put("function", rte[i].getMethodName());
            frame.put("lineno", rte[i].getLineNumber());
            array.add(frame);
        }

        JSONObject stacktrace = new JSONObject();
        stacktrace.put("frames", array);
        return stacktrace;
    }

    /**
     * Take the raw message body and get it ready for sending. Encode and compress it.
     *
     * @param jsonMessage the message we want to prepare
     * @return Encode and compressed version of the jsonMessage
     */
    private String buildMessageBody(String jsonMessage) {
        //need to zip and then base64 encode the message.
        // compressing doesn't work right now, sentry isn't decompressing correctly.
        // come back to it later.
        //return compressAndEncode(jsonMessage);

        // in the meantime just base64 encode it.
        return encodeBase64String(jsonMessage.getBytes());

    }


    private String buildMessage(RavenTrace rt) {
        String jsonMessage = buildJSON(rt);

        return buildMessageBody(jsonMessage);
    }
    /**
     * Send the message to the sentry server.
     *
     * @param messageBody the encoded json message we are sending to the sentry server
     * @param timestamp   the timestamp of the message
     */
    private void sendMessage(String messageBody, long timestamp) {
        try {
            messageSender.send(messageBody, timestamp);
        } catch (IOException e) {
            // Eat the errors, we don't want to cause problems if there are major issues.
            e.printStackTrace();
        }
    }

    public String captureException(RavenTrace rt)
    {
        String body = buildMessage(rt);
        sendMessage(body, rt.getTimestamp());
        return getLastID();
    }
    public static class MessageSender {

        public final RavenConfig config;
        public final URL endpoint;

        public MessageSender(RavenConfig config, URL endpoint) {
            this.config = config;
            this.endpoint = endpoint;
        }

        public void send(String messageBody, long timestamp) throws IOException {
            // get the hmac Signature for the header
            String hmacSignature = RavenUtils.getSignature(messageBody, timestamp, config.getSecretKey());

            // get the auth header
            String authHeader = buildAuthHeader(hmacSignature, timestamp, config.getPublicKey());

            doSend(messageBody, authHeader);
        }

        protected void doSend(String messageBody, String authHeader) throws IOException {
            HttpURLConnection connection = getConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("X-Sentry-Auth", authHeader);
            OutputStream output = connection.getOutputStream();
            output.write(messageBody.getBytes());
            output.close();
            connection.connect();
            InputStream input = connection.getInputStream();
            input.close();
        }

        /**
         * Build up the sentry auth header in the following format.
         * <p/>
         * The header is composed of a SHA1-signed HMAC, the timestamp from when the message was generated, and an
         * arbitrary client version string. The client version should be something distinct to your client,
         * and is simply for reporting purposes.
         * <p/>
         * X-Sentry-Auth: Sentry sentry_version=2.0,
         * sentry_signature=<hmac signature>,
         * sentry_timestamp=<signature timestamp>[,
         * sentry_key=<public api key>,[
         * sentry_client=<client version, arbitrary>]]
         *
         * @param hmacSignature SHA1-signed HMAC
         * @param timestamp     is the timestamp of which this message was generated
         * @param publicKey     is either the public_key or the shared global key between client and server.
         * @return String version of the sentry auth header
         */
        protected String buildAuthHeader(String hmacSignature, long timestamp, String publicKey) {
            StringBuilder header = new StringBuilder();
            header.append("Sentry sentry_version=2.0,sentry_signature=");
            header.append(hmacSignature);
            header.append(",sentry_timestamp=");
            header.append(timestamp);
            header.append(",sentry_key=");
            header.append(publicKey);
            header.append(",sentry_client=");
            header.append(RAVEN_JAVA_VERSION);

            return header.toString();
        }

        protected HttpURLConnection getConnection() throws IOException {
            return (HttpURLConnection) endpoint.openConnection(config.getProxy());
        }
    }

    public static class NaiveHttpsMessageSender extends MessageSender {

        public final HostnameVerifier hostnameVerifier;

        public NaiveHttpsMessageSender(RavenConfig config, URL endpoint) {
            super(config, endpoint);
            this.hostnameVerifier = new AcceptAllHostnameVerifier();
        }

        @Override
        protected HttpURLConnection getConnection() throws IOException {
            HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection(config.getProxy());
            connection.setHostnameVerifier(hostnameVerifier);
            return connection;
        }
    }

    public static class UdpMessageSender extends MessageSender {

        private final DatagramSocket socket;

        public UdpMessageSender(RavenConfig config, URL endpoint) {
            super(config, endpoint);
            try {
                socket = new DatagramSocket();
                socket.connect(new InetSocketAddress(config.getHost(), config.getPort()));
            } catch (SocketException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        protected void doSend(String messageBody, String authHeader) throws IOException {
            byte[] message = (authHeader + "\n\n" + messageBody).getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(message, message.length);
            socket.send(packet);
        }

    }

    public static class AcceptAllHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession sslSession) {
            return true;
        }
    }

}
