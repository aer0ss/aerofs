"""
Class used for forwarding messages from a VM host to a KVM.
"""

import inception.common.forward

class VmHostForwarder(inception.common.forward.Forwarder):

    def __init__(self, vmhost_client):
        self._vmhost_client = vmhost_client

    # Required override (identifier => vmHostId).
    def forward(self, vmHostId, bytes):
        return self._vmhost_client.send_msg_to_vm_host(vmHostId, bytes).data
