package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.rest.util.OAuthToken;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.util.EntityTagSet;

import java.io.InputStream;

public class EIFileUpload extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final EntityTagSet _ifMatch;
    public final InputStream _content;

    public EIFileUpload(IIMCExecutor imce, OAuthToken token, RestObject object, EntityTagSet ifMatch,
            InputStream content)
    {
        super(imce, token);
        _object = object;
        _ifMatch = ifMatch;
        _content = content;
    }
}
