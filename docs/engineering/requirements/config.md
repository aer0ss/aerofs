# Synopsis
This document outlines the design for the configuration system, both services
and clients, and the migration from today's system to tomorrow's system.

# Problem
The configuration system as it exists today is a constant source of confusion,
frustration, and bugs for developers. For new developers using the
configuration system, it means taking more time to develop, test, and debug an
application if the said application uses the configuration system.

Configuration system has evolved to the current state for historical reasons.
Most of those reasons have changed due to dockerization, so let's modernize the
configuration system.

# Vision
The configuration system should:

- simply be a persistent key-value store.
- load the initial configuration from disk.
- allow site admins to modify the configuration from bunker.
- allow other services and clients to read the configuration.

# Client-Visible Changes

- what you read is what you've written, same key and same value.

# Plan of Execution

- Clean up usages in the desktop client.
    - Remove default values; they existed purely to support HC vs. PC.
    - Remove using configuration properties via static final const.
    - Remove `is_private_deployment`.
    - Load in labelling as configuration properties.
- Implement a new config service.
    - Reads the same external.properties to support appliance upgrade.
    - Serves out different routes.
- Update other servers to read from and write to the new routes.
- Update the desktop and mobile clients to read from the new routes.
- Burn the old service.
