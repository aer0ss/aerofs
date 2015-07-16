# In-Place Upgrade - Front-End Work

This document describes the work to be done on front-end for shipping live upgrade. This is a document I used for my own work, but should be readable for any developer who would like to help or take over. 
Start: July 16 2015. author: John


## In-Place Upgrade

Today if an admin wants to upgrade her appliance, she needs to make a backup of the current appliance, launch a new VM, restore the new backup to the new VM, optionally update the DNS record to point to the new appliance IP, the last step being not under control and difficult to achieve if dependent on the company processes. 

This has two effects. First, it increases the perception that managing AeroFS is difficult, which is the opposite of what we claim and customers were sold on. Second, admins have been reluctant to upgrade, resulting in lower adoption in new features or bug fixes.

Making upgrade easier helps carry our value 'Customers First'.


## Experience


### Where To Upgrade


To upgrade, admins:

* go to their admin interface (internally named as Bunker)
* click on the 'upgrade' link in the left panel - _option: provide information about the new version in the homepage interface. discuss security and privacy implications_

### Upgrade Page

* displays current version. 
* provides a way to check if a new version is available _option: check upon opening the page, without user consent. discuss security and privacy implications_ 

* If new version is found: 
  * provides a button to start the upgrade. See next section.
  * provides an option to set up their own private repository so they don't need to rely on us. by default, the upgrade process pulls the docker image to our own registry. Some customers like Bloomberb might not want to do this. They prefer to download the new containers in a airtight environment. In that case, they need to provide a private registry url. 
 
*  If no version is found:
  * provides a message that says no new version was found
  
* If error:
  * provides a message to try again in a while, and contact support@aerofs.com if issue persists. 
  
  
### Download

*  When admin pushes on the 'download upgrade' button:

	* a progress bar starts. 
	* Once the download is completed, message the download is complete.
	* A 'switch to new version' button appears, with a message explaining there will be a short downtime. _can we provide a time estimate?_ .
	
### Switching 
	
* Once the admin pushes the button: 
	* a 'in progress' bar with a message that says the process will take a couple of minutes. If nothing works, contact support?
	* once the switch is successful, provide a success with the new version number.

_there is no fail safe mechanism. if the switching fails, the admin has to use root shell and ssh into the box. In the future, make the failure less critical by allowing admins to revert to the former version._


## How It Works

_This section needs to be filled. Loader API instructions are in docker/ship/README.md_ 

### Check Version

1. Loader regularly checks if a version with registry.aerofs.com.
2. Browser calls Loader to see if a new version exists.

### Download 

3. If a new version is available, download the latest Loader image from the registry. 
4. Query the Loader to check if the images are all the same. (no new image, no deleted image)
5. Download the new images from the registry.

### Switching

6. Stop the current containers
7. Create the new containers
8. Copy the data
9. Start the new containers
10. Delete the old containers.

	



## Lexicon


`Loader`
Contains the list of images that are required to run the appliance. Loader is one of the containers. It orchestrate other containers, it tells you what containers you should use for the target, and how to link them. It tells you the dependencies between the containers. It links the containers together. 

`Loader API` 
The interface to interact with the Loader. See the API document at /docker/ship/README.md

`Bunker`
Web back-end. Acts as a passthrough for the POST and GET request between browser and Loader API. Bunker is used to make the requests are authenticated.


`registry` 
The repository that contains the new appliance. At https://registry.aerofs.com

`boot` 
Action that boots a target.

`target`
A subset of containers. Maintenance defines a subset of containers that are required for maintenance.


## Things to do


0. ~~read the uxreview discussions.~~
1. ~~read the documentation in docker/ship/README.md~~
2. dk-create. (on-going)
3. run bunker locally.
4. get up to speed on the front-end framework.
5. write test cases, finish design, provide timeline.
6. implement changes bunker.
7. implement changes front-end.
8. CI and test.
9. Ship.
10. Test in production.
