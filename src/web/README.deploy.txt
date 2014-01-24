================
Deployment Steps
================

The steps are as follows:

    cd ~/repos/aerofs
    ant clean proto package_web -Dmode=PUBLIC -Dproduct=CLIENT
    cd packaging
    BIN=PUBLIC make upload
    cd ~/repos/aerofs
    ./tools/puppet/kick puppet.arrowfs.org webadmin.aerofs.com

And if you have updated static assets:

    cd ~/repos/aerofs
    ant update_cloudfront -Dmode=PUBLIC

N.B. the last steps requires that you have installed and configured s3cmd.
