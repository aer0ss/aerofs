package com.aerofs.lib;

import com.aerofs.lib.cfg.Cfg;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.LinkedList;
import java.util.Set;

/**
 * The class obfuscates file path strings to avoid exposing private information in log files.
 */
public class PathObfuscator
{
    /**
     * TODO (WW) move this method to Path.toString. This change would affect lots of code. In
     * particular, if some code incorrectly use Path.toString() as an input to other methods
     * (which is a common mistake), changing toString()'s behavior would break the system.
     * Therefore, I'd like to defer it until it becomes necessary, and check it in after thorough
     * tests.
     */
    public static String obfuscate(Path path)
    {
        return obfuscate(path.elements());
    }

    public static String obfuscate(String path)
    {
        LinkedList<String> names = Lists.newLinkedList();
        File f = new File(path);

        // Root directory has an empty filename
        while (f != null && !f.getName().isEmpty()) {
            names.addFirst(f.getName());
            f = f.getParentFile();
        }

        String[] elements = new String[names.size()];
        names.toArray(elements);

        return obfuscate(elements);
    }

    public static String obfuscate(Set<String> paths)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("[");
        String separator = "";
        for (String path : paths) {
            sb.append(separator);
            separator = ", ";
            sb.append(obfuscate(path));
        }
        sb.append("]");

        return sb.toString();
    }

    public static String obfuscate(String[] pathElements)
    {
        StringBuilder sb = new StringBuilder();

        if (pathElements.length == 0) {
            sb.append("/");
        }

        for (String name : pathElements) {
            sb.append('/');
            sb.append(Cfg.staging() ? name : Util.crc32(name));
        }

        return sb.toString();
    }
}
