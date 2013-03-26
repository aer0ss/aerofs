
Invariances than govern interaction between AeroFS and Stripe
----

o A team has a Stripe customer ID in SP database <=> the team has a Stripe customer record

o the team has a Stripe customer record <=> the team has a Stripe subscription

o the team has a subscription <=> the team is on a paid plan and paying

Therefore:

o A team has a Stripe customer ID in SP database <=> the team is on a paid plan and paying

Note that some old teams don't have customer IDs while the number of users have exceed what's allowed in the free plan. The system is designed so that these teams can reduce numbers of users no problem, but will prompt for a payment method when they increase the number.


If a team wants to avoid payments
----

1. remove customer ID from SP database (do this step first!)

    // this is to show the customer id for Step 2.
    select o_stripe_customer_id from sp_organization where o_id in (select u_org_id from sp_user where u_id='<email_address>');
    update sp_organization set o_stripe_customer_id = null where o_id in (select u_org_id from sp_user where u_id='<email_address>');

2. remove Stripe customer record

Later we may want to automate the process.


After subscription removal, if the customer deletes users, the system will NOT re-subscribe the customer.

After subscription removal, if the customer adds users, the system WILL ask the user for credit card and re-subscribe the user, and will remain subscribed until we manually remove it again. It is true even if the customer deletes users after the subscription removal and before user addition. For example, if the user has 6 users at the time of subscription removal, then he deletes 2 users. As soon as he adds a user the subscription will resume.

If a customer ID is in SP database but not in Stripe
----

The system requires that all customer IDs in SP database to exist in Stripe database. If a Stripe customer is missing due to mis-operations, simply remove the customer ID from SP database.
Next time when the customer adds users, the system will prompt the user to enter payment method again.