package com.aerofs.sp.server;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface IExternalAuthHandler {
    void handleAuthRequest(HttpServletRequest req, HttpServletResponse resp) throws Exception;
    void handleAuthResponse(HttpServletRequest req, HttpServletResponse resp) throws Exception;
}
