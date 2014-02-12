/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.sharing_rules;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExExternalServiceUnavailable;
import com.aerofs.lib.ex.sharing_rules.ExSharingRulesWarning;
import com.aerofs.sp.server.lib.SharedFolder;
import com.aerofs.sp.server.lib.user.User;

public interface ISharingRules
{
    Permissions onUpdatingACL(SharedFolder sf, User sharee, Permissions newPermissions) throws Exception;

    void throwIfAnyWarningTriggered() throws ExExternalServiceUnavailable,
            ExSharingRulesWarning;

    boolean shouldBumpEpoch();
}
