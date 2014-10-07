package com.aerofs.daemon.core.mock.physical;

import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.injectable.InjectableDriver.FIDAndType;
import com.aerofs.lib.injectable.InjectableFile.Factory;

import javax.annotation.Nullable;
import org.mockito.Mockito;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class mocks InjectableFiles and wires the factory accordingly. Usage:
 *
 *  MockPhysicalTree root =
 *          dir("root",
 *              file("f1"),
 *              dir("d2",
 *                  file("f2.1"),
 *                  dir("d2.2")
 *              )
 *          );
 *
 *     InjectableFile.Factory factFile = mock(InjectableFile.Factory.class);
 *     root.mock(fact);
 *     PhysicalObjectsPrinter.printRecursively(fact.create("root"));
 *
 * This will prints:
 *
 *     root/
 *     root/f1
 *     root/d2/
 *     root/d2/f2.1
 *     root/d2/d2.2/
 *
 * Note that directories with be printed with a trailing slash.
 */
public class MockPhysicalTree
{
    private final String _name;
    private final @Nullable MockPhysicalTree[] _children;

    /**
     * @param children the list of children, null for files
     */
    private MockPhysicalTree(String name, MockPhysicalTree[] children)
    {
        _name = name;
        _children = children;
    }

    /**
     * @param fact a mock Factory object
     * @param dr a mock Driver object. If non-null, a randomly generated FID will be assigned to the
     * OS object.
     */
    public void mock(Factory fact, @Nullable InjectableDriver dr) throws IOException, ExNotFound
    {
        // mock the factory's default behavior.
        doAnswer(invocation -> {
            InjectableFile f = Mockito.mock(InjectableFile.class);
            when(f.getAbsolutePath()).thenReturn((String)invocation.getArguments()[0]);
            when(f.exists()).thenReturn(false);
            return f;
        }).when(fact).create(anyString());
        doAnswer(invocation -> {
            InjectableFile f = Mockito.mock(InjectableFile.class);
            String parent = (String)invocation.getArguments()[0];
            String name = (String)invocation.getArguments()[1];
            when(f.getAbsolutePath()).thenReturn(Util.join(parent, name));
            when(f.exists()).thenReturn(false);
            return f;
        }).when(fact).create(anyString(), anyString());

        mockRecursively(fact, dr, null);
    }

    private InjectableFile mockRecursively(Factory fact, @Nullable InjectableDriver dr, @Nullable InjectableFile parent)
            throws IOException, ExNotFound
    {
        InjectableFile f = Mockito.mock(InjectableFile.class);
        when(f.getName()).thenReturn(_name);
        when(f.isDirectory()).thenReturn(_children != null);
        when(f.isFile()).thenReturn(_children == null);
        when(f.exists()).thenReturn(true);

        String path;
        String pathParent;
        InjectableFile fParent;
        if (parent == null) {
            fParent = f;
            pathParent = _name;
            path = _name;
        } else {
            fParent = parent;
            pathParent = parent.getPath();
            path = Util.join(pathParent, _name);
        }
        when(f.getParentFile()).thenReturn(fParent);
        when(f.getPath()).thenReturn(path);
        when(f.getParent()).thenReturn(pathParent);
        when(f.getAbsolutePath()).thenReturn(path);

        // mock factory methods
        doReturn(f).when(fact).create(path);
        doReturn(f).when(fact).create(pathParent, _name);
        doReturn(f).when(fact).create(parent, _name);

        if (_children != null) {
            InjectableFile[] children = new InjectableFile[_children.length];
            for (int i = 0; i < _children.length; i++) {
                children[i] = _children[i].mockRecursively(fact, dr, f);
            }
            mockChildren(f, children);
        }

        // mock driver methods
        if (dr != null) {
            FID fid = new FID(UniqueID.generate().getBytes());
            when(dr.getFIDAndTypeNullable(path)).thenReturn(new FIDAndType(fid, _children != null));
        }

        return f;
    }

    private static void mockChildren(InjectableFile parent, final InjectableFile[] children)
    {
        assert parent.isDirectory();
        // nameSet is for assertion only
        HashSet<String> nameSet = new HashSet<String>();
        for (int i = 0; i < children.length; i++) {
            assert children[i].getParentFile() == parent;
            assert nameSet.add(children[i].getName());
        }

        when(parent.listFiles()).thenReturn(children);

        String[] names = new String[children.length];
        for (int i = 0; i < names.length; i++) names[i] = children[i].getName();
        when(parent.list()).thenReturn(names);

        when(parent.listFiles(any(FilenameFilter.class))).thenAnswer(invocationOnMock -> {
            FilenameFilter filter = (FilenameFilter) invocationOnMock.getArguments()[0];
            ArrayList<InjectableFile> ret = new ArrayList<>(children.length);
            for (InjectableFile child : children) {
                if (filter.accept(null, child.getName())) ret.add(child);
            }
            return ret.toArray(new InjectableFile[0]);
        });
    }

    public static MockPhysicalTree dir(String name, MockPhysicalTree... children)
    {
        return new MockPhysicalTree(name, children);
    }

    public static MockPhysicalTree file(String name)
    {
        return new MockPhysicalTree(name, null);
    }
}
