#Customer Analytics

This document outlines the technical design of a system to collect and expose analytics data
(configuration information & usage statistics) from AeroFS Private Cloud deployments.

##The Components
The system will be composed of five components:

- A final destination for our data. This component will store the collected data and provide an
interface for the Customer Success team (and anybody else) to view metrics and reports created based
on the data. We will use Mixpanel for this.
- An on-site analytics server that customers can optionally set up. This server application will
simply dump the JSON data that it receives to disk so that it may be viewed by a system admin.
- A Segment instance that will be the initial exterior destination for analytics data. Segment will
be used to relay analytics data to Mixpanel.
- A new container in the AeroFS appliance which will be the initial destination for all analytics
data.
- Analytics Client implementations in one or various languages to provide a consistent interface to
programatically create and record events.

##Mixpanel
Mixpanel is a cloud-based analytics solution that provides a nice UI for creating visualizations
based on your data. Data is uploaded to Mixpanel in the format of "events". Since we need the
ability to dump analytics data for customer inspection, we will pass around Mixpanel-compliant
JSON objects in our systems until we are ready to send them to Mixpanel itself.

###Analytics Data Specification
Analytics data will be in the form of JSON objects representing **events**.

Every event will have the following fields:

- **event:** The type of the event that the JSON object represents
- **customer_name**: We will retrieve the Customer Name and pass it in this field in order to
allow segmenting by Customer.
- **time:** A timestamp indicating when the event happened. By including the timestamp on the
event itself, we allow ourselves to batch events or import old events.
- **token**: The Mixpanel project's token. This identifies which project in Mixpanel should
receive the event. (TODO: figure out when this gets added to an event)

Conditional fields:
- **distinct_id**: This is a special Mixpanel field used for certain grouping functionality in
Mixpanel's front-end. In order to track Active Users, we will pass in a hash of the user_id in
this field, for the appropriate events.

Additionally, arbitrary property fields can be added to an event to describe the context in which
it occurred, or to give further clarifications about what type of event has occurred. For example,
nearly all events will contain a **value** field, in order to indicate a boolean value, or to
indicate an integer value as the result of aggregation.

###Why Mixpanel?
This section will detail why we've decided to use Mixpanel as our front-end.

The main evaluation criteria for these systems are as follows:

- Data display capabilities: Our primary use case for this data is visualizing it and viewing it in
dashboard format to give a high-level overview on a customer-by-customer basis. A dashboard is
composed of widgets which display graphs/tables/numbers representing the underlying metrics that we
have collected data for in our system. The tool chosen should provide some solution to this.
- Developer effort: Whatever solution is chosen will be used by not only the Customer Success team
but also the Sales team. General use of the tool should be available without dev effort, and changes
and customizations should require a minimum of developer involvement.
- Flexibility: The tool chosen should be flexible and extensible. Additional metrics in the future
should be easily addable and visualizable. Existing data should also be visualizable in different
ways.
- Cost: The cost of the solution is also a factor.

####Alternatives
The three possibilities considered were Google Analytics, Mixpanel and Splunk. Initially, Splunk
was chosen as the front-end solution, but Mixpanel has been chosen instead now, due to concerns
about development effort required for Splunk.

**Google Analytics**

Pros

- Cost: Google Analytics is free for 10 million hits per month. This would potentially be sufficient
with clever aggregation of data.
- Dashboards: Google Analytics supports dashboarding.
- Developer effort: Dashboard creation and tweaking is all done through the UI and requires no
developer intervention.

Cons

- Cost: If we cannot guarantee fewer than 10 million hits per month, Google Analytics becomes very
expensive, at $150 000 per year.
- Flexibility: Google Analytics is highly inflexible, and runs at cross purposes to many of our
goals. Google Analytics would force us to use a very restricting event model to submit data, to the
detriment of what we can collect. Google Analytics events have a maximum of 4 property fields, and
thus provide very limited support for tracking of events other than a simple count. State-based
metrics would also be difficult to represent in this event model.
- Misc: Google Analytics is very website-analytics focused, and it shows in the UI. Almost all of
the UI consists of extraneous features that are useless to us, and would simply get in the way and
confuse.

**Mixpanel**

Pros

- UI & visualizations: There is a very nice UI that users can use to delve into
the data. After data arrives in Mixpanel's system, it is immediately visualizable in customizable
ways.
- Developer effort: Mixpanel would require no additional developer effort once the event data
exists.

Cons

- Dashboards: Mixpanel provides little to no support for dashboards. It might be possible to use a
third-party dashboarding system to provide some of this functionality if desired.
- Cost: Mixpanel would cost around $30 000 per year. See the Cost Analysis section for more.

**Splunk**

Pros

