import logging

from time import time
from math import ceil
from pyramid.view import view_config
from pyramid.httpexceptions import HTTPFound, HTTPServerError, HTTPNoContent, HTTPBadRequest

from stripe import CardError, InvalidRequestError

from aerofs_common.exception import ExceptionReply
from web.helper_functions import *
from web.views.payment import stripe_billing

log = logging.getLogger(__name__)

# HTML form field names
_ORG_NAME_FIELD_NAME = 'organization-name'
_ORG_CONTACT_PHONE_FIELD_NAME = 'organization-contact-phone'

@view_config(
    route_name='free_user',
    permission='admin',
    request_method='GET',
    renderer='free_user.mako'
)
def free_user(request):
    return {}

########
# Business Signup

@view_config(
    route_name='business_activate',
    permission='user',
    request_method='GET',
    renderer='business_activate.mako'
)
def business_activate_get_request(request):

    if is_admin(request):
        return HTTPFound(request.route_url('business_activate_done'))
    try:
        return {
          'STRIPE_PUBLISHABLE_KEY': stripe_billing.STRIPE_PUBLISHABLE_KEY,
          'ORG_NAME_FIELD_NAME': _ORG_NAME_FIELD_NAME,
          'ORG_CONTACT_PHONE_FIELD_NAME': _ORG_CONTACT_PHONE_FIELD_NAME,
        }
    except ExceptionReply as e:
        log.error(e)
        return HTTPServerError(detail=get_error(e))

@view_config(
    route_name='business_activate',
    permission='user',
    request_method='POST',
)
def business_activate_post_request(request):
    try:
        _business_activate(request)
        return HTTPNoContent()
    except CardError as e:
        log.error(e)
        message = stripe_billing.get_card_error_message(e)
        return HTTPBadRequest(detail=message)
    except ExceptionReply as e:
        log.error(e)
        return HTTPServerError(detail=get_error(e))

def _business_activate(request):
    name, contact_phone = _get_organization_from_request(request)

    stripe_customer = _new_stripe_customer(request)
    stripe_customer_id = stripe_customer.id

    log.info("Creating Organization, name: {} phone: {} strip_customer_id: {}"
        .format(name, contact_phone, stripe_customer_id))

    stripe_plan_id = _get_stripe_plan_id(1)

    log.info("Starting Subscription, customer_id: {} plan_id: {}"
        .format(stripe_customer_id, stripe_plan_id))

    _create_organization(request, name, contact_phone, stripe_customer_id)

    # This could happen with customer creation, but I don't trust us to always
    # create the organization properly, so I don't want to start billing a customer
    # unless I'm sure we properly setup the organization in SP
    #
    # TODO (WW) have an automatic tool to periodically check consistency
    # between SP and Stripe?
    #
    # start with the single-user plan
    _set_stripe_subscription(stripe_customer, 1)

@view_config(
    route_name='business_activate_done',
    permission='admin',
    request_method='GET',
    renderer='business_activate_done.mako'
)
def business_activate_done(request):
    return {}

########
# Manage Subscription

@view_config(
    route_name='manage_subscription',
    permission='admin',
    request_method='GET',
    renderer='manage_subscription.mako'
)
def manage_subscription(request):

    # See https://stripe.com/docs/api?lang=python#subscriptions for the
    # subscription object.
    stripe_customer = _get_stripe_customer(request)
    # The subscription object is None if the user has cancelled the plan.
    if not stripe_billing.has_subscription(stripe_customer):
        return HTTPFound(request.route_url('cancel_subscription_done'))

    trialing = stripe_billing.is_trialing(stripe_customer)

    if trialing:
        trial_end = stripe_customer.subscription.trial_end
        trial_days_left = int(ceil((trial_end - time()) / 60 / 60 / 24));
        if trial_days_left < 0: trial_days_left = 0
        user_count = None
    else:
        trial_days_left = None
        user_count = _get_user_count(request)

    return {
        'trialing': trialing,
        'trial_days_left': trial_days_left, # None if trialing is False
        'user_count': user_count, # None if trialing is True
        'unit_price_dollars': 10,
    }

########
# Cancel Subscription

@view_config(
    route_name='cancel_subscription',
    permission='admin',
    request_method = 'POST'
)
def cancel_subscription(request):
    stripe_customer = _get_stripe_customer(request)
    stripe_billing.cancel_stripe_customer_subscription(stripe_customer)

    # AeroFS people can use this string in their email filters to signify emails
    # related to business cases. See BUSINESS_USER_EMAIL_TAG in Param.java.
    send_internal_email("[BUSINESS_USER] {} Cancelled Business Subscription"
        .format(_get_current_user_email(request)), '')

    return HTTPFound(request.route_url('cancel_subscription_done'))

@view_config(
    route_name='cancel_subscription_done',
    permission='user',
    request_method='GET',
    renderer='cancel_subscription_done.mako'
)
def cancel_subscription_done(request):
    return {}

########
# Manage Credit Card

@view_config(
    route_name='manage_credit_card',
    permission='admin',
    request_method='GET',
    renderer='manage_credit_card.mako'
)
def manage_credit_card_get_request(request):
    card = None
    try:
        card = _get_card(request)
    except InvalidRequestError as e:
        # allow the customer to update even if we can't retrieve the existing
        # card info, this may be why they are trying to update!
        log.warn(e)

    return {
      'stripe_publishable_key': stripe_billing.STRIPE_PUBLISHABLE_KEY,
      'card': card
    }

