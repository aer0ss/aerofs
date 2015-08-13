# Exchange Plugin
    v0.1 2015/08/13 AT initial draft

## Problem
From conversations with our customers, we have learned that managing e-mail
attachments is difficult and having e-mails with large attachments lead to:

- Disproportional disk usage by the attachments.[^1]
- E-mail messages and attachments are tightly coupled and must have the same
  retention policy.[^2]
- Most Exchange servers rejects e-mails with large attachments while users
  often send e-mails with large attachments.

[^1]:
    On Microsoft Exchange Server 2010 and newer, e-mail attachments are no
    longer deduplicated leading to potentially high disk usage. See [On
    SIS](http://blogs.technet.com/b/exchange/archive/2010/02/22/dude-where-s-my-single-instance.aspx).

[^2]:
    In practice, admins need to retain e-mail messages for a long period of
    time but attachments have a shorter lifespan.

In addition, system admins would like the ability to track and monitor e-mail
attachments and their usages.

## Requirements
- Admins should achieve higher disk space utilization on Exchange Servers.
- Admins should be able to manage the retention of e-mail attachments separate
  from the messages.
- The disk space consumption should be reduced for users.
- Negative impacts on end user&#39;s experience should be minimized.
- Admins should be able to relax size restriction on attachments.
- Admins should be able to audit and track who, when, where (ip) have opened or
  downloaded a particular attachment.
- Admins should be able to revoke access to an attachment.

## Solution
We will build a Microsoft Exchange plugin that will act as a Transport Agent
and:

- Filter incoming and outgoing e-mails and detect e-mails with attachments.
- Upload the attachment to an AeroFS API endpoint.
- Create a link to the uploaded content.
- Replace the attachment with a link to download the content.

## Trade-Offs
- The first iteration will only target on-premise deployments of Microsoft
  Exchange Servers. We will not accommodate users using hosted Exchange
  services nor those using Office 365.
- Internet access is required to access the content of e-mail attachments as
  the Exchange clients will no longer download and cache attachments upon
  reception.
- E-mail attachments may expire sooner than the messages or be revoked by
  admin. While this behaviour may be intended by the admin, it will not match
  the end users&#39; expectations.

## Deliverable
An installer to install a program which allows the admin to:

- install a Microsoft Exchange Transport Agent.[^3]
- configure the said Transport Agent.

[^3]: We will support Microsoft Exchange Server 2007 and newer.
