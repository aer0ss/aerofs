# DevStack Setup

1. Obtain dedicated hardware running `ubuntu trusty` or create a virtual
   machine. If using a virtual machine, the following Vagrant environment is
   recommended:

        Vagrant.configure("2") do |config|
            config.vm.define :trusty64 do |box|
                box.vm.network "public_network" # Optional: remove for NAT network configuration.
                box.vm.box = "ubuntu/trusty64"
                box.vm.hostname = "trusty64"
                box.vm.provider :virtualbox do |vb|
                    vb.gui = false
                    vb.customize ["modifyvm", :id, "--name", "trusty64"]
                    vb.customize ["modifyvm", :id, "--memory", "4096"]
                end
            end
            config.vm.synced_folder "~/Repos", "/mnt/repos"
        end

2. Install [devstack](http://docs.openstack.org/developer/devstack/). Make sure
   to install this as a non-root user, e.g.:

        sudo su
        apt-get update && apt-get install -y git
        git clone https://git.openstack.org/openstack-dev/devstack
        ./devstack/tools/create-stack-user.sh
        rm -rf devstack
        su stack
        cd
        git clone https://git.openstack.org/openstack-dev/devstack # clone again, in stack user home directory
        cd devstack && ./stack.sh # this time it will work; takes a long time.

   At this point your Openstack cluster is available via `http://127.0.0.1`
   on your vm or via `http://<bridged-ip>` if using bridged networking.
   Great! Make sure to note the admin password provided in the output of the
   `stack.sh` execution.

3. If you are using NAT configuration, to access the OpenStack web interface
   you can use a reverse ssh tunnel.

        vagrant ssh -c \
            "ssh -R 8888:localhost:22 <dev-user>@<dev-ip> -t 'ssh -L 8080:localhost:80 stack@localhost -p 8888 -N'"

   Where `dev-user` and `dev-ip` are the user and IP of your dev machine, e.g.
   your laptop. For this to work you will need to set a username for the
   `stack` user on your OpenStack machine, and you will need to allow
   interactive ssh authentication on your dev machine.
