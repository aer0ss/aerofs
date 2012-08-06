"""
This module contains everything related to the VM host manager application.

In order to service requests, the RequestServicer owns the KVM conn manager, and
when it needs access to the KVMs it takes the conn manager's lock and takes
control of the KVM sockets until it is done (the scheme here probably could have
been optimized, but this layout is simple and was selected because this isn't a
high bandwidth application anyway).

It is important to understand who is connecting to who (i.e. who is the
connecting client) and who makes requests (i.e. who is the request client),
because they are different.

A diagram to explain (A = admin panel, V = VM host, K = KVM):

    A
   ^ |
   | |
   | v
    V
   ^ |
   | |
   | v
    K

The left side represents who initiates the TCP connection, and right side
represents who makes the protobuf requests. The KVM connects to the VM host, but
the VM host makes the requests to the KVM. Similarly, the VM host connects to
the admin panel, but the admin panel makes the requests to the VM host.

Why? Because the admin panel doesn't know the IP of the VM host and the VM host
doesn't know the IP of the KVMs during the bootstrap phase. Hence this "client/
server irregularity". Also, this structure is self-organizing; move something
around, plug it back it, and it will just work.

We can, of course, have multiple KVMs attach to a VM host and multiple VM hosts
attach to the admin panel (not shown in this diagram).
"""

import ssl
import time
import select
import struct
import socket
import logging
import threading
import subprocess
import inception.common.exp
import inception.common.network
import inception.common.server
import inception.common.impl
import inception.vmhost.qemu
import inception.gen.kvm_pb2
import inception.gen.vmhost_pb2
import inception.common.connmgr

"""
--------------------------------------------------------------------------------
Class that manages active KVM connections.
--------------------------------------------------------------------------------
"""

class KvmsConnManager(inception.common.connmgr.ConnManager):

    def __init__(self, logger):
        inception.common.connmgr.ConnManager.__init__(self, logger)

    def detect_name(self, sock):
        kclient = inception.gen.kvm_pb2.KvmServiceRpcStub(
                inception.common.impl.NetworkImpl(sock))

        try:
            return kclient.get_service_name().serviceName
        except inception.common.impl.NetworkImplException, e:
            raise inception.common.connmgr.ConnManagerException(str(e))

"""
--------------------------------------------------------------------------------
Class that services requests from the shell clients and the admin panel.
--------------------------------------------------------------------------------
"""

