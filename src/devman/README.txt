===========================
Device Management Interface
===========================

Interface:
    GET http://<host>:9019/last_seen/<did>
    GET http://<host>:9019/polling_interval

Example:
    GET http://sp.aerofs.com:9019/last_seen/155aa68bc35e426f9535619fb1d89d14
    GET http://sp.aerofs.com:9019/polling_interval

Where the polling interval is specified in seconds.

The last_seen page will return a 500 if the DID is malformed, a 404 if the
device is not found in the redis db (i.e. if the device has never been seen
online) and the last seen time in epoch seconds otherwise.

Default 5 minute granularity as specified in devman.yml (300 seconds).
