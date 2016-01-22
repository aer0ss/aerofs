/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.gui.conflicts;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Common;
import com.aerofs.proto.Ritual.ExportConflictReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.ListConflictsReply;
import com.aerofs.proto.Ritual.ListConflictsReply.ConflictedPath;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBBranch.PBPeer;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.ui.UIUtil;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

/**
 * This class encapsulates the GUI's view of conflicts.
 *
 * The subclasses have methods to perform async ritual calls; these methods expects an executor and
 * a callback. When invoked from the GUI, the GUIExecutor is expected, and it will be used to
 * invoke the callback on the GUI thread.
 *
 * The intent of having a model is to de-couple non-GUI logic away from GUI so that GUI code won't
 * be as messy and intertwined, and these methods can be independently tested should we choose to.
 *
 * N.B. Also includes formatting logic which probably doesn't belong here.
 */
public class ConflictsModel
{
    private final IRitualClientProvider _provider;
    private final CfgLocalUser _localUser;

    public ConflictsModel(IRitualClientProvider provider, CfgLocalUser localUser)
    {
        _provider = provider;
        _localUser = localUser;
    }

    public Conflict createConflictFromPath(Path path)
    {
        return new Conflict(path);
    }

    public void listConflicts(Executor executor,
            final FutureCallback<Collection<Conflict>> callback)
    {
        Futures.addCallback(_provider.getNonBlockingClient().listConflicts(),
                new FutureCallback<ListConflictsReply>()
                {
                    @Override
                    public void onSuccess(ListConflictsReply reply)
                    {
                        List<Conflict> conflicts = newArrayListWithCapacity(
                                reply.getConflictCount());

                        for (ConflictedPath path : reply.getConflictList()) {
                            conflicts.add(createConflictFromPath(Path.fromPB(path.getPath())));
                        }

                        callback.onSuccess(conflicts);
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        callback.onFailure(throwable);
                    }
                }, executor);
    }

    public class Conflict
    {
        public final Path _path;

        private Conflict(Path path)
        {
            _path = path;
        }

        public void listBranches(Executor executor,
                final FutureCallback<Collection<Branch>> callback)
        {
            Futures.addCallback(_provider.getNonBlockingClient().getObjectAttributes(_path.toPB()),
                    new FutureCallback<GetObjectAttributesReply>()
                    {
                        @Override
                        public void onSuccess(GetObjectAttributesReply reply)
                        {
                            List<PBBranch> pbBranches = reply.getObjectAttributes().getBranchList();
                            List<Branch> branches = newArrayListWithCapacity(pbBranches.size());

                            for (PBBranch pbBranch : pbBranches) {
                                branches.add(createBranchFromPB(pbBranch));
                            }

                            callback.onSuccess(branches);
                        }

                        @Override
                        public void onFailure(Throwable throwable)
                        {
                            callback.onFailure(throwable);
                        }

                        private Branch createBranchFromPB(PBBranch pbBranch)
                        {
                            List<Contributor> contributors = newArrayListWithCapacity(
                                    pbBranch.getAncestorToBranchCount());

                            for (PBPeer peer : pbBranch.getAncestorToBranchList()) {
                                contributors.add(createContributorFromPB(peer));
                            }

                            return new Branch(Conflict.this, new KIndex(pbBranch.getKidx()),
                                    pbBranch.getLength(), pbBranch.getMtime(), contributors);
                        }

                        private Contributor createContributorFromPB(PBPeer pbPeer)
                        {
                            return new Contributor(pbPeer.getUserName(), pbPeer.hasDeviceName() ?
                                    pbPeer.getDeviceName() : "Unknown computer");
                        }
                    }, executor);
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o instanceof Conflict && _path.equals(((Conflict)o)._path));
        }

