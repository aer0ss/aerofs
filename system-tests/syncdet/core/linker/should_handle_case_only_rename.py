"""
On case-insensitive filesystems, post-Phoenix, with fine granularity notifier,
a device receiving two consecutive case-only renames not being buffered would
generate consecutive notifications. Because of case-insensitivity, the first
ones would not be correctly ignored as the source file would "exist", which
would lead to an endless loop of spurious renames being propagated across
all devices, amplified exponentially if multiple devices meet the required
condition to trigger it.

This was seen at BB between a group of a dozen Windows devices.
"""

import os

from lib import ritual
from lib.app.aerofs_proc import stop_all, run_ui
from lib.files import instance_path

from syncdet.case.sync import sync


def renamer():
    os.makedirs(instance_path("foo"))
    sync("stopped")
    r = ritual.connect()
    r.wait_path(instance_path("foo"))

    # successive case-only renames
    os.rename(instance_path("foo"), instance_path("Foo"))
    r.wait_path(instance_path("Foo"))
    os.rename(instance_path("Foo"), instance_path("foo"))
    r.wait_path(instance_path("foo"))

    sync("renamed")


def spectator():
    r = ritual.connect()
    r.wait_path(instance_path("foo"))

    stop_all()
    sync("stopped")
    sync("renamed")
    run_ui()

    r = ritual.connect()
    r.wait_path(instance_path("foo"))

    # TODO: check that we don't get spurious renames


spec = {'entries': [renamer], 'default': spectator}