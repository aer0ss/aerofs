# How to fix your username in Gerrit

    ssh gerrit.arrowfs.org

People who have access:

 * Alex
 * Jon
 * Matt
 * Mickael
 * Yuri

After sshing on the box run the following:

    sudo su
    cd
    ./fix-username-in-mysql.sh <email_address> <desired_userid>

## TODO

The above script is not currently backed up; we should do that.
