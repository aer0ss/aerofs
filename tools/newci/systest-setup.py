#!/usr/bin/env python
"""
This script generates a YAML file for syncdet tests. Each generate file uses
unique AeroFS users which are created on CI. The input is a file specifying
required characteristics for the actors that will be put in the yaml file.
These conf files have the following form:

actors:
    - os: [wlo] # can be any word whose first character is w or l or o
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
from ConfigParser import ConfigParser

file_root = os.path.dirname(__file__)
python_aerofs_lib = os.path.join(file_root, "../../src/python-lib")
sys.path.append(python_aerofs_lib)

from aerofs_sp import connection
from aerofs_sp.gen import sp_pb2


# read config .ini file for sp proto version
ini_config = ConfigParser()
ini_config.read(os.path.join(file_root, "../../src/web/development/modes/private.ini"))

#################################
##   DEFAULT/CONSTANT VALUES   ##
#################################

# Default arg values
DEFAULT_LOGIN = 'aerofstest'
DEFAULT_PASSWORD = 'temp123'
DEFAULT_ROOT = '~/syncdet'
DEFAULT_HOST = 'share.syncfs.com'
DEFAULT_RSH = 'ssh'
DEFAULT_USERID_FMT = getpass.getuser() + '+syncdet+{}@aerofs.com'

ARCHIVE_DIR = '~/archive'

# CI Server Connection Settings
CODE_URL = "http://share.syncfs.com:21337/get_code"
POOL_URL = "http://newci.arrowfs.org:8040"
CI_SP_URL = "https://share.syncfs.com:4433/sp"
CI_SP_VERSION = ini_config.getint('app:main', 'sp.version')
JSON_HEADERS = {'Content-type': 'application/json', 'Accept': 'application/json'}

S3_DETAILS = {'s3_bucket_id': 'ci-build-agent-nat2.test.aerofs',
              's3_access_key': 'AKIAJMTPOZHMGO7DVEDA',
              's3_secret_key': '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',
              's3_encryption_password': 'password'}

ADMIN_USERID = 'admin@syncfs.com'
ADMIN_PASS = 'temp123'


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


def create_user(userid, password, admin_userid=None, admin_pass=None, sp_url=CI_SP_URL):
    if userid is None:
        userid = generate_unique_userid(DEFAULT_USERID_FMT)
    conn = connection.SyncConnectionService(sp_url, CI_SP_VERSION)
    sp = sp_pb2.SPServiceRpcStub(conn)
    if admin_userid is None:
        sp.request_to_sign_up(userid)
    else:
        sp.credential_sign_in(admin_userid, admin_pass)
        sp.invite_to_organization(userid)
    code = get_signup_code(userid)
    sp.sign_up_with_code(code, password, "SyncDET", "TestUser")
    return userid


def make_user_admin(userid, admin_userid=ADMIN_USERID, admin_pass=ADMIN_PASS, sp_url=CI_SP_URL):
    conn = connection.SyncConnectionService(sp_url, CI_SP_VERSION)
    sp = sp_pb2.SPServiceRpcStub(conn)
    sp.credential_sign_in(admin_userid, admin_pass)
    sp.set_authorization_level(userid, sp_pb2.PBAuthorizationLevel.Value("ADMIN"))
    return userid


class ActorPoolServiceException(Exception):
    pass


def get_addresses_from_pool_service(actor_data, build_id):
    actors = []
    for actor in actor_data:
        d = {}
        os = actor.get('os')
        if os is not None:
            if os[0] in 'lLwWoO':
                d['os'] = os[0].lower()
            else:
                raise ValueError('"os" must be windows or linux or osx')
        vm = actor.get('vm')
        if vm is not None:
            if vm in [True, 'true', 'True', 'yes', 'Yes']:
                d['vm'] = True
            elif vm in [False, 'false', 'False', 'no', 'No']:
                d['vm'] = False
            else:
                raise ValueError('"vm" must be true or false')
        isolated = actor.get('isolated')
        if isolated is not None:
            if isolated in [True, 'true', 'True', 'yes', 'Yes']:
                d['isolated'] = True
            elif isolated in [False, 'false', 'False', 'no', 'No']:
                d['isolated'] = False
            else:
                raise ValueError('"isolated" must be true or false')
        actors.append(d)
    raw_params = {'actors': actors, 'build_id': build_id}
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
    actor_defaults['aero_host'] = args.host
    actor_defaults['archive_dir'] = ARCHIVE_DIR
    actor_defaults['aero_flags'] = args.flags
    if not args.multiuser:
        assert not isinstance(username, list)
        actor_defaults['aero_userid'] = username
        actor_defaults['aero_password'] = args.password

    # Query actor pool service for addresses
    addresses = get_addresses_from_pool_service(actor_data, args.build_id)
    while addresses is None:
        sys.stderr.write("Actors weren't available. Trying again in 10s...\n")
        sleep(10)
        addresses = get_addresses_from_pool_service(actor_data, args.build_id)

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
                raise ValueError('"teamserver" must be LINKED, LOCAL, or S3')

        # actor params
        d = {}
        if details != {}:
            d['details'] = details
        d['address'] = str(addresses.pop())
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
    parser.add_argument('--host', default=DEFAULT_HOST,
        help="Hostname of Private Deployment image")
    parser.add_argument('--rsh', default=DEFAULT_RSH,
        help="Default is SSH")
    parser.add_argument('--login', default=DEFAULT_LOGIN,
        help="Default is aerofstest")
    parser.add_argument('--root', default=DEFAULT_ROOT,
        help="Default is ~/syncdet")
    parser.add_argument('--userid', default=None,
        help="AeroFS userid")
    parser.add_argument('--build-id', default=None, type=int,
        help="Teamcity build ID")
    parser.add_argument('--flag', action='append', dest='flags', default=[],
        help="Optional rtroot flag file. Multiple values can be passed.")
    args = parser.parse_args()

    # Parse the conf file to get actor IP's
    actor_data = get_actor_data(args.profile)

    # Create user(s) and get the AeroFS username(s). Make users admin if necessary.
    if args.multiuser:
        username = []
        for a in actor_data:
            username.append(create_user(args.userid, args.password, ADMIN_USERID, ADMIN_PASS))
            if a.get('teamserver') is not None:
                make_user_admin(username[-1])
        username.reverse()
    else:
        username = create_user(args.userid, args.password, ADMIN_USERID, ADMIN_PASS)
        if any(a.get('teamserver') is not None for a in actor_data):
            make_user_admin(username)

    # Clear S3 bucket if necessary
    if any(a.get('teamserver', '').upper() == 'S3' for a in actor_data):
        clear_s3_bucket(S3_DETAILS['s3_access_key'],
                        S3_DETAILS['s3_secret_key'],
                        S3_DETAILS['s3_bucket_id'])

    # Generate YAML file
    generate_yaml(args, username, actor_data)


if __name__ == '__main__':
    main()
