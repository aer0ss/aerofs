# Customer Success Metrics

## Overview

At a high level, "customer success", as a metric, is an aggregate of:

* Usage (patterns, user counts)
* Engagement
    * Support
        * Engagement
        * Satisfaction
        * Product Health (e.g. bug ratios)
    * CSE engagement
    * Sales engagement

Each of these items factors into the overall customer health number.

## Tools

Currently these items are not automatically aggregated in any way. In the
future, we might choose to look into one of these customer success tools to
accomplish this:

* Gainsight
* Bluenose
* Totango
* Silota
* FrontLeaf

## Usage Data Collection

As a first step toward comprehensive customer success metrics, we would like to
collect anonymized audit stream data directly from our customers. This will
allow the CS and sales teams to have better insight into customer/prospect
usage patters.

Implementation wise, we will provide several options:

1. Default: upload directly AeroFS. (N.B. messaging is important here).
2. Relay through Dryad.
3. Do no upload anonymized usage data.

Deployment through dryad is ideal for our larger enterprise customers, as it
will allow them to inspect payloads. We can sell deploying dryad as a way to:

1. Do log collection, for support.
2. Do incident management.
3. Drive product improvement.

*Coming soon: a detailed engineering design document.*
