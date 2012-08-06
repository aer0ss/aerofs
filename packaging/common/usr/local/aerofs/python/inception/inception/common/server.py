"""
Common server classes used by the VM hosts manager and the KVMs managers.
"""

import ssl
import select
import socket
import logging
import threading

"""
--------------------------------------------------------------------------------
Class that listens for client connections and notifies a listener when a new
connection has been made.
--------------------------------------------------------------------------------
"""

class ConnListener(threading.Thread):

    # The number of seconds select should wait.
    CONST_SELECT_SECONDS = 1.0

    # Socket operatiosn timeout.
    CONST_SOCKET_TIMEOUT = 1.0

    # The listener must implement the new_socket(sock, addr) method.
    def __init__(self, logger, max_conns, name, listener, port, cert_key_file):
        self._logger = logger
        self._max_conns = max_conns
        self._name = name
        self._listener = listener
        self._port = port
        self._cert_key_file = cert_key_file

        # Thread startup.
        self._shutdown = False
        thread_name = 'ConnListener (' + self._name + ')'
        threading.Thread.__init__(self, name=thread_name)

    def shutdown(self):
        self._shutdown = True

    def run(self):
        self._logger.info('ConnListener thread started (%s).', self._name)

        # Start listening on the required port.
        addr = ('', self._port)
        listen_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listen_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listen_sock.bind((addr))
        listen_sock.listen(self._max_conns)

        while self._shutdown == False:
            input_ready,output_ready,error_ready = select.select(
                    [listen_sock], [], [], ConnListener.CONST_SELECT_SECONDS)

            for s in input_ready:
                try:
                    sock,addr = s.accept()
                    sock.settimeout(ConnListener.CONST_SOCKET_TIMEOUT)

                    sock = ssl.wrap_socket(sock,
                            server_side=True,
                            ca_certs=self._cert_key_file,
                            certfile=self._cert_key_file,
                            keyfile=self._cert_key_file,
                            cert_reqs=ssl.CERT_REQUIRED)
                except socket.error, msg:
                    self._logger.warning(
                            'Listener socket error (msg=%s)', str(msg))
                    sock.close()
                    continue
                except ssl.SSLError, msg:
                    self._logger.warning(
                            'Listener ssl error (msg=%s)', str(msg))
                    sock.close()
                    continue

                self._listener.new_socket(sock, addr)

        listen_sock.close()
        self._logger.info('ConnListener thread finished (%s).', self._name)
