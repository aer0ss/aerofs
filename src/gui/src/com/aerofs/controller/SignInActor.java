package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.cli.CLI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Sp.OpenIdSessionAttributes;
import com.aerofs.proto.Sp.OpenIdSessionNonces;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation for various sign-in mechanisms.
 */
public abstract class SignInActor
{
    /**
     * Sign in the user. Use any context needed from the SetupModel instance.
     *
     * If user information is returned as part of sign-in, update the SetupModel accordingly.
     *
     * If the user signs in, the model must be updated with the SP client instance
     *
     * If the user cannot be signed in, implementation must signal this case with an
     * appropriate exception.
     */
    public abstract void signInUser(Setup setup, SetupModel model) throws Exception;

    /**
     * An actor that signs in using stored credentials (requires userID and password
     * in the model).
     */
    public static class CredentialActor extends SignInActor
    {
        @Override
        public void signInUser(Setup setup, SetupModel model) throws Exception {
            SPBlockingClient sp = new SPBlockingClient.Factory()
                    .create_(Cfg.user(), SPBlockingClient.ONE_WAY_AUTH_CONNECTION_CONFIGURATOR);
            // NOTE: In LDAP mode, we pass the un-scrypt'ed password so it can be referred
            // to an external service for verification. This is the only difference in the
            // two following calls to SP.
            // TODO: use inheritance/polymorphism here instead. Consider this from the viewpoint
            // where we have both LDAP and non-LDAP users in the same SP instance.
            if (Identity.AUTHENTICATOR == Authenticator.EXTERNAL_CREDENTIAL) {
                sp.signInUser(model.getUsername(), ByteString.copyFrom(model.getPassword()));
            } else {
                sp.signInUser(model.getUsername(), ByteString.copyFrom(model.getScrypted()));
            }
            model.setClient(sp);
        }
    }

    /**
     * OpenID sign-in flow for a GUI - where we can ask GUIUtil to launch a URL.
     */
    public static class OpenIdGUIActor extends SignInActor
    {
        @Override
        public void signInUser(Setup setup, SetupModel model) throws Exception
        {
            OpenIdHelper helper = new OpenIdHelper(model);

            GUIUtil.launch(helper.getDelegateUrl());

            helper.getSessionAttributes();
        }
    }

    public static class OpenIdCLIActor extends SignInActor
    {
        public OpenIdCLIActor(CLI cli) { _out = cli; }

        @Override
        public void signInUser(Setup setup, SetupModel model) throws Exception
        {
            OpenIdHelper helper = new OpenIdHelper(model);
            String msg = S.OPENID_SETUP_MESSAGE + " by pasting the following URL in a web " +
                    "browser. This URL can be used only once, and only for this session.\n" +
                    "Setup will complete automatically once the OpenID Provider confirms " +
                    "your identity.\n";

            _out.show(MessageType.INFO, msg);
            _out.show(MessageType.INFO, helper.getDelegateUrl() + '\n');

            helper.getSessionAttributes();
        }

        private final CLI _out;
    }

    private static class OpenIdHelper extends ElapsedTimer
    {
        OpenIdHelper(SetupModel model) throws Exception
        {
            _model = model;
            _spclient = new SPBlockingClient.Factory()
                    .create_(Cfg.user(), SPBlockingClient.ONE_WAY_AUTH_CONNECTION_CONFIGURATOR);
            _sessionKeys = _spclient.openIdBeginTransaction();

            start();
        }

        void getSessionAttributes() throws Exception
        {
            while (elapsed() < (OpenId.DELEGATE_TIMEOUT * C.SEC)) {
                Thread.sleep(OpenId.SESSION_INTERVAL * C.SEC);

                OpenIdSessionAttributes session
                        = _spclient.openIdGetSessionAttributes(_sessionKeys.getSessionNonce());
                if (session.getUserId().isEmpty()) { continue; }

                l.info("OpenID user {}", session.getUserId());

                _model.setUserID(session.getUserId());
                _model.setClient(_spclient);
                return;
            }
            throw new ExBadCredential("Timed out waiting for authentication.");
        }

        // Return a URL of the form  https://transient/openid/oa?token=ab33f
        String getDelegateUrl()
        {
            return OpenId.IDENTITY_URL + OpenId.IDENTITY_REQ_PATH
                    + "?" + OpenId.IDENTITY_REQ_PARAM + "=" + _sessionKeys.getDelegateNonce();
        }

        private SPBlockingClient    _spclient;
        private OpenIdSessionNonces _sessionKeys;
        private final SetupModel    _model;
        private static Logger       l = LoggerFactory.getLogger(OpenIdHelper.class);
    }
}
