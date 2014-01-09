/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.syncstatus;

import com.aerofs.gui.syncstatus.SyncStatusModel.SyncStatusEntry;
import com.google.common.collect.Lists;
import org.eclipse.jface.viewers.ArrayContentProvider;

import java.util.Arrays;
import java.util.List;

public class SyncStatusContentProvider extends ArrayContentProvider
{
    public static final String ID_DEVICES       = "My Devices";
    public static final String ID_USERS         = "Other Users";
    public static final String ID_NO_DEVICES    = "Not synced to other devices.";
    public static final String ID_NO_USERS      = "Not synced to other users.";

    @Override
    public Object[] getElements(Object input)
    {
        SyncStatusEntry[] entries = getEntries(super.getElements(input));
        List<Object> elements = Lists.newArrayList();

        elements.addAll(Arrays.asList(entries));
        elements.add(ID_DEVICES);
        elements.add(ID_USERS);

        if (!containsAnyDeviceEntry(entries)) elements.add(ID_NO_DEVICES);
        if (!containsAnyUserEntry(entries)) elements.add(ID_NO_USERS);

        return elements.toArray();
    }

    private SyncStatusEntry[] getEntries(Object[] elements)
    {
        SyncStatusEntry[] entries = new SyncStatusEntry[elements.length];

        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof SyncStatusEntry) entries[i] = (SyncStatusEntry) elements[i];
            else throw new IllegalArgumentException("Invalid input.");
        }

        return entries;
    }

    private boolean containsAnyDeviceEntry(SyncStatusEntry[] entries)
    {
        for (SyncStatusEntry entry : entries) if (entry.isLocalUser()) return true;
        return false;
    }

    private boolean containsAnyUserEntry(SyncStatusEntry[] entries)
    {
        for (SyncStatusEntry entry : entries) if (!entry.isLocalUser()) return true;
        return false;
    }

    public static boolean isSectionHeader(Object element)
    {
        return element == ID_DEVICES || element == ID_USERS;
    }

    public static boolean isEmptyPlaceHolder(Object element)
    {
        return element == ID_NO_DEVICES || element == ID_NO_USERS;
    }

    public static void throwOnInvalidElement(Object element)
    {
        if (!isValidElement(element)) throw new IllegalArgumentException("Invalid element.");
    }

    private static boolean isValidElement(Object element)
    {
        return isSectionHeader(element)
                || isEmptyPlaceHolder(element)
                || element instanceof SyncStatusEntry;
    }
}
