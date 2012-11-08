/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.history;

import com.aerofs.gui.history.HistoryModel.IDecisionMaker.Answer;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.proto.Ritual.ExportRevisionReply;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.proto.Ritual.PBRevChild;
import com.aerofs.proto.Ritual.PBRevision;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A model of the version history information
 */
public class HistoryModel
{
    private static final Logger l = Util.l(HistoryModel.class);

    private final String _user;
    private RitualBlockingClient _ritual;
    private final RitualBlockingClient.Factory _factory;

    private List<ModelIndex> topLevel = null;

    public static class Version
    {
        public Path path;
        public @Nullable ByteString index;
        public String tmpFile;
        public long size;
        public long mtime;

        Version(Path p, ByteString i, long s, long t)
        {
            path = p;
            index = i;
            tmpFile = null;
            size = s;
            mtime = t;
        }
    }

    public static class ModelIndex implements Comparable<ModelIndex>
    {
        String name;
        boolean isDir;
        boolean isDeleted;

        protected ModelIndex parent;
        protected @Nullable List<ModelIndex> children = null;

        ModelIndex(ModelIndex p, String n, boolean dir, boolean del)
        {
            name = n;
            isDir = dir;
            isDeleted = del;
            parent = p;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof ModelIndex)) {
                return false;
            }
            ModelIndex c = (ModelIndex)obj;
            return c.name.equals(name) && c.isDir == isDir;
        }

        @Override
        public int hashCode()
        {
            return name.hashCode();
        }

        @Override
        public int compareTo(ModelIndex index)
        {
            int cmp = name.compareTo(index.name);
            return cmp != 0 ? cmp : (isDir ? 1 : 0) - (index.isDir ? 1 : 0);
        }
    }

    public HistoryModel()
    {
        _user = Cfg.user();
        _factory = new RitualBlockingClient.Factory();
        _ritual = _factory.create();
    }

    public HistoryModel(CfgLocalUser user, RitualBlockingClient.Factory factory)
    {
        _user = user.get();
        _factory = factory;
        _ritual = _factory.create();
    }

    public void clear()
    {
        topLevel = null;
        _ritual = _factory.create();
    }

    /**
     * @return the full path (under root anchor) for the given {@code index}
     */
    public Path getPath(@Nullable ModelIndex index)
    {
        List<String> elems = Lists.newArrayList();
        while (index != null) {
            elems.add(0, index.name);
            index = index.parent;
        }
        return new Path(elems);
    }

    /**
     * @return number of children of the given {@code parent}
     * NOTE: this will automatically fetch more data through Ritual if needed
     */
    public int rowCount(@Nullable ModelIndex parent)
    {
        if (parent == null) {
            if (topLevel == null)
                topLevel = children(parent);
            // avoid crash if Ritual connection fail
            return topLevel == null ? 0 : topLevel.size();
        } else {
            if (parent.children == null)
                parent.children = children(parent);
            // avoid crash if Ritual connection fail
            return parent.children == null ? 0 : parent.children.size();
        }
    }

    /**
     * @return child index of the given {@code parent} at the given {@code row}
     */
    public ModelIndex index(@Nullable ModelIndex parent, int row)
    {
        ModelIndex child;
        if (parent == null) {
            assert topLevel != null;
            assert row >= 0 && row < topLevel.size();
            child = topLevel.get(row);
        } else {
            assert parent.children != null;
            assert row >= 0 && row < parent.children.size();
            child = parent.children.get(row);
        }
        return child;
    }

    /**
     * Build list of children for a ModelIndex (lazy population of the model)
     */
    private @Nullable List<ModelIndex> children(@Nullable ModelIndex parent)
    {
        List<ModelIndex> children = null;
        try {
            Path path = getPath(parent);
            ListRevChildrenReply reply = _ritual.listRevChildren(path.toPB());
            assert reply != null;
            children = Lists.newArrayList();

            ModelIndex idx;
            Map<ModelIndex, ModelIndex> cm = Maps.newHashMap();
            for (PBRevChild c : reply.getChildList()) {
                idx = new ModelIndex(parent, c.getName(), c.getIsDir(), true);
                cm.put(idx, idx);
            }

            if (parent == null || !parent.isDeleted) {
                // TODO(huguesb): do we really need to pass user ids through Ritual?
                GetChildrenAttributesReply ca = _ritual.getChildrenAttributes(_user, path.toPB());
                assert ca != null;
                for (int i = 0; i < ca.getChildrenNameCount(); i++) {
                    String name = ca.getChildrenName(i);
                    PBObjectAttributes oa = ca.getChildrenAttributes(i);
                    // TODO(huguesb): special handling for conflict branches?
                    if (!oa.getExcluded()) {
                        idx = new ModelIndex(parent, name, oa.getType() != Type.FILE, false);
                        cm.put(idx, idx);
                    }
                }
            }

            children = Lists.newArrayList(cm.values());
            Collections.sort(children);
        } catch (Exception e) {
            l.warn(Util.e(e));
        }
        return children;
    }

    /**
     * @return list of versions for a given ModelIndex, null if Ritual call fails
     */
    @Nullable List<Version> versions(ModelIndex index)
    {
        Path path = getPath(index);
        List<Version> revs = null;
        try {
            ListRevHistoryReply reply = _ritual.listRevHistory(path.toPB());
            revs = Lists.newArrayList();
            for (PBRevision r : reply.getRevisionList()) {
                revs.add(new Version(path, r.getIndex(), r.getLength(), r.getMtime()));
            }
            if (!index.isDeleted) {
                // For non-deleted files, add current version
                File f = new File(path.toAbsoluteString(Cfg.absRootAnchor()));
                Version current = new Version(path, null, f.length(), FileUtil.lastModified(f));
                current.tmpFile = f.getAbsolutePath();
                revs.add(current);
            }
        } catch (Exception e) {
            l.warn(Util.e(e));
        }
        return revs;
    }

    /**
     * Exports a version to a temporary file
     * NOTE: the current version is never exported, the tmpFile variable points directly to it
     */
    void export(Version version) throws Exception
    {
        if (version.tmpFile != null) return;
        ExportRevisionReply reply = _ritual.exportRevision(version.path.toPB(), version.index);
        version.tmpFile = reply.getDest();
    }

    public static interface IDecisionMaker
    {
        enum Answer {
            Abort,
            Retry,
            Ignore
        };
        Answer retry(ModelIndex a);
    }

    /**
     * Ideally we should treat files and folders differently to avoid cases such as :
     * "2012.01.01" -> "2012.01 - restored.01"
     * However, because OSX is retarded and allows folders to have extensions (e.g. ".app") this
     * cannot be done consistently on all platforms so we always treat folders as files (until
     * some users start complaining about this behavior).
     *
     * @return a suitable name for a restored file/folder in case of conflict
     */
    private File getRestoredFile(String absPath, String name)
    {
        FileName fn = FileName.fromBaseName(name);
        String restoredName = fn.base + " - restored" + fn.extension;

        File dst = new File(Util.join(absPath, restoredName));
        while (dst.exists()) {
            restoredName = Util.newNextFileName(restoredName);
            dst = new File(Util.join(absPath, restoredName));
        }
        return dst;
    }

    /**
     * Recursively restore a deleted directory
     *
     * The most recent version of every file is restored.
     *
     * @param base root of directory tree to restore
     * @param absPath absolute path under which to restore the deleted directory tree
     * @param delegate an interface through which decisions are delegated
     * @return whether the operation was aborted by the decision maker
     *
     * @throws java.io.IOException
     *  - {@code absPath} does not fully exist and missing folders cannot be created
     *  - {@code index.name} cannot be created under {@code absPath}
     */
    boolean restore(ModelIndex base, String absPath, IDecisionMaker delegate) throws Exception
    {
        File parent = new File(absPath);
        File dst = new File(Util.join(absPath, base.name));
        if (base.isDir) {
            if (dst.exists()) {
                if (!dst.isDirectory()) {
                    // TODO: finer conflict resolution through delegate if users request it
                    dst = getRestoredFile(absPath, base.name);
                }
            }

            int n = rowCount(base);
            for (int i = 0; i < n; ++i) {
                ModelIndex child = index(base, i);
                if (restore(child, dst.getAbsolutePath(), delegate)) {
                    return true;
                }
            }
        } else if (base.isDeleted) {
            // delayed parent creation to avoid creating folder hierarchy when no files are restored
            if (!parent.exists()) {
                FileUtil.mkdirs(parent);
            }

            if (dst.exists()) {
                // TODO: finer conflict resolution through delegate if users request it
                dst = getRestoredFile(absPath, base.name);
            }

            List<Version> v = versions(base);
            while (v == null || v.isEmpty()) {
                Answer a = delegate.retry(base);
                if (a == Answer.Abort) return true;
                if (a == Answer.Ignore) return false;
                // TODO(huguesb): Do we need to create a new client?
                v = versions(base);
            }

            Version version = v.get(v.size() - 1);
            export(version);

            FileUtil.moveInOrAcrossFileSystem(new File(version.tmpFile), dst);
        }
        return false;
    }
}
