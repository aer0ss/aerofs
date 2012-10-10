import inception.common.forward
import inception.vmhost.forward
import inception.gen.vmhost_pb2

class ProtoClientGenerator(object):
    """
    Class to simplify client access to vmhost proto interface.
    """

    def __init__(self, vimpl):
        self._vimpl = vimpl
        self._vclient = inception.gen.vmhost_pb2.VmHostServiceRpcStub(vimpl)
        self._kclients = {}

    def vclient(self):
        return self._vclient

    def kclient(self, service_name):
        if service_name in self._kclients.keys():
            return self._kclients[service_name]
        else:
            kimpl = inception.common.forward.ForwardingImpl(
                    service_name,
                    inception.vmhost.forward.KvmForwarder(self._vclient))
            kclient = inception.gen.kvm_pb2.KvmServiceRpcStub(kimpl)
            self._kclients[service_name] = kclient

            return kclient