package com.aerofs.gui;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.gui.conflicts.DlgConflicts;
import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.sharing.invitee.DlgInviteUsers;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.error.ErrorMessage;
import com.aerofs.ui.error.ErrorMessages;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
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
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.aerofs.gui.sharing.invitee.DlgInviteUsers.getLabelByPath;

public class GUIUtil
{
    private static final Logger l = Loggers.getLogger(GUIUtil.class);

    // this is the Unicode character that looks like a downward solid triangle, we use it
    // on some widgets to indicate that there's a drop-down menu.
    public static final String TRIANGLE_DOWNWARD = "\u25BE";
    // this is the unicode character for black circle. It is used as a bullet.
    public static final String BULLET = "\u25CF";

    public static String getNewText(Text txt, VerifyEvent ev)
    {
        return getNewText(txt.getText(), ev);
    }

    public static String getNewText(String oldText, VerifyEvent ev)
    {
        return oldText.substring(0, ev.start) + ev.text + oldText.substring(ev.end);
    }

    public static abstract class AbstractListener implements Listener
    {
        IAnalyticsEvent _analyticsEvent;

        public AbstractListener(@Nullable IAnalyticsEvent analyticsEvent)
        {
            _analyticsEvent = analyticsEvent;
        }

        @Override
        public void handleEvent(Event event)
        {
            if (_analyticsEvent != null) UIGlobals.analytics().track(_analyticsEvent);
            handleEventImpl(event);
        }

        protected abstract void handleEventImpl(Event event);
    }

