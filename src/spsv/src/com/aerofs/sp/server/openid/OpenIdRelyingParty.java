package com.aerofs.sp.server.openid;

import com.aerofs.lib.LibParam;
import com.aerofs.sp.server.UserManager;
import com.dyuproject.openid.*;
import com.dyuproject.openid.ext.AxSchemaExtension;
import com.dyuproject.openid.ext.SRegExtension;
import com.dyuproject.util.http.SimpleHttpConnector;

public class OpenIdRelyingParty {

    public static RelyingParty getRelyingParty()
    {
        RelyingParty relyingParty = new RelyingParty(
                new OpenIdContext(
                        new DefaultDiscovery(),
                        makeAssociation(),
                        new SimpleHttpConnector()),
                new UserManager(), new IdentifierSelectUserCache(), true);

        if (LibParam.OpenId.IDP_USER_EXTENSION.equals("ax")) {
            relyingParty.addListener(new AxSchemaExtension().addExchange("email")
                    .addExchange("firstname")
                    .addExchange("lastname"));
        }

        if (LibParam.OpenId.IDP_USER_EXTENSION.equals("sreg")) {
            relyingParty.addListener(
                    new SRegExtension().addExchange("email").addExchange("fullname"));
        }
        // other values are ignored as possible communist plots.

        return relyingParty;
    }

    protected static Association makeAssociation()
    {
        return LibParam.OpenId.ENDPOINT_STATEFUL ? new DiffieHellmanAssociation() : new DumbAssociation();
    }
}
