package com.aerofs.gui;

import com.aerofs.lib.C;
import com.aerofs.lib.os.OSUtil;

public class GUIParam {

    public static final int MARGIN  = 16;
    // the spacing between major composites
    public static final int MAJOR_SPACING = MARGIN;
    public static final long STAT_UPDATE_INTERVAL = 5 * C.SEC;
    public static final int BUTTON_HORIZONTAL_SPACING = OSUtil.isOSX() ? 0 : 5;
}
