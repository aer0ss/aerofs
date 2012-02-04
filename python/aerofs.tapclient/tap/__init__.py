'''
TAP is a testing utility for use with the Transport layer of the
AeroFS Daemon. It communicates with an AeroFS Daemon process
via Protobuf-based RPC calls. 

The main submodule of interest is the 'transport' module, which
offers a 'connect' method. Calling 'connect' returns the object
which exposes all of TAP's methods. Just do:

    from tap import transport

    service = transport.connect(('somehost', PORT_NUM))
    stream = service.beginStream(...)

Any errors that may occur will be raised as exceptions from the
offending method.

All mention of DID and SID refer to a 'uuid' object which can
be generated via the builtin python 'uuid' module
'''

__all__ = ["tap_pb2", "common_pb2", "transport_pb2", "transport"]