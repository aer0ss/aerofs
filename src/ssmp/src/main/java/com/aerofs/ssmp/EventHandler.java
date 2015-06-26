package com.aerofs.ssmp;

import com.aerofs.ssmp.SSMPEvent;

public interface EventHandler {
    void eventReceived(SSMPEvent e);
}
