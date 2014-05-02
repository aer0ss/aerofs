/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.gui.history;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.SID;
import com.aerofs.gui.history.HistoryModel.IDecisionMaker.Answer;
import com.aerofs.labeling.L;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.FileUtil.FileName;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.id.KIndex;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.ListRevChildrenReply;
import com.aerofs.proto.Ritual.ListRevHistoryReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.aerofs.proto.Ritual.PBObjectAttributes.Type;
import com.aerofs.proto.Ritual.PBRevChild;
import com.aerofs.proto.Ritual.PBRevision;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;

/**
 * A model of the sync history information
 */
public class HistoryModel
{
    private static final Logger l = Loggers.getLogger(HistoryModel.class);

    private final IRitualClientProvider _ritualProvider;
    private final StorageType _storageType;
    private final CfgAbsRoots _cfgAbsRoots;
    private final CfgLocalUser _cfgLocalUser;

    private List<ModelIndex> topLevel = null;

    public static class Version
    {
        public Path path;
        public @Nullable ByteString index;
        public String tmpFile;
        public long size;
        public long mtime;

        Version(Path p, @Nullable ByteString i, long s, long t)
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
        final Path path;
        final boolean isDir;
        final boolean isDeleted;
        private final String defaultName;

        final HistoryModel model;
        final protected ModelIndex parent;
        protected @Nullable List<ModelIndex> children = null;

        ModelIndex(HistoryModel m, Path path, boolean dir, boolean del)
        {
            this.path = path;
            isDir = dir;
            isDeleted = del;
            parent = null;
            model = m;
            defaultName = null;
        }

        ModelIndex(ModelIndex p, Path path, boolean dir, boolean del)
        {
           this.path = path;
            isDir = dir;
            isDeleted = del;
            parent = p;
            model = p.model;
            defaultName = null;
        }

