"""
Special class for forwarding protobuf messages over an inception channel.
"""

import abc
import inception.common.impl

class Forwarder(object):
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def forward(self, bytes):
        raise Exception()

class ForwardingImpl(inception.common.impl.NetworkImpl):
    """
    Should be passed to the rpc stub to create a new client (i.e. "service") instance.
    """

    def __init__(self, identifier, forwarder):
        self._identifier = identifier
        self._forwarder = forwarder

    def do_rpc(self, bytes):
        return self._forwarder.forward(self._identifier, bytes)
