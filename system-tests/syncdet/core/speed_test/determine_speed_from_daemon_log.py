"""This test parses daemon.log for transfer speed data."""
from speed_test_lib import get_last_transport_speed
import sys


def print_speed():
    # N.B. It is assumed that the test is run between one OSX and one Windows computer. This is
    # fair, since the TeamCity statistics page itself is only set up to graph "WinToMac" and
    # "MacToWin" at this point.
    graph_key = 'transferSpeed'  # default
    if 'win32' in sys.platform.lower():
        graph_key = 'MacToWin'
    elif 'darwin' in sys.platform.lower():
        graph_key = 'WinToMac'
    avg_speed = get_last_transport_speed()
    if avg_speed is not None:
        print "##teamcity[buildStatisticValue key='{}' value='{}']".format(graph_key, avg_speed)


spec = { 'default': print_speed }
