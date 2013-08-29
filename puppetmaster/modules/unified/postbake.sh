#!/bin/bash -e

# Stop pesky services so we can unmount successfully.
for service in \
    ca-server \
    sanity \
    php5-fpm \
    mysql \
    redis-server \
    tomcat6 \
    restund \
    verkehr \
    zephyr \
    ejabberd \
    mysql \
    uwsgi \
    sendmail
do
    chroot /mnt/image service $service stop || true
done

# The ejabberd service script should do this, but alas, it sucks.
chroot /mnt/image killall -9 epmd

# Pull down installers.
chroot /mnt/image /opt/repackaging/tools/pull-installers.sh
