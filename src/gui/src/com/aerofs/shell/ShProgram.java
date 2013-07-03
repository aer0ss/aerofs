package com.aerofs.shell;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.lib.ChannelFactories;
import com.aerofs.base.C;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.cli.CLI;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.ritual.RitualClientProvider;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.shell.ShellCommandRunner.ICallback;
import com.aerofs.shell.hidden.CmdDstat;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.google.common.collect.Maps;
import com.aerofs.ui.error.ErrorMessages;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class ShProgram implements IProgram, ICallback
{
    static final String DEBUG_FLAG = "DEBUG";

    private static final String SEP = "/";
    private static final String PROG = L.productUnixName() + "-sh";

    private final RitualClientProvider _ritualProvider =
            new RitualClientProvider(ChannelFactories.getClientChannelFactory());

    private Path _pwd;
    private SPBlockingClient _sp;
    private ElapsedTimer _spRenewalTimer;
    private ShellCommandRunner<ShProgram> _runner;

    public ShProgram()
    {
        _spRenewalTimer = new ElapsedTimer();
        _spRenewalTimer.start();
    }

    @Override
    public void launch_(String rtRoot, String prog, String[] args)
    {
        try {
            UI.set(new CLI());

            _pwd = Path.root(Cfg.rootSID());

            // FIXME: replace with the builder pattern to add commands

            _runner = new ShellCommandRunner<ShProgram>(this, this, PROG,
                    L.product() + " Shell, the command-line console for " + L.product(),
                    args);

            initCommands_();

            _runner.start_();
        } catch (Exception e) {
            System.out.println(PROG + ": " + ErrorMessages.e2msgNoBracketDeprecated(e));
        }

        // If we want the program to exit when it returns from main(), then we'd have to call
        // releaseExternalResources() on Netty's channel factory inside RitualClientProvider.
        // But because this call can hang if a channel isn't closed or someone might forget to
        // call it, it's just safer to call SystemUtil.exit()
        System.exit(0);
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
        if (_sp == null || _spRenewalTimer.elapsed() > 5 * C.MIN) {
            Cfg.init_(Cfg.absRTRoot(), true);
            SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
            _sp = fact.create_(Cfg.user());
            _sp.signInRemote();
            _spRenewalTimer.restart();
        }
        return _sp;
    }

    private void initCommands_()
    {
        _runner.addCommand_(new CmdList());
        _runner.addCommand_(new CmdCd());
        _runner.addCommand_(new CmdPwd());
        _runner.addCommand_(new CmdMv());
        _runner.addCommand_(new CmdRm());
        _runner.addCommand_(new CmdMkdir());
        _runner.addCommand_(new CmdInvitations());
        _runner.addCommand_(new CmdAccept());
        _runner.addCommand_(new CmdLeave());
        _runner.addCommand_(new CmdInvite());
        _runner.addCommand_(new CmdUsers());
        _runner.addCommand_(new CmdDelUser());
        _runner.addCommand_(new CmdDefect());
        _runner.addCommand_(new CmdVersion());
        _runner.addCommand_(new CmdTransfers());
        _runner.addCommand_(new CmdShared());
        _runner.addCommand_(new CmdExclude());
        _runner.addCommand_(new CmdInclude());
        _runner.addCommand_(new CmdExcluded());
        _runner.addCommand_(new CmdImport());
        _runner.addCommand_(new CmdExport());
        _runner.addCommand_(new CmdPause());
        _runner.addCommand_(new CmdResume());
        _runner.addCommand_(new CmdRelocate());
        _runner.addCommand_(new CmdSyncHistory());
        _runner.addCommand_(new CmdPassword());
        _runner.addCommand_(new CmdActivities());
        _runner.addCommand_(new CmdShutdown());
        _runner.addCommand_(new CmdConflicts());
        _runner.addCommand_(new CmdResolve());
        _runner.addCommand_(new CmdRoots());

        // Hidden commands
        _runner.addCommand_(new CmdDstat());

        // TODO(huguesb): remove conditional when seed files are exposed to users
        if (Cfg.user().isAeroFSUser()) {
            _runner.addCommand_(new CmdSeed());
        }

        // TODO(huguesb): remove conditional when sync stat is enabled in prod
        if (Cfg.user().isAeroFSUser()) {
            _runner.addCommand_(new CmdSyncStatus());
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
                if (!r.isEmpty()) {
                    r = r.removeLast();
                } else if (L.isMultiuser()) {
                    // make cd up consistent with cd down from user root
                    r = Path.root(Cfg.rootSID());
                }
            } else {
                // make cd down consistent with ls from user root
                if (L.isMultiuser() && isUserRoot(r)) {
                    try {
                        r = Path.root(SID.fromStringFormal(token));
                    } catch (ExFormatError e) { throw new ExBadArgs("invalid path"); }
                } else {
                    r = r.append(token);
                }
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

    public boolean isPwdAtUserRoot_()
    {
        return isUserRoot(_pwd);
    }

    private static boolean isUserRoot(Path p)
    {
        return p.equals(Path.root(Cfg.rootSID()));
    }

    public Map<SID, String> getRoots() throws Exception
    {
        if (Cfg.storageType() == StorageType.LINKED) {
            // if multiroot, use phy roots as top level
            return Cfg.getRoots();
        } else if (L.isMultiuser()) {
            Map<SID, String> roots = Maps.newHashMap();
            // use user roots as top level
            for (PBSharedFolder sf : getRitualClient_().listUserRoots().getUserRootList()) {
                roots.put(new SID(sf.getPath().getSid()), sf.getName());
            }
            // need to list shared folders too:
            // 1. otherwise we might miss folders created on a linked TS of the same org
            // 2. otherwise the UX would be inconsistent with linked TS
            for (PBSharedFolder sf : getRitualClient_().listSharedFolders().getSharedFolderList()) {
                roots.put(new SID(sf.getPath().getSid()), sf.getName());
            }
            return roots;
        }
        // default: root sid only
        return Collections.singletonMap(Cfg.rootSID(), "");
    }

    private String rootName(Path path)
    {
        // the root SID is not associated with any path on TeamServer
        if (Cfg.storageType() == StorageType.LINKED) {
            String absRoot = Cfg.getRootPath(path.sid());
            return absRoot != null ? new File(absRoot).getName() : "";
        } else {
            return isUserRoot(path) ? "" : path.sid().toStringFormal();
        }
    }

    @Override
    public String getPrompt_()
    {
        return (_pwd.isEmpty() ? rootName(_pwd) : _pwd.last()) + "> ";
    }
}
