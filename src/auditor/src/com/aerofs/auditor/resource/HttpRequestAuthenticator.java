/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.auditor.resource;

import com.aerofs.audit.client.AuditClient;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.sun.jndi.url.dns.dnsURLContext;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * This ChannelHandler accepts HttpRequest messages on the messageReceived callback.
 *
 * If the Aerofs-Auth-Required field is set "True", identity verification occurs:
 *
 *  - the requestor must submit a valid client certificate;
 *
 * - the following HTTP request header fields must be supplied:
 *  AeroFS-UserID
 *  AeroFS-DeviceID
 *
 * - the given UserID and DeviceID are combined & hashed; the result must match the CName
 * in the client certificate.
 *
 * After verification, this handler removes itself from the pipeline.
 *
 * IMPORTANT: This handler does not pay attention to the CRL. (TODO)
 * Revoked client certificates will pass CName verification. We will allow those users to
 * submit audit events, firm in the knowledge that this Authenticator type does not allow
 * any sync or other privileged actions. We can get the CRL from SP (Verkehr knows how for example)
 */
public class HttpRequestAuthenticator extends SimpleChannelHandler
{
    private final static Logger l = LoggerFactory.getLogger(HttpRequestAuthenticator.class);

    // HTTP headers populated by the nginx frontend:
    public final static String HEADER_AUTH_REQ = "AeroFS-Auth-Required";
    public final static String HEADER_DNAME = "DName";
    public final static String HEADER_VERIFY = "Verify";
    public final static String HEADER_VERIFY_OK = "SUCCESS";

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        Preconditions.checkArgument(e.getMessage() instanceof HttpRequest);
        HttpRequest req = (HttpRequest)e.getMessage();

        try {
            if (Boolean.parseBoolean(req.getHeader(HEADER_AUTH_REQ))) throwIfInvalidCreds(ctx, req);
        } catch (Exception ex) {
            HttpResponseStatus status = (ex instanceof ExBadCredential) ?
                    HttpResponseStatus.UNAUTHORIZED : HttpResponseStatus.BAD_REQUEST;
            ctx.getChannel().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, status));
            throw ex;
        }

        l.debug("Removing HTTP certificate authenticator");
        ctx.getPipeline().remove(this);
        ctx.sendUpstream(e);
    }

    /**
     * Throw a reasonable exception if the HTTP headers don't meet the identity-verification
     * requirements described above.
     */
    private void throwIfInvalidCreds(ChannelHandlerContext ctx, HttpRequest req)
            throws ExBadCredential, IOException, ExFormatError
    {
        String verifyVal = req.getHeader(HEADER_VERIFY);
        String dNameVal = req.getHeader(HEADER_DNAME);
        String userIdVal = req.getHeader(AuditClient.HEADER_UID);
        String deviceIdVal = req.getHeader(AuditClient.HEADER_DID);
        l.debug("Auth required, checking header fields");

        // Header-content preconditions. You must supply authorization, Verify, and DName.
        // Verify must be "SUCCESS".
        if (Strings.isNullOrEmpty(verifyVal)
                || Strings.isNullOrEmpty(dNameVal)
                || !verifyVal.equals(HEADER_VERIFY_OK)
                || Strings.isNullOrEmpty(userIdVal)
                || Strings.isNullOrEmpty(deviceIdVal)) {
            l.warn("Header certificate auth error. uid:{} did:{} v:{} dn:{}",
                    userIdVal, deviceIdVal, verifyVal, dNameVal);
            throw new ExBadCredential("Invalid certificate/identity parameters");
        }

        if ( !claimedCName(userIdVal, deviceIdVal)
                .equals(expectedCName(dNameVal))) {
            l.warn("UID/DID-cert mismatch. uid:{} did:{} DN:{}", userIdVal, deviceIdVal, dNameVal);
            throw new ExBadCredential("UID/DID do not match certificate CName");
        }
    }

    /**
     * Pull the parameter value CN=<val> out of the DName header.
     * @throws com.aerofs.base.ex.ExFormatError CN not found or DName malformed
     */
    private @Nonnull String expectedCName(String dname) throws IOException, ExFormatError
    {
        final String Label = "CN=";
        for (String x : dname.split("/")) {
            if (x.trim().startsWith(Label)) return x.trim().substring(Label.length());
        }
        l.warn("Error parsing DName {}; no {} found", dname, Label);
        throw new ExFormatError("DName value missing " + Label);
    }

    /**
     * Given a (textual) userId and deviceId, return the expected hashed value as defined in
     * BaseSecUtil.
     */
    private String claimedCName(String userId, String deviceId) throws ExFormatError
    {
        return BaseSecUtil.getCertificateCName(
                UserID.fromInternalThrowIfNotNormalized(userId),
                new DID(deviceId));
    }
}
