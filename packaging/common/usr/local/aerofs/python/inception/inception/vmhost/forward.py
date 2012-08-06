"""
Class used for forwarding messages from the VM hosts manager to a VM host.
"""

import inception.common.forward

class KvmForwarder(inception.common.forward.Forwarder):

    def __init__(self, vmhost_client):
        self._vmhost_client = vmhost_client

    # Required override (identifier => serviceName).
    def forward(self, serviceName, bytes):
        return self._vmhost_client.send_msg_to_kvm(serviceName, bytes).data
