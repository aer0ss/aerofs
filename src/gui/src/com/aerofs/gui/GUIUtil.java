package com.aerofs.gui;

import javax.annotation.Nullable;

import com.aerofs.gui.diagnosis.DlgDiagnosis;
import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.sharing.DlgCreateSharedFolder;
import com.aerofs.gui.sharing.DlgManageSharedFolder;
import com.aerofs.gui.syncstatus.DlgSyncStatus;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Objects.PBObjectAttributes;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import org.slf4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.proto.Sv;
import com.swtdesigner.SWTResourceManager;

import java.io.IOException;

public class GUIUtil
{
    public static final Logger l = Util.l(GUIUtil.class);

    public static String getNewText(Text txt, VerifyEvent ev)
    {
        return getNewText(txt.getText(), ev);
    }

    public static String getNewText(String oldText, VerifyEvent ev)
    {
        return oldText.substring(0, ev.start) + ev.text + oldText.substring(ev.end);
    }

    public static abstract class AbstractListener implements Listener {

        Sv.PBSVEvent.Type _eventType;

        public AbstractListener(@Nullable Sv.PBSVEvent.Type t)
        {
            _eventType = t;
        }

        @Override
        public void handleEvent(Event event)
        {
            if (_eventType != null) SVClient.sendEventAsync(_eventType);
            handleEventImpl(event);
        }

        protected abstract void handleEventImpl(Event event);
    };

    public static void setShellIcon(Shell shell)
    {
        if (OSUtil.get().getOSFamily() == OSUtil.OSFamily.OSX) {
            shell.setImage(Images.get(Images.ICON_LOGO64));
        } else {
            shell.setImages(new Image[] {
                    Images.get(Images.ICON_LOGO16),
                    Images.get(Images.ICON_LOGO32),
                    Images.get(Images.ICON_LOGO64),
                });
        }
    }

    /**
     * place the shell at the center of the primary monitor
     */
    public static void centerShell(Shell shell)
    {
        Monitor primary = shell.getDisplay().getPrimaryMonitor();
        Rectangle bounds = primary.getBounds();
        Rectangle rect = shell.getBounds();

        int x = bounds.x + (bounds.width - rect.width) / 2;
        int y = bounds.y + (bounds.height - rect.height) / 2;

        shell.setLocation(x, y);
    }

    /**
     * the shell must be visible for forceActive() to take effect
     */
    public static void forceActive(Shell shell)
    {
        // setVisible() causes problems on Windows
        //boolean visible = shell.getVisible();
        //shell.setVisible(true);
        shell.forceActive();
        shell.setFocus();
        //shell.setVisible(visible);
    }

    public static String shortenText(GC gc, String str, TableColumn tc,
            boolean hasIcon)
    {
        return shortenText(gc, str, tc.getWidth(), hasIcon);
    }

    public static String shortenText(GC gc, String str, int width,
            boolean hasIcon)
    {
        // the magic numbers below are determined via try and error.
        // I found no way to tell the exact width.
        int margin;
        if (hasIcon) {
            margin = 30;
        } else {
            switch (OSUtil.get().getOSFamily()) {
            case LINUX: margin = 6; break;
            case OSX: margin = 3; break;
            case WINDOWS: margin = 30; break;
            default: margin = 0; break;
            }
        }

        return GUIUtil.shortenText(gc, 0, str, width - margin);
    }

    /**
     * Note: the code is copied from org.eclipse.swt.extension.util.UIUtil
     *
     * Shorten the given text <code>t</code> so that its length doesn't exceed
     * the given width. The default implementation replaces characters in the
     * center of the original string with an ellipsis ("..."). Override if you
     * need a different strategy.
     *
     * @param gc
     *            the gc to use for text measurement
     * @param str
     *            the text to shorten
     * @param drawFlags
     *            the flags defined in GC.textExtent()
     * @param width
     *            the width to shorten the text to, in pixels
     * @return the shortened text
     */
    public static String shortenText(GC gc, int drawFlags, String str, int width)
    {
        if (str == null) return null;
        if (gc.textExtent(str, drawFlags).x <= width) return str;

        int w = gc.textExtent(ELLIPSIS, drawFlags).x;
        if (width <= w) return str;

        int l = str.length();
        int max = l / 2;
        int min = 0;
        int mid = (max + min) / 2 - 1;
        if (mid <= 0) return str;
        TextLayout layout = new TextLayout(Display.getDefault());
        layout.setText(str);
        mid = validateOffset(layout, mid);
        while (min < mid && mid < max)
        {
            String s1 = str.substring(0, mid);
            String s2 = str.substring(validateOffset(layout, l - mid), l);
            int l1 = gc.textExtent(s1, drawFlags).x;
            int l2 = gc.textExtent(s2, drawFlags).x;
            if (l1 + w + l2 > width)
            {
                max = mid;
                mid = validateOffset(layout, (max + min) / 2);
            }
            else if (l1 + w + l2 < width)
            {
                min = mid;
                mid = validateOffset(layout, (max + min) / 2);
            }
            else
            {
                min = max;
            }
        }
        String result = mid == 0 ? str : str.substring(0, mid) + ELLIPSIS
                + str.substring(validateOffset(layout, l - mid), l);
        layout.dispose();
        return result;
    }

