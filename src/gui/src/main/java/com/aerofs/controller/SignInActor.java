package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.cli.CLI;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.OpenId;
import com.aerofs.proto.Sp.ExtAuthSessionAttributes;
import com.aerofs.proto.Sp.ExtAuthSessionNonces;
import com.aerofs.proto.Sp.SignInUserReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.IUI.MessageType;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.aerofs.lib.LibParam.Identity.*;
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
            SignInUserReply reply = sp.credentialSignIn(model.getUsername(),
                    ByteString.copyFrom(model.getPassword()));
            // We need to prompt for the second factor if the user has enabled it,
            // or if they must based on org mandate
            model.setNeedSecondFactor(reply.getNeedSecondFactor()
                    || reply.getNeedSecondFactorSetup());
            model.setNeedSecondFactorSetup(reply.getNeedSecondFactorSetup());
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
            ExtAuthHelper helper = new ExtAuthHelper(model);

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
            ExtAuthHelper helper = new ExtAuthHelper(model);

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

    private static class ExtAuthHelper
    {
        ExtAuthHelper(SetupModel model) throws Exception
        {
            _model = model;
            _spclient = newOneWayAuthClientFactory().create();
            _sessionKeys = _spclient.extAuthBeginTransaction();
            _timer = new ElapsedTimer();

            _timer.start();
        }

        void getSessionAttributes() throws Exception
        {
            while (_timer.elapsed() < (DELEGATE_TIMEOUT * C.SEC)) {
                Thread.sleep(SESSION_INTERVAL * C.SEC);

                ExtAuthSessionAttributes session
                        = _spclient.extAuthGetSessionAttributes(_sessionKeys.getSessionNonce());
                if (session.getUserId().isEmpty()) { continue; }

                l.info("{} user {}", AUTHENTICATOR, session.getUserId());

                _model.setUserID(session.getUserId());
                _model.setClient(_spclient);
                _model.setNeedSecondFactorSetup(session.getNeedSecondFactorSetup());
                // We need to prompt for the second factor if the user has enabled it,
                // or if they must based on org mandate
                _model.setNeedSecondFactor(session.getNeedSecondFactor()
                        || session.getNeedSecondFactorSetup());
                return;
            }
            throw new ExTimeout("Timed out waiting for authentication.");
        }

        // Return a URL of the form  https://transient/openid/oa?token=ab33f
        String getDelegateUrl()
        {
            return (LibParam.SAML.enabled() ? ("https://" + LibParam.HOST + "/identity") : OpenId.IDENTITY_URL)
                    + IDENTITY_REQ_PATH + "?" + IDENTITY_REQ_PARAM + "="
                    + _sessionKeys.getDelegateNonce();
        }

        private final SPBlockingClient    _spclient;
        private final ExtAuthSessionNonces _sessionKeys;
        private final SetupModel          _model;
        private final ElapsedTimer        _timer;
        private static Logger             l = LoggerFactory.getLogger(ExtAuthHelper.class);
    }


    public static class SAMLGUIActor extends SignInActor
    {
        @Override
        public void signInUser(SetupModel model) throws Exception
        {
            ExtAuthHelper helper = new ExtAuthHelper(model);
            GUIUtil.launch(helper.getDelegateUrl());

            helper.getSessionAttributes();
        }
    }

    public static class SAMLCLIActor extends SignInActor
    {
        public SAMLCLIActor(CLI cli) { _cli = cli; }

        @Override
        public void signInUser(SetupModel model) throws Exception
        {
            ExtAuthHelper helper = new ExtAuthHelper(model);

            String msg = "To complete " + L.product() + " setup, please sign in with your SAML " +
                    "Provider by pasting the following URL in a web browser. This URL can be " +
                    "used only once, and only for this session.\n" +
                    "Setup will complete automatically once the SAML IDP Provider confirms your " +
                    "identity.";

            _cli.show(MessageType.INFO, msg);
            _cli.show(MessageType.INFO, helper.getDelegateUrl());

            helper.getSessionAttributes();
        }

        private final CLI _cli;
    }

}
