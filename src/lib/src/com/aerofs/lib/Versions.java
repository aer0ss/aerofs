package com.aerofs.lib;

import java.util.regex.Pattern;

public class Versions
{
    public static String ZERO = "0.0.0";

    private static class Version
    {
        // Major number changes are rare and indicate major product milestones. A major number
        // change implies a minor number change (see below).
        final int _major;

        // Minor number changes indicate incompatible protocols between older releases and the new
        // one. Users must update the clients in order for the system to continue working.
        //
        // Minor number is reset to 1 if the major number changes.
        final int _minor;

        // Build number is automatically incremented by the build system.
        //
        // Build number is reset to 1 if either major or minor number changes.
        final int _build;

        private Version(int major, int minor, int build)
        {
            _major = major;
            _minor = minor;
            _build = build;
        }
    }

    private static Pattern PATTERN = Pattern.compile("\\.");

    /**
     * Convert a string version into a Version object. Treat the version as 0.0.0 if string parsing
     * fails.
     */
    private static Version parse(String str)
    {
        String[] tokens = PATTERN.split(str);
        try {
            if (tokens.length != 3) throw new NumberFormatException();
            return new Version(
                    Integer.parseInt(tokens[0]),
                    Integer.parseInt(tokens[1]),
                    Integer.parseInt(tokens[2]));
        } catch (NumberFormatException e) {
            return new Version(0, 0, 0);
        }
    }

    public static enum CompareResult {
        MAJOR_CHANGE,   // build number changes. Users are advised to update ASAP
        MINOR_CHANGE,   // minor number changes. Users are advised to update ASAP
        BUILD_CHANGE,   // build number changes. Users can update at their convenience
        NO_CHANGE
    }

    public static CompareResult compare(String strV1, String strV2)
    {
        Version v1 = parse(strV1);
        Version v2 = parse(strV2);

        if (v2._major > v1._major) return CompareResult.MAJOR_CHANGE;
        if (v2._major < v1._major) return CompareResult.NO_CHANGE;
        if (v2._minor > v1._minor) return CompareResult.MINOR_CHANGE;
        if (v2._minor < v1._minor) return CompareResult.NO_CHANGE;
        if (v2._build > v1._build) return CompareResult.BUILD_CHANGE;
        return CompareResult.NO_CHANGE;
    }
}
