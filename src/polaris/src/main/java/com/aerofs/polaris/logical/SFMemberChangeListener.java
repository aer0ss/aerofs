package com.aerofs.polaris.logical;

import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;

public interface SFMemberChangeListener {
    void userLeftStore(UserID user, SID store);

    void userJoinedStore(UserID user, SID store);
}
