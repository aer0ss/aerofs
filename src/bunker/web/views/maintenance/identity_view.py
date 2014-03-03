import logging
from pyramid.httpexceptions import HTTPInternalServerError, HTTPOk
from pyramid.view import view_config
import requests
from web.error import error
from web.views.maintenance.maintenance_util import is_certificate_formatted_correctly, write_pem_to_file, get_conf_client, format_pem, get_conf
from web.views.maintenance.setup_view import verification_base_url

log = logging.getLogger(__name__)


@view_config(
    route_name='identity',
    permission='maintain',
    renderer='identity.mako'
)
def identity(request):
    return {
        'conf': get_conf(request)
    }


@view_config(
    route_name='json_verify_ldap',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_verify_ldap(request):
    cert = request.params['ldap_server_ca_certificate']
    if cert and not is_certificate_formatted_correctly(write_pem_to_file(cert)):
        error("The certificate you provided is invalid. "
              "Please provide one in PEM format.")

    payload = {}
    for key in _get_ldap_specific_options(request.params):
        # N.B. need to convert to ascii. The request params given to us in
        # unicode format.
        payload[key] = request.params[key].encode('ascii', 'ignore')

    url = verification_base_url(request) + "/ldap"
    r = requests.post(url, data=payload)

    if r.status_code == 200:
        return {}

    # In this case we have a human readable error. Hopefully it will help them
    # debug their LDAP issues. Return the error string.
    if r.status_code == 400:
        error("We couldn't connect to the LDAP server. Please check your "
              "settings. The error is:<br>" + r.text)

    # Server failure. No human readable error message is available.
    raise HTTPInternalServerError()


@view_config(
    route_name='json_set_identity_options',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_set_identity_options(request):
    log.info("setup identity")

    auth = request.params['authenticator']
    ldap = auth == 'external_credential'

    # All is well - set the external properties.
    conf = get_conf_client(request)
    conf.set_external_property('authenticator', auth)
    if ldap:
        _write_ldap_options(conf, request.params)

    return HTTPOk()


def _write_ldap_options(conf, request_params):
    for key in _get_ldap_specific_options(request_params):
        if key == 'ldap_server_ca_certificate':
            cert = request_params[key]
            if cert:
                conf.set_external_property(key, format_pem(cert))
            else:
                conf.set_external_property(key, '')
        else:
            conf.set_external_property(key, request_params[key])


def _get_ldap_specific_options(request_params):
    """
    N.B. This method assumes that an HTTP parameter is LDAP specific iff. it has
    the "ldap_" prefix.
    """
    ldap_params = []
    for key in request_params:
        if key[:5] == 'ldap_':
            ldap_params.append(key)

    return ldap_params