    private static final String ELLIPSIS = "...";

    private static int validateOffset(TextLayout layout, int offset)
    {
        int nextOffset = layout.getNextOffset(offset, SWT.MOVEMENT_CLUSTER);
        if (nextOffset != offset) return layout.getPreviousOffset(nextOffset,
                SWT.MOVEMENT_CLUSTER);
        return offset;
    }

    public static Point getExtent(Drawable d, String text)
    {
        GC gc = new GC(d);
        try {
            return gc.textExtent(text);
        } finally {
            gc.dispose();
        }
    }

    public static int getInterButtonHorizontalSpace(GridLayout gl)
    {
        return OSUtil.isOSX() ? 0 : gl.horizontalSpacing;
    }

    public static Font makeBold(Font org)
    {
        FontData fd = org.getFontData()[0];
        return SWTResourceManager.getFont(fd.getName(), fd.getHeight(), SWT.BOLD);
    }

    public static Font makeSubtitle(Font org)
    {
        FontData fd = org.getFontData()[0];
        return SWTResourceManager.getFont(fd.getName(), (int)
                (fd.getHeight() * 0.9f), fd.getStyle());
    }

    public static boolean isWindowBuilderPro()
    {
        return false;
    }

    // probably due to a bug in SWT, disabling a Text may remove some characters
    // from the text box :S
    public static void setEnabled(Text text, boolean b)
    {
        if (b) {
            text.setEnabled(true);
        } else {
            String str = text.getText();
            text.setEnabled(false);
            text.setText(str);
        }
    }

    public static int alwaysOnTop()
    {
        // ON_TOP makes dialogs frame-less on Linux
        return OSUtil.isLinux() ? 0 : SWT.ON_TOP;
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void createOrManageSharedFolder(final Path path)
    {
        final boolean create;

        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            PBObjectAttributes.Type type = ritual.getObjectAttributes(Cfg.user().getString(),
                    path.toPB())
                    .getObjectAttributes()
                    .getType();
            create = type != PBObjectAttributes.Type.SHARED_FOLDER;
        } catch (Exception e) {
            l.warn(Util.e(e));
            return;
        } finally {
            ritual.close();
        }

        if (path.isEmpty()) {
            // Sharing the root folder? C'mon, the UI should have prevented it.
            SVClient.logSendDefectAsync(true, "share root AeroFS folder?");
            UI.get().show(MessageType.WARN, "The root " + L.PRODUCT + " folder can't be shared.");
            return;
        }

        GUI.get().asyncExec(new Runnable() {
            @Override
            public void run()
            {
                if (create) {
                    new DlgCreateSharedFolder(GUI.get().sh(), path).openDialog();
                } else {
                    new DlgManageSharedFolder(GUI.get().sh(), path).openDialog();
                }
            }
        });
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showSyncStatus(final Path path)
    {
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                new DlgSyncStatus(GUI.get().sh(), path).openDialog();
            }
        });
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showVersionHistory(final Path path)
    {
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                new DlgHistory(GUI.get().sh(), path).openDialog();
            }
        });
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showConflictResolutionDialog(final Path path)
    {
        // TODO: simpler dialog for resolving conflict of a single file
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                new DlgDiagnosis(GUI.get().sh(), false).openDialog();
            }
        });
    }

    /**
     * Open a folder or http or https URI in the appropriate application for the platform.
     *
     * @param pathOrUri a string
     * @return true on success, false if the program failed to launch
     */
    public static boolean launch(String pathOrUri)
    {
        if (OSUtil.isLinux()) {
            try {
                SystemUtil.execBackground("xdg-open", pathOrUri);
            } catch (IOException e) {
                l.warn("Couldn't open " + pathOrUri + ": " + Util.e(e));
                return false;
            }
            return true;
        } else {
            return Program.launch(pathOrUri);
        }
    }
}