class RequestServicer(threading.Thread, inception.gen.vmhost_pb2.VmHostService):

    # The number of seconds select should wait.
    CONST_SELECT_SECONDS = 0.1

    def __init__(self, logger, kvm_conn_manager, vmhost_id_file, autostart_dir,
            kvm_img_dir):
        self._logger = logger
        self._kvm_conn_manager = kvm_conn_manager
        self._vmhost_id_file = vmhost_id_file
        self._autostart_dir = autostart_dir
        self._kvm_img_dir = kvm_img_dir

        self._requesters = []
        self._rm_sock_listeners = []

        self._reactor = inception.gen.vmhost_pb2.VmHostServiceReactor(self)

        # Thread startup.
        self._shutdown = False
        self._reboot = False
        threading.Thread.__init__(self, name='RequestServicer')

    def shutdown(self):
        self._shutdown = True

    def new_socket(self, clt_sock, clt_addr):
        self._logger.info('Got new client %s.', str(clt_addr))
        self._requesters.append(clt_sock)

    # Let users know when certain client connections have been severed. Listener
    # must implement notify_rm_sock(self, fileno)
    def add_rm_sock_listener(self, listener):
        self._rm_sock_listeners.append(listener)

    def rm_sock(self, sock):
        # Notify listeners (i.e. the admin connector) or removed sockets.
        for l in self._rm_sock_listeners:
            l.notify_rm_sock(sock.fileno())

        sock.close()
        self._requesters.remove(sock)

    def run(self):
        self._logger.info('RequestServicer thread started.')

        while self._shutdown == False:
            input_ready,output_ready,error_ready = select.select(
                    self._requesters, [], [],
                    RequestServicer.CONST_SELECT_SECONDS)

            for s in input_ready:
                try:
                   received_body = inception.common.network.receive_message(s)
                except socket.error, msg:
                    self._logger.debug('Removing client socket (fn=%i)',
                            s.fileno())
                    self.rm_sock(s)
                    continue

                # Call the reactor (which wraps in the callbacks below) and
                # handle the request).
                reply = self._reactor.react(received_body)
                header = inception.common.network.send_message(s, reply)

                if self._reboot:
                    try:
                        subprocess.check_output(['reboot'])
                        self._shutdown = True
                    except subprocess.CalledProcessError, e:
                        self._logger.error('Counld not reboot (errorCode=%i)',
                                e.returncode)

        self._logger.info('RequestServicer thread finished.')

    def encode_error(self, msg):
        # Be sure to release any locks we might have taken and been unable to
        # release because of an exception.
        self._kvm_conn_manager.release()

        # Encode the error.
        reply = inception.gen.kvm_pb2.ErrorReply()
        reply.errorMessage = str(msg)
        return reply

    """
    Required VmHostService callbacks and some helpers.
    """

    def send_msg_to_kvm(self, call):
        self._logger.debug('%s: forwarding message.', call.serviceName)
        self._kvm_conn_manager.acquire()

        try:
            sock = self._kvm_conn_manager.get_socket(call.serviceName)
        except KeyError, e:
            raise Exception('service unavailable.')

        # Send and receive the message from the KVM.
        header = inception.common.network.send_message(sock, call.data)
        received_body = inception.common.network.receive_message(sock)

        # And create the reply bytes.
        reply = inception.gen.vmhost_pb2.SendMsgToKvmReply()
        reply.data = received_body

        self._kvm_conn_manager.release()
        return reply

    def do_reboot(self):
        self._logger.debug('do_reboot called')

        # Do the reboot after we send the return packet.
        self._reboot = True

        reply = inception.gen.kvm_pb2.ResultReply()
        reply.result = inception.gen.kvm_pb2.ResultReply.SUCCESS
        return reply

    def get_vm_host_id(self):
        self._logger.debug('get_vm_host_id called')

        # This file is generated during bootstrap.
        fh = open(self._vmhost_id_file)
        vmhostid = fh.readline().strip()
        reply = inception.gen.vmhost_pb2.GetVmHostIdReply()
        reply.vmHostId = vmhostid
        return reply

    # A little helper to eliminate duplicated code for enable/disable service.
    def service_helper(self, call, enable_disable, callback):
        self._logger.debug('%s: %s service.', call.serviceName, enable_disable)
        kvm = inception.vmhost.qemu.KernelVirtualMachine(self._autostart_dir,
                call.serviceName)

        reply = inception.gen.kvm_pb2.ResultReply()
        code = callback(kvm)

        if code == 0:
            reply.result = inception.gen.kvm_pb2.ResultReply.SUCCESS
        else:
            reply.result = inception.gen.kvm_pb2.ResultReply.FAILURE
            reply.message = 'Got error code ' + str(code)

        return reply

    @staticmethod
    def disable_service_callback(kvm):
        return kvm.disable_service()
    def disable_service(self, call):
        return self.service_helper(call, "disable",
                RequestServicer.disable_service_callback)

    @staticmethod
    def enable_service_callback(kvm):
        return kvm.enable_service()
    def enable_service(self, call):
        return self.service_helper(call, "enable",
                RequestServicer.enable_service_callback)

    def get_services_list(self):
        self._logger.debug('get_services_list called.')

        reply = inception.gen.vmhost_pb2.GetServicesListReply()
        self._kvm_conn_manager.acquire()

        # Go through all connected KVMs.
        for name in self._kvm_conn_manager.get_names_list():
            reply.serviceNames.append(name)
            reply.statuses.append(inception.gen.vmhost_pb2.GetServicesListReply.CONNECTED)

        self._kvm_conn_manager.release()

        # For the disabled VM's.
        domains = inception.vmhost.qemu.get_kvm_domain_list(self._kvm_img_dir)

        for d in domains:
            if d not in reply.serviceNames:
                # This VM is offline, update the reply accordingly.
                reply.serviceNames.append(d)

                kvm = inception.vmhost.qemu.KernelVirtualMachine(
                        self._autostart_dir, d)

                if kvm.is_autostart_enabled():
                    reply.statuses.append(
                        inception.gen.vmhost_pb2.GetServicesListReply.DISCONNECTED)
                else:
                    reply.statuses.append(
                        inception.gen.vmhost_pb2.GetServicesListReply.DISABLED)

        return reply

"""
--------------------------------------------------------------------------------
Class that connects to the admin panel.
--------------------------------------------------------------------------------
"""

