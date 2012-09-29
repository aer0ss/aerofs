#!/usr/bin/env python

############################################################################
#
# Extracts AeroFS usernames/emails from the given SafetyNet YAML config data
#
############################################################################
import yaml
import sys

def usernames(data):
    """Extracts AeroFS usernames/emails from the YAML config data"""

    if not data['actors']:
        return set()

    users = set()
    for actor in data['actors']:
        try:
            users.add(actor['aero_userid'])
        except KeyError:
            users.add(data['actor_defaults']['aero_userid'])

    return users

if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.stderr.write('Extracts AeroFS usernames/emails from the given SafetyNet YAML config data.\n')
        sys.stderr.write('\tusage:\t{0} [config_file]\n'.format(sys.argv[0]))
        sys.stderr.write('\t[config_file]: The SafetyNet YAML configuration file\n')
        sys.exit(1)

    try:
        # Open the config file and extract the usernames
        with open(sys.argv[1], 'r') as config_file:
            users = usernames(yaml.load(config_file))

            # Exit if no usernames were found. The general use case is for
            # a script to call this program expecting to do something useful with
            # the usernames, assuming there to be some.
            if len(users) == 0:
                sys.stderr.write('error: no AeroFS usernames found in {0}\n'.format(sys.argv[1]))
                sys.exit(1)

            print ' '.join(users)
    except Exception as e:
        sys.stderr.write('error: failed to read configuration file: {0}\n'.format(e))
        sys.exit(1)
