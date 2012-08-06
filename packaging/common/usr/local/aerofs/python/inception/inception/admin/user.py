"""
Class to simplify client access to admin proto interface.
"""

import inception.common.forward
import inception.admin.forward
import inception.gen.admin_pb2
import inception.vmhost.user

class ProtoClientGenerator(object):

    def __init__(self, aimpl):
        self._aimpl = aimpl
        self._aclient = inception.gen.admin_pb2.AdminPanelServiceRpcStub(aimpl)
        self._vclients = {}
        self._vgenerators = {}

    # Cleanly dispose of the TCP socket.
    def disconnect(self):
        self._aimpl.disconnect()

    # Internal use only; generate a VM host implementation instance.
    def _vimpl(self, vm_host_id):
        vimpl = inception.common.forward.ForwardingImpl(
                vm_host_id,
                inception.admin.forward.VmHostForwarder(self._aclient))
        return vimpl

    # Get the admin.proto client.
    def aclient(self):
        return self._aclient

    # Get a vmhost.proto generator instance (used, in turn, to access kvm.proto)
    def vgenerator(self, vm_host_id):
        if vm_host_id in self._vgenerators.keys():
            return self._vgenerators[vm_host_id]
        else:
            vimpl = self._vimpl(vm_host_id)
            vgenerator = inception.vmhost.user.ProtoClientGenerator(vimpl)

            self._vgenerators[vm_host_id] = vgenerator

            return vgenerator

    # Get a vmhost.proto client.
    def vclient(self, vm_host_id):
        if vm_host_id in self._vclients.keys():
            return self._vclients[vm_host_id]
        else:
            vimpl = self._vimpl(vm_host_id)
            vclient = inception.gen.vmhost_pb2.VmHostServiceRpcStub(vimpl)

            self._vclients[vm_host_id] = vclient

            return vclient
