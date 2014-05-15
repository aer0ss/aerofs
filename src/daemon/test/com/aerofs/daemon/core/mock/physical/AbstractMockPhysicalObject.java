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
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * See MockPhysicalDir for usage
 */
public abstract class AbstractMockPhysicalObject
{
    private final String _name;
    private final @Nullable AbstractMockPhysicalObject[] _children;

    /**
     * @param children the list of children, null for files
     */
    protected AbstractMockPhysicalObject(String name, AbstractMockPhysicalObject[] children)
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
        when(fact.create(any(String.class))).then(Mockito.RETURNS_MOCKS);
        when(fact.create(any(String.class), any(String.class))).then(Mockito.RETURNS_MOCKS);

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

        // mock factory methods
        when(fact.create(path)).thenReturn(f);
        when(fact.create(pathParent, _name)).thenReturn(f);

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

        when(parent.listFiles(any(FilenameFilter.class))).thenAnswer(new Answer<InjectableFile[]>()
        {
            @Override
            public InjectableFile[] answer(InvocationOnMock invocationOnMock) throws Throwable
            {
                FilenameFilter filter = (FilenameFilter) invocationOnMock.getArguments()[0];
                ArrayList<InjectableFile> ret = new ArrayList<InjectableFile>(children.length);
                for (InjectableFile child : children) {
                    if (filter.accept(null, child.getName())) ret.add(child);
                }
                return ret.toArray(new InjectableFile[0]);
            }
        });
    }
}
