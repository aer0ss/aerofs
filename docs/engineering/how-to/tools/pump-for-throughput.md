# Test Throughput with Pump

## Why

Test actual throughput between two clients over a given transport (currently Zephyr or Direct TCP)

## How

Excellent question.

### Setup

You should install two clients, let's say in `~/rtroot/user1` and `~/rtroot/user2`, but shut them down before running Pump.

Then grab the DID of the clients, for example by looking into the configuration database (key `device_id`).

    sqlite3 ~/rtroot/user1/conf
    SELECT * FROM c;

### Running Pump

Pump can be launched exactly like `gui`, `cli` or `daemon`: with `approot/run` (or `approot/rundebug`):

    approot/run ~/rtroot/user1 pump z send <DID_user2>

Do the same for `user2`, pointing to `DID_user1`.

## Parameters

You can choose the transport:

* **z** uses Zephyr
* **t** uses Direct TCP
