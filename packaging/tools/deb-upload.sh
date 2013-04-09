#!/bin/bash -ue

# Constants
APT_SERVER=apt.aerofs.com

# Standard usage function.
# Echo:
#   The script usage.
function usage()
{
    echo "usage: $0 <PROD|STAGING>"
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
MODE=$1

if [[ "$MODE" != "PROD" && "$MODE" != "STAGING" ]]
then
    usage
fi

DEST="staging" # default to the staging portion of the apt repo
if [ "$MODE" == "PROD" ]
then
    DEST="prod"
fi

DEBS_FOLDER=debs-$DEST

# Copy the debs over and add them to the apt repository. Make sure the deb
# dropbox is clean before we perform the update (and clean up when we're done).
ssh $APT_SERVER "mkdir -p ~/$DEBS_FOLDER"
ssh $APT_SERVER "rm -f ~/$DEBS_FOLDER/*"
scp debs/* $APT_SERVER:~/$DEBS_FOLDER/
ssh $APT_SERVER \
    "set -e;
    cd /var/www/ubuntu/$DEST/; \
    for deb in \$(ls ~/$DEBS_FOLDER/*.deb); \
    do \
        echo signing \$deb; \
        sudo dpkg-sig --sign builder \$deb -g --homedir=/root/.gnupg; \
    done; \
    cp ~/$DEBS_FOLDER/*.ver /var/www/ubuntu/$DEST/versions; \
    sudo reprepro --gnupghome=/root/.gnupg includedeb precise ~/$DEBS_FOLDER/*.deb; \
    rm -f ~/$DEBS_FOLDER/*"

if [ $MODE != "STAGING" ]
then
    # Send an email notification.
    echo -e "AeroFS $DEST packages have been updated by $(whoami)@$(hostname)\n" > mail.txt
    echo -e "New Versions:\n" >> mail.txt

    for VER in $(ls debs/*.ver)
    do
        NAME=$(echo $VER | awk -F'/' '{print $2}' | awk -F'.' '{print $1}')
        echo "$NAME: $(cat $VER)" >> mail.txt
    done

    echo "Done. Sending mail notification."
    cat mail.txt | mail -s "[$MODE] AeroFS Debian Package Build Notification" team@aerofs.com
    rm -f mail.txt
else
    echo "Done."
fi
