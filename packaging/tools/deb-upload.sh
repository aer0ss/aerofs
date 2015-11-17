#!/bin/bash -e

# Constants
readonly ERRBADARGS=2
readonly APT_SERVER=apt.aerofs.com
readonly SLACK_WEBHOOK="https://hooks.slack.com/services/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"

# Copy the debs over and add them to the apt repository. Make sure the deb
# dropbox is clean before we perform the update (and clean up when we're done).
function upload_payload() {
    local versions_only=$1

    echo "Uploading to $TARGET_REPOSITORY bin... This might take a while. (versions only = $versions_only)"

    ssh $APT_SERVER "mkdir -p ~/$DEBS_FOLDER"
    ssh $APT_SERVER "rm -f ~/$DEBS_FOLDER/*"

    if [ "$versions_only" = "true" ]
    then
        scp debs/*.ver $APT_SERVER:~/$DEBS_FOLDER/
    else
        scp debs/* $APT_SERVER:~/$DEBS_FOLDER/
    fi

    ssh $APT_SERVER \
        "set -e;
        if [ ! -d /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY ];
        then
            cp -a /var/www/ubuntu/_default_ /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY;
        fi;
        cd /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY/;

        echo --- Copy versions to /var/www;
        cp ~/$DEBS_FOLDER/*.ver /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY/versions;

        if [ $versions_only = false ]
        then
            for deb in \$(ls ~/$DEBS_FOLDER/*.deb);
            do
                echo --- Signing \$deb;
                sudo dpkg-sig --sign builder \$deb -g --homedir=/root/.gnupg;
            done;

            echo --- Call reprepro includedeb;
            sudo reprepro --gnupghome=/root/.gnupg includedeb precise ~/$DEBS_FOLDER/*.deb;
            echo --- Fix permissions; \
            sudo chmod -R 775 /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY;
            sudo chown -R root:admin /var/www/ubuntu/$TARGET_REPOSITORY_DIRECTORY;
        fi

        echo --- Remove old debs;
        rm -f ~/$DEBS_FOLDER/*"
}

# Notify the team via slack that debs have been uploaded.
function notify_team() {
    echo -e "Release notification: $TARGET_REPOSITORY packages have been updated by $(whoami)@$(hostname)\n" > mail.txt
    echo -e "New Versions:\n" >> mail.txt

    for ver in $(ls debs/*.ver)
    do
        NAME=$(echo $ver | awk -F'/' '{print $2}' | awk -F'.' '{print $1}')
        echo "$NAME: $(cat $ver)" >> mail.txt
    done

    echo "Done. Sending slack notification."
    cat mail.txt |
        $(git rev-parse --show-cdup)puppetmaster/modules/slack/files/slack_message \
            -r "#success" \
            -c good \
            -u $SLACK_WEBHOOK \
            -f "Build" > /dev/null

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
    echo "Usage: $0 <repository> [<versions_only>=false]"
    echo " <repository> repository to upload package to (PUBLIC|PRIVATE|CI|$(whoami | tr [a-z] [A-Z]))"
    exit $ERRBADARGS
}

# check number of arguments
if [ $# -ne 1 ] && [ $# -ne 2 ]
then
    print_usage
fi

if [ "x$2" = "xtrue" ]
then
    versions_only=true
else
    versions_only=false
fi

# check the mode the user is invoking
if [[ "$1" != 'PUBLIC' && \
    "$1" != 'PRIVATE' && \
    "$1" != 'CI' && \
    "$1" != "$(whoami | tr [a-z] [A-Z])" ]]
then
    print_usage
fi

# set some globals (yup, it's shell)
readonly TARGET_REPOSITORY="$1"
readonly TARGET_REPOSITORY_DIRECTORY=$( echo "$1" | tr [A-Z] [a-z] ) # convert to lowercase
readonly DEBS_FOLDER="debs-$TARGET_REPOSITORY_DIRECTORY"

# upload the debs / version files
upload_payload $versions_only

# notify the team if necessary that the upload completed
if [ "$TARGET_REPOSITORY" = 'PUBLIC' ]
then
    notify_team
fi

echo "Uploaded to $TARGET_REPOSITORY bin."

