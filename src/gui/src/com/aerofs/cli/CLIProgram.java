package com.aerofs.cli;

import com.aerofs.ChannelFactories;
import com.aerofs.controller.ControllerBadCredentialListener;
import com.aerofs.controller.ControllerService;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.Util;
import com.aerofs.lib.ritual.RitualClientProvider;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.ui.UI;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;

public class CLIProgram implements IProgram
{
    @Override
    public void launch_(String rtRoot, String prog, String[] args) throws Exception
    {
        Util.initDriver("cc"); // "cc" is the log file that aerofsd will write to

        //
        // FIXME (AG): The below is practically identical to code in GUIProgram
        //

        ClientSocketChannelFactory clientChannelFactory = ChannelFactories.getClientChannelFactory();
        ControllerService.init(rtRoot, clientChannelFactory, UI.notifier());
        SPBlockingClient.setListener(new ControllerBadCredentialListener());
        RitualClientProvider ritualProvider = new RitualClientProvider(clientChannelFactory);
        UI.init(new CLI(rtRoot), ritualProvider);

        CLI.get().enterMainLoop_();
    }
}