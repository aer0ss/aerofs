package com.aerofs.gui;

import com.aerofs.base.Lazy;
import com.aerofs.base.Loggers;
import com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.os.OSUtil;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;

import javax.annotation.Nonnull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.IN_SYNC;
import static com.aerofs.gui.tray.TrayIcon.RootStoreSyncStatus.OUT_OF_SYNC;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.round;
import static java.lang.Math.sin;

// learn about resource management: http://help.eclipse.org/ganymede/index.jsp?topic=/org.eclipse.platform.doc.isv/reference/api/index.html

public class Images {

    public static final String ICON_LOGO16 = "logo_16x16.png";
    public static final String ICON_LOGO16x2 = "logo_16x16@2x.png";
    public static final String ICON_LOGO32 = "logo_32x32.png";
    public static final String ICON_LOGO32x2 = "logo_32x32@2x.png";
    public static final String ICON_LOGO48 = "logo_48x48.png";
    public static final String ICON_LOGO48x2 = "logo_48x48@2x.png";
    public static final String ICON_LOGO64 = "logo_64x64.png";
    public static final String ICON_LOGO64x2 = "logo_64x64@2x.png";
    public static final String ICON_LOGO256 = "logo_256x256.png";
    public static final String ICON_LOGO256x2 = "logo_256x256@2x.png";
    public static final String ICON_LOGO512 = "logo_512x512.png";
    public static final String ICON_LOGO512x2 = "logo_512x512@2x.png";
    public static final String ICON_LOGO32_ERROR = "logo32err.png";

    public static final String ICON_FILE = "file.gif";
    private static final String ICON_FOLDER = "folder.png";
    private static final String ICON_SHARED_FOLDER = "sharedFolder.png";
    private static final String ICON_FOLDER_YOSEMITE = "folderYosemite.png";
    private static final String ICON_SHARED_FOLDER_YOSEMITE = "sharedFolderYosemite.png";
    public static final String ICON_TICK = "tick.png";
    public static final String ICON_WARNING = "warning.png";
    public static final String ICON_ERROR = "exclamation.png";
    public static final String ICON_METADATA = "bulletBlue.png";
    public static final String ICON_ARROW_UP = "arrowUp.png";
    public static final String ICON_ARROW_UP2 = "arrowUp2.png";
    public static final String ICON_ARROW_DOWN = "arrowDown.png";
    public static final String ICON_ARROW_DOWN2 = "arrowDown2.png";
    public static final String ICON_NIL = "nil.png";
    public static final String ICON_SPIN = "spin.gif";
    // N.B. visually, signal1, 2, 3, has 2, 3, 4 bars respectively.
    public static final String ICON_SIGNAL1 = "signal1.png";
    public static final String ICON_SIGNAL2 = "signal2.png";
    public static final String ICON_SIGNAL3 = "signal3.png";

    public static final String IMG_SETUP = "setup.png";

    public static final String ICON_USER = "user.png";
    public static final String ICON_GROUP = "group.png";

    private static final Map<String, Image> s_imgs = new HashMap<>();

    // delay (ms) between each spinner frame
    private static final int SPINNER_DELAY = 100;
    private static Image[] s_spinner_frames;

    public static Image get(String key)
    {
        Image img = s_imgs.get(key);
        if (img == null) {
            String path = AppRoot.abs() + LibParam.ICONS_DIR + key;
            try {
                img = new Image(Display.getCurrent(), path);
                s_imgs.put(key, img);
            } catch (Exception e) {
                Loggers.getLogger(Images.class).error("cannot load image " + path + ": " + e);
            }
        }
        return img;
    }

    public static int getSpinnerFrameDelay()
    {
        return SPINNER_DELAY;
    }

    public static Image getSpinnerFrame(int frame)
    {
        if (s_spinner_frames == null) {
            // image downloaded from http://ajaxload.info/
            ImageLoader loader = new ImageLoader();
            loader.load(AppRoot.abs() + LibParam.ICONS_DIR + Images.ICON_SPIN);
            s_spinner_frames = new Image[loader.data.length];
            Display display = Display.getCurrent();
            for (int i = 0; i < loader.data.length; i++) {
                s_spinner_frames[i] = new Image(display, loader.data[i]);
            }
        }
        return s_spinner_frames[frame % s_spinner_frames.length];
    }

