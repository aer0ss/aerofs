/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Util to class to store common code for decoding posts to java servlets. Do not put this in lib
 * so we avoid a dependency on servlet at that level.
 */
public class DoPostDelegate
{
    private String _postParamProtocol;
    private String _postParamData;

    public DoPostDelegate(String postParamProtocol, String postParamData)
    {
        this._postParamProtocol = postParamProtocol;
        this._postParamData = postParamData;
    }

    public int getProtocolVersion(HttpServletRequest req)
    {
        return Integer.valueOf(req.getParameter(_postParamProtocol));
    }

    public byte[] getDecodedMessage(HttpServletRequest req)
            throws IOException
    {
        String encodedMsg = req.getParameter(_postParamData);
        return Base64.decode(encodedMsg);
    }

    public void sendReply(HttpServletResponse resp, byte[] reply)
            throws IOException
    {
        String encodedReply = Base64.encodeBytes(reply);
        resp.setContentLength(encodedReply.length());
        resp.getWriter().print(encodedReply);
        resp.getWriter().flush();
    }
}
