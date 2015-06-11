# Help Center Revamp

## Requirements

- Backed by us (git); not Zendesk
- Revision Control
- Article Reviews/Accuracy
- Platform Flexibility

## Design

- It will be easier to continue to host on Zendesk for the time being

## Solution

#### Creating a New Article

- Process begins with creating a new article on Zendesk and placing it into a
  section to receive a new article identification code
- The html of the article should be written on git
- The images should be pushed up to Cloudfront with a reference to their domain
  in the html of the article
- The html of the article should be pushed up to Zendesk

*This process can be automated in the future using a script to further enhance
the revamp; however automation is not an immediate requrement.*

#### Existing Articles

- All current articles must be backed up to git
- All current images must be backed up to Cloudfront

## Other Notes

- If a doc is created directly in Zendesk, team should be notified
- The articles created on git should have the capability to be tested locally
- Articles left in draft mode must be brought to the deployment engineer's
  attention so that the content can be published to be pushed to the user via
  Zendesk
