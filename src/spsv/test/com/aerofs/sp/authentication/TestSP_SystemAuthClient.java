/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.authentication;

import com.aerofs.base.id.UserID;
import com.aerofs.proto.Sp.RegisterDeviceCall.Interface;
import com.aerofs.sp.server.authentication.ISystemAuthEndpoint;
import com.aerofs.sp.server.authentication.SystemAuthClient;
import com.google.common.collect.ImmutableList.Builder;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * For unit test purposes we limit this suite to testing functionality related to the encoding of
 * the request body that is bound for the system authorization endpoint. Testing the the single
 * REST contract isn't worthwhile here (for now), and should be covered by larger system tests.
 */
public class TestSP_SystemAuthClient
{
    @Test
    public void shouldEncodeBodyAsValidJSON() throws Exception
    {
        SystemAuthClient client = new SystemAuthClient(new ISystemAuthEndpoint()
        {
            @Override
            public boolean isSystemAuthorized(UserID userID, JSONObject body)
                    throws IOException
            {
                // Fully encode and decode the JSON object to ensure toJSONString() is working.
                body = (JSONObject) JSONValue.parse(body.toJSONString());

                // Verification: verify that we have in the JSON object what we expect.

                // OS information.
                JSONObject os = (JSONObject) body.get("os");
                assertEquals(os.get("family"), "Mac");
                assertEquals(os.get("name"), "Mac OS X");
                // System name.
                assertEquals(body.get("name"), "Matt's Super Awesome Laptop");

                // Interfaces.
                @SuppressWarnings("unchecked")
                List<JSONObject> interfaces = (List<JSONObject>) body.get("interfaces");
                assertEquals(2, interfaces.size());
                for (JSONObject i : interfaces) {
                    @SuppressWarnings("unchecked")
                    List<String> ips = (List<String>) i.get("ips");

                    if (i.get("name").equals("en0")) {
                        assertEquals(i.get("mac"), "00:0a:27:02:00:43:dd:09");
                        assertEquals(1, ips.size());
                        assertEquals("192.168.1.1", ips.get(0));
                    } else if (i.get("name").equals("lo0")) {
                        assertEquals(i.get("mac"), "");
                        assertTrue(
                            (ips.get(0).equals("127.0.0.1") && ips.get(1).equals("0:0:0:0:0:0:0:1")) ||
                            (ips.get(1).equals("127.0.0.1") && ips.get(0).equals("0:0:0:0:0:0:0:1"))
                        );
                    } else {
                        // Got a bad interface name, fail.
                        assertTrue(false);
                    }
                }

                return true;
            }
        });

        List<Interface> interfaces = new Builder<Interface>()
                .add(Interface.newBuilder()
                        .setName("en0")
                        .addAllIps(Arrays.asList("192.168.1.1"))
                        .setMac("00:0a:27:02:00:43:dd:09")
                        .build())
                .add(Interface.newBuilder()
                        .setName("lo0")
                        .addAllIps(Arrays.asList("127.0.0.1", "0:0:0:0:0:0:0:1"))
                        .setMac("")
                        .build())
                .build();

        boolean authorized = client.isSystemAuthorized(UserID.fromExternal("matt@aerofs.com"),
                "Mac", "Mac OS X", "Matt's Super Awesome Laptop", interfaces);

        assertEquals(true, authorized);
    }

}