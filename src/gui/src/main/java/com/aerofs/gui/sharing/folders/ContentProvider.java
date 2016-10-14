package com.aerofs.gui.sharing.folders;

import com.aerofs.lib.Path;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.ui.UIGlobals;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class ContentProvider implements ITreeContentProvider
{
    /* We store the set of SharedFolders under the root here so that it can be accessed by the Label
       provider to decide which icon to display. This is done so that a deamon call doesn't have to
       be made from the Label provider to determine if a folder is shared or not. That would add
       more overhead.
    */
    private Set<Path> sharedFolderPathsSet;

    // We want the constructor to be exposed only within the package. Hence, no scope specifier.
    ContentProvider()
    {
        sharedFolderPathsSet = new HashSet<Path>();
    }

    // This function again must only be exposed to files within the package.
    boolean isPathForSharedFolder(Path path)
    {
        return sharedFolderPathsSet.contains(path);
    }

    @Override
    public Object[] getElements(Object arg0)
    {
        return (Object[]) arg0;
    }

    @Override
    public void inputChanged(Viewer arg0, Object arg1, Object arg2)
    {
    }

    @Override
    public Object[] getChildren(Object item)
    {
        if (!(item instanceof Path)) {
            return null;
        }

        try {
            return getSubfolders((Path) item).toArray();
        } catch (Exception e) {
            return new Object[] { e };
        }
    }

    Map<Path, List<Path>> _cache = Maps.newHashMap();

    List<Path> getSubfolders(Path parent) throws Exception
    {
        List<Path> ret = _cache.get(parent);
        if (ret == null) {
            ret = Lists.newArrayList();
            GetChildrenAttributesReply reply =
                    UIGlobals.ritual().getChildrenAttributes(parent.toPB());
            for (int i = 0; i < reply.getChildrenNameCount(); i++) {
                PBObjectAttributes oa = reply.getChildrenAttributes(i);

                if (oa.getType() != Type.FILE && !oa.getExcluded()) {
                    Path path = parent.append(reply.getChildrenName(i));
                    ret.add(path);

                    if(oa.getType() == Type.SHARED_FOLDER) {
                        sharedFolderPathsSet.add(path);
                    }
                }
            }
            _cache.put(parent, ret);
        }

        return ret;
    }

    @Override
    public Object getParent(Object item)
    {
        if (item instanceof Path) {
            Path path = (Path) item;
            return path.isEmpty() ? null : path.removeLast();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasChildren(Object item)
    {
        if (item instanceof Path) {
            try {
                return !getSubfolders((Path) item).isEmpty();
            } catch (Exception e) {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public void dispose()
    {
        // noop
    }
}
