/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExExternalAuthFailure;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.sp.server.saml.SAMLAuthHandler;
import com.aerofs.sp.server.openid.OpenIdAuthHandler;
import com.aerofs.sp.server.openid.OpenIdRelyingParty;
import org.opensaml.xml.validation.ValidationException;
import org.slf4j.Logger;
import org.xml.sax.SAXParseException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static com.aerofs.lib.LibParam.SAML;

public class IdentityServlet extends HttpServlet
{
    private static final Logger l = Loggers.getLogger(IdentityServlet.class);
    private static final long serialVersionUID = 1L;

    private IExternalAuthHandler authHandler;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        if (authHandler != null) return;

        if (OpenId.enabled()) {
            authHandler = new OpenIdAuthHandler(OpenIdRelyingParty.getRelyingParty());
        } else if (SAML.enabled()) {
            authHandler = new SAMLAuthHandler();
        }
    }

    public void setAuthHandler(IExternalAuthHandler authHandler) {
        this.authHandler = authHandler;
    }

    /** This servlet only supports post for SAML. */
    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp) throws IOException
    {
        if (!SAML.enabled()) {
            resp.sendError(405);
            return;
        }
        try {
            authHandler.handleAuthResponse(req, resp);
        } catch (IllegalArgumentException|ValidationException|SAXParseException e) {
            resp.sendError(400, e.getMessage());
        } catch (ExExternalAuthFailure e) {
            resp.sendError(403, e.getMessage());
        } catch (Exception e) {
            resp.sendError(500, e.getMessage());
        }
    }

    /**
     * Dispatch a GET based on the path to either the auth request handler or the auth
     * response handler.
     *
     * All errors are responded with a 404. TODO: review this strategy. Return no error? Proper error codes?
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        if (!OpenId.enabled() && !SAML.enabled()) {
            resp.sendError(405);
            return;
        }

        String path = req.getPathInfo();
        try {
            if (path == null) {
                resp.sendError(404);
            } else if (path.equals(Identity.IDENTITY_REQ_PATH)) {
                authHandler.handleAuthRequest(req, resp);
            } else if (path.equals(Identity.IDENTITY_RESP_PATH)) {
                authHandler.handleAuthResponse(req, resp);
            } else {
                l.info("Illegal path requested {}", path);
                resp.sendError(404);
            }
        } catch (Exception e) {
            l.warn("Error in identity handling", e);
            resp.sendError(500, e.getMessage());
        }
    }
}
