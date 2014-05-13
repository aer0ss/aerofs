/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.google.common.base.Joiner;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class represents object paths. The path it presents is always relative to the root anchor.
 */
public class Path implements Comparable<Path>
{
    private final SID _sid;
    private final String[] _elems;

    /**
     * N.B we do NOT save a local copy of the {@code elems} array. DO NOT modify the array!
     */
    public Path(SID sid, String... elems)
    {
        assert sid != null;
        _sid = sid;
        _elems = elems;
        assertNoEmptyElement();
    }

    /**
     * avoid using this method when possible. it's expensive
     */
    public Path(SID sid, List<String> elems)
    {
        assert sid != null;
        _sid = sid;
        _elems = new String[elems.size()];
        elems.toArray(_elems);
        assertNoEmptyElement();
    }

    public static Path root(SID sid)
    {
        return new Path(sid);
    }

    public static Path fromPB(PBPath pb)
    {
        String[] elems = new String[pb.getElemCount()];
        for (int i = 0; i < elems.length; i++) elems[i] = pb.getElem(i);
        return new Path(new SID(pb.getSid()), elems);
    }

    private void assertNoEmptyElement()
    {
        for (String elem : _elems) assert !elem.isEmpty();
    }

    public SID sid()
    {
        return _sid;
    }

    public String[] elements()
    {
        return _elems;
    }

    @Override
    public int compareTo(Path otherPath)
    {
        int diff = _sid.compareTo(otherPath._sid);
        return diff == 0 ? BaseUtil.compare(_elems, otherPath._elems) : diff;
    }

    public Path append(String elem)
    {
        String[] elemsNew = Arrays.copyOf(_elems, _elems.length + 1);
        elemsNew[_elems.length] = elem;
        return new Path(_sid, elemsNew);
    }

    public Path removeLast()
    {
        assert _elems.length > 0;
        return new Path(_sid, Arrays.copyOf(_elems, _elems.length - 1));
    }

    @Override
    public boolean equals(Object o)
    {
        // Don't remove the "o instanceof Path" in the test below.
        // SWT calls Path.equals() somewhere when you list children of a folder under your
        // AeroFS folder in the "Share New Folder..." dialog.
        // This caused a ClassCastException deep in the bowels of SWT/jface.
        return this == o || (o != null && o instanceof Path &&
                    _sid.equals(((Path)o)._sid) && Arrays.equals(_elems, ((Path) o)._elems));
    }

    public boolean isEmpty()
    {
        return _elems.length == 0;
    }

    /**
     * @return the last element of the path (i.e. file name)
     */
    public String last()
    {
        assert !isEmpty();
        return _elems[_elems.length - 1];
    }

    public PBPath toPB()
    {
        PBPath.Builder bd = PBPath.newBuilder();
        bd.setSid(_sid.toPB());
        for (String elem : _elems) bd.addElem(elem);
        return bd.build();
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(_elems);
    }

    /**
     * N.B. the returned string is informative and can't be used as input to other methods. Use
     * toAbsoluteString() or toStringFormal() instead.
     */
    @Override
    public String toString()
    {
        return _sid + FORMAL_SEP + toStringRelative();
    }

    private final static char FORMAL_STORE_SEP = ':';

    // We use a formal separator which may be different from the native file path separator. This
    // is to ensure that we keep consistent paths amongst Operating Systems when users interact
    // with aerofs-sh.
    private final static String FORMAL_SEP = "/";

    private final static Joiner FORMAL_SEP_JOINER = Joiner.on(FORMAL_SEP);

    private final static Pattern FILE_SEP_PATTERN =
            Pattern.compile(File.separatorChar == '\\' ? "\\\\" : File.separator);

    private final static Pattern FORMAL_SEP_PATTERN = File.separator.equals(FORMAL_SEP) ?
            FILE_SEP_PATTERN : Pattern.compile(FORMAL_SEP);


    public String toStringRelative()
    {
        return FORMAL_SEP_JOINER.join(_elems);
    }

