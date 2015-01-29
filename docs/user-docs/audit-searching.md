# Data Leak Prevention - Searching Audit Events with Splunk

## Purpose

This document summarizes some of the common Splunk searches that can be used with AeroFS Private Cloud Audit Events.

### All File Transfers

The following search shows a table of file transfer events. This table includes the source and desination IPs of the devices in question as seen by the appliance.

    file.transfer |
        spath |
        rename verified_submitter.device_id as src_device |
        rename soid.sid as sid |
        rename soid.oid as oid |
        rename destination_device as dst_device |
        table timestamp, src_device, dst_device, sid, oid |
    join dst_device
        [
            search device.signin |
            spath |
            rename device_id as dst_device |
            rename ip as dst_ip |
            table dst_device, dst_ip
        ] |
    join src_device
        [
            search device.signin |
            spath |
            rename device_id as src_device |
            rename ip as src_ip |
            table src_device, src_ip
        ] |
    table timestamp, src_device, dst_device, src_ip, dst_ip, sid, oid

### File Transfers Across Network Boundaries

This search is similar to the seach above, except that it goes a step further and filters the results based on source and destination subnets of interest.

    [ all_file_transfers_search ] |
        where (cidrmatch("204.17.5.32/27", src_ip)) |
        where (cidrmatch("204.17.5.128/27", dst_ip))

