# Transport Presence Service Design Document

## Overview

The following document describes the rationale and design of the `Presence Service` within an AeroFS transport implementation.

## Definitions

* Transport: component that allows the local node to communicate to multiple remote devices over one-to-one and one-to-many channels
* Device: peer identifiable by a DID
* Presence: whether a device is `Potential Available` or `Unavailable`
* Potentially Available: whether a device can *potentially* be communicated with
* Unavailable: whether a device *cannot* be communicated with
* Presence Service: component that issues a notification of a device's current presence when it *transitions* from one presence to another
* Unicast Service: component that communicates over a one-to-one channel between the local and remote device
* Multicast Service: component that communicates over a one-to-many channel between the local and remote devices
* Link State Service: component that issues notifications when a local interface is added or removed

## Requirements

The Presence Service should notify listeners when a device transitions to/from the `Potentially Available` and `Unavailable` state. Design simplicity and correctness are the primary design criteria.

## Proposed Design

Presence can be determined completely from the following inputs:

1. Whether *any* network interface is available on the local system
2. Whether the Multicast Service has recently heard from a remote device or can reach the remote device
3. Whether a valid connection to the remote device exists on the Unicast Service
4. Whether the Unicast Service is able to make one-to-one channels to remote devices
5. Whether the Multicast Service is able to make one-to-many channels to remote devices

Presence should be recomputed whenever any of these inputs change, and presence notifications broadcast if this change results in a transition.

It is obvious that the inputs above can be grouped into three categories:

* unicast-related
  * 3, 4
* multicast-related
  * 2, 5
* local link state
  * 1

It is further obvious that **local link state** plays a direct role in whether either the Unicast Service or Multicast Service can create channels from the local device to remote devices. As a result we can further group the inputs as:

* unicast-related
  * 1, 3, 4
* multicast-related
  * 1, 2, 5

This allows us to visualize the interaction between the different services and presence computation as follows:

<pre>
                               +-------------------+
                               |                   |
                               |                   |
               +-------------->+  Unicast Service  +--------------+
               |               |                   |              |
               |               |                   |              |
               |               +-------------------+              |
               |                                                  V
     +---------+---------+                              +---------+---------+
     |                   |                              |                   |
     |                   |                              |                   |
     | LinkState Service |                              |  Presence Service +-----> {DID => Presence}*
     |                   |                              |                   |
     |                   |                              |                   |
     +---------+---------+                              +----------+--------+
               |                                                   ^
               |               +-------------------+               |
               |               |                   |               |
               |               |                   |               |
               +-------------->+ Multicast Service +---------------+
                               |                   |
                               |                   |
                               +-------------------+
</pre>

Presence is primarily used to determine whether one-to-one communication can be established between the local and remote device. This means that *the most important* criteria in computing presence is the readiness, or the ability, of the Unicast Service to create one-to-one channels. This means that (4) above, is the gating factor in presence computation.

This gives us our final presence model:

**Presence(device) = (Multicast_Availability(device) || Unicast_Availability(device)) && Readiness(Unicast Service)**

