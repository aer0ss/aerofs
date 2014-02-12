/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.BaseSecUtil.KeyDerivation;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.server.lib.SPParam;
import com.lambdaworks.crypto.SCrypt;

import java.security.GeneralSecurityException;

/**
 * A static container for implementation related to the generation
 * and comparison of locally-managed credentials.
 *
 * Why this class? It's terrible OOP. Well, imaginary friend, you are right and here's why:
 * we want to consolidate all calls to SCrypt from the codebase into (ideally) one place.
 * Once we are down to a small enough set, we can improve the salt-generation mechanism
 * and start to port users without impacting compatibility.
 */
public class LocalCredential
{
    private LocalCredential() { /* uninstantiable */ }
    /**
     * Given a user and a cleartext credential value, derive a secure key that we can
     * feel good about storing in a production database. The key-generation must be
     * repeatable for the given inputs.
     *
     * Today this means SCrypt with some well-chosen arguments, and the userId functioning as salt.
     *
     * @param user user identifier (used for salting the credential)
     * @param credential cleartext credential
     */
    public static byte[] deriveKeyForUser(UserID user, byte[] credential)
            throws GeneralSecurityException
    {
        return SCrypt.scrypt(credential, KeyDerivation.getSaltForUser(user),
                KeyDerivation.N, KeyDerivation.r, KeyDerivation.p, KeyDerivation.dkLen);
    }

    /**
     * Hash an SCrypt'ed credential; this is a compatibility shim for the many calls that
     * can deal with pre-SCrypt'ed credential information.
     */
    public static byte[] hashScrypted(byte[] scrypted)
    {
        return SPParam.getShaedSP(scrypted);
    }

}
