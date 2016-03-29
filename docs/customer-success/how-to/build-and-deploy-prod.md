# How-To Build and Deploy Prod

This process generates the following signed artifacts:

1. On S3:

    - Signed Appliance OVA
    - Signed Appliance QCow2
    - Signed Appliance VHD (gzipped)

2. On the docker registry:

    - Signed Docker Images

All of the above artifacts will share the same version. The version is computed using the latest
version on the docker registry + 1. To specify a manual version, use `--release-version <version>` 
in step 5 below.

## Procedure

1. Ensure your docker-dev machine is up and running by executing the following:

       dk-start-vm
       ./tools/cache/start.sh

2. Obtain the signing key truecrypt volume `aerofskeys.truecrypt` (used for executable signing) and
   mount it. You will need the mount password. Ask Matt. After mounting, open keychain and do a
   File > Add Keychain... and select the `aerofs.keychain` file which you just mounted. Press the
   little unlock button and enter the keychain password (which is _not_ the same as the mount
   password). For this, also ask Matt.

3. Make and push Eyja javascript/images for iOS, Android and web; package the Eyja
installers. To do this, run the following commands:

       git pull
       ~/repos/aeroim-client/bin/aero build <appliance_version_to_build>
       ~/repos/aeroim-client/bin/aero web publish

5. Build using:

       invoke --signed clean proto build_client package_clients build_images build_vm build_sa_images build_sa_vm

   *Note: you will not be able to successfully build the appliance if the keys for
   building the Eyja iOS app are present on the build machine. See below.

   [FIXME](https://aerofs.atlassian.net/browse/ENG-2455): during the build_vm phase, ssh onto the
   ship-enterprise builder using the ssh command provided in the execution output. Then run
   `top -d 0.1` to speed up the docker pull.

6. [QA the VMs](../testing/private-cloud-manual-test-plan.html)

7. When you are ready to release,

       invoke push_images push_sa_images push_vm push_sa_vm tag_release

   This will upload the artifacts to S3 and the docker registry and send corresponding slack
   notifications. The docker registry images will be available to the public immediately. The S3
   artifacts need to be released manually by updating the version on the [Private Cloud Admin
   Portal](http://enterprise.aerofs.com:8000/release).

   If for any reason, a release needs to be pulled from the registry, tag the loader that should be
   the correct release with the tag latest. Use the following command to list the loader tags to
   find the correct loader image you want to give the lastest tag:

       curl -s https://registry.aerofs.com/v1/repositories/aerofs/loader/tags | python -m json.tool

   Then run,

       curl -v -X PUT https://registry.aerofs.com:5050/v1/repositories/aerofs/loader/tags/latest -H "Content-Type: application/json" -d "<loader_image_hash>"

   The loader image will now be set to latest.

8. After releasing, write the [Release
   Notes](https://support.aerofs.com/hc/en-us/articles/201439644-AeroFS-Release-Notes), the
   Internal Release notes for product updates released only to the AeroFS team (Slack channel
   [#srs-bizness](https://aerofs.slack.com/messages/srs-bizness)), and go through the JIRA ENG
   sprint report to take note of the bug fixes going out in the release. The ENG tickets for the
   bug fixes should be mapped to Zendesk tickets with their respective asignees. Notify members of
   the Customer Success Team who should follow-up with their customers to inform them of the bug
   fix.

9. Deploy the release notes. A notification will be sent to the
   [#success](https://aerofs.slack.com/messages/success) channel on slack.

       cd ~/repos/support-website && python deploy.py


# Eyja App Build


### Get the Certs and Provisioning Profiles

**Important**: Build this app on a machine other than the build machine due to conflicting certs
for the appliance and the app. This will not be an issue when we upgrade our Apple certs.

1. I assume you have Xcode and Xcode Command line tools installed and have a Gerrit account.

2. Install fastlane `gem install fastlane`

3. In the project, look for `fastlane/Fastfile` and add your Gerrit username to the  `git_url`
   field. For example, change `ssh://gerrit.arrowfs.org:29418/ios-certificates` to
   `ssh://rahul@gerrit.arrowfs.org:29418/ios-certificates`.

4. Fastlane is the automation tool that lets you run your deployment in a pipeline.

       cd ios
       fastlane ios initialize username:<your_gerrit_username>

   This will get you all the certificates and provisioning profiles and put them where
   they belong.

5. When fastlane asks for the *passphrase* to decrypt the keys and certificates it
downloads, enter the `101University210`.

### Build the code

Open `Eyja.xcodeproj` in Xcode. From there, you should be able to build.

## Distribution Instructions

### Internal Distribution

To distribute phone apps via crashlytics:

    ~/repos/aeroim-client/bin/aero clean
    ~/repos/aeroim-client/bin/aero install

    ~/repos/aeroim-client/bin/aero ios build
    ~/repos/aeroim-client/bin/aero android build

    ~/repos/aeroim-client/bin/aero ios publish
    ~/repos/aeroim-client/bin/aero android publish

This will send an email with the download link to users.

### App Store Distribution

This feature is coming soon. Until then, talk to Rahul.
