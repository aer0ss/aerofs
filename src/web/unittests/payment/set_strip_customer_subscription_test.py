from ..test_base import TestBase
from mock import Mock

_PLAN_ID = 'test plan id'

class SetStripCustomerSubscriptionTest(TestBase):
    def setUp(self):
        self.setup_common()
        self.stripe_customer = Mock()

    def test_should_not_specify_trial_end_for_new_subscription(self):
        # Set subscription to None to mock a new customer
        self.stripe_customer.subscription = None

        self._set_subscription()
        self.stripe_customer.update_subscription\
                .assert_called_once_with(plan=_PLAN_ID)

    def test_should_end_trial_now_for_non_trial_customer(self):
        """
        If we don't specify to end the trial now Stripe will convert a paying
        customer to a trial customer when updating to a subscription with
        free trial :S
        """

        # Set trial_end to None to mock a paying customer
        self.stripe_customer.subscription.trial_end = None

        self._set_subscription()
        self.stripe_customer.update_subscription\
                .assert_called_once_with(plan=_PLAN_ID, trial_end='now')

    def test_should_inherit_trial_period_for_trial_customer(self):
        """
        If we don't manually inherit the trial end date from the previous
        subscription Stripe will restart the trial period when updating
        subscriptions with free trial :S
        """

        self.stripe_customer.subscription.trial_end = 1234

        self._set_subscription()
        self.stripe_customer.update_subscription\
        .assert_called_once_with(plan=_PLAN_ID, trial_end=1234)

    def _set_subscription(self):
        # place the import here rather than file header since it has to be done
        # _after_ setup_common(). TODO (WW) a better approach?
        from web.views.payment.stripe_billing import\
            set_stripe_customer_subscription

        set_stripe_customer_subscription(self.stripe_customer, _PLAN_ID)