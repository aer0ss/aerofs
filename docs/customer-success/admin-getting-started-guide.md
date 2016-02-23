# AeroFS Getting Started Guide

Topics covered in this guide:

* AeroFS Terms
* Infrastructure Requirements
    * System requirements
    * Firewall ports
    * Hostname
    * SSL certificate
* Optional Integrations
    * MobileIron
    * LDAP
    * OpenID
    * Audit
* Custom Integrations
* File Sync Architecture
* Performance Recommendations
    * Location of appliance
    * Storage Server
    * Bandwidth
* Knowledge Base

## AeroFS Terms

* Appliance: virtual machine that provides central administration and certificate authority
* Desktop client: client that provides data syncing to the user's device
* Storage Server: client that provides a central syncing location for all the users in your
  organization

## Infrastructure Requirements

### System requirements

For more information, please refer to our Knowledge Base articles on [appliance
system requirements](https://support.aerofs.com/hc/en-us/articles/204592794-What-are-the-system-requirements-to-run-the-AeroFS-Private-Cloud-Appliance)
and [desktop client and Storage Server system requirements](https://support.aerofs.com/hc/en-us/articles/201439374-What-are-the-system-requirements-to-run-AeroFS-and-AeroFS-Team-Server).

### Firewall ports

Your corporate firewalls and VPN must be configured in a way that the AeroFS desktop clients and
Storage Servers can access the AeroFS Appliance via the following ports:

* 4433
* 5222
* 8084
* 8888
* 29438

Open the following ports for web browser access:

* 80
* 443

Desktop clients, mobile clients, and Storage Servers won't sync files or update sharing permissions
without access to the appliance over these ports. Additionally, users won't be able to access the
AeroFS web interface without opening ports 80 and 443. These are required ports; it's not possible
to use different ports for the AeroFS appliance.

For more information, please refer to our Knowledge Base article on [the usage of these
ports](https://support.aerofs.com/hc/en-us/articles/204624454-What-Is-Each-Port-On-The-AeroFS-Private-Cloud-Appliance-Used-For).

### Hostname

Before setting up your AeroFS appliance you'll need:

* a public IP address for the appliance
* a fully qualified domain name (FQDN)

If you want users to access AeroFS outside of your network, a dedicated public IP address is
required for the appliance. You should configure your DNS to point the FQDN (e.g. share.acme.com)
to this IP address to provide users in your organization with an easy way to access the AeroFS web
interface. We recommend not using an IP address as the hostname for production use, because
changing it will require users to reinstall AeroFS desktop clients and relink mobile apps.

### SSL certificate

We recommended that you get an SSL certificate to certify the AeroFS appliance. Your SSL
certificate must be signed by a trusted certificate authority such as Verisign or GoDaddy in order
for web browsers to trust it. Without a publicly signed SSL certificate, a warning message will
appear in the browser when users browse to your AeroFS web interface. The message indicates that
the connection to the site is not secured or trusted, which can be alarming to end-users.

For more information, please refer to our Knowledge Base article on [obtaining a signed
certificate](https://support.aerofs.com/hc/en-us/articles/205654494-How-Do-I-Obtain-a-Signed-Certificate-and-Key-for-My-AeroFS-Appliance).

## Optional Integrations

### AD/LDAP

AeroFS allows integration with AD/LDAP to delegate account management to your existing
ActiveDirectory or LDAP server. In both cases, the AeroFS Appliance uses LDAP to talk to the
account directory. AeroFS only needs your standard AD/LDAP credentials and tree information to
configure the integration. We require the following information:

* server host
* server port
* base DN
* bind username
* bind user password

If you don't have an ActiveDirectory or LDAP server, users can login to AeroFS with their email and
an AeroFS-specific password.

For more information, please refer to our Knowledge Base article on [configuring ActiveDirectory and
LDAP](https://support.aerofs.com/hc/en-us/articles/204861930-How-Do-I-Configure-ActiveDirectory-And-LDAP-With-My-AeroFS-Appliance).

### MobileIron

If you want to integrate with MobileIron, please refer to our Knowledge Base article
on [MobileIron configuration](https://support.aerofs.com/hc/en-us/articles/204861880-How-do-I-configure-MDM-Support).

### OpenID

If you want to integrate with OpenID, please contact support@aerofs.com.

### Audit

The audit service facilitates the capture of AeroFS usage and sharing data, which is useful for
Data Leakage Prevention (DLP) initiatives.

AeroFS allows you to configure a downstream auditing endpoint that accepts of feed of data in
JSON format. Endpoint configuration requires the following information:

* server host
* server port

A detailed events reference is available on our [Developers
Portal](https://developers.aerofs.com/#events). To learn more about configuring a downstream audit
service, please refer to our Knowledge Base article on [configuring your Audit
Service](https://support.aerofs.com/hc/en-us/articles/204862650-How-Do-I-Configure-My-AeroFS-Private-Cloud-Audit-Service).

## Custom Integrations

AeroFS provides a powerful full-featured API that exposes all the user-facing components of the
AeroFS application programmatically. Building custom integrations is easy with the AeroFS API, and
the many well-supported AeroFS API SDKs.

Consult our [Developers Portal](https://developers.aerofs.com) for more information.

## File Sync Architecture Overview

AeroFS file sync is dependent upon the AeroFS appliance and other AeroFS clients in the
organization. The AeroFS appliance stores all file metadata including file size, file names, file
modification time, hashes of file content, and the file structure. It does not store any of the
files.

Devices running the AeroFS client or the Storage Server store the actual file content. Files sync
directly between clients using end to end encryption. Whenever possible the sync algorithm tries to
do direct file transfers via LAN but if not, it directs transfers through the appliance.

## Performance Recommendations

### Appliance location

We recommend that the AeroFS appliance has internet access and external clients can access it over
the internet. If this access is prohibited for security reasons, the following features will not
work:

* syncing between clients that are not on the local network
* in-place upgrades

### Storage Server

The AeroFS Storage Server is a high availability syncing node that backs up files synced within your
AeroFS system. While the Storage Server is an optional component, the following use cases require a
Storage Server:

* Accessing files stored in AeroFS via the AeroFS web interface, AeroFS mobile apps, or 3rd-party
  applications built with the developer API while desktop clients are offline
* Giving file access to users who are not permitted to install desktop clients
* Syncing files between clients when they're not online at the same time

The Storage Server also provides the following benefits:

* Backups of sync history

The device you install the Storage Server on must have internet access.

For more information, please refer to our Knowledge Base article on [installing the AeroFS Storage
Agent](https://support.aerofs.com/hc/en-us/articles/203618620-How-Do-I-Install-The-AeroFS-Team-Server).

### Appliance Bandwidth

We recommend at least 500 mbps (up/down) for the appliance. Bandwidth requirements scale with
users' file sync activity; you may experience sync issues if insufficient bandwidth is allocated to
the appliance.

## Knowledge Base

For more information about AeroFS or instructions for configuration, please refer to our [Knowledge
Base](https://support.aerofs.com/hc/en-us).
