package com.aerofs.sp.server.authentication;

import com.aerofs.base.id.UserID;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface ISystemAuthEndpoint
{
    public boolean isSystemAuthorized(UserID userID, JSONObject body)
            throws IOException, GeneralSecurityException;
}