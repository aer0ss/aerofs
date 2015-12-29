# Private Cloud Customer Analytics

## Objective

Today, we don't have any insight into how customers are using AeroFS within their company. We lack
basic information such as how many users are using AeroFS, how many shared folders do they have,
how many Team Servers, what version they are running, etc.

The main motivation behind Customer Analytics is as follows:

* Enable Customer Success team to have proactive conversations with customers around product usage
and how we can make things better.
* Enable Sales team to identify customers who demonstrate steady growth and actively reach out to
them to upsell/renew.


## Important Things to Keep in Mind

* Analytics should be an opt-in feature
* We should not collect any sensitive information from customers
* Should not take any additional effort on Admin's part to enable analytics on their appliance


## Functional Requirements


### Data Sets of Interest

#### State-based information
This information will give us an idea of a customer's current state.

Example data points:

- Is LDAP enabled?
- Is LDAP group sync enabled?
- What is the current appliance version number?
- What is the total number of users in the system?
- What is the total number of shared folders in the system?
- How many Team Servers have been deployed?
- Is Team Server sharding enabled?

#### Usage-based information
This information will give us an idea of product usage trends over a given period of time. It would
be particularly beneficial to the product and marketing team to gauge how customers are actually
using AeroFS.

Example data points:

- Number of files synced
- Number of Web sign-ins
- Number of links created
- Number of new sign-ups
- Number of shared folder creations
- Number of user deletions


### Enabling Analytics During Appliance Setup
- The systems admin should be able to select whether or not they want to send analytics information
to AeroFS during the setup. Similar to how we currently have a checkbox for collecting appliance
setup experience data. This option should be checked by default.
- The analytics option should be available to all users regardless of whether they're using the
free license or business license.


### Change Analytics Options via Bunker Settings Page
The following analytics options should be available to the admin in bunker settings page:

- Disable analytics [_Should already be checked if they opted out of sending analytics during 
appliane setup_]
- Enable analytics [_Should already be checked if they agreed to send analytics during appliance 
setup_]
    - Send analytics data to AeroFS [_Should already be checked if they agreed to send analytics 
during appliance setup_]
    - Send analytics data to on-site server
        - Configure server (hostname, port, certificate)

### On-Site Analytics Collection Server
Setting up an on-site analytics collection server is optional. It's purpose is to allow admins to 
take a look at the analytics data before giving the green light to send it over to AeroFS.

   - Admins should be able to view the JSON dump of analytics data via command line.
   - The following options should be configurable from the analytics server:
       - Send analytics data to AeroFS
       - Don't send analytics data to AeroFS
   - If they choose to send data to AeroFS, analytics data will get relayed from the appliance to 
AeroFS through the analytics server


### Generate Reports From Analytics Data
We should be able to perform queries and generate reports using the data we collect. The type of 
information we want to extract are as follows.

Percentage of customer that have:

- Auditing enabled
- Desktop client authorization enabled
- MDM enabled
- AD/LDAP enabled
- Sign-in required for link access enforced
- Password restriction enabled
- LDAP group syncing enabled
- Email integration (MS Exhange Plugin) enabled
- Team Server/Storage Agent installed


Product Usage Queries:

- Number of new user signups over the last X days by customer ID
- Number of users in customer org versus number of desktop clients installed
- Number of users in customer org versus number of mobile app installations
- Number of Windows, OSX, Linux desktop client installations by customer ID
- Number of links created from desktop client versus Web interface by customer ID
- Number of groups based on customer ID
- Number of android app users versus iOS app users
- Number of folder invitations sent versus accepted
- Number of invitations sent versus accepted
- Number of internal users (LDAP) versus external users (locally managed)
- Max file size uploaded/downloaded via Web in the last X days by customer ID
- Total amount of data (bytes) synced in the last X days


Success Queries:

- Number of times Team Server went offline in the last X days
- Number of desktop client unlinks/re-installs/restarts in the last X days by customer ID
- Number of mobile app unlinks in the last X days
- Customer count based on appliance version number
- Average number of shared folders by customer size (total number of users)
- Average number of files in a shared folder
- Max number of files in a shared folder
- Number of shared folders by customer ID
- Percentage of customers who successfully completed appliance setup and installed a desktop client


Sales Queries:

- Free accounts that are currently at X number of users listed by customer ID
- Number of free private cloud accounts that have less than X number of users versus greater than 
  X number of users
- Paid accounts that are approaching their license quota listed by customer ID

