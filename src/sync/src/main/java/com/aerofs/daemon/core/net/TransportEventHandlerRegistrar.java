package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.ICoreEventHandlerRegistrar;
import com.aerofs.daemon.event.net.EIDevicePresence;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.event.net.rx.*;
import com.google.inject.Inject;


public class TransportEventHandlerRegistrar implements ICoreEventHandlerRegistrar
{
    @Inject HdStreamBegun _hdStreamBegun;
    @Inject HdMaxcastMessage _hdMaxcastMessage;
    @Inject HdUnicastMessage _hdUnicastMessage;
    @Inject HdDevicePresence _hdDevicePresence;
    @Inject HdStoreAvailability _hdStoreAvailability;

    @Override
    public void registerHandlers_(CoreEventDispatcher disp) {
        disp
                .setHandler_(EIStoreAvailability.class, _hdStoreAvailability)
                .setHandler_(EIDevicePresence.class, _hdDevicePresence)
                .setHandler_(EIUnicastMessage.class, _hdUnicastMessage)
                .setHandler_(EIMaxcastMessage.class, _hdMaxcastMessage)
                .setHandler_(EIStreamBegun.class, _hdStreamBegun)
                ;
    }
}
