import os
import logging

import stripe

log = logging.getLogger(__name__)

STRIPE_PUBLISHABLE_KEY = os.environ['STRIPE_PUBLISHABLE_KEY']
stripe.api_key = os.environ['STRIPE_SECRET_KEY']

def new_stripe_customer(email, stripe_card_token):
    # see also: https://stripe.com/docs/api?lang=python#create_customer
    return stripe.Customer.create(
        email=email,
        card=stripe_card_token
    )

def get_stripe_customer(stripe_customer_id):
    # see also: https://stripe.com/docs/api?lang=python#retrieve_customer
    return stripe.Customer.retrieve(stripe_customer_id)

def set_stripe_customer_card(stripe_customer, new_stripe_card_token):
    # see also: https://stripe.com/docs/api?lang=python#update_customer
    stripe_customer.card = new_stripe_card_token
    stripe_customer.save()

def set_stripe_customer_subscription(stripe_customer, stripe_plan_id):
    # see also: https://stripe.com/docs/api?lang=python#update_subscription
    if not has_subscription(stripe_customer):
        stripe_customer.update_subscription(plan=stripe_plan_id)
    else:
        # Inherit the trial end time from the previous subscription. Otherwise,
        # Stripe will restart trial using the new subscription's definition
        # regardless whether the user was trialing previously :S
        if not is_trialing(stripe_customer): trial_end = 'now'
        else: trial_end = stripe_customer.subscription.trial_end
        stripe_customer.update_subscription(plan=stripe_plan_id,
            trial_end=trial_end)

def has_subscription(stripe_customer):
    return stripe_customer.subscription is not None

def is_trialing(stripe_customer):
    return stripe_customer.subscription.trial_end is not None

def get_stripe_card(stripe_card_token):
    # see also: https://stripe.com/docs/api?lang=python#retrieve_token
    return stripe.Token.retrieve(stripe_card_token)

def get_stripe_customer_invoices(stripe_customer):
    # see also: https://stripe.com/docs/api#list_customer_invoices
    return stripe.Invoice.all(customer=stripe_customer.id).data

def cancel_stripe_customer_subscription(stripe_customer):
    # see also: https://stripe.com/docs/api#cancel_subscription
    return stripe_customer.cancel_subscription()

def get_card_error_message(e):
    body = e.json_body
    err = body['error']
    return err['message']