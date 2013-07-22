package com.aerofs.controller;

import com.aerofs.sp.client.SPBlockingClient;

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
    public static class Credential extends SignInActor
    {
        @Override
        public void signInUser(Setup setup, SetupModel model) throws Exception {
            SPBlockingClient sp = setup.signInUser(model.getUserID(), model.getScrypted());
            model.setClient(sp);
        }
    }
}
