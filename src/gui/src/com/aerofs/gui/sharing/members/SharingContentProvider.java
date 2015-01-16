/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.gui.sharing.members;

import com.aerofs.gui.sharing.SharedFolderMember;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;

public class SharingContentProvider implements ITreeContentProvider
{
    private @Nonnull List<SharedFolderMember> _input = emptyList();

    @Override
    public Object[] getChildren(Object parent)
    {
        return _input.stream()
                .filter(member -> Objects.equals(member.getParent(), parent))
                .toArray();
    }

    @Override
    public Object getParent(Object element)
    {
        return element instanceof SharedFolderMember ? ((SharedFolderMember)element).getParent()
                : null;
    }

    @Override
    public boolean hasChildren(Object element)
    {
        boolean ans = _input.stream()
                .filter(member -> Objects.equals(member.getParent(), element))
                .findAny()
                .isPresent();

        return ans;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object[] getElements(Object input)
    {
        return input == _input ? _input.stream()
                .filter(member -> member.getParent() == null)
                .toArray()
                : new String[] { input.toString() };
    }

    @Override
    public void dispose()
    {
        _input = emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
    {
        if (newInput instanceof List<?>) {
            _input = (List<SharedFolderMember>)newInput;
        } else {
            _input = emptyList();
        }
    }
}
