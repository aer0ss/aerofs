# Exchange Plugin
    v0.1 2015/08/13 AT initial draft.
    v0.2 2015/09/01 AT updated functional spec.
    v0.3 2015/09/02 AT added size threshold.

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

## Solution
We will build a plugin to Microsoft Exchange Server to automatically replace
e-mail attachments with links to download the same content from an AeroFS API
endpoint.

## Functional Requirements
- We will deliver an installer that will install:
    - a Transport Agent as a plugin for Microsoft Exchange Server.
    - an Application to configure and monitor the Transport Agent.
- The Transport Agent will:
    - read the configuration values from the Windows registry.
    - report error and stop if the configuration is not available or invalid.
    - filter incoming, outgoing, and internal e-mails to detect e-mails with
      attachments.
    - upload the attachments to an AeroFS API endpoint.
    - create a link to the uploaded conten.
    - replace the attachments with links to download the content.
- The Application will:
    - show a setup wizard to guide users through configuring the Transport
      Agent if the Transport Agent is not configured.
    - show an user interface to re-configure the Transport Agent or monitor
      Transport Agent's status if the Transport Agent is already configured.
    - write the configuration values to Windows registry and restart the
      Transport Agent when the user finishes configuring the Transport Agent.
- The status screen should show:
    - whether the Transport Agent is running or stopped.
    - any error that may have occurred while Transport Agent is running.
    - [optional] nifty statistics.
    - [optional] a happy, fluffy, smiling, and non-creepy cloud.
- The system admins should be able to selectively delete the content of the
  attachments without deleting the e-mails. In which case, the e-mail and the
  links will remain, and the user will see a helpful error message if they
  click on such links.
- The system admins should be able to set a threshold so that only attachments
  whose size exceeds the threshold will be replaced with links.

## Technical Requirements
Guide lines: an extensive test suite is an overkill for a MVP. We should have a
codified manual test plan to verify correctness.

- We should have detailed setup guide to setup a test environment including a
  test Exchange server and some test exchange clients.
- We should have detailed manual test plan as system tests. This serves as our
  primary defence against regressions.
- We should have unit tests to allow developers to iterate fast and safe.
- The communication between the Transport Agent and the REST API endpoint
  should be secured.

## Trade-Offs
- The product will only support Microsoft Exchange Server 2007 and newer. At
  the time of writing, this means Microsoft Exchange Server 2007, 2010, and
  2013.
- The product will only support on-premise deployments of Microsoft Exchange
  Servers. We will not accomodate organizations using hosted Exchange services
  nor those using Microsoft Office 365.
- Internet access is required to access the content of e-mail attachments as
  the Exchange clients will no longer download and cache attachments upon
  reception.
- E-mail attachments may expire sooner than the messages or be revoked by
  admin. While this behaviour may be intended by the admin, it will not match
  the end users&#39; expectations. We will show a helpful error message in this
  case.
