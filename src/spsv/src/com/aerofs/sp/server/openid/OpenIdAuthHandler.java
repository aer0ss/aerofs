package com.aerofs.sp.server.openid;

import com.aerofs.base.BaseParam;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.servlets.lib.db.jedis.PooledJedisConnectionProvider;
import com.aerofs.sp.server.*;
import com.dyuproject.openid.*;
import com.dyuproject.openid.ext.AxSchemaExtension;
import com.dyuproject.openid.ext.SRegExtension;
import com.dyuproject.util.http.SimpleHttpConnector;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenIdAuthHandler implements IExternalAuthHandler {

    private static final Logger l = Loggers.getLogger(OpenIdAuthHandler.class);

    private IdentityProvider _provider;
    private RelyingParty _reliar;
    private static final long serialVersionUID = 1L;

    public OpenIdAuthHandler(RelyingParty reliar)
    {
        _reliar = reliar;
        _provider = new IdentityProvider(_reliar);
    }

    // When multiple OP are supported, then return UI with a list of providers,
    // or require provider name as a parameter to this address.
    //  Provider attributes: name, discovery_url, cached endpoint_url, TTL for cache, manager
    //
    @Override
    public void handleAuthRequest(HttpServletRequest req, HttpServletResponse resp) throws Exception
    {
        String updateToken = req.getParameter(Identity.IDENTITY_REQ_PARAM);

        if (updateToken != null) {
            _provider.authRequest(updateToken, req, resp);
        }
    }

    @Override
    public void handleAuthResponse(HttpServletRequest req, HttpServletResponse resp) throws IOException
    {
        try {
            _provider.authResponse(req, resp);
        } catch (Exception e) {
            resp.setContentType("text/html");
            resp.getWriter().println("<html><body>" +
                    "Authentication failed. Please try again later." +
                    "</body></html>");
            return;
        }

        String onComplete = req.getParameter(Identity.ONCOMPLETE_URL);
        if (!StringUtils.isBlank(onComplete)) {
            resp.sendRedirect(onComplete);
        } else {
            resp.setContentType("text/html");
            resp.getWriter().println("<html><body onLoad=\"window.close()\">"
                    + getOpenIDResponse(req.getParameter("openid.mode"))
                    + "</body></html>");
        }
    }

    private String getOpenIDResponse(String mode)
    {
        if (StringUtils.isBlank(mode)) return "AeroFS received an empty response from the " +
                "OpenID Provider and is unable to authenticate. Please contact " +
                BaseParam.WWW.SUPPORT_EMAIL_ADDRESS + " for assistance.";
        else if (mode.equals("id_res")) return "The AeroFS authentication process is complete. " +
                "You may close this browser window now.";
        else if (mode.equals("cancel")) return "The AeroFS authentication process has been " +
                "canceled. You may close this browser window now.";
        else return "AeroFS does not recognize the response from the OpenID Provider and is " +
                    "unable to authenticate. Please contact " + BaseParam.WWW.SUPPORT_EMAIL_ADDRESS +
                    " for assistance.";
    }

    /**
     * Actions and configuration that should be scoped to a particular OpenID provider
     *
     * (for now we only support one provider)
     */
    static class IdentityProvider
    {
        public IdentityProvider(RelyingParty relyingParty)
        {
            _reliar = relyingParty;

            PooledJedisConnectionProvider jedis = new PooledJedisConnectionProvider();
            jedis.init_(LibParam.REDIS.AOF_ADDRESS.getHostName(), LibParam.REDIS.AOF_ADDRESS.getPort(), LibParam.REDIS.PASSWORD);
            _identitySessionManager = new IdentitySessionManager(jedis);
        }

        void authRequest(String token, HttpServletRequest req, HttpServletResponse resp)
                throws Exception
        {
            OpenIdUser user = OpenIdUser.populate(
                    "", // no discovery URL
                    YadisDiscovery.IDENTIFIER_SELECT,
                    LibParam.OpenId.ENDPOINT_URL);

            String returnto = getReturnToUrl(token);
            String nextUrl = req.getParameter(Identity.ONCOMPLETE_URL);
            if (!((nextUrl == null) || nextUrl.isEmpty())) {
                returnto += '&' +
                        Identity.ONCOMPLETE_URL + '=' + URLEncoder.encode(nextUrl, "UTF-8");
            }

            req.setAttribute(Identity.DELEGATE_NONCE, token);

            _reliar.associateAndAuthenticate(
                    user, req, resp,
                    LibParam.OpenId.IDENTITY_REALM, LibParam.OpenId.IDENTITY_REALM,
                    returnto);
        }

        /**
         * Handle an auth response from this ID provider. Possible side effect:
         * if everything is great, we will mark the session nonce as authenticated.
         * */
        void authResponse(HttpServletRequest req, HttpServletResponse resp) throws Exception
        {
            final String delegateNonce = req.getParameter(Identity.DELEGATE_NONCE);

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
            req.setAttribute(Identity.DELEGATE_NONCE, delegateNonce);
            OpenIdUser user = _reliar.discover(req);

            if (user == null) {
                l.warn("OpenID lookup failure for delegateNonce {}", delegateNonce);
                return;
            }

            if (user.isAssociated() && (_reliar.verifyAuth(user, req, resp))) {
                l.info("OpenID authorized {} for user {}", delegateNonce, user.getIdentity());

                _identitySessionManager.authenticateSession(
                        delegateNonce,
                        LibParam.Identity.SESSION_TIMEOUT,
                        _authParser.populateAttrs(req));

                // we no longer care about the OpenID cached user
                _reliar.invalidate(req, resp);
            } else {
                l.warn("OpenID failed to verify auth for {}", user.getIdentifier());
            }
        }

        /** Build the return_to URL to pass to the OpenID provider */
        private static String getReturnToUrl(String token) throws UnsupportedEncodingException
        {
            StringBuilder sb = new StringBuilder(LibParam.OpenId.IDENTITY_URL);
            sb.append(Identity.IDENTITY_RESP_PATH);
            sb.append('?');
            sb.append(Identity.DELEGATE_NONCE);
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
                String uid = req.getParameter(LibParam.OpenId.IDP_USER_ATTR);
                if (uid == null) {
                    throw new ExBadCredential("No identifier: " + LibParam.OpenId.IDP_USER_ATTR);
                }

                Matcher args = (_uidPattern == null) ? null : _uidPattern.matcher(uid);
                return new IdentitySessionAttributes(
                        getFromRequest(LibParam.OpenId.IDP_USER_EMAIL, req, args),
                        getFromRequest(LibParam.OpenId.IDP_USER_FIRSTNAME, req, args),
                        getFromRequest(LibParam.OpenId.IDP_USER_LASTNAME, req, args));
            }

            /**
             * Convert a configured pattern into an actual value by either finding it directly
             * in the OpenID response or building it from capture groups in the uid.
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

        private final RelyingParty _reliar;
        private final IdentitySessionManager _identitySessionManager;
        private final AuthParser _authParser = new AuthParser(LibParam.OpenId.IDP_USER_PATTERN);
    }
}