        @Override
        public int hashCode()
        {
            return _path.hashCode();
        }
    }

    public class Branch
    {
        public final Conflict _conflict;
        public final KIndex _kidx;
        public final long _length;
        public final long _mtime;
        public final Collection<Contributor> _contributors;

        // the Branch object caches the path to the exported file to avoid multiple exports
        private @Nullable String _exportedPath;

        private Branch(Conflict conflict, KIndex kidx, long length, long mtime,
                Collection<Contributor> contributors)
        {
            _conflict = conflict;
            _kidx = kidx;
            _length = length;
            _mtime = mtime;
            _contributors = contributors;

            if (isMaster()) _exportedPath = UIUtil.absPathNullable(_conflict._path);
        }

        public boolean isMaster()
        {
            return _kidx.isMaster();
        }

        public boolean isDummy() {
            return new CfgUsePolaris().get() && _mtime == 0 && _contributors.isEmpty();
        }

        public String formatVersion()
        {
            if (isMaster()) return "Current version on this computer";

            // N.B. KIndex is 0-based whereas natural language is 1-based.
            // this logic will cause the branches to read "Version 2", "Version 3", etc.
            if (!new CfgUsePolaris().get()) return  "Version " + (_kidx.getInt() + 1);

            if (isDummy()) {
                return "Unavailable remote version";
            }

            Contributor c = _contributors.iterator().next();
            return c.isLocalUser()
                    ? "My version from " + c._devicename
                    : "Version from " + c._username;
        }

        public String formatContributors()
        {
            List<String> contributors = newArrayListWithCapacity(_contributors.size());

            for (Contributor contributor : _contributors) {
                contributors.add(contributor.formatContributor());
            }

            return "Contributors: " + StringUtils.join(contributors, ", ");
        }

        public String formatFileSize()
        {
            return Util.formatSize(_length);
        }

        public String formatLastModified()
        {
            return "Last modified " + Util.formatAbsoluteTime(_mtime);
        }

        public void export(Executor executor, final FutureCallback<String> callback)
        {
            if (isExportedPathValid()) {
                // use immediate future to avoid re-implementing the async callback mechanism.
                // Implementing the async callback mechanism isn't hard, but it creates a messy
                // try-catch that can all be avoided if we just use immediate future.
                Futures.addCallback(Futures.immediateFuture(_exportedPath), callback, executor);
            } else {
                Futures.addCallback(_provider.getNonBlockingClient()
                        .exportConflict(_conflict._path.toPB(), _kidx.getInt()),
                        new FutureCallback<ExportConflictReply>()
                        {
                            @Override
                            public void onSuccess(ExportConflictReply reply)
                            {
                                _exportedPath = reply.getDest();
                                new File(_exportedPath).setReadOnly();

                                callback.onSuccess(_exportedPath);
                            }

                            @Override
                            public void onFailure(Throwable throwable)
                            {
                                callback.onFailure(throwable);
                            }
                        }, executor);
            }
        }

        public void delete(Executor executor, final FutureCallback<Void> callback)
        {
            Futures.addCallback(_provider.getNonBlockingClient().
                    deleteConflict(_conflict._path.toPB(), _kidx.getInt()),
                    new FutureCallback<Common.Void>()
                    {
                        @Override
                        public void onSuccess(Common.Void aVoid)
                        {
                            callback.onSuccess(null);
                        }

                        @Override
                        public void onFailure(Throwable throwable)
                        {
                            callback.onFailure(throwable);
                        }
                    }, executor);
        }

        private boolean isExportedPathValid()
        {
            return !StringUtils.isEmpty(_exportedPath) && new File(_exportedPath).exists();
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o instanceof Branch
                    && _conflict.equals(((Branch)o)._conflict)
                    && _kidx.equals(((Branch)o)._kidx));
        }

        @Override
        public int hashCode()
        {
            return _conflict.hashCode() ^ _kidx.hashCode();
        }
    }

    public class Contributor
    {
        public final String _username;
        public final String _devicename;

        private Contributor(String username, String devicename)
        {
            _username = username;
            _devicename = devicename;
        }

        public boolean isLocalUser()
        {
            return _username.equals(_localUser.get().getString());
        }

        public String formatContributor()
        {
            return isLocalUser() ? "Me on " + _devicename : _username;
        }
    }
}
