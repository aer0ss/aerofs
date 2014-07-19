package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.cli.CLI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam.Identity;
import com.aerofs.lib.LibParam.Identity.Authenticator;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.proto.Sp.OpenIdSessionAttributes;
import com.aerofs.proto.Sp.OpenIdSessionNonces;
import com.aerofs.proto.Sp.SignInUserReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newOneWayAuthClientFactory;

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
     * If the user signs in, the model must be updated with the SP client instance.
     *
     * If the user cannot be signed in, implementation must signal this case with an
     * appropriate exception.
     */
    public abstract void signInUser(SetupModel model) throws Exception;

    public void provideSecondFactor(SetupModel model) throws Exception
    {
        model.getClient().provideSecondFactor(model.getSecondFactorCode());
    }

    /**
     * An actor that signs in using stored credentials (requires userID and password
     * in the model).
     */
    public static class CredentialActor extends SignInActor
    {
        @Override
        public void signInUser(SetupModel model) throws Exception {
            SPBlockingClient sp = newOneWayAuthClientFactory().create();

            // FIXME: Soon we will remove this if() statement. The client
            // should not bother with scrypt'ing the credential and talking to
            // (legacy) signInUser. credentialSignIn() accepts cleartext credential for
            // LDAP and locally-authenticated users.
            SignInUserReply reply;
            if (Identity.AUTHENTICATOR == Authenticator.EXTERNAL_CREDENTIAL) {
                reply = sp.credentialSignIn(model.getUsername(), ByteString.copyFrom(model.getPassword()));
            } else {
                // legacy call:
                reply = sp.signInUser(model.getUsername(), ByteString.copyFrom(model.getScrypted()));
            }
            model.setNeedSecondFactor(reply.getNeedSecondFactor());
            model.setClient(sp);
        }
    }

    /**
     * OpenID sign-in flow for a GUI - where we can ask GUIUtil to launch a URL.
     */
    public static class OpenIdGUIActor extends SignInActor
    {
        @Override
        public void signInUser(SetupModel model) throws Exception
        {
            OpenIdHelper helper = new OpenIdHelper(model);

            GUIUtil.launch(helper.getDelegateUrl());

            helper.getSessionAttributes();
        }
    }

    public static class OpenIdCLIActor extends SignInActor
    {
        public OpenIdCLIActor(CLI cli) { _cli = cli; }

        @Override
        public void signInUser(SetupModel model) throws Exception
        {
            OpenIdHelper helper = new OpenIdHelper(model);

            String msg = "To complete " + L.product() + " setup, please sign in with your OpenID " +
                    "Provider by pasting the following URL in a web browser. This URL can be " +
                    "used only once, and only for this session.\n" +
                    "Setup will complete automatically once the OpenID Provider confirms your " +
                    "identity.";

            _cli.show(MessageType.INFO, msg);
            _cli.show(MessageType.INFO, helper.getDelegateUrl());

            helper.getSessionAttributes();
        }

        private final CLI _cli;
    }

    private static class OpenIdHelper
    {
        OpenIdHelper(SetupModel model) throws Exception
        {
            _model = model;
            _spclient = newOneWayAuthClientFactory().create();
            _sessionKeys = _spclient.openIdBeginTransaction();
            _timer = new ElapsedTimer();

            _timer.start();
        }

        void getSessionAttributes() throws Exception
        {
            while (_timer.elapsed() < (OpenId.DELEGATE_TIMEOUT * C.SEC)) {
                Thread.sleep(OpenId.SESSION_INTERVAL * C.SEC);

                OpenIdSessionAttributes session
                        = _spclient.openIdGetSessionAttributes(_sessionKeys.getSessionNonce());
                if (session.getUserId().isEmpty()) { continue; }

                l.info("OpenID user {}", session.getUserId());

                _model.setUserID(session.getUserId());
                _model.setClient(_spclient);
                _model.setNeedSecondFactor(session.getNeedSecondFactor());
                return;
            }
            throw new ExTimeout("Timed out waiting for authentication.");
        }

        // Return a URL of the form  https://transient/openid/oa?token=ab33f
        String getDelegateUrl()
        {
            return OpenId.IDENTITY_URL + OpenId.IDENTITY_REQ_PATH
                    + "?" + OpenId.IDENTITY_REQ_PARAM + "=" + _sessionKeys.getDelegateNonce();
        }

        private final SPBlockingClient    _spclient;
        private final OpenIdSessionNonces _sessionKeys;
        private final SetupModel          _model;
        private final ElapsedTimer        _timer;
        private static Logger             l = LoggerFactory.getLogger(OpenIdHelper.class);
    }
}