class AdminConnector(threading.Thread, inception.common.exp.AwareSleeper):

    # Timeout for connect(), etc.
    CONST_SOCKET_TIMEOUT = 1.0

    def __init__(self, logger, vmhosts_srv_port, adm_addr_file,
            request_servicer, cert_key_file):
        self._logger = logger
        self._vmhosts_srv_port = vmhosts_srv_port
        self._adm_addr_file = adm_addr_file
        self._request_servicer = request_servicer
        self._cert_key_file = cert_key_file

        # Register as a removed socket listener to the RequestServicer.
        self._request_servicer.add_rm_sock_listener(self)

        # The fileno of the admin panel connection.
        self._admin_fileno = 0

        # Thread init.
        self._shutdown = False
        threading.Thread.__init__(self, name='AdminConnector')
        inception.common.exp.AwareSleeper.__init__(self)

    def shutdown(self):
        self._shutdown = True

    def notify_rm_sock(self, fileno):
        if self._admin_fileno == fileno:
            self._admin_fileno = 0

    def run(self):
        self._logger.info('AdminConnector thread started.')

        # Connect and service loop.
        while self._shutdown == False:

            # Connect loop.
            timer = inception.common.exp.ExponentialRetryTimer()

            while self._shutdown == False:
                self.aware_sleep(timer.next_time())

                try:
                    addr = inception.common.network.read_addr_file(
                            self._adm_addr_file,
                            self._vmhosts_srv_port)

                    # Set up the socket.
                    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

                    sock = ssl.wrap_socket(sock,
                            ca_certs=self._cert_key_file,
                            certfile=self._cert_key_file,
                            keyfile=self._cert_key_file,
                            cert_reqs=ssl.CERT_REQUIRED)

                    sock.settimeout(AdminConnector.CONST_SOCKET_TIMEOUT)
                    sock.connect((addr))

                except ssl.SSLError, msg:
                    self._logger.warning(
                            'SSL error on admin connect (msg=%s)', str(msg))
                    continue
                except socket.error, msg:
                    self._logger.warning(
                            'Cannot connect to admin panel (msg=%s)', str(msg))
                    continue
                except IOError, msg:
                    self._logger.error(
                            'Unable to read admin addr file (msg=%s)', str(msg))
                    continue

                break

            self._logger.info('Successfully connected to admin panel %s.', addr)
            self._admin_fileno = sock.fileno()
            self._request_servicer.new_socket(sock, addr)

            # Wait for callback of removed fileno or shutdown.
            while self._shutdown == False and self._admin_fileno != 0:
                time.sleep(1)

        self._logger.info('AdminConnector thread finished.')

"""
--------------------------------------------------------------------------------
This is the class that will be used by the public.
--------------------------------------------------------------------------------
"""
class KvmsManager(object):

    # The max number of KVMs we can connect to at one point.
    CONST_NUM_KVM_CONNECTIONS = 20

    # The max number of clients we can connect to at one point.
    CONST_NUM_CLT_CONNECTIONS = 5

    def __init__(self,
            logger,
            kvms_clt_port,
            kvms_srv_port,
            vmhosts_srv_port,
            adm_addr_file,
            vmhost_id_file,
            autostart_dir,
            kvm_img_dir,
            cert_key_file):
        # Configuration parameters. Could load these from constants directly but
        # this is more testable.
        self._logger = logger
        self._kvms_clt_port = kvms_clt_port
        self._kvms_srv_port = kvms_srv_port
        self._vmhosts_srv_port = vmhosts_srv_port
        self._adm_addr_file = adm_addr_file
        self._vmhost_id_file = vmhost_id_file
        self._autostart_dir = autostart_dir
        self._kvm_img_dir = kvm_img_dir
        self._cert_key_file = cert_key_file

        self._kvm_conn_manager = None
        self._request_servicer = None
        self._kvm_conn_listener = None
        self._clt_conn_listener = None
        self._admin_connector = None

    def shutdown(self):
        self._logger.info('Got signal, stopping threads.')

        # Stop all the threads we created. Watch the order for race conditions.
        for t in [self._admin_connector, self._clt_conn_listener,
                self._kvm_conn_listener, self._request_servicer,
                self._kvm_conn_manager]:
            if t != None:
                t.shutdown()
                t.join()

    def start(self):
        self._logger.info('Starting threads.')

        # KVM Connection Manager
        self._kvm_conn_manager = KvmsConnManager(self._logger)
        self._kvm_conn_manager.start()

        # Request Servicer
        self._request_servicer = RequestServicer(self._logger,
                self._kvm_conn_manager,
                self._vmhost_id_file,
                self._autostart_dir,
                self._kvm_img_dir)
        self._request_servicer.start()

        # Connection Listeners
        self._kvm_conn_listener = inception.common.server.ConnListener(
                self._logger,
                KvmsManager.CONST_NUM_KVM_CONNECTIONS,
                'kvm',
                self._kvm_conn_manager,
                self._kvms_srv_port,
                self._cert_key_file)
        self._kvm_conn_listener.start()
        self._clt_conn_listener = inception.common.server.ConnListener(
                self._logger,
                KvmsManager.CONST_NUM_CLT_CONNECTIONS,
                'client',
                self._request_servicer,
                self._kvms_clt_port,
                self._cert_key_file)
        self._clt_conn_listener.start()

        # Admin Panel Connector
        self._admin_connector = AdminConnector(self._logger,
                self._vmhosts_srv_port,
                self._adm_addr_file, self._request_servicer,
                self._cert_key_file)
        self._admin_connector.start()
