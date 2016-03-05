# Build and Release Improvements

## Objective

The goal of this project is to improve the build and release process of Eyja and the AeroFS
appliance to simplify the work of the engineer in charge of this. In the process of simplification,
we would like to expand the test coverage to ensure completeness.

As of today, the majority of the build and release process is done manually. The build tasks are
repetitive and error prone due to the line by line manual execution of each of the build/release
commands. The process is also time consuming and disruptive for an engineer as it requires constant
supervision. The lack of UI and functional tests also poses a problem when releasing new patches
because there is a potential for bugs to be uncaught. As the product grows, tests will be
increasingly important to catch any issues before releasing to our customers.

Looking into the future as we expand our product, automated and procedural changes will eliminate
monotonous tasks and decrease the time required to build and release a new appliance.

## Requirements

### Automating Builds

Our current build process requires the engineer to manually run commands and monitor the output to
determine if the commands executed successfully./All of these manual tasks should be obsoleted as
follows:

* Use CI to automate build process for Appliance and AeroIM
* Have CI sign the installer
* Have CI store images to S3
	- VM images (OVA, QCow2, VHD)
	- Docker images
* Deploy lizard, developer website, PagerDuty services
* Build Storage Agent, pretty much the same as Appliance VM

### Developer Productivity

Areas that could improve a developer's productivity.

* Build Farm to automate native code builds
	- Currently, each developer needs to set up their own VM each time they make a change to native
	  code.
* Merge AeroIM and Appliance into one repository
	- Able to git tag everything under one release. Currently, it is only the Appliance releases
	  that are consistently being tagged.

### Expand And Automate Test Coverage

* Automate the following manual tests:
	- Downloading backup file
	- Appliance setup flow
		- upgrade and new setup
* Add Bunker test coverage for UI
	- Test each link to validate that pages would load
* Add in-place upgrade tests
* User interface tests
	- Verify that links are available and directs user to the appropriate page.

## Future

Further requirements will be scoped for Storage Agent and other products in future releases.
