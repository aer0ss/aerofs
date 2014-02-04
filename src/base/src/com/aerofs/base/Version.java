package com.aerofs.base;

import com.aerofs.base.ex.ExFormatError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper to manipulate API versions
 */
@NoObfuscation
public class Version implements Comparable<Version>
{
    public final int major;
    public final int minor;

    public Version(int major, int minor)
    {
        this.major = major;
        this.minor = minor;
    }

    public Version nextMinor()
    {
        return new Version(major, minor + 1);
    }

    public Version nextMajor()
    {
        return new Version(major + 1, 0);
    }

    public static @Nullable Version fromRequestPath(String path)
    {
        if (!path.startsWith("/v")) return null;
        int idx = path.indexOf('/', 2);
        if (idx == -1) return null;
        return Version.fromStringNullable(path.substring(2, idx));
    }

    public static @Nonnull Version fromString(String s) throws ExFormatError
    {
        Version v = fromStringNullable(s);
        if (v == null) throw new ExFormatError(s);
        return v;
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile("([0-9]+)\\.([0-9]+)");
    public static @Nullable Version fromStringNullable(String s)
    {
        Matcher m = VERSION_PATTERN.matcher(s);
        return m.matches()? from(m) : null;
    }

    private static @Nullable Version from(Matcher m)
    {
        try {
            return new Version(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString()
    {
        return Integer.toString(major) + '.' + Integer.toString(minor);
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && o instanceof Version
                                     && major == ((Version)o).major
                                     && minor == ((Version)o).minor);
    }

    @Override
    public int hashCode()
    {
        return major << 16 | minor;
    }

    @Override
    public int compareTo(Version o)
    {
        int c = BaseUtil.compare(major, o.major);
        return c != 0 ? c : BaseUtil.compare(minor, o.minor);
    }
}
