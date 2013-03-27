def includeme(config):
  config.add_route('manage_payment', '/admin/manage_payment')
  config.add_route('json.create_stripe_customer', '/admin/create_stripe_customer')
  config.add_route('json.update_credit_card', '/admin/update_credit_card')
