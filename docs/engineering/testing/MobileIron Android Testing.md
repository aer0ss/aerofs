MobileIron Android Test Doc
---
---

	IT Admin Testing  

	
**Controlling the device remotely**   

- Lock/Unlock
	- User's phone is lcoked and then unlocked by Admin 
- Wipe 
	- User's AeroFS data is deleted in cache
- Retire
	- User is no longer allowed access to App	
	
**Controling the App Remotely**

- Copy 'n Paste
	- enable/disable
- Open with
	- enable/disable opening files with 3rd party apps 		
	
**Secure App Store**

- Adding
	- IT admin can add new apps to secure app store
- Deleting
	- IT admin can delete apps from the secure app store
- Specific Provisioning
	- Limit apps to certain devices/users	

--- 	

	User Testing 
** Device data storage**

- Encryption
	- folder names are encrypted
	- file names are encrypted	
- Stored Seperately
	- Data is not stored in phone's default cache
- Access
	- Cannot open files in other apps via the device's storage system
	
**Interaction with Mobile Device**

- Non-MobileIron apps
	- email app via settings activity
	- opening with non-MobileIron apps disabled 
- MobileIron app
	- can download AeroFS app from MobileIron app
	- does not run without MobileIron app	 
		 
**MobileIron Credentials**

-  Logged In
	- User is not prompted to sign in with MI passcode
- Logged out
	- User is prompted to sign in with MI passcode
- Timeout
	- After IT Admin's interval, user must relogin with MI passcode   

		 	
	 