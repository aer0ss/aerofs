package com.aerofs.gui;

import com.aerofs.base.C;
import com.aerofs.lib.os.OSUtil;

public class GUIParam {

    public static final int MARGIN  = 16;
    // the spacing between major composites
    public static final int MAJOR_SPACING = MARGIN;

    // the margin used for horizontal margin on vertically centred dialogs
    public static final int WIDE_MARGIN = 2 * MARGIN;

    public static final long STAT_UPDATE_INTERVAL = 1 * C.SEC;

    // the spacing between widgets on a vertical grid
    public static final int VERTICAL_SPACING = 8;

    public static final int BUTTON_HORIZONTAL_SPACING = OSUtil.isOSX() ? 0 : 5;

    // this is the margin used by Team Server setup dialog pages at the top and bottom of the page.
    public static final int SETUP_PAGE_MARGIN_HEIGHT = 15;

    // minimum button width enforced on AeroFSButtons
    public static final int AEROFS_MIN_BUTTON_WIDTH = 80;
}
