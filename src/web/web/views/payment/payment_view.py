import logging
from pyramid.security import authenticated_userid

from pyramid.view import view_config
from pyramid.httpexceptions import HTTPBadRequest

from stripe import CardError

from web.util import flash_success, get_rpc_stub, send_sales_email
from web.error import expected_error
import stripe_util
from stripe_util import URL_PARAM_STRIPE_CARD_TOKEN

log = logging.getLogger(__name__)

URL_PARAM_CHANCE = 'chance'
URL_PARAM_FEEDBACK = 'feedback'

@view_config(
    route_name='start_subscription_done',
    permission='admin',
    request_method='GET',
    renderer='manage_subscription.mako'
)
def start_subscription_done(request):
    flash_success(request, "Thank you! Your AeroFS plan has been upgraded.")
    return manage_subscription(request)

@view_config(
    route_name='manage_subscription',
    permission='admin',
    request_method='GET',
    renderer='manage_subscription.mako'
)
def manage_subscription(request):
    stripe_customer, quantity = _get_stripe_customer_and_quantity(request)
    card = stripe_customer.active_card

    return {
        'url_param_chance': URL_PARAM_CHANCE,
        'url_param_feedback': URL_PARAM_FEEDBACK,

        'quantity': quantity,
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
        raise HTTPBadRequest(detail="You are not a paying customer.")

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
    email = authenticated_userid(request)

    log.info("create_stripe_customer " + email)
    stripe_customer_id = None
    try:
        stripe_customer_id = stripe_util.create_stripe_customer(
            email, stripe_card_token).id
    except CardError as e:
        expected_error(stripe_util.get_card_error_message(e))

    sp = get_rpc_stub(request)
    sp.set_stripe_customer_id(stripe_customer_id)
    stripe_util.update_stripe_subscription(sp.get_stripe_data().stripe_data)

    from_email = authenticated_userid(request)
    send_sales_email(from_email,
        "[Subscription Activation] {}".format(from_email), "Congrats, Team!")

@view_config(
    route_name='json.update_credit_card',
    permission='admin',
    request_method='POST',
    renderer='json'
)
def json_update_credit_card(request):
    stripe_customer, _ = _get_stripe_customer_and_quantity(request)
    stripe_card_token = request.params[URL_PARAM_STRIPE_CARD_TOKEN]

    try:
        # see also: https://stripe.com/docs/api?lang=python#update_customer
        stripe_customer.card = stripe_card_token
        stripe_customer.save()
    except CardError as e:
        expected_error(stripe_util.get_card_error_message(e))

@view_config(
    route_name='json.cancel_subscription',
    permission='admin',
    request_method='POST',
    renderer='json'
)
def json_cancel_subscription(request):
    chance = URL_PARAM_CHANCE in request.params
    feedback = request.params[URL_PARAM_FEEDBACK]

    if not chance:
        stripe_customer, _ = _get_stripe_customer_and_quantity(request)
        stripe_customer.delete()
        sp = get_rpc_stub(request)
        sp.delete_stripe_customer_id()

    title = "[Subscription Cancellation] {} {}".format(
            authenticated_userid(request),
            "- CHANCE WITHIN 24 HRS" if chance else "(members to be removed)")

    send_sales_email(title, "Feedback: {}".format(feedback))
