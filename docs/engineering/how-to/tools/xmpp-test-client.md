# Python XMPP test client

There is a Python client using the `sleekxmpp` library, useful to debug / check server and client behaviour.

## Installation

You will need to install the `sleekxmpp` python package:

    pip install sleekxmpp

## Usage

    python xmpp_list_clients.py

You can adjust the verbosity directly in the file.

## What it currently does

* Connect to the local appliance
* Ask the server for its IP (the client's IP)
* Try to join a hardcoded MUC (Multi User Chatroom)

    How to know the MUC to use? You can use [Adium and this documentation](../connect-to-xmpp.html) or use the
    SID like this: `04736070b993370e16d41e36771775de@c.syncfs.com` (where the SID is `04736070b993370e16d41e36771775de`)

* Display features and general information about the MUC
* Display the list of the users connected to this MUX and try to print their vCard DESC metadata, used to exchange
  list of current locations (IP addresses).

## Example

    INFO     Negotiating TLS
    INFO     Using SSL version: TLS 1.0
    WARNING  Could not find pyasn1 and pyasn1_modules. SSL certificate COULD NOT BE VERIFIED.
    INFO     JID set to: johnsnow@syncfs.com/33699820551431554838374702
    WARNING  Could not find pyasn1 and pyasn1_modules. SSL certificate expiration COULD NOT BE VERIFIED.
    INFO     The server told me my IP was: <iq type="result" id="92ba1231-f51e-4b68-964b-764d480e624c-3" to="johnsnow@syncfs.com/33699820551431554838374702" from="johnsnow@syncfs.com"><ip xmlns="urn:xmpp:sic:0">192.168.99.1</ip></iq>
    INFO     Joining MUC 04736070b993370e16d41e36771775de@c.syncfs.com
    WARNING  Use of send mask waiters is deprecated.
    INFO     XMPP Service Discovery: 04736070b993370e16d41e36771775de@c.syncfs.com
    INFO     ---------------------------------------------------------------------
    INFO     Identities:
    INFO       - ('conference', 'text', None, '04736070b993370e16d41e36771775de')
    INFO     Features:
    INFO       - muc_semianonymous
    INFO       - muc_public
    INFO       - muc_open
    INFO       - muc_unsecured
    INFO       - http://jabber.org/protocol/muc
    INFO       - muc_moderated
    INFO       - muc_persistent
    INFO     Items:
    INFO       - ('04736070b993370e16d41e36771775de@c.syncfs.com/1b1cb584130542f682cc31e6b750ba01-z', None, '1b1cb584130542f682cc31e6b750ba01-z')
    INFO         > ["172.19.10.50","10.2.0.1","192.168.33.1","fe80:0:0:0:a2ce:c8ff:fe02:22c0%en4","192.168.138.194","192.168.8.1","192.168.99.1"]
    INFO       - ('04736070b993370e16d41e36771775de@c.syncfs.com/31f99ed7f63b489ea54dfefae1414f9e-z', None, '31f99ed7f63b489ea54dfefae1414f9e-z')
    INFO         > ["172.19.10.50","10.2.0.1","192.168.33.1","fe80:0:0:0:a2ce:c8ff:fe02:22c0%en4","192.168.138.194","192.168.8.1","192.168.99.1"]
    INFO       - ('04736070b993370e16d41e36771775de@c.syncfs.com/johnsnow@syncfs.com', None, 'johnsnow@syncfs.com')
    INFO         > None
    INFO     Waiting for </stream:stream> from server
