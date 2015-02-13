package com.aerofs.sp.server.authorization;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.aerofs.ids.UserID;
import com.google.common.collect.Lists;
import org.json.simple.JSONObject;

import com.aerofs.proto.Sp.RegisterDeviceCall.Interface;

/**
 * See docs/design/device_authorization.md for the design of the endpoint.
 */
public class DeviceAuthClient
{
    private final IDeviceAuthEndpoint _endpoint;

    public DeviceAuthClient(IDeviceAuthEndpoint endpoint)
    {
        _endpoint = endpoint;
    }

    @SuppressWarnings("unchecked")
    private static JSONObject encodeParamsAsJSON(String osFamily, String osName, String deviceName,
            String remoteAddress, List<Interface> interfaces)
    {
        JSONObject jsonBody = new JSONObject();

        jsonBody.put("name", deviceName);
        jsonBody.put("ip",  remoteAddress);

        JSONObject jsonOS = new JSONObject();
        jsonOS.put("family", osFamily);
        jsonOS.put("name", osName);
        jsonBody.put("os", jsonOS);

        List<JSONObject> jsonInterfaces = Lists.newLinkedList();
        for (Interface i : interfaces) {
            JSONObject jsonInterface = new JSONObject();

            jsonInterface.put("name", i.getName());
            jsonInterface.put("ips", i.getIpsList());
            jsonInterface.put("mac", i.getMac());

            jsonInterfaces.add(jsonInterface);
        }

        jsonBody.put("interfaces", jsonInterfaces);
        return jsonBody;
    }

    public boolean isSystemAuthorized(UserID userID, String osFamily, String osName,
            String deviceName, String remoteAddress, List<Interface> interfaces)
            throws IOException, GeneralSecurityException
    {
        JSONObject jsonBody = encodeParamsAsJSON(osFamily, osName, deviceName, remoteAddress,
                interfaces);

        return _endpoint.isDeviceAuthorized(userID, jsonBody);
    }
}