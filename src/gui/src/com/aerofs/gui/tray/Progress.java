package com.aerofs.gui.tray;

public class Progress {
    private final int _id;
    private String _tooltip;

    Progress(int id, String tooltip)
    {
        _id = id;
        _tooltip = tooltip;
    }

    public int getId()
    {
        return _id;
    }

    public String getTooltip()
    {
        return _tooltip;
    }

    public void setTooltip(String tooltip)
    {
        _tooltip = tooltip;
    }
}
