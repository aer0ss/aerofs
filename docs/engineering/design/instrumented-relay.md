Functional Specification: Instrumented Relay Service
==
	v0.2, 2014/05/28

The AeroFS relay server can be used to sync metadata and documents between two devices 
belonging to the same user, or to sync shared content between different users. 
It is used by AeroFS when no direct TCP connection can be established between peers; for 
instance, when they are on isolated networks.

The relay service is built to enable secure file and metadata transfer without exposing
file contents to the relay itself. This document describes an alternate relay type that
provided centralized scanning, reporting, and archiving of user content. 

Note that relay connections are always device-to-device (not broadcast).

Appliance Configuration
==

The appliance configuration will be adjusted to disable the existing Zephyr secure
peer-to-peer relay.

The appliance configuration (which is provided to clients) will provide the host
and port on which the instrumented relay server will be found.

Only one relay server instance can be used in any given installation.

Clients will require firewall access to the instrumented relay server on a documented
port from any approved part of the network. This is in addition to the existing firewall
requirements.


Server
======

An instrumented relay server will be provided, which implements a modified relay
protocol. The server will be provided as a package for 64-bit Linux systems.

The instrumented relay is a simple TCP listener on a configured port.

Clients will authenticate using the same SSL certificate-based authentication system
used in the identity and presence protocols today. This provides the relay server
with sufficient information to verify the device id and user id of the client, and
associate them with the channel.

Authenticated clients receive a channel id from the relay server. Two clients can then
exchange channel ids over the presence server. 

Authenticated channels can bind to another channel using the channel id. Both channels
must bind before any traffic can be exchanged. The response to the bind message will
provide the device and user id of the other party. Either party then has a server-
authenticated way to verify the peer device.

The server disallows attempts to create mismatched bindings - binding to a channel
that does not bind back symmetrically. In this case, both connections are cut and 
a server audit message will be logged.

(In the traditional relay protocol, clients exchange SSL certificate information with
each other; the server is not involved in certificate or identity verification).


Audit messages
==============

Various audit messages are described here. In all cases, audit messages are formatted as
JSON documents. Audit messages are submitted via the AeroFS Audit server.


Data Leakage Prevention : Audit
-------------------------------

The instrumented relay server creates a server-side audit trail for all authenticated
transfers.

Each audit message includes:

 - `transfer_start` timestamp information (UTC, ISO 8601) of the transfer initiation
 
 - `origin_device`, `destination_device` device ID of both parties
 
 - `origin_user`, `destination_user` user ID (email address) of both parties
 
 - `origin_IP`, `destination_IP` IP address of both parties
 
 - `message_type` Message type (metadata, content transfer)
 
 - `content_filename` Filename (when applicable)
 
 - `content_md5` MD5 hash (when applicable)


Data Leakage Prevention : Scanning
==================================

The Instrumented Relay integrates with ICAP systems for content scanning, and reports
the result over the audit stream. Content delivery is not conditional on the result of
scanning; in other words, a negative response from the ICAP system will not 
prevent file transfers over AeroFS.

Two integration mechanisms are supported; only one can be used on any system.

File Scanner
------------

The instrumented relay server will create an MD5 hash of each successfully-transferred
file. If the hash value is not previously known, the file will be written to a configured
directory on the local filesystem, indexed by the hash value.

At that point, an audit event will be created with the following:

 - `modification_time` timestamp information
 
 - `content_md5` content hash (MD5)

 - `content_filename` file name
 
 - `content_file_location` location of local file on relay server
 
A downstream system, or file scanner, is expected to detect the new files in the given
tree, process them, and delete them to prevent unbounded disk growth.


Synchronous ICAP
----------------

When a file transfer is completed, a file can optionally be submitted to an ICAP host for
scanning and classification.

The scanning service may incorporate asynchronous (human-scale) processing. In this case, responding to a content event will be outside the scope of the relay service. Correlation by filename and content_md5 can be used to find the participants and network addresses from the transfer notifications described above.

The relay server will submit a preview header including the file name and a configurable
length preview block in a RESPMOD call. Sample request header:

	RESPMOD icap://icap.sample:1344/ ICAP/1.0
	Host: icap.sample
	User-Agent: AeroFS Malfoy/1.0
	Allow: 204
	Preview: 1024
	Encapsulated: res-hdr=0, res-body=N
	<preview bytes>
	1024

File content upload to the ICAP server continues as required by the preview reponse.
The server may request the upload continue, or skip as unnecessary. The server is
expected to return a 200 or 204 to the client as a result of scanning.

If this is enabled, the local file scanner mechanism described above is disabled.

The response from the ICAP server is optionally built into a second audit message:

 - `scan_time` timestamp information of the scan (UTC, ISO 8601)
 
 - `content_filename` file name
 
 - `icap_result` ICAP response type
 
 - `icap_message` ICAP response string
 
 - `content_md5` content hash (MD5).
 
The instrumented relay server will not keep file contents on a local file system.

Failure of the ICAP server will not prevent file transfers from occurring (fail-open,
not fail-closed).


