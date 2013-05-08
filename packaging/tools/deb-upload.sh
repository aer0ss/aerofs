#!/bin/bash -ue

# constants
readonly ERRBADARGS=2
readonly APT_SERVER=apt.aerofs.com

# Copy the debs over and add them to the apt repository. Make sure the deb
# dropbox is clean before we perform the update (and clean up when we're done).
function upload_debs() {
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
        sudo chown root:admin /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY/versions/*; \
        sudo chmod 666 /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY/versions/*; \
        echo Call reprepro includedeb; \
        sudo reprepro --gnupghome=/root/.gnupg includedeb precise ~/$DEBS_FOLDER/*.deb; \
        echo Remove old debs; \
        rm -f ~/$DEBS_FOLDER/*"
}

# notify the team via email that debs have been uploaded
function notify_team() {
    echo -e "AeroFS $TARGET_REPOSITORY packages have been updated by $(whoami)@$(hostname)\n" > mail.txt
    echo -e "New Versions:\n" >> mail.txt

    for ver in $(ls debs/*.ver)
    do
        NAME=$(echo $ver | awk -F'/' '{print $2}' | awk -F'.' '{print $1}')
        echo "$NAME: $(cat $ver)" >> mail.txt
    done

    echo "Done. Sending mail notification."
    cat mail.txt | mail -s "[$TARGET_REPOSITORY] AeroFS Debian Package Build Notification" team@aerofs.com
    rm -f mail.txt
}

###############################################################################
#
#
# Main script
#
#
###############################################################################

# echos the usage message and exits
function print_usage()
{
    echo "usage: $0 [repository]"                                             >&2
    echo ""                                                                   >&2
    echo "    [repository] repository to upload package to (PROD|CI|STAGING)" >&2
    exit $ERRBADARGS
}

# check number of arguments
if [ $# -ne 1 ]
then
    print_usage
fi

# check the mode the user is invoking
if [[ "$1" != 'PROD' && "$1" != 'CI' && "$1" != 'STAGING' ]]
then
    print_usage
fi

# set some globals (yup, it's shell)
readonly TARGET_REPOSITORY="$1"
readonly TARGET_REPOSITORY_DIRECTORY=$( echo "$1" | tr [A-Z] [a-z] ) # convert to lowercase
readonly DEBS_FOLDER="debs-$TARGET_REPOSITORY_DIRECTORY"

# upload the debs
upload_debs

# notify the team if necessary that the upload completed
if [[ "$TARGET_REPOSITORY" != 'CI' && "$TARGET_REPOSITORY" != 'STAGING' ]]
then
    notify_team
fi

echo "uploaded debs to $TARGET_REPOSITORY"