    public static Image getSharedFolderIcon()
    {
        return OSUtil.isOSXYosemiteOrNewer() ? get(ICON_SHARED_FOLDER_YOSEMITE) : get(ICON_SHARED_FOLDER);
    }

    public static Image getFolderIcon()
    {
        return OSUtil.isOSXYosemiteOrNewer() ? get(ICON_FOLDER_YOSEMITE) : get(ICON_FOLDER);
    }

    /**
     * N.B. remember to dispose the cache after use
     */
    public static Image getFileIcon(String fileName, Map<Program, Image> cache)
    {
        return getFileIcon(fileName, true, cache);
    }

    /**
     * N.B. remember to dispose the cache after use
     */
    public static Image getFileIcon(String fileName, boolean scale, Map<Program, Image> cache)
    {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) return get(ICON_FILE);

        String extension = fileName.substring(dot);
        Program program = Program.findProgram(extension);
        if (program == null) return get(ICON_FILE);

        Image image = cache.get(program);
        if (image == null) {
            ImageData imageData = null;
            try {
                imageData = program.getImageData();
            } catch (NullPointerException ignored) {}     // Work around a SWT bug where a NPE may
                                                          // be thrown for some unknown reason
            if (imageData == null) {
                image = get(ICON_FILE);

            } else {
                image = new Image(Display.getCurrent(), imageData);

                // scale to 16x16
                if (scale) {
                    Rectangle bounds = image.getBounds();
                    if(bounds.width != 16 || bounds.height != 16) {
                        Image scaled = new Image(Display.getCurrent(),
                                image.getImageData().scaledTo(16, 16));
                        image.dispose();
                        image = scaled;
                    }
                }
            }

            cache.put(program, image);
        }

