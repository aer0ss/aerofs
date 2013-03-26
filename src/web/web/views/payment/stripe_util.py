import os
import logging
import stripe

log = logging.getLogger(__name__)

STRIPE_PUBLISHABLE_KEY = os.environ['STRIPE_PUBLISHABLE_KEY']
stripe.api_key = os.environ['STRIPE_SECRET_KEY']

# URL param keys
URL_PARAM_STRIPE_CARD_TOKEN = 'card_token'

# This file only contains functionalities that require invoking global Stripe
# functions. Please do not create functions that access member fields or
# functions of an Stripe object.

def new_stripe_customer(email, stripe_card_token):
    # see also: https://stripe.com/docs/api?lang=python#create_customer
    return stripe.Customer.create(
        email=email,
        card=stripe_card_token
    )

def get_stripe_customer(stripe_customer_id):
    """
    Raise an exception if the customer is not found or is deleted
    """
    # see also: https://stripe.com/docs/api?lang=python#retrieve_customer
    customer = stripe.Customer.retrieve(stripe_customer_id)
    if not customer or (hasattr(customer, "deleted") and customer.deleted):
        raise Exception('Stripe customer {} is not found or has been deleted'
                .format(stripe_customer_id))
    return customer

def get_stripe_card(stripe_card_token):
    # see also: https://stripe.com/docs/api?lang=python#retrieve_token
    return stripe.Token.retrieve(stripe_card_token)

def get_stripe_customer_invoices(stripe_customer):
    # see also: https://stripe.com/docs/api#list_customer_invoices
    return stripe.Invoice.all(customer=stripe_customer.id).data

def get_card_error_message(e):
    return e.json_body['error']['message']

def upgrade_stripe_subscription(stripe_data):
    _update_stripe_subscription(stripe_data, True)

def downgrade_stripe_subscription(stripe_data):
    _update_stripe_subscription(stripe_data, False)

def _update_stripe_subscription(stripe_data, upgrade):
    """
    Update or cancel the subscription based on the information in
    stripe_data, which is an instance of PBStripeData defined
    in sp.proto.

    TODO (WW) An SP method has been invoked before this method (that's how
    stripe_data is obtained in the first place). RACE CONDITION here if
    other users update the subscription at the same time!

    Solution: have an automatic tool to periodically check consistency between
    SP and Stripe? Or use a distributed queue service?
    """

    # Change subscription if and only if the customer ID is present. See
    # PBStripeData for detail.
    if not stripe_data.customer_id: return

    sc = get_stripe_customer(stripe_data.customer_id)
    if stripe_data.quantity == 0:
        # Cancel subscription only if there is one; otherwise Stripe will
        # complain.
        if sc.subscription: sc.cancel_subscription()
    elif upgrade or sc.subscription:
        # Do nothing if the customer doesn't have subscription and the
        # subscription is about to downgrade. This is merely to cater the case
        # where we have manually removed the subscription for the customer.
        sc.update_subscription(plan='business_v1', quantity=stripe_data.quantity)
