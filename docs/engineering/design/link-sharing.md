Link Sharing
===

## Requirements

### Link creation
- Owner of shared folder can create a link to any file in the folder. ('File Link')
- Owner of shared folder can create a link to any subfolder in the folder. ('Folder Link')

### Link Structure
- hybrid: The link URL will be in the form “https://www.aerofs.com/…”
- private: The link URL will be in the form "https://companyURL/..."

### Link Access 
- anyone with the File Link can access the File Link.
- anyone with the Folder Link can access the Folder Link and its subfolders.   
- Opening a File Link into a browser whose file format is viewable by the browser would open it in the browser. e.g. standard files (txt, pdf, xml), pictures (jpg, png, bmp), audio/video (avi, mp3).
- Opening a File Link into a browser whose file format is not viewable by the browser would download it to the desktop. e.g. compressed (zip), propertiary (pptx, psd).  
- Opening a Folder Link into a browser would open the AeroFS folder Web view.
- Opening an expired Link in a browser will display an error message. "The file or folder you're looking for has been deleted or moved."

### Link Protection
- Owner of shared folder can specify a password for the link.
- Owner of shared folder can specify an expiration time for the link.
- Owner of shared folder can specify a number of downloads for the link. A download is a file downloaded into a browser.
- All protections can be complementary. 
- All protections can be removed at any time.

### Link Deletion
- Owner of the shared folder where the link is can delete the link.

### Link Expiration

The link expires if:

- The file or folder is deleted.
- The file or folder is moved beyond shared folder. 

### Admin
- Admin can see the list of link downloads:
	- link
	- date and time
	- IP address
	

## Design

### Data structure

A naive approach is to have the URL include the object id and access token. It is undesirable as the resulting URL is too long to be user friendly. Therefore, Shelob maintains a persistent map:

    URL => SOID, access token, sha256(password | URL), expiry

This map is the only additional data structure other than the data structures in existing systems (Bifrost, SP, Daemon).

For auditing purposes, deleted entries can be optionally saved in a separate table, so that people can track back to the URL from the access token which is indirectly logged in the auditing system.


### Main control flows

#### 1. Create a link

- Shelob requests from Bifrost an access token scoped by the SOID and the expiry if any.
- Shelob generates the URL and insert a new entry to the map.

#### 2. Access the link

- The JavaScript code calls Shelob server to exchange URL for the SOID and access token.
- Shelob:
  - denies the request and performs Control Flow 3 if the link has expired, or
  - asks for a password if the password is set, or
  - returns the data otherwise.
- Once receiving the data, the JavaScript calls Havre using the access token to access file content as well as the subfolders and files under the shared folder.

#### 3. Delete the link before expiry

- Shelob calls Bifrost to delete the access token.
- Shelob deletes the map entry.

#### 4. Update link options (i.e. password and expiry)

- Shelob performs Control Flow 3 followed by Control Flow 2 using the original URL.

### Design notes

- A token's subject (i.e. user ID) is the user who created the link.
  - Implication: the URL can only access content on the Team Server or on that user's client.
- The expiry should be known by both Bifrost and Shelob:
  - Bifrost needs to control the expiry because the JavaScript exposes access tokens to the browser, and thus attackers may use the token to access Havre bypassing Shelob.
  - Shelob needs to show the expiry to the user who created the link, and having Shelob ask Bifrost for this data creates unnecessary coupling.
- The JavaScript should handle errors due to expiration from *both* Shelob and Havre, in case the JavaScript exchanges the URL for an access token right before the token expires.

