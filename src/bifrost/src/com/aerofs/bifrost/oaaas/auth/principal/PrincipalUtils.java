package com.aerofs.bifrost.oaaas.auth.principal;

import com.aerofs.oauth.AuthenticatedPrincipal;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class PrincipalUtils
{
    private static final Gson _gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public static String serialize(AuthenticatedPrincipal principal) {
        return _gson.toJson(principal);
    }

    public static AuthenticatedPrincipal deserialize(String encodedPrincipal) {
        return _gson.fromJson(encodedPrincipal, AuthenticatedPrincipal.class);
    }
}