        return image;
    }

    /**
     * Creates a simple pie chart diagram showing the progress of "done" over "total"
     *
     * @param done: amount of work already done.
     * @param total: total amount of work to do. Must be > 0.
     * @param size: the height and width of the resulting image.
     * @param bgColor: Color used for the background of the pie chart, or null to disable drawing
     *                 the background
     * @param fgColor: Color used for the foreground of the pie chart. Must be non-null.
     * @param borderColor: Color of the border, or null to disable drawing the border.
     * @param cache: (optional, can be null) an instance of a map where the result will be cached,
     *               to avoid re-drawing the same chart times and times again.
     * @return an Image of size x size pixels showing the pie chart. Remember to dispose
     * the image when it's no longer needed.
     */
    public static Image getPieChart(long done, long total, int size, Color bgColor, Color fgColor, Color borderColor, Map<Integer, Image> cache)
    {
        assert done >= 0 && done <= total;
        assert total > 0;
        assert size > 0;
        assert fgColor != null;

        Image result = null;

        int step = 0, totalSteps = 0;

        if (cache != null) {
            // If we are caching the result, we resample the done/total ratio over a shorter span,
            // otherwise we would not hit the cache for almost similar images (like 1/1000 vs 2/1000)
            // The new span we choose is PI * size of the graph, since this is roughly the amount of
            // pixels in the perimeter of the circle (which is the smallest perceivable change)
            totalSteps = round((float) PI * size);
            step = round(totalSteps * done / total);

            result = cache.get(step);
            if (result != null) {
                return result;
            }
        }

        final Device device = Display.getCurrent();

        Image pie = new Image(device, size, size);
        GC gc = new GC(pie);
        gc.setAntialias(SWT.ON);

        try {
            // Draw the pie chart
            final int start = 90;
            final int angle = (cache != null) ? Math.max(round(360 * step / totalSteps), 1)
                                              : Math.max(round(360 * done / total), 1);

            if (bgColor != null) {
                gc.setBackground(bgColor);
                gc.fillArc(0, 0, size, size , start - angle, angle - 360);
            }

            gc.setBackground(fgColor);
            gc.fillArc(0, 0, size, size , start, -angle);

            if (borderColor != null) {
                gc.setForeground(borderColor);
                gc.drawOval(0, 0, size - 1, size - 1);
            }

            ImageData pieData = pie.getImageData();
            pieData.alphaData = getPieChartMask(size);
            result = new Image(Display.getCurrent(), pieData);

        } finally {
            gc.dispose();
            pie.dispose();
        }

        if (cache != null) {
            cache.put(step, result);
        }

        return result;
    }

    // FIXME(AT): passing in isWindowsVistaAndUp flag is a bad idea, but mocking OSUtil is hard.
    // FIXME(AT): too many boolean flags, maybe we should use a single int flag variable instead.
    @Nonnull
    public static String getTrayIconName(boolean isOnline, boolean hasNotification,
            boolean hasProgress, boolean enableSyncStatus, RootStoreSyncStatus syncStatus,
            boolean isWindowsVistaAndUp)
    {
        StringBuilder sb = new StringBuilder("tray");

        if (!isOnline) sb.append("_off");
        if (hasNotification) sb.append("_n");

        if (hasProgress) sb.append("_sip");
        else if (enableSyncStatus) {
            if (syncStatus == IN_SYNC) sb.append("_is");
            else if (syncStatus == OUT_OF_SYNC) sb.append("_oos");
        }

        if (isWindowsVistaAndUp) sb.append("_win");

        return sb.toString();
    }

    private static final Lazy<Boolean> _isHDPI = new Lazy<>(() -> {
        // we only support HDPI for Retina Display on OS-X
        if (!OSUtil.isOSX()) return Boolean.FALSE;

        /**
         * Here be dragons. (AT)
         *
         * At the time of writing, there are no good methods to detect a high-DPI display:
         * - SWT:Device.getDPI() reports (72, 72) on both MacBook Pro and MacBook Pro with
         *   Retina display. It simply doesn't work.
         * - Google search revealed that the only other Java method is via AWT, and we've
         *   already decided to not bundle AWT with the custom JRE we ship with the OS-X client.
         * - Apple's stance on the subject is to encourage developers to write DPI-aware code
         *   instead of explicitly check for HDPI.
         * - The standard method to detect HDPI display in Objective-C is to check for
         *   [[NSScreen mainScreen] backingScaleFactor].
         * - [[NSScreen mainScreen] backingScaleFactor] is exposed through SWT internal classes,
         *   so the current implementation is to access the variable through SWT instead of
         *   using native driver code.
         *
         * Note, there are couple problems with the current implementation:
         * - It uses a SWT internal class which is only shipped with the cocoa version of SWT.
         *   That means:
         *   * we have to use reflection so the code compiles on all platforms, and reflection
         *     is not robust nor future-proof.
         *   * since the class we used is an internal class, it may not be shipped in future
         *     versions of SWT.
         * - It detects HDPI using [[NSScreen mainScreen] backingScaleFactor]. While this is
         *   the standard way to detect HDPI displays; it is not officially supported by Apple.
         *   Remember, Apple want developers to write DPI-aware code, not specifically check
         *   for HDPI displays.
         *
         * Nevertheless, I decided to implement this because of the following reasons:
         * - We have received a large number of requests from users. Users don't care if the
         *   detection is not robust or future-proof; they just want it to work.
         * - This code is written specifically to deal with certain hardware, and hardware
         *   and their API will most definitely change over time. So robust code is innately
         *   impossible because we can't control nor predict how hardware and their API will
         *   change.
         *   * there may be better API in the future
         *   * even if there's a supported API now, it will certainly become unsupported in the
         *     near future because hardware changes fast.
         *   * the concept of HDPI may change. It could become obsolete as soon as the next
         *     hardware vendor come up with a shinier word, or its definition may change.
         */
        try {
            /**
             * The following block of code is equivalent to:
             *
             * NSArray screens = NSScreen.screens();
             * long count = screens.count();
             *
             * for (long index = 0; index < count; index++) {
             *     id id = screens.objectAtIndex(index);
             *     NSScreen screen = new NSScreen(id);
             *
             *     if (screen.backingScaleFactor() == 2.0) {
             *         return true;
             *     }
             * }
             *
             * only with reflection.
             */
            Class<?> nsArray = Class.forName("org.eclipse.swt.internal.cocoa.NSArray");
            Method objectAtIndex = nsArray.getMethod("objectAtIndex", long.class);
            Class<?> nsScreen = Class.forName("org.eclipse.swt.internal.cocoa.NSScreen");
            Constructor<?> newNSScreen = nsScreen.getConstructor(Class.forName("org.eclipse.swt.internal.cocoa.id"));
            Method backingScaleFactor = nsScreen.getMethod("backingScaleFactor");

            Object screens = nsScreen.getMethod("screens").invoke(null);
            long count = (Long)nsArray.getMethod("count").invoke(screens);

            for (long index = 0; index < count; index++) {
                Object id = objectAtIndex.invoke(screens, index);
                Object screen = newNSScreen.newInstance(id);

                if ((Double)backingScaleFactor.invoke(screen) == 2.0) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            Loggers.getLogger(Images.class)
                    .error("failed to check whether the main display is HDPI or not.", e);
            // defaults to true because it's more likely that failure is caused by newer hardware, presumably hdpi,
            // than detection failures on old hardware.
            return true;
        }
    });

    public static Image getTrayIcon(String iconName)
    {
        String filename = iconName
                + (_isHDPI.get() ? "@2x" : "")
                + ".png";
        Image img = get(filename);
        return img != null ? img : get("tray.png");
    }

    /**
     * Returned a rotated version of the image
     * @param src source image
     * @param angle angle to rotate in degrees. A positive value indicates a clockwise rotation
     * while a negative value indicates a counter-clockwise rotation.
     * @return a rotated copy of the image. You must manually call dispose() on the resulting image
     * when you no longer need it.
     * Note: the rotated image will have a white background
     */
    public static Image rotate(Image src, float angle)
    {
        final int w = src.getBounds().width;
        final int h = src.getBounds().height;
        final double r = Math.toRadians(angle);

        // Computing the rotated size of the image
        int rW = (int) round(abs(w * cos(r) + h * cos(PI/2 - r)));
        int rH = (int) round(abs(w * sin(r) + h * sin(PI/2 - r)));

        Transform transform = new Transform(src.getDevice());
        transform.translate(rW/2, rH/2);
        transform.rotate(angle);
        transform.translate(-rW/2, -rH/2);

        Image dst = new Image(src.getDevice(), rW, rH);
        GC gc = new GC(dst);
        gc.setTransform(transform);
        gc.drawImage(src, (rW-w)/2, (rH-h)/2);
        gc.dispose();
        transform.dispose();
        return dst;
    }

    /**
     * Returns an antialiased mask for the pie chart
     * This function draws an antialiased white circle on black background, and returns a
     * byte array representing the intensity of each pixel (where 0 = black, 255 = white, etc)
     *
     * Fails silently and returns null if there is any error (so the pie chart won't have a
     * transparent background)
     */
    private static byte[] getPieChartMask(final int size)
    {
        Image mask = null;
        GC gc = null;

        try {
            // Draw the alpha mask (a white circle on a black background, with antialiasing)

            mask = new Image(Display.getCurrent(), size, size);
            gc = new GC(mask);
            gc.setAntialias(SWT.ON);
            gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));
            gc.fillRectangle(0, 0, size, size);
            gc.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WHITE));
            gc.fillOval(0, 0, size, size);

            // Now convert the image into a byte array by keeping only the blue channel

            ImageData img = mask.getImageData();
            PaletteData pal = img.palette;

            if (!pal.isDirect) {
                return null;
            }

            byte[] result = new byte[size*size];

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int pixel = img.getPixel(x,y) & pal.blueMask;
                    // if the amount to shift by is negative, it means we have to perform a right
                    // shift. Otherwise, it's a left shift
                    byte p = (pal.blueShift < 0)
                            ? (byte) (pixel >>> -pal.blueShift)
                            : (byte) (pixel << pal.blueShift);

                    result[y * size + x] = p;
                }
            }

            return result;

        } catch (Throwable t) {
            // Maybe we should log something here, but it's not very important and may generate
            // tons of logs, since this is called each time a pie chart is redrawn
            return null;

        } finally {
            if (gc != null) gc.dispose();
            if (mask != null) mask.dispose();
        }
    }
}
