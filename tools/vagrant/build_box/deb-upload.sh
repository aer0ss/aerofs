#!/bin/bash -e

# constants
readonly ERRBADARGS=2
readonly APT_SERVER=apt.aerofs.com
readonly SLACK_WEBHOOK="https://hooks.slack.com/services/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"

# Copy the debs over and add them to the all apt repositories. Make sure the deb
# dropbox is clean before we perform the update (and clean up when we're done).
function upload_payload() {
    echo "Uploading... This might take a while."

    ssh $APT_SERVER "mkdir -p ~/$DEBS_FOLDER"
    ssh $APT_SERVER "rm -f ~/$DEBS_FOLDER/*"

    scp $SOURCE_PACKAGES $APT_SERVER:~/$DEBS_FOLDER/

    ssh $APT_SERVER \
        "set -e;
        for deb in \$(ls ~/$DEBS_FOLDER/*.deb);
        do
            echo --- Signing \$deb;
            sudo dpkg-sig --sign builder \$deb -g --homedir=/root/.gnupg;
        done;

        for dir in \$(ls /var/www/ubuntu);
        do
            cd /var/www/ubuntu/\$dir/;
            echo --- \[\$dir\] Call reprepro includedeb;
            sudo  reprepro --gnupghome=/root/.gnupg includedeb precise ~/$DEBS_FOLDER/*.deb;
            echo --- \[\$dir\] Fix permissions; \
            sudo chmod -R 775 .;
            sudo chown -R root:admin .;
        done;

        echo --- Remove old debs;
        rm -f ~/$DEBS_FOLDER/*"
}

# notify the team via email that debs have been uploaded
function notify_team() {
    echo -e "Base packages have been updated by $(whoami)@$(hostname)\n" > mail.txt
    echo -e "New Versions:\n" >> mail.txt

    for deb in $SOURCE_PACKAGES
    do
        echo $deb | awk -F'_' '{printf "%s: %s\n", $1, $2}' >> mail.txt
    done

    echo "Done. Sending slack notification."

    cat mail.txt | $(git rev-parse --show-cdup)puppetmaster/modules/slack/files/slack_message -c good -u $SLACK_WEBHOOK -f "Build" > /dev/null
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
    echo "Usage: $0 <path>..."
    echo " <path> path of deb package(s) to upload"
    exit $ERRBADARGS
}

# set some globals (yup, it's shell)
readonly SOURCE_PATTERNS=("$@")
readonly SOURCE_PACKAGES=${SOURCE_PATTERNS[@]}
readonly DEBS_FOLDER="debs-all"

# upload the debs
upload_payload

# notify the team if necessary that the upload completed
notify_team

echo "Uploaded."
