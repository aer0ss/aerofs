package com.aerofs.gui;

import com.aerofs.base.Loggers;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
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

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.round;
import static java.lang.Math.sin;

// learn about resource management: http://help.eclipse.org/ganymede/index.jsp?topic=/org.eclipse.platform.doc.isv/reference/api/index.html

public class Images {

    public static final String ICON_LOGO16 = "logo16.png";
    public static final String ICON_LOGO32 = "logo32.png";
    public static final String ICON_LOGO64 = "logo64.png";
    public static final String ICON_LOGO32_ERROR = "logo32err.png";

    public static final String ICON_FILE = "file.gif";
    public static final String ICON_FOLDER = "folder.png";
    public static final String ICON_TICK = "tick.png";
    public static final String ICON_USER = "user.png";
    public static final String ICON_WARNING = "warning.png";
    public static final String ICON_ERROR = "exclamation.png";
    public static final String ICON_DOUBLE_QUESTION = "doubleQuestion.png";
    public static final String ICON_METADATA = "bulletBlue.png";
    public static final String ICON_ARROW_UP = "arrowUp.png";
    public static final String ICON_ARROW_UP2 = "arrowUp2.png";
    public static final String ICON_ARROW_DOWN = "arrowDown.png";
    public static final String ICON_ARROW_DOWN2 = "arrowDown2.png";
    public static final String ICON_HOME = "home.png";
    public static final String ICON_NIL = "nil.png";
    public static final String ICON_SPIN = "spin.gif";
    public static final String ICON_BRICK = "brick.png";
    public static final String ICON_LINK = "link.png";
    public static final String SS_IN_SYNC = "ss_in_sync.png";
    public static final String SS_IN_PROGRESS = "ss_in_progress.png";
    public static final String SS_OFFLINE_NOSYNC = "ss_offline_nosync.png";
    // N.B. visually, signal1, 2, 3, has 2, 3, 4 bars respectively.
    public static final String ICON_SIGNAL1 = "signal1.png";
    public static final String ICON_SIGNAL2 = "signal2.png";
    public static final String ICON_SIGNAL3 = "signal3.png";

    public static final String IMG_SETUP = "setup.png";

    private static final Map<String, Image> s_imgs = new HashMap<String, Image>();

    // delay (ms) between each spinner frame
    private static final int SPINNER_DELAY = 100;
    private static Image[] s_spinner_frames;

    private static Cursor s_cursors[];

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

    public static Cursor getCursor(int key)
    {
        if (s_cursors == null) {
            s_cursors = new Cursor[1];
            s_cursors[0] = new Cursor(Display.getCurrent(), SWT.CURSOR_WAIT);
        }
        return s_cursors[key];
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

    public static Image getFolderIcon()
    {
        return get(ICON_FOLDER);
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
            } catch (NullPointerException e) {}     // Work around a SWT bug where a NPE may be
                                                    // thrown for some unknown reason
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

    /**
     * @return icon name
     */
    public static String getTrayIconName(boolean isServerOnline, boolean notification)
    {
        StringBuilder sb = new StringBuilder("tray");
        if (notification) sb.append("n");
        sb.append(0);
        if (!isServerOnline) sb.append("grey");
        return sb.toString();
    }

    public static Image getTrayIcon(String iconName)
    {
        Image img = get(iconName + ".png");
        return img != null ? img : get("tray0.png");
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
