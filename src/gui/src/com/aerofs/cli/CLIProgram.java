package com.aerofs.cli;

import com.aerofs.ChannelFactories;
import com.aerofs.controller.ControllerBadCredentialListener;
import com.aerofs.controller.ControllerService;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.ritual.RitualClientProvider;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.UIUtil;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

public class CLIProgram implements IProgram
{
    @Override
    public void launch_(final String rtRoot, String prog, String[] args) throws Exception
    {
        Util.initDriver("cc"); // "cc" is the log file that aerofsd will write to

        // process application arguments
        for (String arg : args) processArgument(arg);

        //
        // FIXME (AG): The below is practically identical to code in GUIProgram
        //

        ClientSocketChannelFactory clientChannelFactory = ChannelFactories.getClientChannelFactory();
        ControllerService.init(rtRoot, clientChannelFactory, UIGlobals.notifier());
        SPBlockingClient.setBadCredentialListener(new ControllerBadCredentialListener());
        RitualClientProvider ritualProvider = new RitualClientProvider(clientChannelFactory);

        CLI cli = new CLI();
        UI.set(cli);

        UIGlobals.setRitualClientProvider(ritualProvider);

        // Launch the daemon
        cli.asyncExec(new Runnable()
        {
            @Override
            public void run()
            {
                UIUtil.launch(rtRoot, null, null);
            }
        });

        CLI.get().enterMainLoop_();
    }

    /**
     * Processing a single application argument. Supported arguments are:
     *   -E[message] show error message and then immediately exit
     */
    private void processArgument(String arg)
    {
        if (arg.startsWith("-E")) {
            System.err.println(arg.substring("-E".length()));
            ExitCode.CONFIGURATION_INIT.exit();
        }
    }
}
