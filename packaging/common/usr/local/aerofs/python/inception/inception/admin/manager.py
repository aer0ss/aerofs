"""
VM hosts manager server application. Accepts VM host connections and manages
them, providing access to local clients using the admin.proto interface.

The architecture here is similar to that of the KVMs's manager app. Therefore,
where possible, common classes have been used to avoid duplicated functionality
(see common/server.py).
"""

import ssl
import time
import select
import socket
import logging
import threading
import inception
import inception.gen
import inception.gen.admin_pb2
import inception.gen.vmhost_pb2
import inception.common
import inception.common.server
import inception.common.network
import inception.common.impl
import inception.common.connmgr

"""
Class to hold VM host sockets and periodically remove stale ones.
"""
class VmHostsConnManager(inception.common.connmgr.ConnManager):

    def __init__(self, logger):
        inception.common.connmgr.ConnManager.__init__(self, logger)

    def detect_name(self, sock):
        vclient = inception.gen.vmhost_pb2.VmHostServiceRpcStub(
                inception.common.impl.NetworkImpl(sock))

        try:
            return vclient.get_vm_host_id().vmHostId
        except inception.common.impl.NetworkImplException, e:
            raise inception.common.connmgr.ConnManagerException(str(e))

"""
Main class for the vmhosts manager server application.
"""

class VmHostsManager(inception.gen.admin_pb2.AdminPanelService):

    # Should be much more than enough.
    CONST_NUM_VMHOST_CONNECTIONS = 20

    # The amount of time we will wait before we kill a client connection.
    CONST_SELECT_SECONDS = 1.0

    def __init__(self,
            logger,
            vmhosts_clt_port,
            vmhosts_srv_port,
            cert_key_file):
        self._logger = logger
        self._vmhosts_clt_port = vmhosts_clt_port
        self._vmhosts_srv_port = vmhosts_srv_port
        self._cert_key_file = cert_key_file

        self._shutdown = False
        self._reactor = inception.gen.admin_pb2.AdminPanelServiceReactor(self)

        self._vmhost_conn_manager = None
        self._vmhosts_listener = None
        self._client_listener = None

    def shutdown(self):
        self._logger.info('Got signal, stopping threads.')
        self._shutdown = True

        for t in [self._client_listener, self._vmhosts_listener,
                self._vmhost_conn_manager]:
            if t != None:
                t.shutdown()
                t.join()

    # ConnListener callback. Client connections are short lived. Therefore, we
    # will simply serve them in series.
    def new_socket(self, sock, addr):
        self._logger.info('Got new client connection %s', addr)

        while self._shutdown == False:
            input_ready,output_ready,error_ready = select.select(
                    [sock], [], [],
                    VmHostsManager.CONST_SELECT_SECONDS)

            if len(input_ready) == 0:
                self._logger.debug('Removing client socket (broken client).')
                sock.close()
                break

            try:
                received_body = inception.common.network.receive_message(sock)
            except socket.error, msg:
                self._logger.debug('Removing client socket.')
                sock.close()
                break

            # Call the reactor (which wraps in the callbacks below) and handle
            # the request).
            reply = self._reactor.react(received_body)
            inception.common.network.send_message(sock, reply)

    def start(self):
        self._logger.info('Starting threads.')

        self._vmhost_conn_manager = VmHostsConnManager(self._logger);
        self._vmhost_conn_manager.start()

        self._vmhosts_listener = inception.common.server.ConnListener(
                self._logger,
                VmHostsManager.CONST_NUM_VMHOST_CONNECTIONS,
                "vmhosts",
                self._vmhost_conn_manager,
                self._vmhosts_srv_port,
                self._cert_key_file)
        self._vmhosts_listener.start()

        self._client_listener = inception.common.server.ConnListener(
                self._logger,
                VmHostsManager.CONST_NUM_VMHOST_CONNECTIONS,
                "client",
                self,
                self._vmhosts_clt_port,
                self._cert_key_file)
        self._client_listener.start()

    def encode_error(self, msg):
        # Be sure to release any locks we might have taken and been unable to
        # release because of an exception.
        self._vmhost_conn_manager.release()

        # Encode the error.
        reply = inception.gen.kvm_pb2.ErrorReply()
        reply.errorMessage = str(msg)
        return reply

    """
    Required AdminPanelService callbacks.
    """

    def send_msg_to_vm_host(self, call):
        self._logger.info('%s: forwarding message.', call.vmHostId)
        self._vmhost_conn_manager.acquire()

        try:
            sock = self._vmhost_conn_manager.get_socket(call.vmHostId)
        except KeyError, e:
            raise Exception('No such VM host.')

        # Send and receive the message from the VM host.
        header = inception.common.network.send_message(sock, call.data)
        received_body = inception.common.network.receive_message(sock)

        reply = inception.gen.admin_pb2.SendMsgToVmHostReply()
        reply.data = received_body

        self._vmhost_conn_manager.release()
        return reply

    def get_vm_host_ids_list(self):
        self._logger.info('get_vm_host_ids called.')
        self._vmhost_conn_manager.acquire()

        reply = inception.gen.admin_pb2.GetVmHostIdsListReply()
        for name in self._vmhost_conn_manager.get_names_list():
            reply.vmHostIds.append(name)

        self._vmhost_conn_manager.release()
        return reply
