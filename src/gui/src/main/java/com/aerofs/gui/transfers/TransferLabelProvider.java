package com.aerofs.gui.transfers;

import com.aerofs.base.BaseUtil;
import com.aerofs.ids.DID;
import com.aerofs.gui.GUIUtil;
import com.aerofs.gui.Images;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.RitualNotifications.PBSOCID;
import com.aerofs.proto.RitualNotifications.PBTransferEvent;
import com.aerofs.proto.RitualNotifications.PBTransportMethod;
import com.aerofs.ui.UIUtil;
import com.google.common.base.Objects;
import com.google.protobuf.ByteString;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class TransferLabelProvider extends LabelProvider implements ITableLabelProvider
{

    private final Map<Program, Image> _iconCache = new HashMap<Program, Image>();
    private final Map<Integer, Image> _progressCache = new HashMap<Integer, Image>();

    private boolean _showSOCID;
    private boolean _showDID;

    private GC _gc;
    private TableColumn _tc;

    TransferLabelProvider(GC gc, TableColumn tc)
    {
        _gc = gc;
        _tc = tc;
    }

    public void showSOCID(boolean enable)
    {
        _showSOCID = enable;
    }

    public void showDID(boolean enable)
    {
        _showDID = enable;
    }

    @Override
    public Image getColumnImage(Object element, int columnIndex)
    {
        if (element instanceof PBTransferEvent) {
            PBTransferEvent ev = (PBTransferEvent) element;
            switch (columnIndex) {
            case CompTransfersTable.COL_PATH: {
                if (!ev.hasPath() || UIUtil.isSystemFile(ev.getPath())) {
                    return Images.get(Images.ICON_METADATA);
                } else {
                    Path path = Path.fromPB(ev.getPath());
                    return Images.getFileIcon(path.last(), _iconCache);
                }
            }
            case CompTransfersTable.COL_PROG: return getProgressIcon(ev.getDone(), ev.getTotal());
            case CompTransfersTable.COL_TRANSPORT: return getTransportMethodIcon(ev.getTransport());
            case CompTransfersTable.COL_DEVICE: {
                return ev.getUpload()
                        ? Images.get(Images.ICON_ARROW_UP2)
                        : Images.get(Images.ICON_ARROW_DOWN2);
            }
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

    private Image getTransportMethodIcon(PBTransportMethod transport)
    {
        switch (transport) {
        case TCP:
            return Images.get(Images.ICON_SIGNAL3);
        case ZEPHYR:
            return Images.get(Images.ICON_SIGNAL1);
        default:
            return null;
        }
    }

    /**
     * This method takes fields from an upload or download event and format the text for
     *   display in the path column. The formatting does the following:
     *
     *  - It will attempt to resolve path to get the filename of the file being transferred.
     *  - If showSOCID is on, it will prepend the path with SOCID if the path can be resolved,
     *    and it will return the SOCID if the path cannot be resolved.
     *  - It will display the default text if the path cannot be resolved and showSOCID is off.
     *  - Regardless what the text is, it will shorten the text and add ellipses to make it
     *    fit in the column.
     *
     * @param pbsocid: the SOCID field in the upload/download event.
     * @param pbpath: the path field in the upload/download event if it's available,
     *   null otherwise.
     * @return formatted text to display in the path column.
     */
    private String formatPathText(PBSOCID pbsocid, @Nullable PBPath pbpath)
    {
        if (pbpath == null) {
            return _showSOCID ? formatSOCID(pbsocid) : S.LBL_UNKNOWN_FILE;
        } else {
            String text = UIUtil.getUserFriendlyPath(pbsocid, pbpath, Path.fromPB(pbpath));

            if (text.startsWith("/")) text = text.substring(1);
            if (_showSOCID) text = formatSOCID(pbsocid) + " - " + text;

            return GUIUtil.shortenText(_gc, text, _tc, true);
        }
    }

    private String formatSOCID(PBSOCID pbsocid)
    {
        return new SOCID(pbsocid).toString();
    }

    private String formatDevice(ByteString pbDID, @Nullable String displayName)
    {
        return (_showDID ? (pbDID == null ? "<null>" : new DID(BaseUtil.fromPB(pbDID)).toString()) + " - " : "")
                + Objects.firstNonNull(displayName, S.LBL_UNKNOWN_DEVICE);
    }

    @SuppressWarnings("fallthrough")
    private String formatTransport(PBTransportMethod transport)
    {
        switch (transport) {
        case TCP:
            return S.LBL_TRANSPORT_TCP;
        case ZEPHYR:
            return S.LBL_TRANSPORT_ZEPHYR;
        default:
        case UNKNOWN:
        case NOT_AVAILABLE:
            return "";
        }
    }

    @Override
    public String getColumnText(Object element, int columnIndex)
    {
        if (element instanceof PBTransferEvent) {
            PBTransferEvent ev = (PBTransferEvent) element;
            switch (columnIndex) {
            case CompTransfersTable.COL_PATH:
                return formatPathText(ev.getSocid(), ev.hasPath() ? ev.getPath() : null);
            case CompTransfersTable.COL_PROG:
                return Util.formatProgress(ev.getDone(), ev.getTotal());
            case CompTransfersTable.COL_DEVICE:
                return formatDevice(ev.getDeviceId(), ev.getDisplayName());
            case CompTransfersTable.COL_TRANSPORT:
                return formatTransport(ev.getTransport());
            default: return "";
            }
        } else {
            return columnIndex == CompTransfersTable.COL_PATH ? element.toString() : null;
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
