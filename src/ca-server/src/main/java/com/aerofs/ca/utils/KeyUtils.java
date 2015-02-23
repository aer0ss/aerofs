/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.utils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

public class KeyUtils
{
    public static KeyPair newKeyPair()
            throws NoSuchAlgorithmException, NoSuchProviderException
    {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        // use a PRNG so we don't rely on /dev/random on the VM (it doesn't generate numbers quickly enough)
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(2048, secureRandom);
        return keyGen.generateKeyPair();
    }

    private static PrivateKey decodePrivateKey(KeyFactory keyFact, byte[] encoded)
            throws InvalidKeySpecException
    {
        PKCS8EncodedKeySpec pkSpec = new PKCS8EncodedKeySpec(encoded);
        return keyFact.generatePrivate(pkSpec);
    }

    private static PublicKey generatePublicFromPrivate(KeyFactory keyFact, RSAPrivateCrtKey privateKey)
            throws InvalidKeySpecException
    {
        RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
        return keyFact.generatePublic(spec);
    }

    public static KeyPair decodeKeyPair(byte[] encodedPrivateKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException,
            NoSuchProviderException
    {
        KeyFactory decoder = KeyFactory.getInstance("RSA", "BC");
        PrivateKey privateKey = decodePrivateKey(decoder, encodedPrivateKey);
        if (!(privateKey instanceof RSAPrivateCrtKey)) {
            throw new IllegalStateException("could not decode private key into a RSA private key");
        }
        return new KeyPair(generatePublicFromPrivate(decoder, (RSAPrivateCrtKey)privateKey), privateKey);
    }
}
