#!/usr/bin/env python
"""
This script generates a YAML file for syncdet tests. Each generate file uses
unique AeroFS users which are created on CI.

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

file_root = os.path.dirname(__file__)
python_aerofs_lib = os.path.join(file_root,"../src/python-lib")
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
DEFAULT_SP_URL = 'https://sp.aerofs.com/sp'
DEFAULT_RSH = 'ssh'
DEFAULT_USERID = getpass.getuser() + '+syncdet+{}@aerofs.com'

# CI Server Connection Settings
CI_SP_URL = 'https://ci.sp.aerofs.com/sp'
CI_C_URL = "http://ci.c.aerofs.com:8000/get_code"
CI_STAGING_URL = 'https://staging.aerofs.com/staging-jonathan/sp'
CI_SP_VERSION = 20

# System-specific details
AWS_DETAILS = {'os': 'linux32',
               'distro': 'Ubuntu 12.04',
               'java': 'OpenJDK 1.6.0_24 IcedTea6 1.11.4'}

S3_DETAILS = {'s3_bucket_id': 'ci-build-agent-nat2.test.aerofs',
              's3_access_key': 'AKIAJMTPOZHMGO7DVEDA',
              's3_secret_key': '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',
              's3_encryption_password': 'password'}


##########################
##   HELPER FUNCTIONS   ##
##########################

def get_signup_code(userid):
    params = {"userid" : userid}
    r = requests.get(CI_C_URL, params=params)
    try:
        code = json.loads(r.text)["signup_code"]
    except ValueError:
        print r.text
        raise Exception('Could not find signup code in CI database for user: {}'.format(userid))
    return code


def create_user(userid_fmt, password):
    timestamp = datetime.now().strftime("%Y%m%d-%H%M-%S%f")
    userid = userid_fmt.format(timestamp)
    conn = connection.SyncConnectionService(CI_SP_URL, CI_SP_VERSION)
    sp = sp_pb2.SPServiceRpcStub(conn)
    sp.request_to_sign_up(userid)
    code = get_signup_code(userid)
    sp.sign_up_with_code(code, scrypt(password, userid), "SyncDET", "TestUser")
    return userid


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

    # Create correct number of actors of each OS
    for actor in actor_data:
        # actor details
        details = {'ci_client': True}
        if actor.get('is_on_aws'):
            details.update(AWS_DETAILS)
        if actor.get('TS'):
            details['team_server'] = True
            details['storage_type'] = actor['TS'].get('storage_type')
            if actor['TS'].get('storage_type') == 'S3':
                details.update(S3_DETAILS)
        # actor params
        d = {}
        d['details'] = details
        d['address'] = actor['address']
        if args.multiuser:
            assert isinstance(username, list)
            d['aero_userid'] = username.pop()
            d['aero_password'] = args.password
        actors.append(d)
    # Put it all together and write it to user-specified "outfile"
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
    parser.add_argument('--userid', default=DEFAULT_USERID,
        help="userid pattern")
    args = parser.parse_args()

    # Parse the conf file to get actor IP's
    actor_data = get_actor_data(args.profile)

    # Create user(s) and get the AeroFS username(s)
    if args.multiuser:
        username = [create_user(args.userid, args.password) for i in xrange(len(actor_data))]
    else:
        username = create_user(args.userid, args.password)

    # Clear S3 bucket if necessary
    if any(ts.get('storage_type') == 'S3' for ts in (a for a in actor_data if a.get('TS'))):
        clear_s3_bucket(S3_DETAILS['s3_access_key'],
                        S3_DETAILS['s3_secret_key'],
                        S3_DETAILS['s3_bucket_id'])

    # Generate YAML file
    generate_yaml(args, username, actor_data)


if __name__ == '__main__':
    main()
