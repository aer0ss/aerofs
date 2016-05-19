package com.aerofs.sp.server.openid;

import com.dyuproject.openid.*;
import com.dyuproject.util.http.HttpConnector.Response;
import com.dyuproject.util.http.UrlEncodedParameterMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

// This class is used in place of DiffieHellman for OpenId servers that are too...
// primitive? Dumb? to use the required association model.
// On "associate" we simply say "yes" and hack up the user association; without this,
// the dyu library will fail saying that the user is not associated.
// On "verify" we create a dumb-mode identification request to the server.
public class DumbAssociation implements Association {
    private static final Logger l = LoggerFactory.getLogger(DumbAssociation.class);

    @Override
    public boolean associate(OpenIdUser user, OpenIdContext context) throws Exception {
        // Ugly. We can't do the equivalent of setAssocHandle() any other way than the following
        Map<String, Object> hashMap = new HashMap<String, Object>();
        hashMap.put("a", user.getClaimedId());
        hashMap.put("c", Constants.Assoc.ASSOC_HANDLE);
        hashMap.put("d", new HashMap<Object, Object>());
        hashMap.put("e", user.getOpenIdServer());

        user.fromJSON(hashMap);

        return true;
    }

    @Override
    public boolean verifyAuth(OpenIdUser user, Map<String, String> authRedirect, OpenIdContext context)
            throws Exception {
        if (!Constants.Mode.ID_RES.equals(authRedirect.get(Constants.OPENID_MODE))) {
            l.info("Response from server was not id_res: {}", authRedirect.get(Constants.OPENID_MODE));
            return false;
        }

        l.debug("OpenId using stateless auth-verify mode");

        // Build our new request by starting with everything from the authRedirect map
        UrlEncodedParameterMap map = new UrlEncodedParameterMap(user.getOpenIdServer());

        // Theoretically all auth-response params are to be passed to check_authentication;
        // in practice the OP might choke on non-openid params.
        map.putAll(authRedirect);
        map.put(Constants.OPENID_MODE, "check_authentication");
        map.remove("sp.nonce");

        Response response = context.getHttpConnector().doGET(
                user.getOpenIdServer(), (Map<?, ?>) null, map);
        BufferedReader br = null;
        Map<String, Object> results = new HashMap<String, Object>();
        try {
            br = new BufferedReader(new InputStreamReader(
                    response.getInputStream(), Constants.DEFAULT_ENCODING), 1024);
            DiffieHellmanAssociation.parseInputByLineSeparator(br, ':', results);
        } finally {
            if (br != null)
                br.close();
        }

        if (results.containsKey("is_valid")) {
            String isValid = results.get("is_valid").toString();
            if (isValid.toLowerCase().equals("true")) {
                return true;
            }
        }
        return false;
    }
}
