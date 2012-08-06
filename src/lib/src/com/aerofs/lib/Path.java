package com.aerofs.lib;

import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * This class represents object paths. The path it presents is always relative to the root anchor.
 */
public class Path implements Comparable<Path>
{
    private final String[] _elems;
    private List<String> _list;
    private PBPath _pb;

    /**
     * N.B we do NOT save a local copy of the array. DO NOT modify the array!
     * @param elems
     */
    public Path(String... elems)
    {
        _elems = elems;
        assertNoEmptyElement();
    }

    /**
     * avoid using this method when possible. it's expensive
     */
    public Path(List<String> elems)
    {
        _elems = new String[elems.size()];
        elems.toArray(_elems);
        assertNoEmptyElement();
    }

    public Path(PBPath pb)
    {
        _elems = new String[pb.getElemCount()];
        for (int i = 0; i < _elems.length; i++) _elems[i] = pb.getElem(i);
        _pb = pb;
        assertNoEmptyElement();
    }

    private void assertNoEmptyElement()
    {
        for (String elem : _elems) assert !elem.isEmpty();
    }

    public String[] elements()
    {
        return _elems;
    }

    public List<String> asList()
    {
        if (_list == null) _list = Arrays.asList(_elems);
        return _list;
    }

    @Override
    public int compareTo(Path otherPath)
    {
        return Util.compare(_elems, otherPath._elems);
    }

    public Path append(String elem)
    {
        String[] elemsNew = Arrays.copyOf(_elems, _elems.length + 1);
        elemsNew[_elems.length] = elem;
        return new Path(elemsNew);
    }

    public Path removeLast()
    {
        assert _elems.length > 0;
        return new Path(Arrays.copyOf(_elems, _elems.length - 1));
    }

    @Override
    public boolean equals(Object o)
    {
        return this == o || (o != null && o instanceof Path && Arrays.equals(_elems, ((Path) o)._elems));
    }

    public boolean equalsIgnoreCase(Path path)
    {
        if (this == path) return true;
        if (path == null) return false;

        String [] s1 = this.elements();
        String [] s2 = path.elements();
        if (s1.length != s2.length) return false;

        for (int i = 0; i < this.elements().length; i++) {
            if (!s1[i].equalsIgnoreCase(s2[i]))
                return false;
        }
        return true;
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
        if (_pb == null) {
            PBPath.Builder bd = PBPath.newBuilder();
            for (String elem : _elems) bd.addElem(elem);
            _pb = bd.build();
        }
        return _pb;
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
        return FORMAL_SEP + toStringFormal();
    }

    private final static String FORMAL_SEP = "/";

    private static final Pattern FILE_SEP_PATTERN =
            Pattern.compile(File.separatorChar == '\\' ? "\\\\" : File.separator);

    private final static Pattern FORMAL_SEP_PATTERN = File.separator.equals(FORMAL_SEP) ?
            FILE_SEP_PATTERN : Pattern.compile(FORMAL_SEP);

    /**
     * Unlike toString(), the returned string by this method is machine ready and can be used by
     * other methods like fromString().
     */
    public String toStringFormal()
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String elem : _elems) {
            if (first) first = false;
            else sb.append(FORMAL_SEP);
            assert !elem.isEmpty();
            sb.append(elem);
        }
        return sb.toString();
    }

    /**
     * Convert a string returned from Path.toStringFormal() to a Path object
     */
    public static Path fromString(String strFormal)
    {
        return new Path(splitPath(strFormal, FORMAL_SEP_PATTERN));
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
    public static Path fromAbsoluteString(String absRoot, String absPath)
    {
        assert isUnder(absRoot, absPath) : absRoot + " " + absPath;
        // strip root from the path. +1 to remove the separator
        String relative = absPath.substring(Math.min(absRoot.length() + 1, absPath.length()));
        relative = Util.removeTailingSeparator(relative);
        return new Path(splitPath(relative, FILE_SEP_PATTERN));
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
    public boolean isUnder(Path path)
    {
        String[] mine= elements();
        String[] his = path.elements();
        if (his.length >= mine.length) return false;

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
