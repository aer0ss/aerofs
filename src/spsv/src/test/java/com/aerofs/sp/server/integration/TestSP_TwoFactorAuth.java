/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.ex.ExSecondFactorRequired;
import com.aerofs.base.ex.ExSecondFactorSetupRequired;
import com.aerofs.lib.ex.ExNotAuthenticated;
import com.aerofs.sp.server.business_objects.AbstractBusinessObjectTest;
import com.aerofs.sp.server.lib.organization.Organization.TwoFactorEnforcementLevel;
import com.aerofs.sp.server.lib.session.ISession.Provenance;
import com.aerofs.sp.server.lib.session.ISession.ProvenanceGroup;
import com.aerofs.sp.server.lib.user.AuthorizationLevel;
import com.aerofs.sp.server.lib.user.User;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSP_TwoFactorAuth extends AbstractSPTest
{
    static enum UserTFAState {
        TFA_OFF,
        TFA_ON,
    }

    static enum Expectation {
        FAILURE_NOT_AUTH,
        FAILURE_NEED_SECOND_FACTOR,
        FAILURE_NEED_SECOND_FACTOR_SETUP,
        SUCCESS,
    }

    static ImmutableList<Provenance> AUTH_NONE = ImmutableList.of();
    static ImmutableList<Provenance> AUTH_BASIC = ImmutableList.of(Provenance.BASIC);
    static ImmutableList<Provenance> AUTH_TFA = ImmutableList.of(
            Provenance.BASIC,
            Provenance.BASIC_PLUS_SECOND_FACTOR
    );
    static ImmutableList<Provenance> AUTH_CERT = ImmutableList.of(Provenance.CERTIFICATE);

    void testCase(TwoFactorEnforcementLevel orgLevel, UserTFAState enforced,
            ImmutableList<Provenance> sessionProvenances, ProvenanceGroup necessary,
            Expectation expectedResult)
            throws Exception
    {
        // Set up state
        sqlTrans.begin();
        User admin = saveUser();
        admin.setLevel(AuthorizationLevel.ADMIN);
        // Set org enforcement level
        admin.getOrganization().setTwoFactorEnforcementLevel(orgLevel);
        // Set user enforcement level
        byte[] secret = admin.setupTwoFactor();
        switch (enforced) {
            case TFA_OFF: admin.disableTwoFactorEnforcement(); break;
            case TFA_ON: admin.enableTwoFactorEnforcement(); break;
        }
        sqlTrans.commit();

        // Attempt the action
        Throwable t = null;
        sqlTrans.begin();
        System.out.println("Checking that org "+ orgLevel + " user " + enforced + " with "
                + sessionProvenances + " session needing " + necessary
                + " privilege gets " + expectedResult);
        try {
            User.checkProvenance(admin, sessionProvenances, necessary);
        } catch (Throwable e) {
            t = e;
        }

        // complete or rollback transaction
        if (t != null) {
            sqlTrans.rollback();
        } else {
            sqlTrans.commit();
        }

        // Verify result matches expectations
        switch (expectedResult) {
            case FAILURE_NOT_AUTH:
                assertTrue("Expected ExNotAuthenticated, got " + t,
                        t instanceof ExNotAuthenticated);
                break;
            case FAILURE_NEED_SECOND_FACTOR:
                assertTrue("Expected ExSecondFactorRequired, got " + t,
                        t instanceof ExSecondFactorRequired);
                break;
            case FAILURE_NEED_SECOND_FACTOR_SETUP:
                assertTrue("Expected ExSecondFactorSetupRequired, got " + t,
                        t instanceof ExSecondFactorSetupRequired);
                break;
            case SUCCESS:
                assertTrue("Expected success, but threw: " + t, t == null); break;
            default: assertTrue("Unhandled result", false); break;
        }
    }

    @Test
    public void shouldBehaveAsExpectedWhenOrgEnforcementIsDisallowed() throws Exception
    {
        // user TFA off, LEGACY request
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        // user TFA off, TWO_FACTOR_SETUP request
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        // user TFA off, INTERACTIVE request
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);

        // User TFA on: expect same outcomes, since user can't turn on 2FA anyway

        // user TFA on, LEGACY request
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        // user TFA on, TWO_FACTOR_SETUP request
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        // user TFA on, INTERACTIVE request
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.DISALLOWED, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
    }

    @Test
    public void shouldBehaveAsExpectedWhenOrgEnforcementIsOptIn() throws Exception
    {
        // user TFA off, LEGACY request
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        // user TFA off, TWO_FACTOR_SETUP request
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        // user TFA off, INTERACTIVE request
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);

        // User TFA on: expect failures for things with only AUTH_BASIC

        // user TFA on, LEGACY request
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NEED_SECOND_FACTOR);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        // user TFA on, TWO_FACTOR_SETUP request
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NEED_SECOND_FACTOR);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        // user TFA on, INTERACTIVE request
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NEED_SECOND_FACTOR);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.OPT_IN, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
    }

    @Test
    public void shouldBehaveAsExpectedWhenOrgEnforcementIsMandatory() throws Exception
    {
        // user TFA off, LEGACY request
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NEED_SECOND_FACTOR_SETUP);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NEED_SECOND_FACTOR_SETUP);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        // user TFA off, TWO_FACTOR_SETUP request
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        // user TFA off, INTERACTIVE request
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_NONE,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_BASIC,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NEED_SECOND_FACTOR_SETUP);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_TFA,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NEED_SECOND_FACTOR_SETUP);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_OFF, AUTH_CERT,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);

        // user TFA on: expect FAILURE_NEED_SECOND_FACTOR for things that previously
        //              failed FAILURE_NEED_SECOND_FACTOR_SETUP.

        // userTFA on, LEGACY request
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.LEGACY, Expectation.FAILURE_NEED_SECOND_FACTOR);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.LEGACY, Expectation.SUCCESS);
        // userTFA on, TWO_FACTOR_SETUP request
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NEED_SECOND_FACTOR);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.TWO_FACTOR_SETUP, Expectation.FAILURE_NOT_AUTH);
        // user TFA on, INTERACTIVE request
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_NONE,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_BASIC,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NEED_SECOND_FACTOR);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_TFA,
                ProvenanceGroup.INTERACTIVE, Expectation.SUCCESS);
        testCase(TwoFactorEnforcementLevel.MANDATORY, UserTFAState.TFA_ON, AUTH_CERT,
                ProvenanceGroup.INTERACTIVE, Expectation.FAILURE_NOT_AUTH);
    }
}
