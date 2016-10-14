package com.aerofs.daemon.core.phy.linked;

import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ClientParam.AuxFolder;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Preconditions;

public abstract class AbstractLinkedObject
{
    protected final LinkedStorage _s;

    LinkedPath _path;
    InjectableFile _f;

    protected AbstractLinkedObject(LinkedStorage s)
    {
        _s = s;
    }

    protected void setPath(LinkedPath path)
    {
        _path = path;
        _f = _s._factFile.create(path.physical);
    }

    /**
     * Change the object's physical path to point to point to a suitable location inside
     * {@link AuxFolder#NON_REPRESENTABLE}
     */
    public void markNonRepresentable()
    {
        Preconditions.checkNotNull(_path.virtual);
        setPath(LinkedPath.nonRepresentable(_path.virtual,
                _s._lrm.auxFilePath_(_path.virtual.sid(), soid(), ClientParam.AuxFolder.NON_REPRESENTABLE)));
    }

    abstract SOID soid();

    @Override
    public String toString()
    {
        return _path.toString();
    }
}
