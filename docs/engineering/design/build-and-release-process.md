# Build and Release Process

### Build
- Every Wednesday at 2pm, the release manager will deploy features that: 
	- have passed Continuous Integration.
	- have no known blocker issues, as reported by the feature owner.  
- If a developer finds an issue, she/he must:
	- file a bug in Jira.
	- add the release manager as Watcher.
	- add the bug as high priority its own sprint.
	- email team@aerofs.com and state if the bug is a blocker for the release.
- `guidance` if a release is blocked at 2pm, the release will be skipped to the next week. 
- `guidance` there cannot be more two consecutive release skips.

### Release Criteria
- The build must meet those 4 criteria:
	- the version has been released to Canary for at least a week. 
	- no support anomalies on Canary during the week.
	- manual tests on the version has passed. (Suthan go-ahead) 
	- `not implemented yet` load tests on Canary has passed.
- There might be bypass exceptions to the release criteria, ask WW / John to confirm. See [Exception](### Exception) for known exceptions. 


### Release Cadence
- public will be released first.
- public will be released on Canary first.
- private will release after public is at least one week in Canary (see release criteria)
- `guidance` public and private will be released once a week.
- `guidance` private and public should be canary promoted at the same time with the same release number.
- There might be bypass exceptions to the release cadence, ask WW / John to confirm. See [Exception](### Exception) for known exceptions. 

### Exception

#### Backwards Incompatible Changes

If the build introduces backwards incompatible changes,

- the build will be released to Stable without going through Canary, 
- `guidance` developers introducing backwards incompatible features will communicate about those features, 
- `guidance` developers will test backwards incompatible changes more thoroughly than regular features.


### Release Numbering
- public and private releases with the same version number will have the same features.

### Releases Notes Publication
- Make a note for every release.
- Mention all features visible to customers.
- `guidance` try to come up with a feature, even tiny.
- Do no mention bug fixes, unless it is a publicly known bug.
- If there is no feature for a release, write 'Bug fixes and performance improvements'. 
- [release notes link](https://docs.google.com/a/aerofs.com/spreadsheets/d/1eoWIIXDgG6TDN9F9X52a86bHsI7vlVnAT88OY2kbT18/edit#gid=0)
