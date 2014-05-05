from safetynet import client

from lib import ritual

def assert_launched():
    # The daemon should have restarted from the update process
    # Wait until the ritual service is running
    ritual.wait_for_heartbeat()

    # Now look for the GUI process
    assert client.instance().is_aerofs_running(), 'AeroFS is not running'

spec = { 'default': assert_launched }
