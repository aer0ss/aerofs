import logging

from .fs_operator import get_device_count
from .fs_operator import get_user_count
from .runner import Runner


logger = logging.getLogger('fuzzer')
logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s - %(name)s - %(levelname)s - '
                           '%(message)s')


def fuzzer(objects, operations, delay):
    users = get_user_count()
    devices = get_device_count()
    logger.debug('found %s users and %s devices', users, devices)

    runner = Runner(users, devices, operations, delay)
    runner.initialize(objects)
    runner.start()
    runner.log_stats()
