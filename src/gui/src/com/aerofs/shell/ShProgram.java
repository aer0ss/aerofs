package com.aerofs.shell;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID.ExInvalidID;
import com.aerofs.cli.CLI;
import com.aerofs.defects.Defects;
import com.aerofs.labeling.L;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Path;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.nativesocket.RitualSocketFile;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Ritual.ListUserRootsReply.UserRoot;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.ritual.RitualClientProvider;
import com.aerofs.shell.ShellCommandRunner.ICallback;
import com.aerofs.shell.hidden.CmdDstat;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.error.ErrorMessages;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class ShProgram implements IProgram, ICallback
{
    private static final Logger l = Loggers.getLogger(ShProgram.class);

    static final String DEBUG_FLAG = "DEBUG";

    private static final String SEP = "/";
    private static final String PROG = L.productUnixName() + "-sh";

    private final RitualClientProvider _ritualProvider =
            new RitualClientProvider(new RitualSocketFile());
    private Path _pwd;
    private SPBlockingClient _sp;
    private ElapsedTimer _spRenewalTimer;
    private ShellCommandRunner<ShProgram> _runner;

    public ShProgram()
    {
        _spRenewalTimer = new ElapsedTimer();
    }

    @Override
    public void launch_(String rtRoot, String prog, String[] args)
    {
        // TODO (AT): really need to tidy up our launch sequence
        // Defects system initialization is replicated in GUI, CLI, SH, and Daemon. The only
        // difference is how the exception is handled.
        try {
            Defects.init(prog, rtRoot);
        } catch (Exception e) {
            System.err.println("Failed to initialize the defects system.\n" +
                    "Cause: " + e.toString());
            l.error("Failed to initialized the defects system.", e);
            ExitCode.FAIL_TO_LAUNCH.exit();
        }

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
        ExitCode.NORMAL_EXIT.exit();
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
            _sp = newMutualAuthClientFactory().create().signInRemote();
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
        _runner.addCommand_(new CmdDelUser());
        _runner.addCommand_(new CmdDiagnostics());
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
        _runner.addCommand_(new CmdConfig());

        // Hidden commands
        _runner.addCommand_(new CmdDstat());
        _runner.addCommand_(new CmdSeed());

        _runner.addCommand_(new CmdDefect(_ritualProvider));
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
                    } catch (ExInvalidID e) { throw new ExBadArgs("invalid path"); }
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
            for (UserRoot userRoot : getRitualClient_().listUserRoots().getRootList()) {
                roots.put(new SID(BaseUtil.fromPB(userRoot.getSid())), userRoot.getName());
            }
            // need to list shared folders too:
            // 1. otherwise we might miss folders created on a linked TS of the same org
            // 2. otherwise the UX would be inconsistent with linked TS
            for (PBSharedFolder sf : getRitualClient_().listSharedFolders().getSharedFolderList()) {
                roots.put(new SID(BaseUtil.fromPB(sf.getPath().getSid())), sf.getName());
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
            String absRoot = null;
            try {
                absRoot = Cfg.getRootPathNullable(path.sid());
            } catch (SQLException e) {
                l.error("ignored exception", e);
            }
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
