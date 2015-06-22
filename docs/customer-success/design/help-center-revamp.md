# Help Center Revamp

## Requirements

- Backed by us (git); not Zendesk
- Revision Control
- Article Reviews/Accuracy
- Platform Flexibility

## Design

- It will be easier to continue to host on Zendesk for the time being

## Solution

#### Detailed documentation and notes of creating a new article

- User runs track-changes.py that keeps track of changes to the directory and updates it in the
  “out” folder. This should continue to run in the background until the user is done.
- User runs create-new-post-shell.py with Zendesk ID as parameter to create HTML file and folder
- User opens the article (its location is given by the program)
- User writes the HTML and for images, enters "img src=“{Domain} image-name.png”/"
- track-changes.py continuously updates the changed files in the “out” folder and changes the img
  src so that it is suitable for local viewing (replace the {Domain})
- User goes into the out folder and opens the post to view locally in default browser
- User runs deploy.py with the CloudFront URL as the parameter to prepare article for Zendesk
- User commits changes to github (git add, git commit)
- Content pushed from GitHub to Zendesk

*This process can be automated in the future using a script to further enhance the revamp; however
automation is not an immediate requrement.*

#### Existing Articles

- All current articles must be backed up to git
- All current images must be backed up to Cloudfront

## Other Notes

- If a doc is created directly in Zendesk, team should be notified
- The articles created on git should have the capability to be tested locally
- Articles left in draft mode must be brought to the deployment engineer's attention so that the
  content can be published to be pushed to the user via Zendesk
