#!/usr/bin/env python

###################################################################
#
# Updates the configuration file with the current local IP address
#
###################################################################

import sys
import yaml
import socket

def local_ip_address():
    """Returns the local ip address of this machine for the local subnet."""
    ips = socket.gethostbyname_ex(socket.gethostname())[2]
    candidates = [ip for ip in ips if not ip.startswith('127.')]
    if len(candidates) == 0:
        raise Exception("no IP address found. Debug: {0}".format(ips))
    return candidates[0]

if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.stderr.write('Loads the given SyncDET configuration file and creates a new\n')
        sys.stderr.write('replica with the controller_address field set to your local IP address.\n')
        sys.stderr.write('\tusage:\t{0} [template] [output]\n'.format(sys.argv[0]))
        sys.stderr.write('\t[template]: The SyncDET YAML configuration file to be used as a template\n')
        sys.stderr.write('\t[output]: The location to output the modified SyncDET YAML configuration file\n')
        sys.exit(1)

    # Read the file
    data = None
    with open(sys.argv[1], 'r') as config_file:
        data = yaml.load(config_file)

    # Do the switcherooo!
    data['controller_address'] = local_ip_address()

    # Write the file
    with open(sys.argv[2], 'w') as config_file:
        config_file.write(yaml.dump(data, default_flow_style=False, indent=4))
