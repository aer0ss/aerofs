package com.aerofs.gui.exclusion;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.ui.UIGlobals;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;

import java.util.Collections;
import java.util.HashSet;

public class CompExclusionList extends Composite
{
    CheckboxTreeViewer _v;
    Model _m;

    public CompExclusionList(Composite parent)
    {
        super(parent, SWT.NONE);
        setLayout(new FillLayout(SWT.HORIZONTAL));

        _v = new CheckboxTreeViewer(this, SWT.BORDER);
        _v.setLabelProvider(new LabelProvider());
        _v.setContentProvider(new ContentProvider());
        _v.setSorter(new ViewerSorter());

        try {
            _m = initModel();
            _v.setInput(_m._all.toArray());

            for (Path path : _m._all) _v.setChecked(path, !_m._excluded.contains(path));

        } catch (Exception e) {
            _v.setInput(new Object[] { e });
        }
    }

    private static class Model
    {
        HashSet<Path> _excluded;
        HashSet<Path> _all;
    }

    Model initModel() throws Exception
    {
        Model m = new Model();

        m._excluded = new HashSet<Path>();
        for (PBPath path : UIGlobals.ritual().listExcludedFolders().getPathList()) {
            Util.verify(m._excluded.add(Path.fromPB(path)));
        }

        // TODO: multiroot support
        Path root = Path.root(Cfg.rootSID());
        m._all = new HashSet<Path>();
        GetChildrenAttributesReply reply = UIGlobals.ritual().getChildrenAttributes(root.toPB());
        for (int i = 0; i < reply.getChildrenNameCount(); i++) {
            if (reply.getChildrenAttributes(i).getType() != Type.FILE) {
                m._all.add(root.append(reply.getChildrenName(i)));
            }
        }

        return m;
    }

    Operations getOperations()
    {
        Operations ops = new Operations();
        if (_m == null) {
            ops._exclude = Collections.emptySet();
            ops._include = Collections.emptySet();
            return ops;
        }

        HashSet<Path> checked = new HashSet<Path>();
        for (Object obj : _v.getCheckedElements()) {
            checked.add((Path) obj);
        }

        ops._include = new HashSet<Path>(_m._excluded);
        ops._include.retainAll(checked);

        ops._exclude = new HashSet<Path>(_m._all);
        ops._exclude.removeAll(_m._excluded);
        ops._exclude.removeAll(checked);

        return ops;
    }
}
