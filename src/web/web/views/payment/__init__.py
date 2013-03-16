def includeme(config):
  config.add_route('manage_payment', '/admin/manage_payment')
  config.add_route('json.new_stripe_customer', '/admin/new_stripe_customer')
  config.add_route('json.update_credit_card', '/admin/update_credit_card')