- Dashboards: Splunk provides powerful dashboarding support, and if any deficiencies are found, it
can be extended and changed programmatically. Splunk is able to provide easily accessible
dashboards.
- Flexibility: Splunk imposes no particular data model. It essentially just accepts strings, but we
will use it with JSON objects as the primary data format. The data is indexed and easily
programmatically queryable through the main UI.
- Cost: While Splunk is not free, it is somewhat cheaper than Mixpanel.

Cons

- Developer effort: In order to harness maximum potential from Splunk, some extra development work
will be required. Additionally, certain future extensions (like adding new customizations to
visualizations) would require developer intervention.

####Comparison Conclusions
Google Analytics is too clunky and restrictive in its event model to support what we want to do. It
is simply not designed for our use case, as is evident in the UI, and it would be a constant battle
to try to visualize our underlying data in meaningful ways using Google Analytics.

Splunk is a flexible alternative, with a fair bit of potential, but would require extra developer
work. It would be possible to run into a scenario where Splunk would have data that we were
interested in viewing, but that would not be adquately visualizable without developer effort.

In light of the ease of use for the end user, Mixpanel is a good choice. It will meet most-to-all
of our needs in terms of visualizing our data, and will require no extra development effort.
Mixpanel will require certain sacrifices in order to keep costs down as well. See the Cost Analysis
section for details.

###Metric Types
The Metrics that we will be collecting have two general types, state-based and usage-based

**Usage-based metrics**
are counts or aggregated event values from events generated by actions in AeroFS. Usage-based
metrics have a time dimension in order to show how the events generated in AeroFS change over time.
These metrics will give insight into how our customers use our product in day-to-day usage. In
general, it will not be possible to collect the values for these metrics after the fact, so events
must be generated as they happen. However, due to cost concerns with Mixpanel, we will need to
aggregate events of this nature before sending.

**State-based metrics**
 are metrics about the current configuration of the appliance. For example, version number and
whether Team Server sharding is enabled are both state-based metrics. State-based metrics will be
tracked by sending status update events at regular intervals (e.g. daily). Then, in Mixpanel, only
events that have occurred most recently (in general, today) should be visualized for this type of
event.

###Cost Analysis
This section will consist of estimates of how many events each metric will require, followed by a
total estimate of events per day per customer. This will then be used to calculate a ballpark
figure for how much we can expect to pay for Mixpanel.

**Feature Enabled Metrics:**
There are 9 "feature enabled" metrics that are essentially equivalent within the framework of
customer analytics, as outlined in the spec. Each of these will be represented by an event of
type "Feature State Check", or something similar, with a sub-type field representing the name
of the feature. Then, a boolean value will indicate whether or not the feature is enabled. These
metrics will require one event per day to keep the value in Mixpanel updated.

These are as follows:

- Auditing Enabled
- Desktop Client Autorization Enabled
- MDM Enabled
- AD/LDAP Enabled
- Sign-in Required For Link Access Enforced
- Password Restriction Enabled
- LDAP Group Syncing Enabled
- Email Integration Enabled
- Team Server/Storage Agent Installed

**Product Usage Metrics:**
In general, these will be represented by events with a count of some current number of objects
in the appliance. E.g. Windows desktop client installations or total users. For 12 of these
metrics, a single update per day will suffice. There are 9 metrics that should be reported more
frequently, so that we do not have to wait 24 hours to receive updates.

These 9 metrics are:

- Number of New User Signups
- Number of Desktop Client Installs
- Number of Mobile App Installs
- Number of Links Created From Desktop/Web Interface
- Number of Folder Invitations Sent/Accepted
- Number of Invitations Sent/Accepted.

The 12 state-based metrics are:

- Number of Users
- Number of Windows/OSX/Linux Desktop Client Installs
- Number of Groups
- Number of Android/iOS App Installs
- Number of Internal Users/External Users
- Max File Size Uploaded/Downloaded
- Total Data Synced in Bytes

Note: Max File Size Uploaded/Downloaded and Total Data Synced in Bytes do not really seem
like "state-based" metrics, but they will nevertheless be sent once per day, and are thus included
in this category.

**Success Metrics:**
In the Success Metrics queries, there are 3 metrics that will be collected more frequently than
once per day.

These are:

- Number of Times Team Server Went Offline
- Number of Desktop Client Unlinks
- Number of Mobile App Unlinks.

There are 4 metrics in this category that will be collected once per day.

These are:

- Appliance Version
- Number of Shared Folders
- Max Files in a Shared Folder
- Number of Files in Shared Folders

**Sales queries** as outlined in the spec should require no additional event types.

**Active Users** is a very important metric outlined in the spec. It requires a different method
of tracking in order to get accurate counts. That is, we must keep track of individual users for
this metric in order to prevent duplicate counting. This metric is considered seperately from the
others.

####Cost Analysis
In total, there are 12 metrics that we would like to send more frequently than once per day. There
are also 25 metrics that will be collected once per day. We will use an estimate of 2000 customers
with analytics enabled.

Thus, we will send

    25 * 2000 = 50 000

events once per day, and then an additional

    12 * 2000 = 24 000

