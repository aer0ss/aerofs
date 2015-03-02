package com.aerofs.daemon.event;

import com.aerofs.lib.event.IEvent;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;

/** bi-directional events for Inter-Module Calls */
public interface IEBIMC extends IEvent, IResultWaiter {

}
