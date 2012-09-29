import os
import sys

from safetynet import client

def ensure_clients_running():
    if client.instance().is_aerofs_running():
        return

    print "client not running, starting client..."

    # The client is not running. We must start it
    client.instance().start_aerofs()

spec = { 'default': ensure_clients_running }
