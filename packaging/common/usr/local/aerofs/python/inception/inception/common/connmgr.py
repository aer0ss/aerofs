"""
Class that manages active connections and will periodically check for stale
connections and close them.
"""

import abc
import ssl
import time
import select
import socket
import logging
import threading

class ConnManagerException(Exception):

    def __init__(self, error_message):
        self._error_message = error_message

    def error_message(self):
        return self._error_message

class ConnManager(threading.Thread):
    __metaclass__ = abc.ABCMeta

    def __init__(self, logger):
        self._logger = logger
        self._lock = threading.Lock()
        self._sock_list = []

        self._fileno_to_name = {}
        self._name_to_socket = {}

        # Thread startup.
        self._shutdown = False
        threading.Thread.__init__(self, name='ConnManager')

    def shutdown(self):
        self._shutdown = True

    # Throw a conn manager exception if you can't detect the name.
    @abc.abstractmethod
    def detect_name(self, sock):
        raise Exception()

    def new_socket(self, sock, addr):
        try:
            name = self.detect_name(sock)
        except ssl.SSLError, e:
            self._logger.warning('New connection ssl error: %s', str(e))
            sock.close()
            return
        except ConnManagerException, e:
            self._logger.warning('New connection error: %s', e.error_message())
            return

        # Need to be able to retrieve the name from the socket handle, and vice
        # versa.
        self.acquire()
        self._fileno_to_name[sock.fileno()] = name
        self._name_to_socket[name] = sock
        self._sock_list.append(sock)
        self.release()

        self._logger.info('Got new attacher %s (%s)', str(addr), name)

    def get_socket(self, name):
        return self._name_to_socket[name]

    # This class is pretty dumb. Expose our lock to the user of this class.
    def acquire(self):
        self._lock.acquire()

    def release(self):
        if self._lock.locked():
            self._lock.release()

    def get_names_list(self):
        return self._name_to_socket.keys()

    def run(self):
        self._logger.info('ConnManager thread started.')

        while self._shutdown == False:
            # Periodically check for stale connections and close them. The lock
            # is shared with the user of this class. Whenever the user access a
            # socket, it must take the lock until it is done with the socket,
            # i.e. until it has received a response. Therefore we will never see
            # bytes to be read on these sockets unless the connection has been
            # severed (in the case of the severed connection, select() will tell
            # us there are bytes to read and if we tried to read we would get 0
            # bytes - so don't bother trying, just close the socket
            # immediately).
            time.sleep(1)
            self.acquire()

            input_ready,output_ready,error_ready = select.select(
                    self._sock_list, [], [], 0.0)

            for s in input_ready:
                # This connection has been closed by the client, has been
                # servered or there has been some error, so close it.
                self._logger.debug(
                        'Remove stale connection (fn=%i).', s.fileno())

                # Remove entries from socket list and hashes.
                name = self._fileno_to_name[s.fileno()]
                del self._name_to_socket[name]
                del self._fileno_to_name[s.fileno()]
                self._sock_list.remove(s)

                s.close()

            self.release()

        self._logger.info('ConnManager thread finished.')
