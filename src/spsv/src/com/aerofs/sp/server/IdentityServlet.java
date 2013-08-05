/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.servlets.AeroServlet;
import com.aerofs.sp.server.IdentitySessionManager.UserManager;
import com.dyuproject.openid.IdentifierSelectUserCache;
import com.dyuproject.openid.OpenIdUser;
import com.dyuproject.openid.RelyingParty;
import com.dyuproject.openid.YadisDiscovery;
import com.dyuproject.openid.ext.AxSchemaExtension;
import com.dyuproject.openid.ext.SRegExtension;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IdentityServlet extends AeroServlet
{
    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
        init_();
        _provider = new IdentityProvider();
    }

    /** This servlet does not support POST. */
    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
        throws IOException
    {
        resp.sendError(405);
    }

    /**
     * Dispatch a GET based on the path to either the auth request handler or the auth
     * response handler.
     *
     * All errors are responded with a 404. TODO: review this strategy. Return no error? Proper error codes?
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        if (OpenId.ENABLED.get() == false) { resp.sendError(405); return; }

        String path = req.getPathInfo();

        try {
            if (path == null) {
                resp.sendError(404);
            } else if (path.equals(OpenId.IDENTITY_REQ_PATH)) {
                handleAuthRequest(req, resp);
            } else if (path.equals(OpenId.IDENTITY_RESP_PATH)) {
                _provider.authResponse(req, resp);

                String oncomplete = req.getParameter(OpenId.OPENID_ONCOMPLETE_URL);
                if ((oncomplete != null) && (!oncomplete.isEmpty())) {
                    resp.sendRedirect(oncomplete);
                } else {
                    resp.setContentType("text/html");
                    resp.getWriter().println("<html> <body onLoad=\"window.close()\">" +
                        "Authentication is complete. You can close this browser window." +
                        "</body></html>");
                }
            } else {
                l.info("Illegal path requested {}", path);
                resp.sendError(404);
            }
        } catch (Exception e) {
            l.warn("Error in identity handling", e);
        }
    }

    // When multiple OP are supported, then return UI with a list of providers,
    // or require provider name as a parameter to this address.
    //  Provider attributes: name, discovery_url, cached endpoint_url, TTL for cache, manager
    //
    private void handleAuthRequest(HttpServletRequest req, HttpServletResponse resp)
            throws Exception
    {
        String updateToken = req.getParameter(OpenId.IDENTITY_REQ_PARAM);

        if (updateToken != null) {
            _provider.authRequest(updateToken, req, resp);
        }
    }

    /**
     * Actions and configuration that should be scoped to a particular OpenId provider
     *
     * (for now we only support one provider)
     */
    static class IdentityProvider
    {
        void authRequest(String token, HttpServletRequest req, HttpServletResponse resp)
                throws Exception
        {
            OpenIdUser user = OpenIdUser.populate(
                    OpenId.DISCOVERY_URL.get(),
                    YadisDiscovery.IDENTIFIER_SELECT,
                    OpenId.ENDPOINT_URL.get());

            String returnto = getReturnToUrl(token);
            String nextUrl = req.getParameter(OpenId.OPENID_ONCOMPLETE_URL);
            if (!((nextUrl == null) || nextUrl.isEmpty())) {
                returnto += '&' +
                        OpenId.OPENID_ONCOMPLETE_URL + '=' + URLEncoder.encode(nextUrl, "UTF-8");
            }

            req.setAttribute(OpenId.OPENID_DELEGATE_NONCE, token);

            _reliar.associateAndAuthenticate(
                    user, req, resp,
                    OpenId.IDENTITY_REALM.get(), OpenId.IDENTITY_REALM.get(),
                    returnto);
        }

        /**
         * Handle an auth response from this ID provider. Possible side effect:
         * if everything is great, we will mark the session nonce as authenticated.
         * */
        void authResponse(HttpServletRequest req, HttpServletResponse resp) throws Exception
        {
            final String delegateNonce = req.getParameter(OpenId.OPENID_DELEGATE_NONCE);

            // anything other than "id_res" means an attacker, or a bogus get to this url
            if (!RelyingParty.isAuthResponse(req) || (delegateNonce == null)) {
                if (RelyingParty.isAuthCancel(req)) {
                    l.warn("Identity: negative assertion received from IDP");
                    // FIXME: how do we validate we aren't being trolololed? Is knowledge of delegateNonce sufficient?
                    // FIXME: cancel auth token? or let it time out?
                    return;
                }
            }

            // convert the servlet parameter to an attribute; attr is used by discovery
            req.setAttribute(OpenId.OPENID_DELEGATE_NONCE, delegateNonce);
            OpenIdUser user = _reliar.discover(req);

            if (user == null) {
                l.warn("OpenId lookup failure for delegateNonce {}", delegateNonce);
                return;
            }

            if (user.isAssociated() && (_reliar.verifyAuth(user, req, resp))) {
                l.info("OpenId authorized {} for user {}", delegateNonce, user.getIdentity());

                _identitySessionManager.authenticateSession(
                        delegateNonce,
                        OpenId.SESSION_TIMEOUT.get(),
                        _authParser.populateAttrs(req));

                // we no longer care about the OpenId cached user
                _reliar.invalidate(req, resp);
            } else {
                l.warn("OpenId failed to verify auth for {}", user.getIdentifier());
            }
        }

        /** Build the return_to URL to pass to the OpenId provider */
        private static String getReturnToUrl(String token) throws UnsupportedEncodingException
        {
            StringBuilder sb = new StringBuilder(OpenId.IDENTITY_URL.get());
            sb.append(OpenId.IDENTITY_RESP_PATH);
            sb.append('?');
            sb.append(OpenId.OPENID_DELEGATE_NONCE);
            sb.append('=');
            sb.append(token);
            return sb.toString();
        }

        /**
         * Encapsulate the state and behavior of parsing user attributes from the OpenID
         * auth response. Right now this is just a container for a pre-compiled Pattern;
         * however we could imagine some other state being added here.
         */
        static class AuthParser
        {
            AuthParser(String userIdPattern)
            {
                if (userIdPattern.length() > 0) {
                    _uidPattern = Pattern.compile(userIdPattern);
                }
            }

            IdentitySessionAttributes populateAttrs(HttpServletRequest req)
                    throws ExBadCredential
            {
                String uid = req.getParameter(OpenId.IDP_USER_ATTR.get());
                if (uid == null) {
                    throw new ExBadCredential("No identifier: " + OpenId.IDP_USER_ATTR.get());
                }

                Matcher args = (_uidPattern == null) ? null : _uidPattern.matcher(uid);
                return new IdentitySessionAttributes(
                        getFromRequest(OpenId.IDP_USER_EMAIL.get(), req, args),
                        getFromRequest(OpenId.IDP_USER_FIRSTNAME.get(), req, args),
                        getFromRequest(OpenId.IDP_USER_LASTNAME.get(), req, args));
            }

            /**
             * Convert a configured pattern into an actual value by either finding it directly
             * in the OpenId response or building it from capture groups in the uid.
             *
             * @param val openid parameter name
             * @param args matcher generated from the configuration pattern
             * @return Value of the configured pattern
             */
            private String getFromRequest(String val, HttpServletRequest req, Matcher args)
            {
                if ((_uidPattern == null) || (!val.contains("["))) {
                    String parameter = req.getParameter(val);
                    return (parameter == null) ? val : parameter;
                } else if (args.matches()) {
                    return fancyReplace(val, args);
                } else {
                    return val;
                }
            }

            /**
             *  Replace stuff, all fancy-style.
             *  Replace instances of "uid[i]" with "args.group(i)".
             *  Gives up when it runs out of ['s.
             *  Could be reimplemented using Matcher.replaceAll()?
             */
            private String fancyReplace(String value, Matcher matcher)
            {
                String retval = new String(value);
                int groupMax = matcher.groupCount();
                int groupNum = 1;

                while (retval.contains("[") && groupNum <= groupMax) {
                    retval = retval.replace(
                            "uid[" + groupNum + "]",
                            matcher.group(groupNum));
                    groupNum++;
                }
                return retval;
            }

            private Pattern _uidPattern = null;
        }

        private final IdentitySessionManager _identitySessionManager = new IdentitySessionManager();
        private final AuthParser _authParser = new AuthParser(OpenId.IDP_USER_PATTERN.get());
    }

    private IdentityProvider _provider;
    private static RelyingParty _reliar = new RelyingParty(new UserManager(), new IdentifierSelectUserCache());

    static {
        assert _reliar != null;
        if (OpenId.IDP_USER_EXTENSION.get().equals("ax")) {
            _reliar.addListener(
                    new AxSchemaExtension()
                            .addExchange("email")
                            .addExchange("firstname")
                            .addExchange("lastname"));
        }

        if (OpenId.IDP_USER_EXTENSION.get().equals("sreg")) {
            _reliar.addListener(
                    new SRegExtension()
                            .addExchange("email")
                            .addExchange("fullname"));
        }
        // other values are ignored as possible communist plots.
    }

    private static final Logger l = Loggers.getLogger(IdentityServlet.class);
    private static final long serialVersionUID = 1L;
}