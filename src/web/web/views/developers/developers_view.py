from csv import writer as csv_writer
import logging
import markupsafe

import analytics
from pyramid.security import NO_PERMISSION_REQUIRED
from pyramid.view import view_config

from web.util import send_internal_email


DEVELOPERS_SIGNUP_LOG = '/var/log/web/developer_license_requests.csv'


@view_config(
    route_name='developers_signup',
    permission=NO_PERMISSION_REQUIRED,
    renderer='developers_signup.mako'
)
def signup(request):
    return {}

@view_config(
    route_name='json.developers_signup',
    permission=NO_PERMISSION_REQUIRED,
    renderer='json'
)
def json_developers_signup(request):
    # log to file in case analytics fail
    with open(DEVELOPERS_SIGNUP_LOG, 'a') as f:
        csv_writer(f).writerow([
            request.params['email'],
            request.params['first_name'],
            request.params['last_name'],
            request.params['description'],
        ])

    # disable async because uWSGI seems to kill the analytics thread as soon as the process returns
    # instead of allowing it to finish
    # TODO: disable log=True, log_lvel=DEBUG when we are convinced segmentio works properly
    analytics.init(request.registry.settings['segmentio.secret_key'], flush_at=1, log=True, log_level=logging.DEBUG, async=False)

    context = {
        'providers': {'Salesforce': 'true'},
        'Salesforce': {
            'object': 'Lead',
            'lookup': {'email': markupsafe.escape(request.params['email'])},
        }
    }
    analytics.identify(
        markupsafe.escape(request.params['email']),
        {
            'email': markupsafe.escape(request.params['email']),
            'firstName': markupsafe.escape(request.params['first_name']),
            'lastName': markupsafe.escape(request.params['last_name']),
            'description': markupsafe.escape(request.params['description']),
            'Enterprise': 'true',
        },
        context=context
    )
    analytics.track(markupsafe.escape(request.params['email']), "Requested a Developer License")

    # send the team an email
    body = '\n'.join('{}: {}'.format(k, v) for k, v in request.params.iteritems())
    subject = "[DEVELOPER] Developer license request from {}".format(request.params['email'])
    send_internal_email(subject, body)

    return {
        'email': request.params['email'],
    }
