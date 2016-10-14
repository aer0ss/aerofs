/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.tray;

import com.aerofs.gui.GUI;
import com.aerofs.gui.Images;
import com.aerofs.gui.tray.TrayIcon.TrayPosition;
import com.aerofs.base.C;
import com.google.common.collect.Lists;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.Resource;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.util.List;

/**
 * Displays a big bouncing blue arrow pointing to the tray icon, so that users can find where it is.
 */
public class BigArrow
{
    // Some configurable constants
    private final int ANIMATION_FPS = 60;    // frames per second
    private final int BOUNCE_AMPLITUDE = 80; // how many pixels the arrow will bounce off
    private final double BOUNCES_PER_SECOND = 1;

    private int _animationFrame = 0;
    private final Point _finalPosition;
    private final Shell _shell;

    /*
       Defines a standard, upward-pointing arrow
       The arrow consists of a 150 x 154 triangle followed by a 75 x 116 rectangle
       Total height: 270 pixels
       (Points are defined clockwise from the tip)
    */
    final int[] _arrow = new int[] {
            0, 0,       // tip
            75, 154,    // right corner of the triangle
            37, 154,
            37, 270,    // bottom right corner
            -37, 270,   // bottom left corner
            -37, 154,
            -75, 154    // left corner of the triangle
    };
    private final Runnable _animation;

    /**
     * Displays the big arrow pointing at the specified location.
     * Clicking on the arrow closes it.
     * @param pos position and orientation of the arrow
     */
    public BigArrow(final TrayPosition pos)
    {
        final List<Resource> toDispose = Lists.newArrayList();

        final Display display = GUI.get().disp();
        _shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);

        int rotCount;
        switch (pos.orientation) {
        case Top:    rotCount = 0; break;
        case Right:  rotCount = 1; break;
        case Bottom: rotCount = 2; break;
        case Left:   rotCount = 3; break;
        default: throw new IllegalArgumentException();
        }

        Image arrowBg = Images.rotate(Images.get("bigarrow.png"), 90*rotCount);
        toDispose.add(arrowBg);
        _shell.setBackground(display.getSystemColor(SWT.COLOR_BLUE));
        _shell.setBackgroundImage(arrowBg);

        // Create an arrow-shaped region
        final Region region = new Region();
        toDispose.add(region);
        region.add(rotate90(_arrow, rotCount));

        // Ensure the region starts at (0,0)
        Rectangle bounds = region.getBounds();
        Point tipoffset = new Point(-bounds.x, -bounds.y);
        region.translate(tipoffset.x, tipoffset.y);

        _shell.setRegion(region);
        _shell.setSize(bounds.width, bounds.height);
        _finalPosition = new Point(pos.x - tipoffset.x, pos.y - tipoffset.y);

        _animation = new Runnable()
        {
            @Override
            public void run()
            {
                if (_shell.isDisposed()) return;

                int x = _finalPosition.x;
                int y = _finalPosition.y;

                double time = (double) _animationFrame / ANIMATION_FPS;
                double amplitude = (double) BOUNCE_AMPLITUDE * getEasing(time);

                switch (pos.orientation) {
                case Top:    y += amplitude; break;
                case Right:  x -= amplitude; break;
                case Bottom: y -= amplitude; break;
                case Left:   x += amplitude; break;
                default: throw new IllegalArgumentException();
                }

                _shell.setLocation(x, y);

                _animationFrame++;
                display.timerExec((int) (C.SEC/ANIMATION_FPS), this);
            }
        };

        _shell.addMouseListener(_closeOnClick);

        _shell.addDisposeListener(new DisposeListener()
        {
            @Override
            public void widgetDisposed(DisposeEvent disposeEvent)
            {
                for (Resource resource : toDispose) {
                    resource.dispose();
                }
            }
        });
    }

    public void open()
    {
        _animation.run();
        _shell.open();
    }

    public void close()
    {
        if (_shell.isDisposed()) return;
        _shell.close();
    }

    private final MouseAdapter _closeOnClick = new MouseAdapter()
    {
        @Override
        public void mouseUp(MouseEvent mouseEvent)
        {
            _shell.close();
        }
    };

    /**
     * Given a time t in seconds, returns a number between 0 and 1 that represents the position
     * of the arrow in the animation cycle
     * In other words: this is the function that customises how the arrow will be animated
     */
    private double getEasing(double t)
    {
        return Math.abs(Math.cos(BOUNCES_PER_SECOND * Math.PI * t));
    }

    /**
     * Rotates a points array by 90 degrees clockwise count times around the (0,0) axis
     * @param src an array of (x,y) coordinates
     * @param count number of times to rotate 90 degrees clockwise (0-3)
     */
    private int[] rotate90(int[] src, int count)
    {
        /*
        Algorithm:

        90 degrees           180 degrees           270 degrees
        dst(x) = -src(y)     dst(x) = -src(x)      dst(x) = src(y)
        dst(y) = src(x)      dst(y) = -src(y)      dst(y) = -src(x)

        So for each dst(x) and dst(y) we have two parameters: whether to pick src(x) or src(y),
        and whether to change its sign.
        */

        int a,b,c,d;
        switch (count) {
        case 0: return src; // no-op
        case 1: a=-1; b=1; c= 1; d=0; break;
        case 2: a=-1; b=0; c=-1; d=1; break;
        case 3: a= 1; b=1; c=-1; d=0; break;
        default: throw new IllegalArgumentException();
        }

        int[] dst = new int[src.length];
        for (int i = 0; i<src.length; i+=2) {
            dst[i] = a*src[i+b];
            dst[i+1] = c*src[i+d];
        }
        return dst;
    }
}
