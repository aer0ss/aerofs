package com.aerofs.shell;

import com.aerofs.ChannelFactories;
import com.aerofs.base.C;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientProvider;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.shell.ShellCommandRunner.ICallback;
import com.aerofs.shell.restricted.CmdDstat;
import com.aerofs.shell.restricted.CmdTestMultiuserJoinRootStore;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UIUtil;

import java.io.File;

public class ShProgram implements IProgram, ICallback
{
    static final String DEBUG_FLAG = "DEBUG";

    private static final String SEP = "/";
    private static final String PROG = L.productUnixName() + "-sh";

    private final RitualClientProvider _ritualProvider = new RitualClientProvider(ChannelFactories.getClientChannelFactory());

    private Path _pwd;
    private SPBlockingClient _sp;
    private long _spRenewal;
    private ShellCommandRunner<ShProgram> _s;

    @Override
    public void launch_(String rtRoot, String prog, String[] args)
    {
        try {
            _pwd = Path.root(Cfg.rootSID());

            // FIXME: replace with the builder pattern to add commands

            _s = new ShellCommandRunner<ShProgram>(this, this, PROG,
                    L.product() + " Shell, the command-line console for " + L.product(),
                    args);

            initCommands_();

            _s.start_();
        } catch (Exception e) {
            System.out.println(PROG + ": " + UIUtil.e2msgNoBracket(e));
        }

        // If we want the program to exit when it returns from main(), then we'd have to call
        // releaseExternalResources() on Netty's channel factory inside RitualClientProvider.
        // But because this call can hang if a channel isn't closed or someone might forget to
        // call it, it's just safer to call SystemUtil.exit()
        System.exit(0);
    }

    public RitualClientProvider getRitualProvider_()
    {
        return _ritualProvider;
    }

    public RitualBlockingClient getRitualClient_()
    {
        return _ritualProvider.getBlockingClient();
    }

    /**
     * @return a client which has been remotely signed in
     * TODO this is unused
     */
    public SPBlockingClient getSPClient_() throws Exception
    {
        // renewal is needed as cookies may expire
        long now = System.currentTimeMillis();
        if (_sp == null || now - _spRenewal > 5 * C.MIN) {
            Cfg.init_(Cfg.absRTRoot(), true);
            SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
            _sp = fact.create_(Cfg.user());
            _sp.signInRemote();
            _spRenewal = now;
        }
        return _sp;
    }

    private void initCommands_()
    {
        _s.addCommand_(new CmdList());
        _s.addCommand_(new CmdCd());
        _s.addCommand_(new CmdPwd());
        _s.addCommand_(new CmdMv());
        _s.addCommand_(new CmdRm());
        _s.addCommand_(new CmdMkdir());
        _s.addCommand_(new CmdInvitations());
        _s.addCommand_(new CmdAccept());
        _s.addCommand_(new CmdLeave());
        _s.addCommand_(new CmdInvite());
        _s.addCommand_(new CmdUsers());
        _s.addCommand_(new CmdDelUser());
        _s.addCommand_(new CmdDefect());
        _s.addCommand_(new CmdVersion());
        _s.addCommand_(new CmdTransfers());
        _s.addCommand_(new CmdShared());
        _s.addCommand_(new CmdExclude());
        _s.addCommand_(new CmdInclude());
        _s.addCommand_(new CmdExcluded());
        _s.addCommand_(new CmdImport());
        _s.addCommand_(new CmdExport());
        _s.addCommand_(new CmdPause());
        _s.addCommand_(new CmdResume());
        _s.addCommand_(new CmdRelocate());
        _s.addCommand_(new CmdVersionHistory());
        _s.addCommand_(new CmdPassword());
        _s.addCommand_(new CmdActivities());
        _s.addCommand_(new CmdShutdown());
        _s.addCommand_(new CmdConflicts());
        _s.addCommand_(new CmdResolve());

        _s.addCommand_(new CmdRoots());

        // TODO(huguesb): remove conditional when seed files are exposed to users
        if (Cfg.user().isAeroFSUser()) {
            _s.addCommand_(new CmdSeed());
        }

        // TODO(huguesb): remove conditional when sync stat is enabled in prod
        if (Cfg.user().isAeroFSUser()) {
            _s.addCommand_(new CmdSyncStatus());
        }

        // restricted
        if (L.isStaging()) {
            _s.addCommand_(new CmdDstat());
            _s.addCommand_(new CmdTestMultiuserJoinRootStore());
        }
    }

    // return the abolute path. path can be null to represent pwd
    // use "a/b/c" to present relative paths, and "/a/b/c" for absolute paths
    public Path buildPath_(String path) throws ExBadArgs
    {
        if (path == null) return _pwd;

        try {
            return Path.fromStringFormal(path);
        } catch (ExFormatError e) {
            // not a valid absolute formal (i.e store-prefixed) path
        }

        Path r = path.startsWith(SEP) ? Path.root(_pwd.sid()) : _pwd;

        String[] tokens = path.split(SEP);
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (token.equals("..")) {
                if (r.isEmpty()) throw new ExBadArgs("invalid path");
                r = r.removeLast();
            } else {
                r = r.append(token);
            }
        }

        return r;
    }

    public PBPath buildPBPath_(String path) throws ExBadArgs
    {
        return buildPath_(path).toPB();
    }

    public Path getPwd_()
    {
        return _pwd;
    }

    public void setPwd_(Path pwd)
    {
        _pwd = pwd;
    }

    private String rootName(Path path)
    {
        // the root SID is not associated with any path on TeamServer
        String absRoot = Cfg.getRootPath(path.sid());
        return absRoot != null ? new File(absRoot).getName() : "";
    }

    @Override
    public String getPrompt_()
    {
        return (_pwd.isEmpty() ? rootName(_pwd) : _pwd.last()) + "> ";
    }
}
