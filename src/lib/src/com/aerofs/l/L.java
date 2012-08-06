package com.aerofs.l;

import com.aerofs.lib.C;
import com.aerofs.lib.Util;

import java.io.File;
import java.util.Scanner;

/**
 * L stands for "Labeling". Labeling is determined by either putting the
 * labeling file (named $AppRoot/Path.LABELING, used mostly for staging)
 * or removing unneeded labeling classes (used mostly for prod)
 *
 * classes that implement ILabeling are kept from obfuscation by ProGuard.
 * therefore, they should use obfuscated names themselves to avoid the class
 * names as well as the constant strings in the static block below from being
 * exposed.
 */
public class L
{
    public static enum LabelingType
    {
        AEROFS,
        COMCAST
    }

    private static ILabeling s_l;

    static {
        ILabeling l = getLabelingFromFile();
        if (l == null) l = getLabeling("AA");
        if (l == null) l = getLabeling("CC");
        if (l == null) Util.fatal("no labeling");
        set(l);
    }

    /**
     * @return null if it can't be determined
     */
    private static ILabeling getLabelingFromFile()
    {
        // don't use AppRoot.abs() here to avoid cyclic dependencies
        // among static constructors of these classes
        File f = new File(C.LABELING);
        if (!f.exists()) return null;

        try {
            Scanner s = new Scanner(f);
            try {
                return getLabeling(s.nextLine());
            } finally {
                s.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @return null if the class is not found
     */
    private static ILabeling getLabeling(String name)
    {
        try {
            return (ILabeling) Class.forName("com.aerofs.l." + name)
                    .newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    public static void set(ILabeling l)
    {
        s_l = l;
    }

    public static ILabeling get()
    {
        return s_l;
    }
}
