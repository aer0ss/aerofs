import logging

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPBadRequest, HTTPOk

from stripe import CardError

from web.util import *
from web.error import error
import stripe_util
from stripe_util import URL_PARAM_STRIPE_CARD_TOKEN

log = logging.getLogger(__name__)

@view_config(
    route_name='manage_payment',
    permission='admin',
    request_method='GET',
    renderer='manage_payment.mako'
)
def manage_payment(request):
    stripe_customer, quantity = _get_stripe_customer_and_quantity(request)
    card = stripe_customer.active_card

    return {
        # Set quantity to 0 if the team doesn't have a subscription.
        # The team may have a custmoer ID and a non-zero quantity but no
        # subscription, if the team:
        #   1. is an old team before the new payment workflow, or
        #   2. has been manually removed subscriptoin by us.
        # See README.stripe.txt for more info.
        'quantity': quantity if stripe_customer.subscription else 0,
        'unit_price_dollars': 10,
        'stripe_publishable_key': stripe_util.STRIPE_PUBLISHABLE_KEY,
        'url_param_stripe_card_token': URL_PARAM_STRIPE_CARD_TOKEN,
        'invoices': _get_invoices(stripe_customer),
        'card': {
            'last_four_digits': card.last4,
            'type': card.type,
            'expires_month': card.exp_month,
            'expires_year': card.exp_year
        }
    }

def _get_stripe_customer_and_quantity(request):
    """
    Throw if the user doesn't have a Stripe customer ID. If the user has no
    customer ID, he shouldn't have navigated to the callers of this method in
    the first place.
    """
    sp = get_rpc_stub(request)
    stripe_data = sp.get_stripe_data().stripe_data
    if not stripe_data.customer_id:
        raise HTTPBadRequest(detail="You have no payment method on file.")

    return stripe_util.get_stripe_customer(stripe_data.customer_id), stripe_data.quantity

def _get_invoices(stripe_customer):
    invoices = []

    stripe_invoices = stripe_util.get_stripe_customer_invoices(stripe_customer)
    for invoice in stripe_invoices:
        invoices.append({
            'id': invoice.id,
            'date': invoice.date,
            'period_start': invoice.period_start,
            'period_end': invoice.period_end,
            'total': invoice.total,
            'paid': invoice.paid
        })

    return invoices

@view_config(
    route_name='json.create_stripe_customer',
    permission='admin',
    renderer='json',
    request_method='POST',
)
def json_create_stripe_customer(request):
    stripe_card_token = request.params[URL_PARAM_STRIPE_CARD_TOKEN]
    email = get_session_user(request)

    log.info("create_stripe_customer " + email)
    stripe_customer_id = None
    try:
        stripe_customer_id = stripe_util.new_stripe_customer(
            email, stripe_card_token).id
    except CardError as e:
        error(stripe_util.get_card_error_message(e))

    sp = get_rpc_stub(request)
    sp.set_stripe_customer_id(stripe_customer_id)

@view_config(
    route_name='json.update_credit_card',
    permission='admin',
    request_method='POST'
)
def json_update_credit_card(request):
    stripe_customer, _ = _get_stripe_customer_and_quantity(request)
    stripe_card_token = request.params[URL_PARAM_STRIPE_CARD_TOKEN]

    try:
        # see also: https://stripe.com/docs/api?lang=python#update_customer
        stripe_customer.card = stripe_card_token
        stripe_customer.save()
    except CardError as e:
        error(stripe_util.get_card_error_message(e))

    # WW: there is a case here where the Stripe Customer ID we have in
    # our DB is bad for some reason, the account becomes delinquent and the user
    # attempts to update their CC. Since the Stripe Customer ID we have is bad
    # we will fail here.  What we should do is flush that data and just create
    # a new Stripe Customer. - Eric
    return HTTPOk()
