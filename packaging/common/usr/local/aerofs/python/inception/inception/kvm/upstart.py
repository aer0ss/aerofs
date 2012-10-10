"""
Utility used to interface with upstart on the KVM.
"""

import os
import subprocess

def service_is_healthy(name):
    out = subprocess.check_output(['service', name, 'status'])
    out = out.partition(', ')
    expected = name + ' start/running'

    if expected in out:
        return True
    else:
        return False

def count_of_bad_services():
    """
    Get a count of the bad aerofs services running on this KVM.
    """

    aerofs_services = []

    for root, dirs, files in os.walk('/etc/init'):
        for f in files:
            # String parsing.
            split_period = f.partition('.')
            split_dash = split_period[0].partition('-')

            if len(split_period) == 3 and \
                    split_period[2] == 'conf' and \
                    len(split_dash) > 0 and \
                    split_dash[0] == 'aerofs':
                aerofs_services.append(split_period[0])

    counter = 0
    for a in aerofs_services:
        if not service_is_healthy(a):
            counter += 1

    return counter
