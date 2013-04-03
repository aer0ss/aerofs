package com.aerofs.gui.transfers;

import com.aerofs.gui.Images;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent;
import com.aerofs.proto.RitualNotifications.PBDownloadEvent.State;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBUploadEvent;
import com.aerofs.ui.UIUtil;
import com.google.common.base.Objects;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;

import java.util.HashMap;
import java.util.Map;

public class LabelProvider
extends org.eclipse.jface.viewers.LabelProvider implements ITableLabelProvider
{

    private final Map<Program, Image> _iconCache = new HashMap<Program, Image>();
    private final Map<Integer, Image> _progressCache = new HashMap<Integer, Image>();
    private final CompTransfersTable _view;

    private boolean _showSOCID;

    LabelProvider(CompTransfersTable view)
    {
        _view = view;
    }

    public void showSOCID(boolean enable)
    {
        _showSOCID = enable;
    }

    @Override
    public Image getColumnImage(Object element, int columnIndex)
    {
        if (element instanceof PBUploadEvent) {
            PBUploadEvent ev = (PBUploadEvent) element;
            switch (columnIndex) {
            case CompTransfersTable.COL_PATH: {
                if (!ev.hasPath() || UIUtil.isSystemFile(ev.getPath())) {
                    return Images.get(Images.ICON_METADATA);
                } else {
                    Path path = new Path(ev.getPath());
                    return Images.getFileIcon(path.last(), _iconCache);
                }
            }
            case CompTransfersTable.COL_PROG: return getProgressIcon(ev.getDone(), ev.getTotal());
            case CompTransfersTable.COL_DEVICE: return Images.get(Images.ICON_ARROW_UP2);
            default: return null;
            }

        } else if (element instanceof PBDownloadEvent) {
            PBDownloadEvent ev= (PBDownloadEvent) element;
            switch (columnIndex) {
            case CompTransfersTable.COL_PATH: {
                if (!ev.hasPath() || UIUtil.isSystemFile(ev.getPath())) {
                    return Images.get(Images.ICON_METADATA);
                } else {
                    Path path = new Path(ev.getPath());
                    return Images.getFileIcon(path.last(), _iconCache);
                }
            }
            case CompTransfersTable.COL_PROG: {
                if (ev.getState() == State.ONGOING) {
                    return getProgressIcon(ev.getDone(), ev.getTotal());
                } else {
                    return null;
                }
            }
            case CompTransfersTable.COL_DEVICE: return Images.get(Images.ICON_ARROW_DOWN2);
            default: return null;
            }

        } else if (element instanceof String) {
            return null;

        } else {
            assert element instanceof Exception;
            return columnIndex == CompTransfersTable.COL_PATH ? Images.get(Images.ICON_ERROR) : null;
        }
    }

    private Image getProgressIcon(long done, long total)
    {
        final int columnHeight = 16;
        Color blue = new Color(Display.getCurrent(), 0, 160, 248);
        Color lightGrey = new Color(Display.getCurrent(), 238, 238, 238);
        try {
            return Images.getPieChart(done, total, columnHeight, lightGrey, blue, null, _progressCache);
        } finally {
            blue.dispose();
            lightGrey.dispose();
        }
    }

    private String getText(PBSOCID pbsocid, PBPath pbpath, Path path)
    {
        String text = UIUtil.getUserFriendlyPath(pbsocid, pbpath, path);
        if (text.startsWith("/"))
            text = text.substring(1);
        if (_showSOCID)
            text = new SOCID(pbsocid).toString() + " / " + text;
        return _view.shortenPath(text);
    }

    @Override
    public String getColumnText(Object element, int columnIndex)
    {
        if (element instanceof PBUploadEvent) {
            PBUploadEvent ev = (PBUploadEvent) element;
            switch (columnIndex) {
            case CompTransfersTable.COL_PATH:
                if (ev.hasPath()) {
                    return getText(ev.getSocid(), ev.getPath(), new Path(ev.getPath()));
                } else {
                    return _showSOCID ? new SOCID(ev.getSocid()).toString() : S.LBL_UNKNOWN_FILE;
                }
            case CompTransfersTable.COL_PROG:
                return Util.formatProgress(ev.getDone(), ev.getTotal());
            case CompTransfersTable.COL_DEVICE:
                return Objects.firstNonNull(ev.getDisplayName(), S.LBL_UNKNOWN_DEVICE);
            default: return "";
            }

        } else if (element instanceof PBDownloadEvent) {
            PBDownloadEvent ev = (PBDownloadEvent) element;
            switch (columnIndex) {
            case CompTransfersTable.COL_PATH:
                if (ev.hasPath()) {
                    return getText(ev.getSocid(), ev.getPath(), new Path(ev.getPath()));
                } else {
                    return _showSOCID ? new SOCID(ev.getSocid()).toString() : S.LBL_UNKNOWN_FILE;
                }
            case CompTransfersTable.COL_PROG:
                return downloadStateToProgressString(ev);
            case CompTransfersTable.COL_DEVICE:
                return Objects.firstNonNull(ev.getDisplayName(), S.LBL_UNKNOWN_DEVICE);
            default: return "";
            }

        } else {
            return columnIndex == CompTransfersTable.COL_PATH ? element.toString() : null;
        }
    }

    public static String downloadStateToProgressString(PBDownloadEvent ev)
    {
        State s = ev.getState();
        if (s == State.ONGOING) {
            return Util.formatProgress(ev.getDone(), ev.getTotal());
        } else {
            return s.toString().toLowerCase();
        }
    }

    @Override
    public void dispose()
    {
        for (Image img : _iconCache.values()) img.dispose();
        _iconCache.clear();

        for (Image img : _progressCache.values()) img.dispose();
        _progressCache.clear();
    }
}
