import logging
import iptools
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPOk
from web.util import str2bool
from web.error import error
from maintenance_util import get_conf_client

log = logging.getLogger(__name__)

#exception class where the message is feedback on why the form is invalid
class InvalidFormInput(Exception):
    pass


def parse_cidr_list(cidr_list):
    return [x.strip() for x in cidr_list.split(';') if len(x.strip()) > 0]


#returns the semicolon-separated encoding of the list as one string or raises an exception if the input is invalid
def is_valid_proxy_list(proxies):
    if len(proxies) == 0:
        #Verification needs a non-empty list
        raise InvalidFormInput('Make sure you input at least one CIDR block for your Proxy Servers')
    for cidr in proxies:
        if not iptools.ipv4.validate_cidr(cidr):
            #CIDR block is not valid for ipv4
            raise InvalidFormInput('There was an invalid CIDR block in the input, please correct it and resubmit')
    return ';'.join(proxies)

@view_config(
    route_name='json_set_mobile_device_management',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_set_mobile_device_management(request):
    conf = get_conf_client(request)
    enabled = str2bool(request.params['enabled'])

    #only update proxy list if enabled, otherwise the input field wouldn't be displayed
    if enabled:
        cidr_inputs = [x for x in request.params.getall('CIDR') if len(x.strip()) > 0]
        try:
            proxy_list = is_valid_proxy_list(cidr_inputs)
            conf.set_external_property('mobile_device_management_proxies', proxy_list)
        except InvalidFormInput as feedback:
            error(str(feedback))

    #shouldn't reach this if an error is thrown, so on error no permanent state is changed
    conf.set_external_property('mobile_device_management_enabled', str(enabled))
    log.info("successfully set mobile device management properties")

    return HTTPOk()