@view_config(
    route_name='manage_credit_card',
    permission='admin',
    request_method='POST'
)
def manage_credit_card_post_request(request):
    """
    This method assumes the customer already exists in Stripe and has an active
    subscription.
    """
    try:
        stripe_customer = _get_stripe_customer(request)
        stripe_card_token = _get_stripe_card_token(request)
        stripe_billing.set_stripe_customer_card(stripe_customer,
                stripe_card_token)

        # WW: there is a case here where the Stripe Customer ID we have in
        # our DB is bad for some reason, the account becomes delinquent and the user
        # attempts to update their CC. Since the Stripe Customer ID we have is bad
        # we will fail here.  What we should do is flush that data and just create
        # a new Stripe Customer.  You have refactored a ton of this code and I'm
        # not clear on how you'd want to complete these steps.  Please implement
        # this change at your convenience - Eric

        return HTTPNoContent()
    except CardError as e:
        log.error(e)
        message = stripe_billing.get_card_error_message(e)
        return HTTPBadRequest(detail=message)
    except ExceptionReply as e:
        log.error(e)
        return HTTPServerError(detail=get_error(e))

def update_stripe_subscription(stripe_subscription_data):
    """
    Update the subscription based on the information described in
    stripe_subscription_data, which is an object of PBStripeSubscriptionData
    defined in sp.proto.
    """

    # Noop if the user doesn't have a customer ID.
    if not stripe_subscription_data.HasField('stripe_customer_id'): return

    stripe_customer_id = stripe_subscription_data.stripe_customer_id
    stripe_customer = stripe_billing.get_stripe_customer(stripe_customer_id)

    _set_stripe_subscription(stripe_customer, stripe_subscription_data.user_count)

def _get_user_count(request):
    """
    Get the total number of members and invited users for the current user's
    organization.
    """
    sp = get_rpc_stub(request)

    # TODO (WW) have SP to return PBStripeSubscriptionData instead?
    return sp.list_users('', 0, 0).total_count + \
           len(sp.list_organization_invited_users().user_id)

def _set_stripe_subscription(stripe_customer, user_count):
    plan_id = _get_stripe_plan_id(user_count)
    stripe_billing.set_stripe_customer_subscription(stripe_customer, plan_id)

def _get_stripe_customer(request):
    stripe_customer_id = _get_stripe_customer_id(request)
    stripe_customer = stripe_billing.get_stripe_customer(stripe_customer_id)

    if hasattr(stripe_customer, 'deleted') and stripe_customer.deleted:
        _redirect_non_stripe_user(request)

    return stripe_customer

def _get_stripe_plan_id(user_count):
    return ''.join(["business_", str(user_count), "user"])

def _redirect_non_stripe_user(request):
    # The user is not a Strip customer. Direct them to the signup page.
    raise HTTPFound(request.route_url('free_user'))

def _get_stripe_customer_id(request):
    sp = get_rpc_stub(request)
    sp_reply = sp.get_stripe_customer_id()

    if not sp_reply.HasField('stripe_customer_id'):
        _redirect_non_stripe_user(request)

    return sp_reply.stripe_customer_id

def _get_current_user_email(request):
    return request.session['username']

def _create_organization(request, name, contact_phone, stripe_customer_id):
    sp = get_rpc_stub(request)
    sp.add_organization(name, contact_phone, stripe_customer_id)

    # the user's auth level may have changed to admin of the new org
    reload_auth_level(request)

def _get_organization_from_request(request):
    name = request.params.get(_ORG_NAME_FIELD_NAME)
    contact_phone = request.params.get(_ORG_CONTACT_PHONE_FIELD_NAME)

    return name, contact_phone

def _new_stripe_customer(request):
    stripe_card_token = _get_stripe_card_token(request)

    email = _get_current_user_email(request)

    log.info("Getting Stripe Customer Id, email: " + email);
    stripe_customer = \
        stripe_billing.new_stripe_customer(email, stripe_card_token)

    return stripe_customer

def _get_stripe_card_token(request):
    # will only be None if Stripe.js was not used to generate a token
    # Stripe.js will perform client side validation if a token is needed prior
    # to submission
    return request.params.get('stripeToken')

def _get_card(request):
    stripe_customer = _get_stripe_customer(request)

    card = stripe_customer.active_card
    return {
        'last_four_digits': card.last4,
        'type': card.type,
        'expires_month': card.exp_month,
        'expires_year': card.exp_year
    }

@view_config(
   route_name='payments',
   permission='admin',
   request_method='GET',
   renderer='payments.mako'
)
def payments(request):
    try:
        view_invoices = []

        stripe_customer = _get_stripe_customer(request)
        if stripe_customer is not None:
            stripe_invoices = stripe_billing.get_stripe_customer_invoices(stripe_customer)

            for stripe_invoice in stripe_invoices:
                if stripe_invoice.paid:
                    id = stripe_invoice.id
                    date = stripe_invoice.date
                    period_start = stripe_invoice.period_start
                    period_end = stripe_invoice.period_end
                    total = stripe_invoice.total
                    paid = stripe_invoice.paid

                    view_invoices.append({
                            'id': id,
                            'date': date,
                            'period_start': period_start,
                            'period_end': period_end,
                            'total': total,
                            'paid': paid
                        })

        log.info(view_invoices)

        return {
            'invoices': view_invoices
        }
    except ExceptionReply as e:
        log.error(e)
        return HTTPServerError(detail=get_error(e))
