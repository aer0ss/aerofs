
Invariances than govern interaction between AeroFS and Stripe
----

o A team has a credit card on file <=> The team has a Stripe customer ID in the SP database
o A team is on the paid plan <=> The team has a Stripe customer ID, AND the team has a Stripe subscription.
o A team on the free plan <=> The team doesn't have a Stripe customer ID, OR the team has a stripe customer ID but has no Stripe subscription.

Note that some old teams don't have customer IDs while the number of users have exceed what's allowed in the free plan. The system is designed so that these teams can reduce numbers of users no problem, but will prompt for credit card when they increase the number.


If a customer wants to avoid payments
----

If a customer wants to avoid payments, remove the customer's subscription from Stripe ONLY.

DO NOT remove the customer from Stripe. It would cause inconsistency between Stripe and SP database, and will lose past payment history (see below).

After subscription removal, if the customer deletes users, the system will NOT re-subscribe the customer.

After subscription removal, if the customer adds users, the system WILL re-subscribe the user, and will remain subscribed until we manually remove it again. It is true even if the customer deletes users after the subscription removal and before user addition. For example, suppose the user has 6 users at the time of subscription removal, then he deletes 2 users. As soon as he adds a user the subscription will resume, even though the new number of users (5) is less than the number at the time of subscription removal.


If a customer ID is in SP database but not in Stripe
----

The system requires that all customer IDs in SP database to exist in Stripe database as well. If a Stripe customer is missing due to mis-operations, simply remove the customer ID from SP database with:

    update sp_organization set o_stripe_customer_id = null where o_id = <org_id>;

Next time when the customer adds users, the system will prompt the user to enter payment method again.