    public static void setShellIcon(Shell shell)
    {
        if (OSUtil.get().getOSFamily() == OSUtil.OSFamily.OSX) {
            shell.setImage(Images.get(Images.ICON_LOGO256));
        } else {
            shell.setImages(new Image[] {
                    Images.get(Images.ICON_LOGO16),
                    Images.get(Images.ICON_LOGO16x2),
                    Images.get(Images.ICON_LOGO32),
                    Images.get(Images.ICON_LOGO32x2),
                    Images.get(Images.ICON_LOGO48),
                    Images.get(Images.ICON_LOGO48x2),
                    Images.get(Images.ICON_LOGO64),
                    Images.get(Images.ICON_LOGO64x2),
                    Images.get(Images.ICON_LOGO256),
                    Images.get(Images.ICON_LOGO256x2),
                    Images.get(Images.ICON_LOGO512),
                    Images.get(Images.ICON_LOGO512x2),
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
     * @return  SWT.SHEET if {@param sheet} is true, else SWT.DIALOG_TRIM
     *          | SWT.RESIZE if resizable
     *          & ~SWT.CLOSE if not closable
     */
    public static int createShellStyle(boolean sheet, boolean resizable, boolean closable)
    {
        return (sheet ? SWT.SHEET : SWT.DIALOG_TRIM)
                | (resizable ? SWT.RESIZE : SWT.NONE)
                // N.B. SWT.DIALOG_TRIM includes SWT.CLOSE by default.
                & ~(closable ? SWT.NONE: SWT.CLOSE);
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

    public static Font makeEmphasis(Font org)
    {
        FontData fd = org.getFontData()[0];
        return  SWTResourceManager.getFont(fd.getName(), fd.getHeight() * 3 / 2, SWT.BOLD);
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

    /**
     * @param height, the ratio (out of 100) to adjust the control's font height by.
     *                In other words, 100 -> 100% -> unchanged, 120 -> 120% -> 20% taller, 80 -> 80% -> 20% shorter.
     */
    public static void updateFont(Control control, int height, int style)
    {
        FontData fd = control.getFont().getFontData()[0];
        control.setFont(SWTResourceManager.getFont(fd.getName(), fd.getHeight() * height / 100, style));
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

    private static boolean isTryingToShareRoot(Path path)
    {
        if (new Path(Cfg.rootSID()).equals(path)) {
            // Sharing the default root folder?
            newDefectWithLogs("gui.share_folder")
                    .setMessage("share default root?")
                    .sendAsync();
            UI.get().show(MessageType.WARN, "The root " + L.product() + " folder can't be shared.");
            return true;
        }
        return false;
    }

    public static void createLink(final Path path)
    {
        // Do not allow link sharing for the root.
        if (isTryingToShareRoot(path)) {
            return;
        }
        try {
            final String link = UIGlobals.ritual().createUrl(path.toPB()).getLink();
            GUI.get().asyncExec(() -> {
                String linkUrl = WWW.DASHBOARD_HOST_URL + "/l/" + link;
                copyLinkToClipboard(linkUrl);
                showLinkUrl(path, linkUrl);
            });
        } catch (Exception e) {
            ErrorMessages.show(e, "Unable to create link for " + path.last() + ".",
                    new ErrorMessage(ExNoPerm.class, S.NON_OWNER_CANNOT_CREATE_LINK));
            l.info("Unable to create link for {}. Got exception {}", path, LogUtil.suppress(e));
        }
    }

    private static void showLinkUrl(Path path, final String linkUrl)
    {
        String toastMsg = "A link has been created for " + path.last() +
                " and copied to your clipboard (Click to view)";
        // Notify the user and open link in browser if clicked.
        UI.get().notify(MessageType.INFO, toastMsg, () -> GUIUtil.launch(linkUrl));
    }

    private static void copyLinkToClipboard(String linkUrl)
    {
        // Copy link to the clipboard.
        Clipboard clipboard = new Clipboard(GUI.get().disp());
        clipboard.setContents(new String[]{ linkUrl},
            new Transfer[]{TextTransfer.getInstance()});
        clipboard.dispose();
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void shareFolder(final Path path, final String name)
    {
        if (isTryingToShareRoot(path)) {
            return;
        }
        GUI.get().asyncExec(() -> new DlgInviteUsers(GUI.get().sh(),
                getLabelByPath(path), path, name, false).openDialog());
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showVersionHistory(final Path path)
    {
        GUI.get().asyncExec(() -> new DlgHistory(GUI.get().sh(), path).openDialog());
    }

    /**
     * This method can be run in a non-UI thread
     */
    public static void showConflictResolutionDialog(final Path path)
    {
        GUI.get().asyncExec(() -> new DlgConflicts(GUI.get().sh(), path).openDialog());
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

    /**
     * @param url - the URL to launch
     * @return a SelectionListener who, when invoked, will open the URL on the native platform.
     */
    public static SelectionListener createUrlLaunchListener(final String url)
    {
        return new SelectionAdapter()
        {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                launch(url);
            }
        };
    }

    public static RowLayout newCentredRowLayout(int style)
    {
        RowLayout layout = new RowLayout(style);

        // SWT RowLayout has, by default, 0 margin width/height and 3 margin top/bottom/left/right
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.spacing = 0;
        layout.center = true;

        return layout;
    }

    public static GridLayout newGridLayout()
    {
        return newGridLayout(1, false);
    }

    public static GridLayout newGridLayout(int numCols, boolean equalWidth)
    {
        GridLayout layout = new GridLayout(numCols, equalWidth);

        // SWT GridLayout has, by default, 0 margin top/bottom/left/right and 5 margin width/height
        layout.marginTop = 0;
        layout.marginBottom = 0;
        layout.marginLeft = 0;
        layout.marginRight = 0;
        layout.marginWidth = 0;
        layout.marginHeight = 0;

        return layout;
    }

    /**
     * @return a container for a row of buttons in which the buttons are packed (preferred size)
     */
    public static Composite newPackedButtonContainer(Composite parent)
    {
        return newButtonContainer(parent, true);
    }

    /**
     * @return a Composite suitable for hosting a row of buttons
     *
     * This method takes care of SWT and platform specific crazyness re margins and spacing...
     *
     * The result container will remove the extra margin on OSX and the container will bound the
     * buttons as tight as possible
     */
    public static Composite newButtonContainer(Composite parent, boolean pack)
    {
        Composite buttons = new Composite(parent, SWT.NONE);
        RowLayout buttonLayout = new RowLayout();
        buttonLayout.pack = pack;
        buttonLayout.wrap = false;
        buttonLayout.fill = true;
        buttonLayout.center = true;
        buttonLayout.marginBottom = 0;
        buttonLayout.marginTop = 0;
        buttonLayout.marginHeight = 0;
        buttonLayout.marginLeft = 0;
        buttonLayout.marginRight = 0;
        buttonLayout.marginWidth = 0;
        buttonLayout.spacing = GUIParam.BUTTON_HORIZONTAL_SPACING;

        if (OSUtil.isOSX()) {
            // workaround broken margins on OSX
            buttonLayout.marginLeft = -4;
            buttonLayout.marginRight = -4;
            buttonLayout.marginTop = -4;
            buttonLayout.marginBottom = -6;
        }
        buttons.setLayout(buttonLayout);
        return buttons;
    }

    /**
     * This will create a composite that will vertically align its content / children with
     * a platform specific group.
     *
     * Say if you have a Tree and a Group side-by-side. Since Group impl. is based on platform's
     * native impl, the chances are the top and bottom edge won't line up.
     *
     * To work around this, put the Tree (not the Group!) in this container, and this
     * container will add sufficient margins to line it up with the Group.
     *
     * Note that the child, the Tree in this case, needs to set it's layout data to
     * GridData(SWT.FILL, SWT.FILL, true, true) as well.
     */
    public static Composite createGroupAligningContainer(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        if (OSUtil.isOSX()) {
            layout.marginHeight = 4;
        } else if (OSUtil.isWindows()) {
            layout.marginHeight = 0;
            layout.marginTop = 7;
            layout.marginBottom = 2;
        } else {
            layout.marginHeight = 0;
        }
        container.setLayout(layout);
        return container;
    }

    /**
     * Stubs out constructor for Button so we can do some custom logic.
     *
     * @return an AeroFSButton if the OS is windows or linux and it's a push button,
     *   a vanilla SWT Button otherwise.
     */
    public static Button createButton(Composite parent, int style)
    {
        return (OSUtil.isWindows() || OSUtil.isLinux())
                && (style & (SWT.ARROW | SWT.CHECK | SWT.RADIO | SWT.TOGGLE)) == 0
                ? new AeroFSButton(parent, style)
                : new Button(parent, style);
    }

    public static Label createLabel(Composite parent, int style)
    {
        return new AeroFSLabel(parent, style);
    }
}
