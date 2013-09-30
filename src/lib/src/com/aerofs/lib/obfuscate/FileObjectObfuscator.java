/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.obfuscate;

import com.aerofs.lib.LibParam.EnterpriseConfig;
import com.aerofs.lib.Util;
import com.google.common.collect.Lists;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Obfuscates file paths taken from a File object.
 */
class FileObjectObfuscator implements IObfuscator<File>
{
    @Override
    public String obfuscate(File f)
    {
        // disable obfuscation in Enterprise deployment
        if (EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT) return f.getPath();

        LinkedList<String> names = Lists.newLinkedList();

        // Root directory has an empty filename
        while (f != null && !f.getName().isEmpty()) {
            names.addFirst(f.getName());
            f = f.getParentFile();
        }

        return obfuscatePathElements(names);
    }

    private static String obfuscatePathElements(List<String> elements)
    {
        if (elements.isEmpty()) {
            return "/";
        }

        StringBuilder sb = new StringBuilder();
        for (String name : elements) {
            sb.append('/');
            sb.append(Util.crc32(name));
        }
        return sb.toString();
    }

    @Override
    public String plainText(File f)
    {
        return f.getAbsolutePath();
    }
}
