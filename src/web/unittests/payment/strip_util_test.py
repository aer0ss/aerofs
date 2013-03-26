from aerofs_sp.gen.sp_pb2 import PBStripeData
from ..test_base import TestBase
from mock import Mock

class SetStripCustomerSubscriptionTest(TestBase):
    def setUp(self):
        self.setup_common()
        self.stripe_customer = Mock()
        self.stripe_data = PBStripeData()

        from web.views.payment import stripe_util
        stripe_util.get_stripe_customer = Mock(return_value=self.stripe_customer)

    def test_should_do_nothing_if_no_customer_id(self):
        # Do not set customer id
        self.stripe_data.quantity = 100

        self._update()

        self._verify_interaction(0)

    def test_downgrade_should_update_plan_if_customer_id_exists(self):
        self._set_customer_id()
        self.stripe_data.quantity = 3

        self._update()

        self._verify_interaction(1)

    def _verify_interaction(self, update_count):
        self.assertEqual(update_count,
            self.stripe_customer.update_subscription.call_count)

    def _set_customer_id(self):
        self.stripe_data.customer_id = "wwh"

    def _update(self):
        # place the import here rather than file header since it has to be done
        # _after_ setup_common(). TODO (WW) a better approach?
        from web.views.payment import stripe_util
        stripe_util.get_stripe_customer = Mock(return_value=self.stripe_customer)
        stripe_util.update_stripe_subscription(self.stripe_data)