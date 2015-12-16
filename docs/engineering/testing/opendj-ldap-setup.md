# OpenDJ LDAP Setup

## Setting Up OpenDJ Server

1. Download the latest [OpenDJ archive](https://forgerock.org/downloads/opendj-archive/).
2. Unzip the archive and `cd` into the unzipped folder
3. Run `./setup` to launch the OpenDJ GUI setup wizard
4. Click **Next**
5. From the "Server Settings" page, click **Configure** and enable SSL and StartTLS for LDAP, and
  select the "Generate Self-Signed Certificate" option and hit **OK**
6. Enter password for Root User DN ("Directory Manager" is the default root user) and click through
  and hit **Finish**
7. Click on **Launch Control Panel**
8. Enter the root user password you set in step 6 and hit **OK**
9. Your OpenDJ Server should be up and running now.

## Adding User Entries
1. Click on "Manage Entries" and select "dc=example,dc=com"
2. Click on **Entries > New User** from the Application Menu at the top menu bar
3. Enter values for "First Name", "Last Name", "Common Name", "Password", and "E-mail".
  "Common Name" should be something like "users".
4. Click **OK** to save the entry

TODO: consider automating the above using [docker](https://github.com/mpillar/opendj-docker-example)

## Obtaining the Server Certificate in PEM Format

This step is required for the appliance to connect to the OpenDJ server over TLS/SSL if the
server's certificate isn't publicly signed (which it isn't in our case).

`cd` into the "config" folder located inside the OpenDJ folder

Then run:

```
keytool -export -alias server-cert -keystore keystore -storepass `cat keystore.pin` -file opendj-server-cert.crt
```

```
openssl x509 -inform der -in opendj-server-cert.crt -out opendj-server-cert.pem
```

Now copy the output of the following command and paste it into the "Server certificate for StartTLS
and SSL (optional):" field in the Appliance's Identify page. You'll need to click on **Show
advanced options** to see this field.

```
cat opendj-server-cert.pem
```

Now you're ready to configure the Appliance to use ActiveDirectory/LDAP.

## Configuring the Appliance to Use Active Directory/LDAP

1. Go to the "Identify Management" page (`<appliance_hostname>/admin/identity`)
2. Select the "Use ActiveDirectory or LDAP" option
3. For "Server host" enter your device's IP address
4. For "Server port", look at the OpenDJ Control Panel's "Connection Handlers" section to see which
   port is being used for StartTLS and SSL (LDAPS) respectively.
5. For "Base DN" use "cn=users,dc=example,dc=com"
6. For "Bind username" use "cn=users,dc=example,dc=com" and enter the password of the user entry
   you created in section 2.
7. Select "StartTLS" or "SSL" depending on the port you specifed in step 4 and click **Save**
8. You should get a success message. Sign out and sign back in using one of the LDAP user
   credentials to verify that it's working.
