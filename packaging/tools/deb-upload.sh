#!/bin/bash -ue

# Constants
readonly APT_SERVER=apt.aerofs.com

# Standard usage function.
# Echo:
#   The script usage.
function usage()
{
    echo "usage: $0 <repository>"
    echo
    echo "example: $0 STAGING"
    exit 2
}

# Main Script.

# args count
if [ $# -ne 1 ]
then
    usage
fi

# check which mode the user is invoking
readonly TARGET_REPOSITORY=$1

if [[ "$TARGET_REPOSITORY" != "PROD" && "$TARGET_REPOSITORY" != "STAGING" ]]
then
    usage
fi

TARGET_REPOSITORY_DIRECTORY="staging" # default to the staging portion of the apt repo
if [ "$TARGET_REPOSITORY" == "PROD" ]
then
    TARGET_REPOSITORY_DIRECTORY="prod"
fi

readonly TARGET_REPOSITORY_DIRECTORY
readonly DEBS_FOLDER=debs-$TARGET_REPOSITORY_DIRECTORY

# Copy the debs over and add them to the apt repository. Make sure the deb
# dropbox is clean before we perform the update (and clean up when we're done).
ssh $APT_SERVER "mkdir -p ~/$DEBS_FOLDER"
ssh $APT_SERVER "rm -f ~/$DEBS_FOLDER/*"
scp debs/* $APT_SERVER:~/$DEBS_FOLDER/
ssh $APT_SERVER \
    "set -e;
    cd /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY/; \
    for deb in \$(ls ~/$DEBS_FOLDER/*.deb); \
    do \
        echo Signing \$deb; \
        sudo dpkg-sig --sign builder \$deb -g --homedir=/root/.gnupg; \
    done; \
    echo Copy debs to /var/www; \
    cp ~/$DEBS_FOLDER/*.ver /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY/versions; \
    echo Call reprepro includedeb; \
    sudo reprepro --gnupghome=/root/.gnupg includedeb precise ~/$DEBS_FOLDER/*.deb; \
    echo Remove old debs; \
    rm -f ~/$DEBS_FOLDER/*"

if [ $TARGET_REPOSITORY != "STAGING" ]
then
    # Send an email notification.
    echo -e "AeroFS $TARGET_REPOSITORY packages have been updated by $(whoami)@$(hostname)\n" > mail.txt
    echo -e "New Versions:\n" >> mail.txt

    for VER in $(ls debs/*.ver)
    do
        NAME=$(echo $VER | awk -F'/' '{print $2}' | awk -F'.' '{print $1}')
        echo "$NAME: $(cat $VER)" >> mail.txt
    done

    echo "Done. Sending mail notification."
    cat mail.txt | mail -s "[$TARGET_REPOSITORY] AeroFS Debian Package Build Notification" team@aerofs.com
    rm -f mail.txt
else
    echo "Done."
fi
