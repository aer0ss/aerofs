# Exchange Plugin
    v0.1 2015/09/01 AT initial design
    v0.2 2015/09/02 AT added size threshold under configuration.

## Motivation
See [requirements](../requirements/exchange-plugin.md).

## Technical Design

### Configurations
The Transport Agent requires the following configuration properties to operate:
- hostname: the hostname of the appliance. The URL to the REST API endpoint
  will be derived from the hostname.
- token: an access token to use when talking to the REST API endpoint. The
  access token should be associated with an user and the Transport Agent will
  upload content to that user's root folder.
- threshold: attachments whose content size is below the threshold will not be
  replaced with a link. This value default to 0 which means all attachments
  will be replaced with links unless configured otherwise.

### Persistence
When an e-mail with attachment is processed, the plugin creates the following:
- An application folder under the user's root folder, say `Attachments`.
- A per e-mail folder under the application folder named with the sender's
  email and a timestamp, e.g. `alex@aerofs.com_20150825172900000`. The aim here
  is to offer some organization should the admin attempt to "manage"
  attachments.
- One file per attachment under the per e-mail folder with identical names and
  content as the attachments.

### Other Considerations
- The Transport Agent will rely on the trust store on the system to establish
  trust. If the user is not using a globally-trusted certificate, the user will
  need to import the API endpoint's certificate into the Exchange Server's
  trust store.
