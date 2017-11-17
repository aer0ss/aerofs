package com.aerofs.daemon.rest.event;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.ids.UploadID;
import com.aerofs.rest.auth.OAuthToken;
import com.aerofs.base.id.RestObject;
import com.aerofs.restless.util.ContentRange;
import com.aerofs.restless.util.EntityTagSet;

import javax.annotation.Nullable;
import java.io.InputStream;

public class EIFileUpload extends AbstractRestEBIMC
{
    public final RestObject _object;
    public final EntityTagSet _ifMatch;
    public final UploadID _ulid;
    public final @Nullable ContentRange _range;
    public final InputStream _content;

    public EIFileUpload(IIMCExecutor imce, OAuthToken token, RestObject object,
            EntityTagSet ifMatch, UploadID ulid, @Nullable ContentRange range, InputStream content)
    {
        super(imce, token);
        _object = object;
        _ifMatch = ifMatch;
        _ulid = ulid;
        _range = range;
        _content = content;
    }
}
