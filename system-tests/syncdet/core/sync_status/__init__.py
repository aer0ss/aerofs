"""
This package contains two-box system tests for sync status.
The first actor MUST be a Team Server configured to use LINKED storage.
"""

__author__ = 'jaredk'

import time

from aerofs_common import param
from aerofs_ritual.gen import path_status_pb2

from syncdet.case.assertion import assertTrue


def assert_synced(r, path):
    status = r.get_path_status(path)
    assertTrue(status.status[0].sync == path_status_pb2.PBPathStatus.Sync.Value("IN_SYNC"))


def assert_not_synced(r, path):
    status = r.get_path_status(path)
    assertTrue(status.status[0].sync == path_status_pb2.PBPathStatus.Sync.Value("UNKNOWN"))


def wait_synced(r, path, polls=1000):
    status = None
    for i in range(polls):
        status = r.get_path_status(path)
        if status.status[0].sync == path_status_pb2.PBPathStatus.Sync.Value("IN_SYNC"):
            break
        else:
            time.sleep(param.POLLING_INTERVAL)
    assertTrue(status.status[0].sync == path_status_pb2.PBPathStatus.Sync.Value("IN_SYNC"))


def wait_not_synced(r, path, polls=1000):
    status = None
    for i in range(polls):
        status = r.get_path_status(path)
        if status.status[0].sync == path_status_pb2.PBPathStatus.Sync.Value("UNKNOWN"):
            break
        else:
            time.sleep(param.POLLING_INTERVAL)
    assertTrue(status.status[0].sync == path_status_pb2.PBPathStatus.Sync.Value("UNKNOWN"))
