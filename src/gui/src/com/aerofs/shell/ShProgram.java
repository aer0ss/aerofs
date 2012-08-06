package com.aerofs.shell;

import java.util.ArrayList;
import java.util.List;

import com.aerofs.l.L;
import com.aerofs.lib.C;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.S;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadArgs;
import com.aerofs.lib.fsi.FSIClient;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.spsv.SPBlockingClient;
import com.aerofs.lib.spsv.SPClientFactory;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.shell.ShellCommandRunner.ICallback;
import com.aerofs.shell.restricted.CmdDstat;
import com.aerofs.ui.UIUtil;

public class ShProgram implements IProgram, ICallback<ShProgram>
{
    public static final String SEP = "/";
    public static final char SEP_CHAR = '/';

    static final String DEBUG_FLAG = "DEBUG";

    private static final String PROG = L.get().productUnixName() + "-sh";
    private List<String> _pwd;
    private FSIClient _fsi;
    private RitualBlockingClient _ritual;
    private SPBlockingClient _sp;
    private long _spRenewal;
    private ShellCommandRunner<ShProgram> _s;

    @Override
    public void launch_(String rtRoot, String prog, String[] args)
    {
        try {
            _pwd = new ArrayList<String>();

            // FIXME: replace with the builder pattern to add commands

            _s = new ShellCommandRunner<ShProgram>(this, this, PROG,
                    S.PRODUCT + " Shell, the command-line console for " +
                    S.PRODUCT,
                    args);

            initCommands_();

            _s.start_();
        } catch (Exception e) {
            System.out.println(PROG + ": " + UIUtil.e2msgNoBracket(e));
        }

        if (_ritual != null) _ritual.close();

        // If we want the program to exit when it returns from main(), then we'd have to call
        // releaseExternalResources() on Netty's channel factory inside RitualClientFactory.
        // But because this call can hang if a channel isn't closed or someone might forget to
        // call it, it's just safer to call System.exit()
        System.exit(0);
    }

    public FSIClient getFSIClient_()
    {
        if (_fsi == null) {
            _fsi = FSIClient.newConnection();
        }

        return _fsi;
    }

    public RitualBlockingClient getRitualClient_()
    {
        if (_ritual == null) {
            _ritual = RitualClientFactory.newBlockingClient();
        }
       return _ritual;
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
            _sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
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
        _s.addCommand_(new CmdAccept());
        _s.addCommand_(new CmdInvite());
        _s.addCommand_(new CmdUsers());
        _s.addCommand_(new CmdDelUser());
        _s.addCommand_(new CmdReport());
        _s.addCommand_(new CmdVersion());
        _s.addCommand_(new CmdTransfers());
        _s.addCommand_(new CmdShared());
        _s.addCommand_(new CmdExclude());
        _s.addCommand_(new CmdInclude());
        _s.addCommand_(new CmdExcluded());
        _s.addCommand_(new CmdExport());
        _s.addCommand_(new CmdPause());
        _s.addCommand_(new CmdResume());
        _s.addCommand_(new CmdRelocate());
        _s.addCommand_(new CmdRevisions());
        _s.addCommand_(new CmdPassword());
        _s.addCommand_(new CmdActivities());
        _s.addCommand_(new CmdSyncStatus());

        // restricted
        if (Cfg.staging() || Cfg.isSP()) {
            _s.addCommand_(new CmdDstat());
        }
    }

    // return the abolute path. path can be null to represent pwd
    // use "a/b/c" to present relative paths, and "/a/b/c" for absolute paths
    public List<String> buildPathElemList_(String path) throws ExBadArgs
    {
        if (path == null) return _pwd;

        List<String> stack = new ArrayList<String>();

        // if it's a relative path, ...
        if (!path.startsWith(SEP)) stack.addAll(_pwd);

        String[] tokens = path.split(SEP);
        for (String token : tokens) {
            if (token.isEmpty()) continue;

            if (token.equals("..")) {
                if (stack.size() == 0) throw new ExBadArgs("invalid path");
                stack.remove(stack.size() - 1);
            } else {
                stack.add(token);
            }
        }

        return stack;
    }

    public void setPwdElements_(List<String> path)
    {
        _pwd = path;
    }

    public List<String> getPwdElements_()
    {
        return _pwd;
    }

    public String getPwd_()
    {
        StringBuilder sb = new StringBuilder();

        if (getPwdElements_().size() == 0) {
            sb.append("/");
        } else {
            for (String token : getPwdElements_()) {
                sb.append("/");
                sb.append(token);
            }
        }

        return sb.toString();
    }

    @Override
    public String getPrompt_()
    {
        return getPwd_() + "> ";
    }

    public PBPath buildPath_(String path)
            throws ExBadArgs
    {
        return buildPath_(buildPathElemList_(path));
    }

    public static PBPath buildPath_(List<String> path)
            throws ExBadArgs
    {
        return PBPath.newBuilder().addAllElem(path).build();
    }
}
