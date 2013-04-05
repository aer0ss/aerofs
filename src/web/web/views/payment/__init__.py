def includeme(config):
  config.add_route('manage_subscription', 'admin/manage_subscription')
  config.add_route('start_subscription_done', 'admin/start_subscription_done')
  config.add_route('json.create_stripe_customer', 'admin/create_stripe_customer')
  config.add_route('json.update_credit_card', 'admin/update_credit_card')
  config.add_route('json.cancel_subscription', 'admin/cancel_subscription')