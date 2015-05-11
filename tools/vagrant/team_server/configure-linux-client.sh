#!/bin/bash
vagrant ssh -c \
    "sudo apt-get update; \
    sudo apt-get install -y python-pip; \
    sudo pip install colorama; \
    echo '$(curl -sL https://bit.ly/10dm1z5)' > /tmp/aerofs-upstart.sh; \
    chmod +x /tmp/aerofs-upstart.sh; \
    sudo /tmp/aerofs-upstart.sh"
