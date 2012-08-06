"""
This module contains everything related to the services manager application.
"""

import ssl
import time
import socket
import select
import threading
import inception.common.exp
import inception.common.impl
import inception.common.network
import inception.kvm.config
import inception.gen.kvm_pb2
import inception.kvm.upstart

class ServicesManager(threading.Thread,
        inception.gen.kvm_pb2.KvmService,
        inception.common.exp.AwareSleeper):

    # Timeout for connect and other socket operations.
    CONST_SOCKET_TIMEOUT = 1.0

    # Timeout for select()
    CONST_SELECT_SECONDS = 1.0

    def __init__(self,
            logger,
            kvms_srv_port,
            vmhost_addr_file,
            kvm_service_file,
            cert_key_file):
        self._logger = logger
        self._kvms_srv_port = kvms_srv_port
        self._vmhost_addr_file = vmhost_addr_file
        self._kvm_service_file = kvm_service_file
        self._cert_key_file = cert_key_file

        self._sock = None
        self._reactor = inception.gen.kvm_pb2.KvmServiceReactor(self)

        # Parent init.
        threading.Thread.__init__(self, name='ServicesManager')
        inception.common.exp.AwareSleeper.__init__(self)

    def shutdown(self):
        self._logger.info('Got signal, stopping service thread.')
        self._shutdown = True
        self.join()

    """
    Connect to the VM host manager (or enter retry loop) and communicate
    forever.
    """
    def run(self):
        # Connect and service loop.
        while self._shutdown == False:

            # Connect loop.
            timer = inception.common.exp.ExponentialRetryTimer()

            while self._shutdown == False:
                self.aware_sleep(timer.next_time())

                try:
                    addr = inception.common.network.read_addr_file(
                            self._vmhost_addr_file,
                            self._kvms_srv_port)

                    # Set up the socket.
                    self._sock = socket.socket(socket.AF_INET,
                            socket.SOCK_STREAM)

                    self._sock = ssl.wrap_socket(self._sock,
                            ca_certs=self._cert_key_file,
                            certfile=self._cert_key_file,
                            keyfile=self._cert_key_file,
                            cert_reqs=ssl.CERT_REQUIRED)

                    self._sock.settimeout(ServicesManager.CONST_SOCKET_TIMEOUT)
                    self._sock.connect((addr))

                except ssl.SSLError, msg:
                    self._logger.warning(
                            'SSL connection error (msg=%s)', str(msg))
                    continue
                except socket.error, msg:
                    self._logger.warning(
                            'Cannot connect to VM host (msg=%s)', str(msg))
                    continue
                except IOError, msg:
                    self._logger.error(
                            'Unable to read VM addr file (msg=%s)', str(msg))
                    continue

                break

            self._logger.info('Successfully connected to VM host.')

            # Service loop
            while self._shutdown == False:
                input_ready,output_ready,error_ready = select.select(
                        [self._sock], [], [],
                        ServicesManager.CONST_SELECT_SECONDS)

                if len(input_ready) == 0:
                    continue

                s = input_ready[0]
                try:
                    received_body = inception.common.network.receive_message(s)
                except socket.error, msg:
                    self._logger.warning('Lost VM host connection')
                    break

                reply = self._reactor.react(received_body)
                header = inception.common.network.send_message(s, reply)

    """
    Required KvmService callbacks
    """

    def encode_error(self, msg):
        reply = inception.gen.kvm_pb2.ErrorReply()
        reply.errorMessage = str(msg)
        return reply

    def get_service_name(self):
        self._logger.debug('get_service_name called.')

        # This file is created during the bootstrap phase.
        fh = open(self._kvm_service_file)
        service = fh.readline().strip()
        reply = inception.gen.kvm_pb2.GetServiceNameReply()
        reply.serviceName = service
        return reply

    def get_status(self):
        self._logger.debug('get_status called.')

        # Extract current values from the interface file.
        reply = inception.kvm.config.read_etc_network_interfaces()

        if inception.kvm.upstart.count_of_bad_services() == 0:
            reply.status = inception.gen.kvm_pb2.GetStatusReply.GOOD
        else:
            reply.status = inception.gen.kvm_pb2.GetStatusReply.BAD

        reply.hostname = socket.gethostname()
        return reply

    def configure_hostname(self, call):
        self._logger.debug('hostname: configure.')

        reply = inception.gen.kvm_pb2.ResultReply()
        reply.result = inception.gen.kvm_pb2.ResultReply.FAILURE

        if inception.kvm.config.configure_hostname(call.hostname):
            reply.result = inception.gen.kvm_pb2.ResultReply.SUCCESS

        return reply

    def configure_network(self, call):
        self._logger.debug('network: configure.')

        reply = inception.gen.kvm_pb2.ResultReply()
        reply.result = inception.gen.kvm_pb2.ResultReply.FAILURE

        if call.networkType == inception.gen.kvm_pb2.ConfigureNetworkCall.DHCP:
            if len(call.address) > 0 or len(call.netmask) > 0 or \
                    len(call.network) > 0 or len(call.broadcast) > 0 or \
                    len(call.gateway) > 0:
                raise Exception('DHCP cannot take static args.')

            # DHCP
            if inception.kvm.config.configure_network_dhcp(call.dns):
                reply.result = inception.gen.kvm_pb2.ResultReply.SUCCESS
        else:
            if len(call.address) == 0:
                raise Exception('STATIC requires an IP address.')

            # Static
            if inception.kvm.config.configure_network_static(
                    call.address,
                    call.netmask,
                    call.network,
                    call.broadcast,
                    call.gateway,
                    call.dns):
                reply.result = inception.gen.kvm_pb2.ResultReply.SUCCESS

        return reply
