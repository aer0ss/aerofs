import os
import logging
import stripe

log = logging.getLogger(__name__)

STRIPE_PUBLISHABLE_KEY = os.environ['STRIPE_PUBLISHABLE_KEY']
stripe.api_key = os.environ['STRIPE_SECRET_KEY']

# URL param keys
URL_PARAM_STRIPE_CARD_TOKEN = 'card_token'

# This file only provides functions that invoke static Stripe library methods.
# Please do not create functions that access member fields or methods of an
# Stripe object.

def create_stripe_customer(email, stripe_card_token):
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

def update_stripe_subscription(stripe_data):
    """
    Update the subscription based on the information in stripe_data, which is an
    instance of sp.proto:PBStripeData.

    TODO (WW) An SP method has been invoked before this method (that's how
    stripe_data is obtained in the first place). RACE CONDITION here if
    other users update the subscription at the same time!

    Solution: have an automatic tool to periodically check consistency between
    SP and Stripe? Or use a distributed queue service?
    """

    # Change subscription if and only if the customer ID is present.
    if not stripe_data.customer_id: return

    sc = get_stripe_customer(stripe_data.customer_id)
    sc.update_subscription(plan='business_v1', quantity=stripe_data.quantity)
