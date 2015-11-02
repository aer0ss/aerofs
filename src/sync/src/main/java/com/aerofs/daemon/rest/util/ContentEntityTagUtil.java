package com.aerofs.daemon.rest.util;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.rest.handler.RestContentHelper;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import javax.ws.rs.core.EntityTag;
import java.sql.SQLException;

public class ContentEntityTagUtil
{
    private final RestContentHelper _helper;

    @Inject
    public ContentEntityTagUtil(RestContentHelper helper)
    {
        _helper = helper;
    }

    public @Nullable EntityTag etagForContent(SOID soid) throws SQLException
    {
        ContentHash hash = _helper.content(new SOKID(soid, _helper.selectBranch(soid)));
        return hash == null ? null : new EntityTag(BaseUtil.hexEncode(hash.getBytes()));
    }
}
