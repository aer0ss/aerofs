import logging
import random
import time

from .client import Client
from .fs_operator import FSOperator


logger = logging.getLogger(__name__)


class Runner(object):
    def __init__(self, users, devices, operations, delay):
        fs_operator = FSOperator()

        self.clients = list()
        for user in xrange(users):
            for device in xrange(devices):
                self.clients.append(Client(fs_operator, user, device))

        self.operations = operations
        self.delay = delay

        self.ops = 0
        self.time = 0

    def initialize(self, objects):
        for client in self.clients:
            client.initialize(objects, self.delay)

        logger.debug('built client approots')

    def start(self):
        start_at = time.time()
        for _ in xrange(self.operations):
            random.shuffle(self.clients)

            for client in self.clients:
                client.operate()
                self.ops += 1
                time.sleep(self.delay)
        self.time = time.time() - start_at

    def log_stats(self):
        logger.debug('executed %s operations in %ss.', self.ops, self.time)
        logger.debug('calculated ops/second: %s', self.ops / self.time)
