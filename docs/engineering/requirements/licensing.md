# Requirements of the Licensing System

**A license should include the following data:**

- Customer ID
- Expiry date
- Maximum number of users

**The licensing service should:**

- Enforce license expiry time
- Enforce the number of users (and AeroFS should support user deletion)
- Allow addition of feature-based enforcement in the future. However for the time being we don't have any feature enforcement.
- Display license data on one of the adminstration Web pages as well as the license's current status.
- Forbid the user to upload licenses belonging to different customer IDs than the customer ID of the previous license.

**When the license is about to expire, the licensing service should:**

- Email notify all organization administrators three times: a month, a week, and a day ahead.

**When the license expires, the licensing service should:**

- Shutdown the following services: SP and XMPP
- Optionally, show "license has expired" message on the Web site
- Allow admins to renew the license with minimal service disruption (e.g. avoid discarding the deployed appliance and client reinstallation and relogin if possible)

### Features _not_ required for the first implementation

- A call-home or challenge-response scheme that verifies or activates the license by communicating with servers hosted by AeroFS.
- A license expiry mechanism that forces a license to expire before its expiry date.

