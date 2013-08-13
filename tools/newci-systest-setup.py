#!/usr/bin/env python
"""
This script generates a YAML file for syncdet tests. Each generate file uses
unique AeroFS users which are created on CI. The input is a file specifying
required characteristics for the actors that will be put in the yaml file.
These conf files have the following form:

actors:
    - os: [wl] # can be any word whose first character is w or l
      vm: [fF]alse, [tT]rue, [yY]es, [nN]o
      isolated: [fF]alse, [tT]rue, [yY]es, [nN]o
      teamserver: [sS]3, [lL]inked, [lL]ocal
    - ...
    - ...

If a field (os, vm, isolated) is left out, then any value is acceptable.
i.e. if no "os" field is specified, then the resulting actor may run either
operating system.

"""

import argparse
from datetime import datetime
import yaml
import os
import sys
import requests
import json
import getpass
from boto.s3.connection import S3Connection
from time import sleep

file_root = os.path.dirname(__file__)
python_aerofs_lib = os.path.join(file_root, "../src/python-lib")
sys.path.append(python_aerofs_lib)

from aerofs_sp.scrypt import scrypt
from aerofs_sp import connection
from aerofs_sp.gen import sp_pb2


#################################
##   DEFAULT/CONSTANT VALUES   ##
#################################

# Default arg values
DEFAULT_LOGIN = 'aerofstest'
DEFAULT_PASSWORD = 'temp123'
DEFAULT_ROOT = '~/syncdet'
DEFAULT_APPROOT = os.path.join(DEFAULT_ROOT, 'deploy', 'approot')
DEFAULT_RTROOT = os.path.join(DEFAULT_ROOT, 'user_data', 'rtroot')
DEFAULT_ANCHOR_PARENT = os.path.join(DEFAULT_ROOT, 'user_data')
DEFAULT_SP_URL = 'https://transient.syncfs.com:9000/sp'
DEFAULT_RSH = 'ssh'
DEFAULT_USERID_FMT = getpass.getuser() + '+syncdet+{}@aerofs.com'

# CI Server Connection Settings
CODE_URL = "http://newci.arrowfs.org:8025/get_code"
POOL_URL = "http://newci.arrowfs.org:8040"
CI_SP_URL = "https://newci.arrowfs.org:9000/sp"
CI_SP_VERSION = 20

# System-specific details
# AWS_DETAILS = {'os': 'linux32',
#                'distro': 'Ubuntu 12.04',
#                'java': 'OpenJDK 1.6.0_24 IcedTea6 1.11.4'}

S3_DETAILS = {'s3_bucket_id': 'ci-build-agent-nat2.test.aerofs',
              's3_access_key': 'AKIAJMTPOZHMGO7DVEDA',
              's3_secret_key': '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',
              's3_encryption_password': 'password'}

JSON_HEADERS = {'Content-type': 'application/json', 'Accept': 'application/json'}


##########################
##   HELPER FUNCTIONS   ##
##########################

def get_signup_code(userid):
    params = {"userid": userid}
    r = requests.get(CODE_URL, params=params)
    try:
        code = json.loads(r.text)["signup_code"]
    except ValueError:
        print r.text
        raise Exception('Could not find signup code in CI database for user: {}'.format(userid))
    return code


def generate_unique_userid(userid_fmt):
    timestamp = datetime.now().strftime("%Y%m%d-%H%M-%S%f")
    return userid_fmt.format(timestamp)


def create_user(userid, password, sp_url=CI_SP_URL):
    if userid is None:
        userid = generate_unique_userid(DEFAULT_USERID_FMT)
    conn = connection.SyncConnectionService(sp_url, CI_SP_VERSION)
    sp = sp_pb2.SPServiceRpcStub(conn)
    sp.request_to_sign_up(userid)
    code = get_signup_code(userid)
    sp.sign_up_with_code(code, scrypt(password, userid), "SyncDET", "TestUser")
    return userid


class BadConfFileException(Exception):
    pass


class ActorPoolServiceException(Exception):
    pass


def get_addresses_from_pool_service(actor_data):
    raw_params = []
    for actor in actor_data:
        d = {}
        os = actor.get('os')
        if os is not None:
            if os[0] in 'lLwW':
                d['os'] = os[0].lower()
            else:
                raise BadConfFileException('"os" must be windows or linux')
        vm = actor.get('vm')
        if vm is not None:
            if vm in [True, 'true', 'True', 'yes', 'Yes']:
                d['vm'] = True
            elif vm in [False, 'false', 'False', 'no', 'No']:
                d['vm'] = False
            else:
                raise BadConfFileException('"vm" must be true or false')
        isolated = actor.get('isolated')
        if isolated is not None:
            if isolated in [True, 'true', 'True', 'yes', 'Yes']:
                d['isolated'] = True
            elif isolated in [False, 'false', 'False', 'no', 'No']:
                d['isolated'] = False
            else:
                raise BadConfFileException('"isolated" must be true or false')
        raw_params.append(d)
    r = requests.get(POOL_URL, data=json.dumps(raw_params), headers=JSON_HEADERS)
    response = r.json()
    if r.status_code != 200:
        raise ActorPoolServiceException('[{}] {}'.format(r.status_code, response.get('error')))
    return response.get('actors')


