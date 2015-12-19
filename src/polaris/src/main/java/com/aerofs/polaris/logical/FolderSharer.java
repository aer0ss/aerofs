package com.aerofs.polaris.logical;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.ids.SID;

public interface FolderSharer {
    boolean shareFolder(AeroOAuthPrincipal principal, SID sid, String name);
}
