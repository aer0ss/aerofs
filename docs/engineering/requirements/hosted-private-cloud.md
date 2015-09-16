# Hosted Private Cloud

## Objective

The Hosted Private Cloud will allow prospects to try AeroFS more easily and more quickly.

Today, prospects have a difficult time getting AeroFS up and running. They need to configure their
network, firewall rules, email server, and optionally add an SSL certificate. At any step they
might abandon the trial. This is one of the most important problems in getting more customers.

To solve this, we will set up and host the AeroFS appliances for our prospects. We will host the
appliance in EC2 for 15 days. After the 15 days, we will transfer the customer's appliance and data
to their own infrastructure.

## Most Important Thing

* Admins should be up and running in the least possible amount of time and effort.

## Requirements

### Sign up

Admins need to provide:

* First and last name.
* Company name.
* Phone number.
* Subdomain name 'xxx' for the URL xxx.hosted.aerofs.
* Email address to receive "create first user" email.

### User Data Tracking

- Mixpanel to track user interaction with signup flow.
- Track number of shared files and number of people in org for sales leads.

### Provisional Appliance

- The appliance lasts for 15 days. After 15 days the appliance is backed up and turned off or
  placed in maintenance mode. This process can be manual for now.
- Sales team has the ability to extend the trial period.

### Migration

- When the admin is ready to buy, she or he can move the appliance on-premise.
- The data should stay intact.
- The migration should be as effortless as possible for the admin.

## Future

- Ability for the appliance to be hosted forever on our servers.
