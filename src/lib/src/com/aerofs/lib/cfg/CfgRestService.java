/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.cfg;

import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.cfg.CfgDatabase.Key;

public class CfgRestService
{
    public boolean isEnabled()
    {
        CfgDatabase db = Cfg.db();
        Key key = Key.REST_SERVICE;
        boolean defaultValue = PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT
                || Cfg.user().isAeroFSUser();

        // N.B. the default value for the whether the REST service is enabled depends on whether
        // we are in the hybrid cloud or the private cloud.
        // FIXME(AT): having Cfg dependent on configuration service is a bad idea.
        return db.getBoolean(key, defaultValue);
    }
}
