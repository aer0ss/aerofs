import argparse
import ConfigParser
import requests
import sys
from aerofs_sp.gen.sp_pb2 import SPServiceRpcStub
from aerofs_sp.connection import SyncConnectionService

cfg_defaults = {'sp_version': '21'}


def _mandatory_section(cfg, section):
    if not cfg.has_section(section):
        sys.exit('Sorry, a [{}] section is required in the configuration file.'.format(section))


def _get_access_code(cfg, username, password):
    sp_url = 'https://{}:4433/sp'.format(cfg.get('appliance', 'hostname'))
    sp_conn = SPServiceRpcStub(SyncConnectionService(sp_url, cfg.get('appliance', 'sp_version')))

    try:
        sp_conn.credential_sign_in(username, password)
        ac_response = sp_conn.get_access_code()
    except Exception as e:
        sys.exit('Error signing in with the username/credential given:\n\t' + str(e.reply))
    else:
        return ac_response.accessCode


def _get_accesstoken(cfg, access_code):
    bifrost_url = 'https://{}/auth/token'.format(cfg.get('appliance', 'hostname'))
    post_body = {
        'grant_type': 'authorization_code',
        'code': access_code,
        'code_type': 'device_authorization',
        'client_id': cfg.get('client', 'id'),
        'client_secret': cfg.get('client', 'secret'),
        'scope': 'organization.admin,files.read,files.write,files.appdata,user.read,user.write,user.password,acl.read,acl.write,acl.invitations'
    }
    resp = requests.post(bifrost_url, post_body)

    if not resp.ok:
        sys.exit('Received an error requesting the access token:\n\n' + resp.text)

    return resp.json()['access_token']


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Generate an OAuth access token by signing in with user credentials'
                                                 'and requesting device authorization.')
    parser.add_argument('-c', '--config-file', default='./site.cfg',
                        help="Path to a configuration file (default site.cfg)")
    parser.add_argument('username', help="User for which to generate an Access Token")
    parser.add_argument('password', help="Password for the given user")
    args = parser.parse_args()

    cfg = ConfigParser.ConfigParser(defaults=cfg_defaults)
    cfg.read(args.config_file)

    _mandatory_section(cfg, 'appliance')
    _mandatory_section(cfg, 'client')

    auth_code = _get_access_code(cfg, args.username, args.password)
    print _get_accesstoken(cfg, auth_code)
