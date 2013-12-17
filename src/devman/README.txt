===========================
Device Management Interface
===========================

Interface:

    GET http://<host>:9020/devices/<did>
        On success:
            Return json encoded device information. Specifically:
                last_seen_time:
                    The time (in epoch seconds) that the device was last seen
                    online.
                ip_address:
                    The last known IP address for this device.
        On failure:
            500: if the DID is malformed.
            404: if the device has never been online.

    GET http://<host>:9020/polling_interval
        Returns the polling interval, in seconds.

Examples:

    GET http://<host>:9020/devices/155aa68bc35e426f9535619fb1d89d14
        Returns: {"last_seen_time": 1363304610, "ip_address": 50.196.168.145}

    GET http://<host>:9020/polling_interval
        Returns: 300
