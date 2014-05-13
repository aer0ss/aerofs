/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.testlib.AbstractTest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.subethamail.smtp.server.SessionIdFactory;
import org.subethamail.smtp.server.TimeBasedSessionIdFactory;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;
import javax.mail.Session;
import java.util.Properties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Some simple tests for the email sender.
 * This is using "Wiser" which is a test server from the SubEthaSMTP project. Be gentle
 * with Wiser, it's not very smart.
 *
 * In an ideal world, we would use more mocking of the AsyncEmailSender internals. However, that
 * would let us escape without testing our use of javax.mail, which is occasionally problematic.
 */
public class TestEmailSender extends AbstractTest
{
    static final int WAIT_INTERVALS = 5;
    static final long WAIT_SLEEP = 1 * C.SEC;
    static Wiser _wiser;
    static int _port;
    static TimeoutFactory _idFactory;

    private Properties getProperties(String host, Integer port, String username, String password,
            boolean enable_tls)
    {
        Properties properties = new Properties();
        // N.B. 127.0.0.1 is not "localhost", so the server will be considered "external"
        properties.setProperty("email.sender.public_host", host);
        properties.setProperty("email.sender.public_port", Integer.toString(port));
        properties.setProperty("email.sender.public_username", username);
        properties.setProperty("email.sender.public_password", password);
        properties.setProperty("email.sender.public_enable_tls", Boolean.toString(enable_tls));
        properties.setProperty("email.sender.timeout", String.valueOf(2 * C.SEC));
        return properties;
    }

    // create one Wiser server for all the test cases in this class...
    // We use a 2-second command timeout for all tests in this class.
    @BeforeClass
    public static void setUpClass() throws Exception
    {
        _wiser = new Wiser(0);
        _wiser.start();
        _port = _wiser.getServer().getPort();
        _idFactory = new TimeoutFactory();
        _wiser.getServer().setSessionIdFactory(_idFactory);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        _wiser.stop();
    }

    @Before public void setUp() { _wiser.getMessages().clear(); }

    @Test
    public void testSendPublicEmail() throws Exception
    {
        Properties properties = getProperties("localhost", _port, "", "", false);
        ConfigurationProperties.setProperties(properties);
        AsyncEmailSender.create().sendPublicEmail(
                "f1@example.com", null, "to1@example.com", "r1@example.com",
                "Subject subject", "Body body", null);
        waitForMessages(1);

        for (WiserMessage msg : _wiser.getMessages()) {
            Assert.assertEquals("f1@example.com", msg.getEnvelopeSender());
            Assert.assertEquals("to1@example.com", msg.getEnvelopeReceiver());
        }
    }

    @Test
    public void testSendPublicEmailFromSupport()
            throws Exception
    {
        Properties properties = getProperties("localhost", _port, "", "", false);
        ConfigurationProperties.setProperties(properties);
        AsyncEmailSender.create().sendPublicEmailFromSupport(null, "to2@example.com", "r2@example.com",
                "Subject2 subject2", "Body body", null);
        waitForMessages(1);

        for (WiserMessage msg : _wiser.getMessages()) {
            Assert.assertEquals(msg.getEnvelopeSender(), WWW.SUPPORT_EMAIL_ADDRESS);
            Assert.assertEquals(msg.getEnvelopeReceiver(), "to2@example.com");
        }
    }