def generate_yaml(args, username, actor_data):
    actors = []
    actor_defaults = {}
    # Actor defaults
    actor_defaults['rsh'] = args.rsh
    actor_defaults['login'] = args.login
    actor_defaults['root'] = args.root
    actor_defaults['aero_app_root'] = args.approot
    actor_defaults['aero_rt_root'] = args.rtroot
    actor_defaults['aero_sp_url'] = args.sp_url
    if not args.multiuser:
        assert not isinstance(username, list)
        actor_defaults['aero_userid'] = username
        actor_defaults['aero_password'] = args.password

    # Query actor pool service for addresses
    addresses = get_addresses_from_pool_service(actor_data)
    while addresses is None:
        sys.stderr.write('Actors weren\'t available. Trying again in 10s...\n')
        sleep(10)
        addresses = get_addresses_from_pool_service(actor_data)

    # Reverse addresses so they can be popped in order
    addresses.reverse()

    for actor in actor_data:
        details = {}
        # if actor.get('is_on_aws'):
        #     details.update(AWS_DETAILS)
        teamserver = actor.get('teamserver')
        if teamserver is not None:
            if isinstance(teamserver, str) and teamserver.upper() in ['LINKED', 'LOCAL', 'S3']:
                details['team_server'] = True
                details['storage_type'] = teamserver.upper()
                if teamserver.upper() == 'S3':
                    details.update(S3_DETAILS)
            else:
                raise BadConfFileException('"teamserver" must be LINKED, LOCAL, or S3')

        # actor params
        d = {}
        if details != {}:
            d['details'] = details
        d['address'] = str(addresses.pop())
        if teamserver is not None:
            d['aero_root_anchor'] = os.path.join(args.anchor_parent, 'AeroFS Team Server Storage')
        else:
            d['aero_root_anchor'] = os.path.join(args.anchor_parent, 'AeroFS')
        if args.multiuser:
            assert isinstance(username, list)
            d['aero_userid'] = username.pop()
            d['aero_password'] = args.password
        actors.append(d)

    # Put it all together and write it to stdout
    yaml_obj = {'actor_defaults': actor_defaults, 'actors': actors}
    print yaml.dump(yaml_obj, default_flow_style=False, indent=4)


def clear_s3_bucket(access_key, secret_key, bucket_id):
    s3conn = S3Connection(access_key, secret_key)
    bucket = s3conn.get_bucket(bucket_id)
    bucket.delete_keys(bucket.list())


def get_actor_data(conf):
    with open(conf, 'r') as f:
        data = yaml.load(f.read())
    return data['actors']


#######################
##   MAIN FUNCTION   ##
#######################

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('profile',
        help="Location of YAML-formatted file containing actor definitions")
    parser.add_argument('--multiuser', action='store_true',
        help="If flag is specified, each VM will use a separate AeroFS account")
    parser.add_argument('--password', action='store', default=DEFAULT_PASSWORD,
        help="Non-default password to use for AeroFS accounts")
    parser.add_argument('--approot', default=DEFAULT_APPROOT,
        help="Location of approot directory")
    parser.add_argument('--rtroot', default=DEFAULT_RTROOT,
        help="Location of rtroot directory")
    parser.add_argument('--sp-url', default=DEFAULT_SP_URL,
        help="URL of sp server")
    parser.add_argument('--rsh', default=DEFAULT_RSH,
        help="Default is SSH")
    parser.add_argument('--login', default=DEFAULT_LOGIN,
        help="Default is aerofstest")
    parser.add_argument('--root', default=DEFAULT_ROOT,
        help="Default is ~/syncdet")
    parser.add_argument('--userid', default=None,
        help="AeroFS userid")
    parser.add_argument('--anchor-parent', default=DEFAULT_ANCHOR_PARENT,
        help="Location of the root anchor's parent")
    args = parser.parse_args()

    # Parse the conf file to get actor IP's
    actor_data = get_actor_data(args.profile)

    # Create user(s) and get the AeroFS username(s)
    if args.multiuser:
        username = [create_user(args.userid, args.password) for _ in xrange(len(actor_data))]
    else:
        username = create_user(args.userid, args.password)

    # Clear S3 bucket if necessary
    if any(a.get('teamserver', '').upper() == 'S3' for a in actor_data):
        clear_s3_bucket(S3_DETAILS['s3_access_key'],
                        S3_DETAILS['s3_secret_key'],
                        S3_DETAILS['s3_bucket_id'])

    # Generate YAML file
    generate_yaml(args, username, actor_data)


if __name__ == '__main__':
    main()
