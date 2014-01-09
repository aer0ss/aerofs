package com.aerofs.gui;

import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.IAnalyticsEvent;
import com.aerofs.gui.conflicts.DlgConflicts;
import com.aerofs.gui.history.DlgHistory;
import com.aerofs.gui.sharing.DlgCreateSharedFolder;
import com.aerofs.gui.sharing.DlgManageSharedFolder;
import com.aerofs.gui.syncstatus.SyncStatusDialog;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.swtdesigner.SWTResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
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
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;

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

    /**
     * This is intended to be used to aggregate comparison results. The intention is to use it like:
     *
     * aggregateComparisonResults(comparison1, comparison2, comparison3)
     *
     * which will, in effect, compare two objects using a series of comparisons where the earlier
     * comparisons have priorities over the later comparisons.
     */
    public static int aggregateComparisonResults(int... results)
    {
        for (int result : results) if (result != 0) return result;
        return 0; // if we are here, all values must be 0
    }

    // compare booleans using true < false
    public static int compare(boolean p, boolean q)
    {
        if (p && !q) return -1;
        else if (q && !p) return 1;
        else return 0;
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

    public static void changeFont(Control control, int height, int style)
    {
        FontData fd = control.getFont().getFontData()[0];
        control.setFont(SWTResourceManager.getFont(fd.getName(), height, style));
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

        try {
            PBObjectAttributes.Type type = UIGlobals.ritual().getObjectAttributes(path.toPB())
                    .getObjectAttributes()
                    .getType();
            create = type != PBObjectAttributes.Type.SHARED_FOLDER;
        } catch (Exception e) {
            l.warn(Util.e(e));
            return;
        }

        if (path.isEmpty()) {
            // Sharing the root folder? C'mon, the UI should have prevented it.
            SVClient.logSendDefectAsync(true, "share root AeroFS folder?");
            UI.get().show(MessageType.WARN, "The root " + L.product() + " folder can't be shared.");
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
                new SyncStatusDialog(GUI.get().sh(), path).openDialog();
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
        GUI.get().asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                new DlgConflicts(GUI.get().sh(), path).openDialog();
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
     * Stubs out constructor for Button so we can do some custom business logic.
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

    /**
     * This class is used as an utility for implementing a new GUI layout and determining
     *   margins.
     *
     * Coater.coat(Control) visits a control and all its descendants recursively and
     *   set them each to a different color. It helps the developer visualize the layout
     *   and identify the cause of layout flaws. Typically used at top-level shells.
     *
     * Usage: new Coater().coat(shell);
     */
    public static class Coater
    {
        Color[] colors;
        int index;

        /**
         * Recursively visit the given control and its descendants and set each
         *   control's background to a different colour.
         *
         * @param control the root control to start coating
         */
        public void coat(Control control)
        {
            if (colors == null) init(control.getDisplay());
            visit(control);
        }

        /**
         * initialize the color pallete.
         *
         * @param device the device to allocate the color for
         */
        private void init(Device device)
        {
            // please use prime number of colors
            colors = new Color[] {
                    new Color(device, 0xFF, 0, 0),
                    new Color(device, 0xFF, 0xBB, 0),
                    new Color(device, 0xFF, 0xFF, 0),
                    new Color(device, 0xBB, 0xFF, 0),
                    new Color(device, 0, 0xFF, 0),
                    new Color(device, 0, 0xFF, 0xBB),
                    new Color(device, 0, 0xFF, 0xFF),
                    new Color(device, 0, 0xBB, 0xFF),
                    new Color(device, 0, 0, 0xFF),
                    new Color(device, 0xBB, 0, 0xFF),
                    new Color(device, 0xFF, 0, 0xFF),
            };

            index = 0;
        }

        private void visit(Control control)
        {
            control.setBackground(colors[index++ % colors.length]);

            if (control instanceof Composite) {
                for (Control child : ((Composite)control).getChildren()) {
                    visit(child);
                }
            }
        }
    }
}
