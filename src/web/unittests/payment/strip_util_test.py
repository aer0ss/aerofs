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

        self._upgrade()
        self._downgrade()

        self._verify_interaction(0, 0)

    def test_should_cancel_if_quality_is_zero(self):
        self._set_customer_id()
        self.stripe_data.quantity = 0

        self._upgrade()
        self._downgrade()

        self._verify_interaction(0, 2)

    def test_should_do_nothing_if_quality_is_zero_and_no_subscription(self):
        self._set_customer_id()
        self.stripe_data.quantity = 0
        self.stripe_customer.subscription = None

        self._upgrade()
        self._downgrade()

        self._verify_interaction(0, 0)

    def test_downgrade_should_do_nothing_if_no_subscription(self):
        self._set_customer_id()
        self.stripe_data.quantity = 3
        self.stripe_customer.subscription = None

        self._downgrade()

        self._verify_interaction(0, 0)

    def test_downgrade_should_update_plan_if_subscription_exists(self):
        self._set_customer_id()
        self.stripe_data.quantity = 3

        self._downgrade()

        self._verify_interaction(1, 0)

    def test_upgrade_should_update_plan_if_no_subscription(self):
        self._set_customer_id()
        self.stripe_data.quantity = 3
        self.stripe_customer.subscription = None

        self._upgrade()

        self._verify_interaction(1, 0)

    def test_upgrade_should_update_plan_if_subscription_exists(self):
        self._set_customer_id()
        self.stripe_data.quantity = 3

        self._upgrade()

        self._verify_interaction(1, 0)

    def _verify_interaction(self, update_count, cancel_count):
        self.assertEqual(cancel_count,
            self.stripe_customer.cancel_subscription.call_count)
        self.assertEqual(update_count,
            self.stripe_customer.update_subscription.call_count)

    def _set_customer_id(self):
        self.stripe_data.customer_id = "wwh"

    def _upgrade(self):
        # place the import here rather than file header since it has to be done
        # _after_ setup_common(). TODO (WW) a better approach?
        from web.views.payment import stripe_util
        stripe_util.upgrade_stripe_subscription(self.stripe_data)

    def _downgrade(self):
        # place the import here rather than file header since it has to be done
        # _after_ setup_common(). TODO (WW) a better approach?
        from web.views.payment import stripe_util
        stripe_util.get_stripe_customer = Mock(return_value=self.stripe_customer)
        stripe_util.downgrade_stripe_subscription(self.stripe_data)