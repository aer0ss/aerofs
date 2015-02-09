#
# This file contains utility functions for AeroFS OAuth clients
#
import logging
import re

import requests

from web import util

log = logging.getLogger(__name__)

# The URL to Bifrost, i.e. the OAuth server
def get_bifrost_url(request):
    return request.registry.settings["deployment.oauth_server_uri"]

def get_bifrost_client(request):
    return BifrostClient(get_bifrost_url(request))

class BifrostClient(object):
    def __init__(self, base_url, custom_cert=None, custom_cert_bundle=None):
        self.base_url = base_url
        self.custom_cert = custom_cert
        self.custom_cert_bundle = custom_cert_bundle
        self.last_response = None

    def raise_on_error(self, failed_response=None):
        resp = failed_response or self.last_response
        if not resp.ok:
            util.error(_get_error_message_for_bifrost_resonse(resp))

    def flash_on_error(self, request, failed_response=None):
        resp = failed_response or self.last_response
        if not resp.ok:
            util.flash_error(request, _get_error_message_for_bifrost_resonse(resp))

    def delete_all_tokens(self, owner):
        url = '{}/users/{}/tokens'.format(self.base_url, owner)
        self.last_response = requests.delete(url)
        return self.last_response

    def delete_delegated_tokens(self, owner):
        url = '{}/users/{}/delegates'.format(self.base_url, owner)
        self.last_response = requests.delete(url)
        return self.last_response

    def get_new_oauth_token(self, mobile_access_code, client_id, client_secret, expires_in=0, scopes=None):
        # N.B. the get_mobile_access_code RPC returns a proof-of-identity nonce that
        # Bifrost uses for authentication. It was originally designed for a mobile app
        # and its original name remains to maintain backwards compatibility.
        # Obtain one with get_rpc_stub(request).get_mobile_access_code().accessCode
        data={
            'grant_type': 'authorization_code',
            'code': mobile_access_code,
            'code_type': 'device_authorization',
            'client_id': client_id,
            'client_secret': client_secret,
            'expires_in': expires_in,
        }

        if scopes is not None:
            data['scope'] = ','.join(scopes)
        url = '{}/token'.format(self.base_url)
        self.last_response = requests.post(url, data)
        self.last_response.raise_for_status()
        token = self.last_response.json()['access_token']
        return token

    def delete_oauth_token(self, token):
        assert is_valid_access_token(token)
        url = '{}/token/{}'.format(self.base_url, token)
        self.last_response = requests.delete(url)
        if self.last_response.status_code == 404:
            return
        self.last_response.raise_for_status()
        return self.last_response

    def get_registered_apps(self):
        url = '{}/clients'.format(self.base_url)
        self.last_response = requests.get(url)
        return self.last_response

    def register_app(self, client_name, redirect_uri):
        url = '{}/clients'.format(self.base_url)
        self.last_response = requests.post(url, data={
            'resource_server_key': 'oauth-havre',
            'client_name': client_name,
            'redirect_uri': redirect_uri
        })
        return self.last_response

    def get_app_info(self, client_id):
        url = '{}/clients/{}'.format(self.base_url, client_id)
        self.last_response = requests.get(url)
        return self.last_response

    def delete_app(self, client_id):
        url = '{}/clients/{}'.format(self.base_url, client_id)
        self.last_response = requests.delete(url)
        return self.last_response

    def get_access_tokens_for(self, owner):
        url = '{}/tokenlist'.format(self.base_url)
        self.last_response = requests.get(url, params={
            'owner': owner,
        })
        return self.last_response

    def delete_access_token(self, token):
        assert is_valid_access_token(token)
        url = '{}/token/{}'.format(self.base_url, token)
        self.last_response = requests.delete(url)
        return self.last_response


def is_builtin_client_id(client_id):
    """
    Some client IDs are for internal use and not to exposed to end users, such
    as 'aerofs-ios.' This method returns whether the given client ID is internal.
    """
    return len(client_id) != 36

def is_valid_access_token(token):
    return re.match("^[0-9a-f]{32}$", token)

def is_valid_non_builtin_client_id(client_id):
    return re.match("^[\-0-9a-f]{36}$", client_id)

def is_aerofs_mobile_client_id(client_id):
    return client_id in ('aerofs-android', 'aerofs-ios')

def _get_error_message_for_bifrost_resonse(response):
    log.error('bifrost error: {} text: {}'.format(response.status_code, response.text))
    return 'An error occurred. Please try again. The error is: {} {}'\
            .format(response.status_code, response.text)
