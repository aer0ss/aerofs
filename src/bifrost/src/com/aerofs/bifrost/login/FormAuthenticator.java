/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.login;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.id.UserID;
import com.aerofs.bifrost.oaaas.auth.AbstractAuthenticator;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.protobuf.ByteString;
import com.sun.jersey.api.representation.Form;
import com.sun.jersey.spi.container.ContainerRequest;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.util.Scanner;

/**
 * This authenticator is responsible for displaying a login form, authenticating the user,
 * and returning the appropriate session contents if the user authenticates.
 *
 * TODO: if indicated by server config, forward to an OpenId signin instead
 * TODO: LDAP integration (skip scrypt)
 */
public class FormAuthenticator extends AbstractAuthenticator
{
    private static final Logger l = Loggers.getLogger(FormAuthenticator.class);
    private static final String FORM_RESOURCE = "templates/loginform.xml";
    @Inject
    private SPBlockingClient.Factory spFactory;

    /**
     * Does this request have enough context to authenticate a user?
     */
    @Override
    public boolean canCommence(ContainerRequest request)
    {
        return request.getMethod().equalsIgnoreCase("POST")
                && request.getFormParameters().containsKey("j_username");
    }

    /**
     * Response type for OAuth Requests.
     */
    static class OAuthResponse
    {
        OAuthResponse(String authState)
        {
            this.authRequired = true;
            this.authState = authState;
        }
        String getAuthState() { return authState; }
        void setAuthState(String authState) { this.authState = authState; }

        String authState;
        boolean authRequired;
    }

    /**
     * Handle user authentication.
     *
     * Based on the request type, we might:
     *  - return the auth_state so the user can submit authentication credentials
     *  - return a web form the user can fill to authenticate
     *  - attempt to authenticate using the request parameters and authorization request context.
     */
    @Override
    public void authenticate(ContainerRequest request, String authStateValue, String returnUri)
    {
        if (request.getMethod().equals("GET")) {
            throw new WebApplicationException(
                    Response.ok(new OAuthResponse(authStateValue), MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }

        try {
            processForm(request);
        } catch (ExBadCredential ebc) {
            throw new WebApplicationException(
                    Response.status(Status.UNAUTHORIZED)
                            .entity("Bad credential.")
                            .build());
        } catch (Exception e) {
            throw new WebApplicationException(
                    Response.status(Status.INTERNAL_SERVER_ERROR)
                            .entity("Invalid authentication request")
                            .build());
        }
    }

    /**
     * Build and display a login form.
     */
    private void renderForm(ContainerRequest request, String returnUri, String authStateValue)
    {
        request.getProperties().put(AUTH_STATE, authStateValue);
        request.getProperties().put("actionUri", returnUri);

        throw new WebApplicationException(
                Response.ok()
                        .entity(decorateForm(request))
                        .build());
    }

    /**
     * Hook for actually validating the username/ password against a database,
     * ldap, external webservice or whatever to perform authentication
     *
     * @param request the {@link ContainerRequest}
     */
    protected void processForm(final ContainerRequest request) throws Exception
    {
        Form formParams = request.getFormParameters();

        setAuthStateValue(request, formParams.getFirst(AUTH_STATE));

        UserID user = validateCredential(formParams);

        // If we get here, the client authenticated successfully
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(user.getString());
        setPrincipal(request, principal);
    }

    private UserID validateCredential(MultivaluedMap<String, String> formParams)
            throws Exception
    {
        UserID user = UserID.fromExternal(formParams.getFirst("j_username"));
        String cred = formParams.getFirst("j_password");

        l.info("Validate user credential {}", user.getString());

        // recall that validateCredential uses a throw to indicate bad credentials
        spFactory.create()
                .validateCredential(user.getString(), ByteString.copyFromUtf8(cred));
        return user;
    }


    /*
     * FIXME: An inefficient (though functional) chunk follows.
     * Instead, precompile and cache the regex'es.
     */

    /**
     * Decorate a text template by replacing marked-up variables with actual values from
     * the request attributes.
     */
    private String decorateForm(ContainerRequest request)
    {
        return getTemplate()
                .replaceAll("\\$\\{AUTH_STATE\\}", (String)request.getProperties().get(AUTH_STATE))
                .replaceAll("\\$\\{actionUri\\}", (String)request.getProperties().get("actionUri"));
    }

    /**
     * Return the contents of the form template. The form template is loaded via
     * the classloader getResource mechanism, meaning it should be found
     * at a predictable location in the classpath.
     *
     * FIXME: cache this resource content instead of scanning it every time?
     */
    private String getTemplate()
    {
        InputStream istr = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(FORM_RESOURCE);
        if (istr == null) {
            throw new WebApplicationException(
                    Response.serverError()
                            .entity("Can't load form resource!")
                            .build());
        }
        return new Scanner(istr).useDelimiter("\\A").next();
    }
}
