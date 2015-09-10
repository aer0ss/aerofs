# Exchange Plugin Milestones
    v0.1 2015/09/02 AT initial draft and milestone for MVP.
    v0.2 2015/09/02 AT added size threshold to MVP.

## Milestones
### MVP
We need to have a functional Transport Agent, manual test plan, and an user
guide:

The Transport Agent needs to:
- read the configuration values from the Windows Registry.
- filter incoming, outgoing, and internal e-mails and replace attachments with
  links to download the attachment content from an AeroFS REST API endpoint.
- only replace attachments whose content exceeds the configured threshold.
- a way to log the Transport Agent's activities and errors.
- a way to manually retrieve the Transport Agent's logs.

The manual test plan needs to contain:
- detailed instructions on how to set up a Microsoft Exchange server.
- detailed instructions on how to install and configure the Transport Agent.
- detailed instructions of tests to perform.

The user guide needs to contain:
- detailed instructions on how to install, setup, configure, and operate the
  Transport Agent.
- detailed instructions on how to locate and recover the Transport Agent's
  logs.

Note that the following features _will not_ be built without further validation
from users:
- an application to guide users to setup and configure the Transport Agent.
- an application to monitor the Transport Agent's status and report nifty
  report statistics.
- an automated test suite.
- embedded media in the e-mail will not be processed.
