from lib.files import instance_unique_path, wait_file
from lib.app.cfg import get_cfg
import re
import os

FILE_SIZE = 1024 * 1024 * 1024
REGEX_PATTERN = "processed:(\d+) time:(\d+)"
MIN_FILE_SIZE = 1000


def put():
    print 'put {0}'.format(instance_unique_path())
    with open(instance_unique_path(), 'wb') as f:
        f.seek(FILE_SIZE - 1)
        f.write('\0')
        f.flush()


def get():
    print 'get {0}'.format(instance_unique_path())
    wait_file(instance_unique_path())


def _get_speeds_from_daemon_log():
    with open(os.path.join(get_cfg().get_rtroot(), 'daemon.log')) as f:
        file_string = f.read()

    matches = re.finditer(REGEX_PATTERN, file_string)
    return [float(m.group(1)) / float(m.group(2)) for m in matches if float(m.group(2)) >= MIN_FILE_SIZE]


def get_average_transport_speed():
    speeds = _get_speeds_from_daemon_log()
    try:
        avg_speed = sum(speeds) / len(speeds)
    except ZeroDivisionError:
        print "Error: No lines matched the regex pattern"
        return None
    else:
        return avg_speed


def get_last_transport_speed():
    speeds = _get_speeds_from_daemon_log()
    try:
        return speeds[-1]
    except IndexError:
        print "Error: No lines matched the regex pattern"
        return None