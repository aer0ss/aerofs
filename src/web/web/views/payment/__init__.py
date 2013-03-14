def includeme(config):
  # The "/business/activate" string must be identical to the one in
  # RequestToSignUpEmailer.java.
  # TODO (WW) use protobuf to share constants between Python and Java code?
  config.add_route('business_activate', '/business/activate')
  config.add_route('business_activate_done', '/business/activate/done')
  config.add_route('manage_credit_card', '/admin/manage_credit_card')
  config.add_route('manage_subscription', '/admin/manage_subscription')
  config.add_route('cancel_subscription', '/admin/cancel_subscription')
  config.add_route('cancel_subscription_done', '/admin/cancel_subscription_done')
  config.add_route('payments', '/admin/payments')
  config.add_route('free_user', '/admin/free_user')