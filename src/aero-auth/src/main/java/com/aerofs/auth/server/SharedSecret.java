package com.aerofs.auth.server;

public class SharedSecret
{
    private final String secret;

    public SharedSecret(String secret)
    {
        this.secret = secret;
    }

    public boolean constantTimeEquals(String s)
    {
        if (secret.length() != s.length()) {
            return false;
        }
        int equality = 0;
        for (int i = 0 ; i < secret.length(); i++) {
            equality |= secret.charAt(i) ^ s.charAt(i);
        }
        return equality == 0;
    }
}