    /**
     * Test that the timeout functionality works. We send a message that will get
     * stuck in SMTP. We expect the transport to bail out after a few seconds and
     * keep working the queue. If it does not bail out, the waitMessages() will
     * fail waiting for all the queued msgs.
     */
    @Test
    public void testTimeout() throws Exception
    {
        Properties properties = getProperties("localhost", _port, "", "", false);
        ConfigurationProperties.setProperties(properties);
        AbstractEmailSender _emailSender = AsyncEmailSender.create();

        _idFactory.delayMsec = 180000;
        _emailSender.sendPublicEmailFromSupport(null, "die@bart.die", "I@said.die",
                "It's German", "For 'the bart, the'", null);
        _emailSender.sendPublicEmailFromSupport(null, "to3@example.com", "r@example.com",
                "This mail is ok", "Oh yes it is", null);
        _emailSender.sendPublicEmailFromSupport(null, "to3@example.com", "r@example.com",
                "This mail is ok", "Oh yes it is", null);
        _emailSender.sendPublicEmailFromSupport(null, "to3@example.com", "r@example.com",
                "This mail is ok", "Oh yes it is", null);
        waitForMessages(3);

        for (WiserMessage msg : _wiser.getMessages()) {
            Assert.assertEquals("to3@example.com", msg.getEnvelopeReceiver());
        }
        // this way JUnit will not hang around waiting for a spawned thread to complete:
        _idFactory.isRunning = false;
    }

    @Test
    public void testShouldSendEmailToExternalServer() throws Exception
    {
        // N.B. 127.0.0.1 is not "localhost", so it is treated like an external mail server
        Properties properties = getProperties("127.0.0.1", _port, "", "", false);
        ConfigurationProperties.setProperties(properties);

        AsyncEmailSender.create().sendPublicEmail(
                "f1@example.com", null, "to1@example.com", "r1@example.com",
                "Subject subject", "Body body", null);
        waitForMessages(1);

        for (WiserMessage msg : _wiser.getMessages()) {
            Assert.assertEquals("f1@example.com", msg.getEnvelopeSender());
            Assert.assertEquals("to1@example.com", msg.getEnvelopeReceiver());
        }
    }

    @Test
    public void testMailSessionShouldUseTLS() throws Exception
    {
        // N.B. 127.0.0.1 is not "localhost", so it is treated like an external mail server
        Properties properties = getProperties("127.0.0.1", _port, "", "", true);
        ConfigurationProperties.setProperties(properties);
        AsyncEmailSender mailer = AsyncEmailSender.create();
        Session session = mailer.getMailSession();

        assertEquals("false", session.getProperty("mail.smtp.auth"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.required"));
    }

    @Test
    public void testMailSessionShouldUseTLSAndAuth() throws Exception
    {
        // N.B. 127.0.0.1 is not "localhost", so it is treated like an external mail server
        Properties properties = getProperties("127.0.0.1", _port, "userid", "hunter2", true);
        ConfigurationProperties.setProperties(properties);
        AsyncEmailSender mailer = AsyncEmailSender.create();
        Session session = mailer.getMailSession();

        assertEquals("true", session.getProperty("mail.smtp.auth"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.required"));
    }

    private static class TimeoutFactory implements SessionIdFactory
    {
        @Override
        public String create()
        {
            // slightly tricky logic to sleep _once_ if delayMsec is non-zero
            int tempVal = 0;
            synchronized(this) {
                if (delayMsec > 0) {
                    tempVal = delayMsec;
                    delayMsec = 0;
                }
            }
            safeSleep(tempVal);
            return underlying.create();
        }

        // sleep only if delayMs is non-zero, and swallow the annoying InterruptedException
        private void safeSleep(int requested)
        {
            int remaining = requested;
            while (remaining > 0 && isRunning) {
                try { Thread.sleep(250); } catch (InterruptedException e) { }
                remaining -= 250;
            }
        }

        public int delayMsec = 0;
        public boolean isRunning = true;
        private SessionIdFactory underlying = new TimeBasedSessionIdFactory();
    }

    private void waitForMessages(int expected) throws InterruptedException
    {
        for (int i=0; i < WAIT_INTERVALS; i++) {
            if (_wiser.getMessages().size() >= expected) { return; }
            Thread.sleep(WAIT_SLEEP);
        }
        fail("Timed out waiting for " + expected + " messages; "
                + _wiser.getMessages().size() + " were received.");
    }
}