        ModelIndex(HistoryModel m, Path path, String name)
        {
            this.path = path;
            isDir = true;
            isDeleted = false;
            parent = null;
            model = m;
            defaultName = name.startsWith("root store: ") ? name.substring(12) : name;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof ModelIndex)) {
                return false;
            }
            ModelIndex c = (ModelIndex)obj;
            return c.path.equals(path) && c.isDir == isDir;
        }

        @Override
        public int hashCode()
        {
            return path.hashCode();
        }

        @Override
        public int compareTo(ModelIndex index)
        {
            int cmp = name().compareTo(index.name());
            return cmp != 0 ? cmp : (isDir ? 1 : 0) - (index.isDir ? 1 : 0);
        }

        public String name()
        {
            return path.isEmpty() ? UIUtil.sharedFolderName(path, defaultName, model._cfgAbsRoots)
                    : path.last();
        }
    }

    public HistoryModel(IRitualClientProvider ritualProvider)
    {
        // TODO: use dependency injection
        this(ritualProvider, Cfg.storageType(), new CfgAbsRoots(), new CfgLocalUser());
    }

    public HistoryModel(IRitualClientProvider ritualProvider, StorageType storageType,
            CfgAbsRoots absRoots, CfgLocalUser cfgLocalUser)
    {
        _ritualProvider = ritualProvider;
        _storageType = storageType;
        _cfgAbsRoots = absRoots;
        _cfgLocalUser = cfgLocalUser;
    }

    public void clear()
    {
        topLevel = null;
    }

    /**
     * @return the full path (under root anchor) for the given {@code index}
     */
    public Path getPath(@Nonnull ModelIndex index)
    {
        return index.path;
    }

    private List<ModelIndex> topLevel()
    {
        if (_storageType == StorageType.LINKED) {
            // if multiroot, use phy roots as top level
            Map<SID, String> r;
            try {
                r = _cfgAbsRoots.getAll();
            } catch (SQLException e) {
                l.error("ignored exception", e);
                r = Collections.emptyMap();
            }
            List<ModelIndex> list = Lists.newArrayListWithExpectedSize(r.size());
            for (SID sid : r.keySet()) {
                list.add(new ModelIndex(this, Path.root(sid), true, false));
            }
            return list;

        } else if (L.isMultiuser()) {
            try {
                List<ModelIndex> list = Lists.newArrayList();
                // use user roots as top level
                for (PBSharedFolder sf : ritual().listUserRoots().getUserRootList()) {
                    list.add(new ModelIndex(this, Path.fromPB(sf.getPath()), sf.getName()));
                }
                // need to list shared folders too:
                // 1. otherwise we might miss folders created on a linked TS of the same org
                // 2. otherwise the UX would be inconsistent with linked TS
                for (PBSharedFolder sf : ritual().listSharedFolders().getSharedFolderList()) {
                    list.add(new ModelIndex(this, Path.fromPB(sf.getPath()), sf.getName()));
                }
                return list;
            } catch (Exception e) {
                l.warn("ignored exception: " + Util.e(e));
                return getModelIndexForDefaultRoot();
            }
        } else {
            // default: root sid only
            return getModelIndexForDefaultRoot();
        }
    }

    private List<ModelIndex> getModelIndexForDefaultRoot()
    {
        return Collections.singletonList(
                new ModelIndex(this, Path.root(SID.rootSID(_cfgLocalUser.get())), true, false));
    }

    /**
     * @return number of children of the given {@code parent}
     * NOTE: this will automatically fetch more data through Ritual if needed
     */
    public int rowCount(@Nullable ModelIndex parent)
    {
        if (parent == null) {
            if (topLevel == null) {
                topLevel = topLevel();
            }

            // if there is a single phy root, no need to show it...
            if (topLevel != null && topLevel.size() == 1) return rowCount(topLevel.get(0));

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

            // if there is a single phy root, no need to show it...
            if (topLevel.size() == 1) return index(topLevel.get(0), row);

            assert row >= 0 && row < topLevel.size();
            child = topLevel.get(row);
        } else {
            assert parent.children != null;
            assert row >= 0 && row < parent.children.size();
            child = parent.children.get(row);
        }
        return child;
    }

    boolean hasMasterBranch(List<PBBranch> l)
    {
        for (PBBranch b : l) if (b.getKidx() == KIndex.MASTER.getInt()) return true;
        return false;
    }

    /**
     * Build list of children for a ModelIndex (lazy population of the model)
     */
    private @Nullable List<ModelIndex> children(@Nullable ModelIndex parent)
    {
        List<ModelIndex> children = null;
        try {
            Path path = getPath(parent);
            ListRevChildrenReply reply = ritual().listRevChildren(path.toPB());
            assert reply != null;
            children = newArrayList();

            ModelIndex idx;
            Map<ModelIndex, ModelIndex> cm = Maps.newHashMap();
            for (PBRevChild c : reply.getChildList()) {
                idx = new ModelIndex(parent, path.append(c.getName()), c.getIsDir(), true);
                cm.put(idx, idx);
            }

            if (parent == null || !parent.isDeleted) {
                GetChildrenAttributesReply ca = ritual().getChildrenAttributes(path.toPB());
                assert ca != null;
                for (int i = 0; i < ca.getChildrenNameCount(); i++) {
                    String name = ca.getChildrenName(i);
                    PBObjectAttributes oa = ca.getChildrenAttributes(i);
                    boolean isDir = oa.getType() != Type.FILE;
                    if (!oa.getExcluded() && (isDir || hasMasterBranch(oa.getBranchList()))) {
                        idx = new ModelIndex(parent, path.append(name), isDir, false);
                        cm.put(idx, idx);
                    }
                }
            }

            children = newArrayList(cm.values());
            Collections.sort(children);
        } catch (Exception e) {
            l.warn(Util.e(e));
        }
        return children;
    }

    List<Version> versions(ModelIndex index) throws Exception
    {
        Path path = getPath(index);
        List<Version> revs = newArrayList();
        ListRevHistoryReply reply = ritual().listRevHistory(path.toPB());
        for (PBRevision r : reply.getRevisionList()) {
            revs.add(new Version(path, r.getIndex(), r.getLength(), r.getMtime()));
        }
        if (!index.isDeleted) {
            GetObjectAttributesReply oa = ritual().getObjectAttributes(path.toPB());
            for (PBBranch branch : oa.getObjectAttributes().getBranchList())
            {
                if (branch.getKidx() == KIndex.MASTER.getInt())
                {
                    Version current = new Version(path, null,
                            branch.getLength(), branch.getMtime());
                    current.tmpFile = UIUtil.absPathNullable(path);
                    revs.add(current);
                    break;
                }
            }
        }
        return revs;
    }

    /**
     * @return list of versions for a given ModelIndex, null if Ritual call fails
     */
    @Nullable List<Version> versionsNoThrow(ModelIndex index)
    {
        try {
            return versions(index);
        } catch (Exception e) {
            // TODO: invalidate index?
            l.warn(Util.e(e));
            return null;
        }
    }

    private RitualBlockingClient ritual()
    {
        return _ritualProvider.getBlockingClient();
    }

    /**
     * Exports a version to a temporary file
     * NOTE: the current version is never exported, the tmpFile variable points directly to it
     */
    void export(Version version) throws Exception
    {
        if (version.tmpFile != null) return;
        if (version.index == null) {
            version.tmpFile = ritual().exportFile(version.path.toPB()).getDest();
        } else {
            version.tmpFile = ritual().exportRevision(version.path.toPB(), version.index).getDest();
        }
        // Make sure users won't try to make changes to the temp file: their changes would be lost
        //noinspection ResultOfMethodCallIgnored
        new File(version.tmpFile).setReadOnly();
    }

    public void delete(Version version) throws Exception
    {
        if (version.index == null) throw new ExBadArgs();
        ritual().deleteRevision(version.path.toPB(), version.index);
    }

    public void delete(ModelIndex index) throws Exception
    {
        ritual().deleteRevision(getPath(index).toPB(), null);
        // TODO: avoid complete model refresh...
    }

    public static interface IDecisionMaker
    {
        enum Answer {
            Abort,
            Retry,
            Ignore
        }
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
            restoredName = Util.nextFileName(restoredName);
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
        File dst = new File(Util.join(absPath, base.name()));
        if (base.isDir) {
            if (dst.exists()) {
                if (!dst.isDirectory()) {
                    // TODO: finer conflict resolution through delegate if users request it
                    dst = getRestoredFile(absPath, base.name());
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
                dst = getRestoredFile(absPath, base.name());
            }

            List<Version> v = versionsNoThrow(base);
            while (v == null || v.isEmpty()) {
                Answer a = delegate.retry(base);
                if (a == Answer.Abort) return true;
                if (a == Answer.Ignore) return false;
                // TODO(huguesb): Do we need to create a new client?
                v = versionsNoThrow(base);
            }

            Version version = v.get(v.size() - 1);
            export(version);

            // throws if file already exists at destination, which should not occur because of
            // earlier filename-picking logic.
            FileUtil.copy(new File(version.tmpFile), dst, true, true);
        }
        return false;
    }
}
