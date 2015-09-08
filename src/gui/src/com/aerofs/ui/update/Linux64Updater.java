package com.aerofs.ui.update;

import com.aerofs.labeling.L;

class Linux64Updater extends AbstractLinuxUpdater
{
    Linux64Updater()
    {
        super(L.productUnixName() + "-%s-x86_64.tgz");
    }
}
