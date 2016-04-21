/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.DID;
import com.aerofs.ids.ExInvalidID;
import com.aerofs.ids.UserID;
import com.aerofs.rest.auth.AuthTokenExtractor;
import com.google.inject.Inject;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.HttpRequestContext;
import org.slf4j.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This authentication provider checks that a request was made by a user with a trustworthy cert
 * signed by the internal CA.
 *
 * Note that this authentication provider expects to be placed behind a SSL-terminating reverse
 * proxy which provides the following trusted headers:
 *  - DName
 *  - Verify
 *  - Serial
 *
 * To actually verify that the certificate is still valid, the provider must also have access to
 * the certificate database.
 */
public final class CertAuthExtractor implements AuthTokenExtractor<CertAuthToken>
{
    private final Logger l = Loggers.getLogger(CertAuthExtractor.class);

    private CertificateRevocationChecker _crc;

    private final static Base64.Decoder base64 = Base64.getDecoder();

    private final static String HEADER_DNAME = "DName";
    private final static String HEADER_VERIFY = "Verify";
    private final static String HEADER_VERIFY_OK = "SUCCESS";
    private final static String HEADER_SERIAL = "Serial";

    // We expect a header that looks like:
    // Authorization: Aero-Device-Cert <base64(userid)> <hex(did)>
    private final static Pattern DEVICE_CERT_PATTERN =
            Pattern.compile("Aero-Device-Cert ([0-9a-zA-Z+/]+=*) ([0-9a-f]{32})");

    public interface CertificateRevocationChecker {
        boolean isRevoked(long serial) throws ExNotFound, SQLException;
    }

    @Inject
    public CertAuthExtractor(CertificateRevocationChecker crc)
    {
        _crc = crc;
    }

    @Override
    public String challenge()
    {
        return "Aero-Device-Cert realm=\"AeroFS\"";
    }

    @Override
    public CertAuthToken extract(HttpContext context)
    {
        HttpRequestContext req = context.getRequest();
        // We'll attempt cert authentication on every request before OAuth, so only log requests
        // that appear to be trying to use cert-based auth.
        if (!isInterestingRequest(req)) return null;

        // Okay, the request definitely tried to use Aero-Device-Cert auth.  If anything else
        // is amiss, we should just throw.
        CertContext certContext = extractCertContext(req);
        validateAuth(certContext);

        // Everything checks out.  Construct a new IUserAuthToken.
        l.info("Authentic request from {}:{} (cert)", certContext.userid,
                certContext.did.toStringFormal());
        return new CertAuthToken(certContext.userid, certContext.did);
    }

    private static class CertContext
    {
        public final UserID userid;
        public final DID did;
        public final String distinguishedName;
        public final boolean verified;
        public final long _serial;
        public CertContext(UserID user, DID device, String dname, boolean verify, long serial)
        {
            userid = user;
            did = device;
            distinguishedName = dname;
            verified = verify;
            _serial = serial;
        }
    }

    private static String getExpectedHeader(HttpRequestContext req, String header)
            throws CertAuthFailureException
    {
        List<String> headers = req.getRequestHeader(header);
        if (headers == null || headers.size() != 1) {
            throw new CertAuthFailureException("Missing or duplicate header: " + header);
        }
        return headers.get(0);
    }

    private CertContext extractCertContext(HttpRequestContext req)
    {
        try {
            String userid_string;
            String did_string;
            Matcher m = DEVICE_CERT_PATTERN.matcher(
                    getExpectedHeader(req, HttpHeaders.AUTHORIZATION));
            if (m.matches()) {
                userid_string = m.group(1);
                did_string = m.group(2);
            } else {
                // Malformed authorization header.
                throw new CertAuthFailureException("Invalid authorization header");
            }

            // Check validity of device id header
            DID did;
            try {
                did = new DID(DID.fromStringFormal(did_string));
            } catch (ExInvalidID e) {
                throw new CertAuthFailureException("Couldn't decode device id " + did_string);
            }

            // Check validity of user id header
            UserID userid;
            try {
                userid = UserID.fromExternal(BaseUtil.utf2string(base64.decode(userid_string)));
            } catch (IllegalArgumentException|ExInvalidID e) {
                throw new CertAuthFailureException("Couldn't decode user id " + userid_string);
            }

            long serial;
            try {
                serial = Long.parseLong(getExpectedHeader(req, HEADER_SERIAL), 16);
            } catch (NumberFormatException e) {
                throw new CertAuthFailureException("Invalid serial number");
            }

            return new CertContext(userid, did,
                    getExpectedHeader(req, HEADER_DNAME),
                    getExpectedHeader(req, HEADER_VERIFY).equals(HEADER_VERIFY_OK),
                    serial);
        } catch (CertAuthFailureException e) {
            l.warn("cert auth failed:", e);
            throw invalidAuthorizationException(e.getMessage());
        }
    }

    private void validateAuth(CertContext ctx)
    {
        try {
            // If the cert didn't verify, no authentication for you
            if (!ctx.verified) {
                throw invalidAuthorizationException("Client certificate missing or untrusted");
            }

            // Check that the certificate's DName matches what we'd expect for that UserID/DID pair
            String cname = getCN(ctx.distinguishedName);
            String expectedCN = BaseSecUtil.getCertificateCName(ctx.userid, ctx.did);
            if (!expectedCN.equals(cname)) {
                throw invalidAuthorizationException("mismatching cname and purported identity");
            }

            // If the cert's serial is missing or invalid, no authentication for you
            verifyCertNotRevoked(ctx._serial);
        } catch (InternalFailureException e) {
            l.warn("cert auth failed:", e);
            // Don't leak internal failures
            throw invalidAuthorizationException("failed to validate purported identity");
        }
    }

    private boolean isInterestingRequest(HttpRequestContext req)
    {
        // check the Authorization: header.  Do we have one?
        List<String> authz_headers = req.getRequestHeader(HttpHeaders.AUTHORIZATION);
        if (authz_headers == null || authz_headers.size() != 1) {
            return false;
        }
        // If we have one, does it use the scheme that this auth provider handles?
        return authz_headers.get(0).startsWith("Aero-Device-Cert ");
    }

    // Failures that should result in a 500 with no user-facing message
    private static class InternalFailureException extends Exception
    {
        private static final long serialVersionUID = 0;
        public InternalFailureException(String msg)
        {
            super(msg);
        }
    }

    // Failures that are the requester's fault and carry a user-facing message
    private static class CertAuthFailureException extends Exception
    {
        private static final long serialVersionUID = 0;
        public CertAuthFailureException(String msg)
        {
            super(msg);
        }
    }

    private static String getCN(String dname)
            throws InternalFailureException
    {
        String[] fields = dname.split("/");
        String cname = null;
        for (String f : fields) {
            if (f.startsWith("CN=")) {
                cname = f.substring(3);
            }
        }
        if (cname == null) {
            throw new InternalFailureException("No CN field found in cert DName: " + dname);
        }
        return cname;
    }

    private void verifyCertNotRevoked(long serial)
            throws InternalFailureException
    {
        try {
            if (_crc.isRevoked(serial)) {
                throw invalidAuthorizationException("Certificate " + serial + " is revoked");
            }
        } catch (SQLException e) {
            throw new InternalFailureException("sqlexception: " + e);
        } catch (ExNotFound exNotFound) {
            throw new InternalFailureException("No cert known with serial " + serial);
        }
    }

    // A 401 for when the user fails to authorize correctly
    private WebApplicationException invalidAuthorizationException(String message)
    {
        return AuthTokenExtractor.unauthorized(message, challenge());
    }
}
