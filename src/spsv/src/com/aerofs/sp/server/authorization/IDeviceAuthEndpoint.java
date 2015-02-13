package com.aerofs.sp.server.authorization;

import com.aerofs.ids.UserID;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;

public interface IDeviceAuthEndpoint
{
    public boolean isDeviceAuthorized(UserID userID, JSONObject body)
            throws IOException, GeneralSecurityException;
}