usage-based events will be sent at a frequency of 12 times per day.
We will not send the usage-based events if no change occurs. Thus, it is a safe assumption that for
most customers, the bulk of event activity will occur on business days, or about 22 days per month.
If we send each of these events 12 times per day, that results in a total of

    50 000 + (24 000 * 12 * 22) = 6 386 000

per month. This keeps us safely within an upper limit of 8 million events per
month.

However, we must consider the Active Users metric as a special case. In order to reduce dependence
on the appliance, we will send 1 event per active user per day. Then, aggregation of this metric
can be done on a daily/weekly/monthly basis in Mixpanel itself.

An estimate for the maximum number of events that we would send to track Active Users is:

    50 000 * 22 = 1 100 000

where 22 is the number of business days in a month.

This brings the total estimate of events to

    1 100 000 + 6 386 000 = 7 486 000

This level of use of Mixpanel would cost $1150 per month, with the additional $150 charge coming
from the purchase of a People plan to support profiles for our customers.

In addition to Mixpanel costs, there will be a $99 per month cost for a Segment subscription.

###Additional Considerations
In order to maintain an affordable number of events that we send to Mixpanel, we need to aggregate
all usage-based events, and send state updates rather infrequently (once per day). The following
is a small exploration of some of the considerations surrounding this.

####Aggregation
Aggregation is absolutely necessary in order to prevent usage statistics from ballooning into
massive amounts of events. Certain actions would generate thousands of events per day, quickly
rendering Mixpanel unaffordable. Thus, we must aggregate.

However, by aggregating events, we lose the ability to track anything more specific than a count.
For example, suppose we wanted to associate a UUID with Link Sharing events, to see whether a
particular anonymous user is sharing a disproportionate number of links within an organization.
This will not be possible, as that would require us to send a separate event each time a link is
shared, or to aggregate on a per-user basis, both of which can potentially grow in a more or less
unbounded fashion. Thus, any user/device-specific tracking is impossible, as the data we collect
will only be as fine-grained as our event model allows it to be.

According to the specification, this is not necessary functionality, but it is important to be
aware that this could be very difficult to change in the future.

##On-site Collection Server
The on-site analytics data collection server is an optional component for especially
security-conscious customers. We will publish an open source project containing a light-weight
program to accept an incoming event stream and dump the received JSON to disk, so that a system
admin could audit the data that we intend to send to Mixpanel. The software would
also configurably permit automatic relay of data to Mixpanel.

##Analytics Container
A new container will be added to the appliance. The container will serve as the internal endpoint
for analytics data.

This container will validate, process and then send the data to either the Mixpanel server or to the
on-site collection server, if it has been set up.

In addition, the analytics container will be responsible for querying various components of the
appliance at a regular interval (a few times per day, possibly) in order to measure the values
of state-based, and then create corresponding events to send to Mixpanel.

###POST /analytics/events
This is the route that can be accessed in order to track usage events with the appliance. Events
sent to this endpoint will be simple JSON objects with two fields: **event** and **user_id**. All
further processing of the event data will happen inside this container, so the simplified event
suffices. The purpose of the **user_id** field is to support the calculation of
daily/weekly/monthly active user counts inside the analytics container.

###Container Aggregation Design
The analytics container is the brain of the AeroFS analytics set-up. It is responsible for
aggregation and collection of analytics data. As usage events arrive to the container, it will
aggregate them based on event type. Then, periodically, this aggregated data will be sent to
Segment in order to be relayed on to Mixpanel.

For the usage-based events, a simple map of event name to count would suffice. Since we will send
usage-based events every two hours, this would guarantee that we will lose no more than 2 hours of
data if the appliance goes down. However, no data loss at all is ideal, and we must consider the
Active Users metrics.

We will use [BoltDB](https://github.com/boltdb/bolt) as a persistence mechanism to track active
users and to aggregate usage-based events throughout the day. BoltDB is a thin layer
that will create only a single file on the filesystem. Bolt is used in production by many
companies, and is the most-starred embedded database for Go on Github.

###Data Collection Settings
In Bunker, there will be two settings that configure how data is collected and sent on to Mixpanel.

**Enable/Disable Analytics:**
This setting controls whether events are generated or not. This setting will be propogated to all
containers and clients that might generate an event. If enabled, all events will be sent to the
analytics container for processing/relay. If disabled, no events will be sent or recorded.

**Enable/Disable Automatic Transmission to AeroFS:**
This setting controls whether the data collected by the analytics container will be transmitted to
our Mixpanel server or to the on-site collection server. Data will be automatically relayed to
Mixpanel by the appliance if enabled. If disabled, the customer must supply the network information
for the server to which to relay the analytics data.

##Analytics Client
The final piece of the puzzle is a standardized interface through which to POST events to the
container. This will be a Java class (and possibly implementations in other languages as well) that
allows a user to create and send a hit to the analytics container.
