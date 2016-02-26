package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.daemon.lib.db.trans.Trans;

public interface IShareListener
{
    void onShare_(Trans t);
}
