import logging
from maintenance_util import get_conf_client, get_conf
from web.util import str2bool
from pyramid.view import view_config
from aerofs_common.constants import DEFAULT_MIN_PASSWORD_LENGTH, DEFAULT_PASSWORD_EXPIRATION_PERIOD_MONTHS

log = logging.getLogger(__name__)

@view_config(
    route_name='password_restriction',
    permission='maintain',
    renderer='password_restriction.mako',
    request_method='GET'
)
def password_restriction(request):
    conf = get_conf(request)
    min_password_length = conf.get('password.restriction.min_password_length')
    is_numbers_letters_required = str2bool(
        conf.get('password.restriction.numbers_letters_required'))
    expiration_period_months = conf.get('password.restriction.expiration_period_months')
    expiration_period_months_str = ""


    if min_password_length == "":
        min_password_length = DEFAULT_MIN_PASSWORD_LENGTH

    if expiration_period_months == "":
        expiration_period_months = DEFAULT_PASSWORD_EXPIRATION_PERIOD_MONTHS

    if int(expiration_period_months) == 0:
        expiration_period_months_str = " Never"
    elif int(expiration_period_months) == 1:
        expiration_period_months_str = expiration_period_months + " Month"
    else:
        expiration_period_months_str = expiration_period_months + " Months"

    return {
        'min_password_length': min_password_length,
        'is_numbers_letters_required': is_numbers_letters_required,
        'expiration_period_months_str': expiration_period_months_str,
        'expiration_period_months': expiration_period_months
    }

@view_config(
    route_name='json_set_password_restriction',
    permission='maintain',
    renderer='json',
    request_method='POST'
)
def json_set_password_restriction(request):
    """
    N.B. the changes won't take effects on the system until relevant services are
    restarted.
    """
    config = get_conf_client(request)
    min_password_length = request.params['min-password-length']
    is_numbers_letters_required = str2bool(request.params['numbers-letters-required'])
    expiration_period_months = request.params['password-expiration-period-months']

    config.set_external_property('password_restriction_min_password_length', min_password_length)
    config.set_external_property('password_restriction_numbers_letters_required', is_numbers_letters_required)
    config.set_external_property('password_restriction_expiration_period_months', expiration_period_months)
    return {}

