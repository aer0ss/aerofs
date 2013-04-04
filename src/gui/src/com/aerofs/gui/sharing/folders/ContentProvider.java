package com.aerofs.gui.sharing.folders;

import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.ui.UI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

import java.util.List;
import java.util.Map;

public class ContentProvider implements ITreeContentProvider
{
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
        if (!(item instanceof Path)) return null;

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
            GetChildrenAttributesReply reply = UI.ritual().getChildrenAttributes(
                    Cfg.user().getString(), parent.toPB());
            for (int i = 0; i < reply.getChildrenNameCount(); i++) {
                PBObjectAttributes oa = reply.getChildrenAttributes(i);
                if (oa.getType() != Type.FILE && !oa.getExcluded()) {
                    ret.add(parent.append(reply.getChildrenName(i)));
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
