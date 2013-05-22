#!/usr/bin/python
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
DEFAULT_LINUX = 0
DEFAULT_LINUX_NAT = 0
DEFAULT_WINDOWS = 0
DEFAULT_AWS = 0
DEFAULT_PASSWORD = 'temp123'
DEFAULT_APPROOT = '/home/aerofstest/syncdet/deploy/approot/'
DEFAULT_RTROOT = '/home/aerofstest/syncdet/user_data/rtroot/'
DEFAULT_SP = 'https://sp.aerofs.com/sp'
DEFAULT_RSH = 'ssh'
DEFAULT_LOGIN = 'aerofstest'
DEFAULT_ROOT = '~/syncdet'
DEFAULT_OUTFILE = 'config.yaml'
DEFAULT_USERID = getpass.getuser() + '+syncdet+{}@aerofs.com'

# CI Server Connection Settings
CI_SP_URL = 'https://ci.sp.aerofs.com/sp'
CI_C_URL = "http://ci.c.aerofs.com:8000/get_code"
CI_STAGING_URL = 'https://staging.aerofs.com/staging-jonathan/sp'
CI_SP_VERSION = 20

# Lists of Linux VM's (one on LAN and one behind NAT)
LINUX_VMS = ['ci-ubuntu-f.local', 'ci-ubuntu-g.local']
LINUX_NAT_VMS = ['ci-ubuntu-a.local', 'ci-ubuntu-b.local', 'ci-ubuntu-c.local', 'ci-ubuntu-d.local',
    'ci-ubuntu-e.local']

# List of Windows VM's
WINDOWS_VMS = ['ci-windows7.local']

# List of AWS VM's (Ubuntu 12.04)
AWS_VMS = ['172.16.5.12', '172.16.5.30']

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


def generate_yaml(args, username):
    actors = []
    actor_defaults = {}
    # Actor defaults
    actor_defaults['rsh'] = args.rsh
    actor_defaults['login'] = args.login
    actor_defaults['root'] = args.root
    actor_defaults['aero_app_root'] = args.approot
    actor_defaults['aero_rt_root'] = args.rtroot
    actor_defaults['aero_sp_url'] = args.sp
    if not args.multiuser:
        assert type(username) != type([])
        actor_defaults['aero_userid'] = username
        actor_defaults['aero_password'] = args.password
    # Create correct number of actors of each OS
    for vm_num, vm_list in [(args.linux, LINUX_VMS),
                            (args.linux_nat, LINUX_NAT_VMS),
                            (args.windows, WINDOWS_VMS),
                            (args.aws, AWS_VMS)]:
        for i in xrange(vm_num):
            # actor details
            details = {'ci_client': True}
            if vm_list == AWS_VMS:
                details.update(AWS_DETAILS)
            if args.include_ts and vm_list == LINUX_VMS and i == 0:
                details['team_server'] = True
                details['storage_type'] = 'LOCAL'
            if args.include_s3_ts and vm_list == LINUX_VMS and i == 0:
                details.update(S3_DETAILS)
                details['team_server'] = True
                details['storage_type'] = 'S3'
            # actor params
            d = {}
            d['details'] = details
            d['address'] = vm_list[i]
            if args.multiuser:
                assert type(username) == type([])
                d['aero_userid'] = username.pop()
                d['aero_password'] = args.password
            actors.append(d)
    # Put it all together and write it to user-specified "outfile"
    yaml_obj = {'actor_defaults': actor_defaults, 'actors': actors}
    fs = open(args.outfile, 'w')
    yaml.dump(yaml_obj, fs, default_flow_style=False, indent=4)
    fs.close()


def clear_s3_bucket(access_key, secret_key, bucket_id):
    s3conn = S3Connection(access_key, secret_key)
    bucket = s3conn.get_bucket(bucket_id)
    bucket.delete_keys(bucket.list())



#######################
##   MAIN FUNCTION   ##
#######################

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--linux', type=int, default=DEFAULT_LINUX,
        help="Number of Linux VM's on LAN to use")
    parser.add_argument('--linux-nat', type=int, default=DEFAULT_LINUX_NAT,
        help="Number of Linux VM's behind NAT to use")
    parser.add_argument('--windows', type=int, default=DEFAULT_WINDOWS,
        help="Number of Windows VM's to use")
    parser.add_argument('--aws', type=int, default=DEFAULT_AWS,
        help="Number of AWS VM's to use")
    parser.add_argument('--multiuser', action='store_true',
        help="If flag is specified, each VM will use a separate AeroFS account")
    parser.add_argument('--include-ts', action='store_true',
        help="If flag is specified, the first linux VM will be configured as a local TS")
    parser.add_argument('--include-s3-ts', action='store_true',
        help="If flag is specified, the first linux VM will be configured as an S3 TS")
    parser.add_argument('--password', action='store', default=DEFAULT_PASSWORD,
        help="Non-default password to use for AeroFS accounts")
    parser.add_argument('--approot', default=DEFAULT_APPROOT,
        help="Location of approot directory")
    parser.add_argument('--rtroot', default=DEFAULT_RTROOT,
        help="Location of rtroot directory")
    parser.add_argument('--sp', default=DEFAULT_SP,
        help="URL of sp server")
    parser.add_argument('--rsh', default=DEFAULT_RSH,
        help="Default is SSH")
    parser.add_argument('--login', default=DEFAULT_LOGIN,
        help="Default is aerofstest")
    parser.add_argument('--root', default=DEFAULT_ROOT,
        help="Default is ~/syncdet")
    parser.add_argument('--outfile', default=DEFAULT_OUTFILE,
        help="Config file generated by this script")
    parser.add_argument('--userid', default=DEFAULT_USERID,
        help="userid pattern")
    args = parser.parse_args()

    assert args.linux >= 0 and args.linux <= len(LINUX_VMS)
    assert args.linux_nat >=0 and args.linux_nat <= len(LINUX_NAT_VMS)
    assert args.windows >= 0 and args.windows <= len(WINDOWS_VMS)
    assert args.aws >= 0 and args.aws <= len(AWS_VMS)

    # Create user(s) and get the AeroFS username(s)
    total_users = args.linux + args.linux_nat + args.windows + args.aws
    if args.multiuser:
        username = [create_user(args.userid, args.password) for i in xrange(total_users)]
    else:
        username = create_user(args.userid, args.password)

    # Clear S3 bucket if necessary
    if args.include_s3_ts:
        clear_s3_bucket(S3_DETAILS['s3_access_key'],
                        S3_DETAILS['s3_secret_key'],
                        S3_DETAILS['s3_bucket_id'])

    # Generate YAML file
    generate_yaml(args, username)


if __name__ == '__main__':
    main()
