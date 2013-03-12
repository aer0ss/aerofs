===========================
Device Management Interface
===========================

Interface:

    GET http://<host>:9020/last_seen/<did>

        On success:
            Returns the last seen time of the device in epoch seconds. (With the
            given polling interval granularity, default 5 mins).

        On failure:
            500: if the DID is malformed.
            404: if the device has never been online.

    GET http://<host>:9020/polling_interval

        Returns the polling interval, in seconds.

Examples:

    GET http://sp.aerofs.com:9020/last_seen/155aa68bc35e426f9535619fb1d89d14
    GET http://sp.aerofs.com:9020/polling_interval
