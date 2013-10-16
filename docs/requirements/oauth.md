# Requirements of the OAuth System

The OAuth system should support OAuth 2.0 workflow. Additional requirements are listed below.

## Requirements from resource services

The OAuth library used by resource services (e.g. SP, Havre, daemons, etc) should:

- Cache authentication results for a configurable amount of time

- Allow services to retrieve the organization ID of the authorized user

## Requirements from clients

- (tentative. don't implement it yet) an authorized team adminstrator can request an access token that uses the organization ID as the user ID. This may be needed to help SP and daemons enforce ACLs for team administrative activities.