    /**
     * @return machine-readable string encoding of the path
     */
    public String toStringFormal()
    {
        assertNoEmptyElement();
        return _sid.toStringFormal() + FORMAL_STORE_SEP + toStringRelative();
    }

    private static final int SID_LENGTH = UniqueID.LENGTH * 2;
    public static Path fromStringFormal(String strFormal) throws ExFormatError
    {
        if (strFormal.length() < SID_LENGTH + 1
                || strFormal.charAt(SID_LENGTH) != FORMAL_STORE_SEP) {
            throw new ExFormatError();
        }
        SID sid = new SID(BaseUtil.hexDecode(strFormal, 0, SID_LENGTH));
        return new Path(sid, splitPath(strFormal.substring(SID_LENGTH + 1),
                FORMAL_SEP_PATTERN));
    }

    public static Path fromString(SID sid, String strFormal)
    {
        return new Path(sid, splitPath(strFormal, FORMAL_SEP_PATTERN));
    }

    /**
     * @return the absolute path string given the root path to which the Path object is relative.
     */
    public String toAbsoluteString(String absRoot)
    {
        StringBuilder sb = new StringBuilder(absRoot);
        for (String elem : _elems) {
            sb.append(File.separatorChar);
            sb.append(elem);
        }
        return sb.toString();
    }

    /**
     * Convert an absolute path string to a Path object relative to {@code absRoot}.
     * @pre isUnder(absRoot, absPath) returns true
     */
    public static Path fromAbsoluteString(SID sid, String absRoot, String absPath)
    {
        return new Path(sid, splitPath(relativePath(absRoot, absPath), FILE_SEP_PATTERN));
    }

    public static String relativePath(String absRoot, String absPath)
    {
        assert isUnder(absRoot, absPath) : absRoot + " " + absPath;
        // strip root from the path. +1 to remove the separator
        String relative = absPath.substring(Math.min(absRoot.length() + 1, absPath.length()));
        return Util.removeTailingSeparator(relative);
    }

    /**
     * The first and last char must not be identical to the pattern. The string must not contain
     * two consecutive patterns.
     */
    private static String[] splitPath(String path, Pattern pattern)
    {
        if (path.isEmpty()) return new String[0];

        String[] tokens = pattern.split(path);
        for (String token : tokens) assert !token.isEmpty();
        return tokens;
    }

    /**
     * Return true if "this" is a child folder the given path. Case sensitive comparison is used.
     */
    public boolean isStrictlyUnder(Path path)
    {
        return _elems.length != path._elems.length && isUnderOrEqual(path);
    }

    public boolean isUnderOrEqual(Path path)
    {
        if (!_sid.equals(path._sid)) return false;

        String[] mine= elements();
        String[] his = path.elements();
        if (his.length > mine.length) return false;

        for (int i = 0; i < his.length; i++) {
            if (!mine[i].equals(his[i])) return false;
        }

        return true;
    }

    /**
     * @return true if {@code absPath} is a sub-directory under {@code absRoot}
     * @param absRoot an absolute path
     * @param absPath an absolute path
     */
    public static boolean isUnder(String absRoot, String absPath)
    {
        absRoot = Util.removeTailingSeparator(absRoot);
        absPath = Util.removeTailingSeparator(absPath);
        if (OSUtil.isWindows()) {
            absRoot = lowercaseDriveLetter(absRoot);
            absPath = lowercaseDriveLetter(absPath);
        }
        return absPath.startsWith(absRoot) && (absPath.length() == absRoot.length() ||
            absPath.charAt(absRoot.length()) == File.separatorChar);
    }

    /**
     * On Windows, ensure the drive letter is lowercase.
     * We need this because Java always store the drive letter as lowercase,
     * but native OS functions (such as in the Shell Extension) may return it as uppercase
     */
    private static String lowercaseDriveLetter(String path)
    {
        assert (OSUtil.isWindows());
        if (path.length() >= 2 && path.charAt(1) == ':'
                && path.charAt(0) >= 'A' && path.charAt(0) <= 'Z') {
            return Character.toLowerCase(path.charAt(0)) + path.substring(1);
        } else {
            return path;
        }
    }
}
