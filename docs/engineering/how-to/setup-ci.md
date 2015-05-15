See also [CI overview](../references/ci.html) and [How to restart CI](restart_ci.html).

# Introduction

This doc describes what must be installed and configured on the CI box. For a higher-level overview of how CI works, see docs/references/ci.md

# Installing a CI box

## Install Ubuntu 12.04 Server

Use default account: aerofsbuild/temp123

Add apt.aerofs.com to the repo list
```
echo "
-----BEGIN PGP PUBLIC KEY BLOCK-----
Version: SKS 1.1.4
Comment: Hostname: http://pgp.mit.edu

mQINBE/hSvgBEADIgTQqWziicSv77UWMeLLygzQQOksouwI71Yvnknf9Km/40VAoowTUd5pi
wh8VmoZ4mSNkaRDd7Jpu/KSz0iJpG5z8FB9zP2RplRONh+pBTFGEuCjsBIZALy1wjvNqTlQq
ZbREhFTWYl9+vRxMfVhQ1ASWYu7ecmDLTWU1HE4XdQyO/wu0k/3MfDdBW97rKKaVMt8yuMXr
pYssSoka+G15Mj29RnQudEjOKpmsbke9ZjP8yr7VltuOEluLznSy5yEGytwR+USmR1SDyMfr
e9ivpowFUq3Wq+h/RX0W7liXjbw/JH6ywzH3/Aq5uvKNFEDKs5Q949rEVVdpXneGaUJ4vZLP
YeBmCOk+AreXzh1+QmuHJspx9mpuec9SifZ+dKjBU3e5hmm/N33o66SXRPU47y4gajxKb4fY
jm7BKyzGU51G7T+NGrD6YhpmyBj9kWZGmeWrGJGgFmKskrvJjCr4R/QCmn2wPtfli5a96a3T
3cKi8ScwkNiS01+FP7vY60uDZRyLXu4YW0XwX1YSDQiMBdKkdfD7yjSyk0j+WUgLWmUjsgwZ
FoRsR1iUIs1OsDqmcEYQJdUtog/MLn3kp2QJnn4sQPvYbYka98jyXneHHBwK5QUF+Y+jv5xF
8gIJ118FkYiRKImpcou/TRQiXGozumT0hJupKmqlHSJTncDuAwARAQABtC1BaXIgQ29tcHV0
aW5nIEluYy4gKEFlcm9GUykgPHRlYW1AYWVyb2ZzLmNvbT6JAjgEEwECACIFAk/hSvgCGwMG
CwkIBwMCBhUIAgkKCwQWAgMBAh4BAheAAAoJEBiS1l5k5yVBSgQP/AkdW8Er1olA6C3JRFkY
miLXt/QYPHEvS36u7NH73nPYMEM1DZ38Cg+NjONhu6lG7SPiQrJcU/8+WClMdYvJG1wN9Hn9
6mH0bvjTnPwtWu1JFbXLl3Ne/4+wJRpcWGS3Du8DSbSi2UeLAGst8pyd0oUlaxH1Xdv9ujZI
UfQwQqqiSlBjAHUDde6k0JKy3seue3kHACzkeXI+PggsRQj1XPviW9ssIM0ezFnpTiWP2tVN
L25JGahzNay8+ORnEZzRrMlPU5Lk4Eqi049aYueWCzl40R0KnsEcP+QB24oZlaNI8eOWBnas
kTo5aX1dk9sdeJN9oVZpOubqy4CZg+5UgavMSISaoMY5jwTdxk31HZtqOrTfV6ztETVirOOR
WNGHx1eo14bVvt4zk7YYzwC9WsxrUVpKrB0duvNnyNgxShsmzRtZUeJSUPSRIVLBjDqMGlEs
G0DNVSjtVqCuiuTLV8sQgiBB6TjkyAaG2Xjm9uJ8GnPPvVDzoYHazRjpGrLRWlJYz2EXfC7z
dGosK8EIJmnHRCPA7UYXXOfPC/RyH7TBE90Kvi0He+QJNV70/zpVUKHh9xSrgcvBTluEqARS
NDMGrHojdLajtsEucMr1XCbcGgtn6fAVSF3igWqGQmJZoZJN/bQado8txa7jy1+1JEHw4Lfv
delkN0o66y5O2XjH
=/w7j
-----END PGP PUBLIC KEY BLOCK-----" | sudo apt-key add -
echo "deb http://apt.aerofs.com/ubuntu/public precise main" | sudo tee /etc/apt/sources.list.d/aerofs.list
sudo apt-get update
```

Install debian packages: 

```
sudo apt-get install openssh-server tree openjdk-6-jdk openjdk-8-jdk openvpn dnsmasq git python-dev python-pip python-mysqldb ant vim bridge-utils
```

NB: yes, you need *both* jdk6 and jdk8.


Install mysql-server (use password "temp123"):

```
sudo apt-get install mysql-server
```

Install python packages:

```
sudo pip install requests flask python-swiftclient
```

Install docker following the instructions [here](https://docs.docker.com/installation/), and set-uid for the docker executable so scripts do need "sudo docker" all the time. (To be consistent with development environment):

    $ sudo chmod u+s `which docker`

## Set up RSA keys

Copy `AeroFS/Air\ Computing\ Team/users/jonathan/ci-ssh-folder.tar` onto the box. Untar it into `~/.ssh`

```
mkdir ~/.ssh
tar -xvf ~/ci-ssh-folder.tar -C ~/.ssh
```

To provision / update the public SSH keys of the team, you need to run the `common` playbook of the [Ansible
provisioning repository](https://github.com/aerofs/ansible-provisioning):

    cd ~/repos
    git clone git@github.com:aerofs/ansible-provisioning.git provisioning && cd provisioning
    ansible-playbook common.yml
    # You may need to pip install ansible to execute the last step

## Set up VPN Access

The canonical instructions for this live at [here](../references/vpn.html). Assuming these don't change, a summary is here:

1. ssh into `vpn.arrowfs.org`
2. run `sudo /root/make_vpn_client <hostname> <hostname>`
3. copy `<hostname>.tgz` onto the box at `/root/`

On the box:

```
sudo -i
cd /root
tar xzvf <hostname>.tgz
mv AeroFSVPN.tblk/* /etc/openvpn/
chmod 400 /etc/openvpn/client.key
echo AUTOSTART=\"aerofs-vpn\" >> /etc/default/openvpn
/etc/init.d/openvpn start
```

## Set up Networking

This section assumes you will use `eth0` as your internet adapter and `eth1`/`br0` as your NAT adapter.

#### iptables rules

To set up the NAT, run as root:

```
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE
iptables -t nat -A PREROUTING -i eth0 -p tcp -m tcp --dport 443 -j REDIRECT --to-ports 8543
iptables -t nat -A PREROUTING -i tun0 -p tcp -m tcp --dport 443 -j REDIRECT --to-ports 8543
iptables-save > /etc/network/iptables.rules
```

#### /etc/sysctl.conf

Make sure the following line is present:

```
net.ipv4.ip_forward=1
```

To make this take effect without a reboot, run as root:

```
echo 1 > /proc/sys/net/ipv4/ip_forward
```

#### /etc/network/interfaces

```
# The loopback network interface
auto lo
iface lo inet loopback

# The outgoing network interface
auto eth0
iface eth0 inet dhcp
post-up /sbin/iptables-restore < /etc/network/iptables.rules

# The nat network interfaces
auto br0
iface br0 inet static
bridge_ports eth1
address 10.0.10.1
netmask 255.255.255.0
broadcast 10.0.10.255
```

#### dnsmasq

CI runs his own DNS/DHCP server. This is mainly beneficial because he brings up a new private deployment box several times a day, which many VMs need to be able to access. Having all the VMs use this DNS means the updated private deployment IP needs to be updated in only one location.

This is a valid `/etc/dnsmasq.conf`:

```
domain-needed
bogus-priv
strict-order
no-resolv
server=172.16.0.83
server=8.8.8.8
server=8.8.4.4
except-interface=eth0
addn-hosts=/etc/hosts.d/unified.syncfs.host
dhcp-range=10.0.10.50,10.0.10.200,1h
log-facility=/var/log/dnsmasq.log
log-queries
log-dhcp
```

Create the additional hosts file for the private deployment box:

```
sudo mkdir -p /etc/hosts.d
sudo touch unified.syncfs.host
```

Make sure dnsmasq is running with the new conf: 

```
sudo service dnsmasq restart
```

At this point, check that you can ping someone on the VPN (e.g. `vpn.arrowfs.org`). This is a good test of VPN setup and DNS setup.


## Set up TeamCity

### Install Teamcity

Download teamcity from the JetBrains website. You will get a file called something like `TeamCity-8.0.4.tar.gz`

Move it to `/usr/local/`

Untar and chown:

```
sudo tar -xzvf /usr/local/TeamCity-8.0.4.tar.gz
sudo chown -R aerofsbuild:aerofsbuild /usr/local/TeamCity
cd /usr/local/TeamCity
```

Create java keystore with a cert `server.crt` and a key `server.key` in two steps (use password "changeit"):

    $ openssl pkcs12 -export -in server.crt -inkey server.key -out server.p12 -name tomcat -CAfile server.crt -caname root

Make sure you enter a password - otherwise you'll get a null reference exception when you try to import it.

    $ keytool -importkeystore -deststorepass changeit -destkeypass changeit -destkeystore ~/.keystore -srckeystore server.p12 -srcstoretype PKCS12 -srcstorepass <password-you-used-in-previous-step> -alias tomcat

Alternatively, create a self-signed cert -- not recommended:

```
keytool -genkey -alias tomcat -keyalg RSA
```

Modify `conf/server.xml`:

Comment out the `<Connector port="8111" />` block.

Uncomment the `<Connector port="8543" />` block. Add two parameters "keystoreFile" and "keystorePass" so that the block looks as follows:

```
<Connector port="8543" protocol="HTTP/1.1" SSLEnabled="true"
	keystoreFile="${user.home}/.keystore" keystorePass="changeit"
	maxThreads="150" scheme="https" secure="true"
	clientAuth="false" sslProtocol="TLS" ciphers="SSL_RSA_WITH_RC4_128_SHA" />
```

Start the server by running `bin/teamcity-server.sh start`

Fire up a web browser and visit `https://<box-ip>`. Use web interface to set up teamcity. 

### Set up VCS Roots

After creating a root project, go to the project edit page, click the `VCS Roots` tab, and click `Create VCS root`. Here is a sample VCS root configuration (you can leave everything else either blank or at the default value):

VCS root name: `AeroFS (Gerrit)`  
Fetch URL: `ssh://NewCI@gerrit.arrowfs.org:29418/aerofs`  
Default branch: `refs/heads/master`  
Username style: `Author Name and Email`  
Authentication Method: `Default Private Key`  

Clicking the `Test connection` button should be succesful, and you can `Save`.

CI currently has VCS roots for:  

- aerofs  
- syncdet  


## Set up required repos

CI requires a number of repos to spawn actors, spawn build agents, manage the actor pool, and perform various tasks:

```
mkdir ~/repos
cd ~/repos
git clone git@github.arrowfs.org:aerofs/syncdet-vagrant.git
git clone git@github.arrowfs.org:jonathang/win7-syncdet-vagrant.git
git clone git@github.arrowfs.org:jonathang/actor-pool.git
git clone git@github.arrowfs.org:jonathang/misc-tools.git
git clone git@github.arrowfs.org:jonathang/signup_code_tools.git
git clone git@github.arrowfs.org:jonathang/agent-vagrant.git
```

### Set up actor pool

```
sudo cp ~/repos/actor-pool/actor-pool.conf /etc/init/
sudo ln -s ~/repos/actor-pool/actor_pool_service.py /usr/local/bin/actor_pool_service.py
mysql -uroot -ptemp123 < ~/repos/actor-pool/actorpooluser.sql
mysql -uroot -ptemp123 < ~/repos/actor-pool/actorpool.sql
sudo service actor-pool start
```


### Set up build agents

Use Dockerized agents when possible. See `tools/ci/agents/README.md`. Eventually
we will eliminate the need for physical agents. For the time being, to set up a physical
agents, first follow steps in `tools/ci/agents/Dockerfile`, and then:

Allow the agent passwordless sudo for some commands (this is needed to make the dmg on linux). Run `sudo visudo` and add these lines:

```
aerofsbuild ALL=(ALL) NOPASSWD:/bin/mount
aerofsbuild ALL=(ALL) NOPASSWD:/bin/cp
aerofsbuild ALL=(ALL) NOPASSWD:/bin/umount
aerofsbuild ALL=(ALL) NOPASSWD:/user/bin/docker
# Update docs/how-to/setup_ci.md when adding new entires here
```

Create the directory for the syncdet yaml files:

```
sudo mkdir -p /etc/syncdet
sudo chown -R aerofsbuild:aerofsbuild /etc/syncdet/
```

*Finally* let's run the agent:

```
/usr/local/TeamCity/buildAgent/bin/agent.sh start
```

### Set up Vagrant/Virtualbox

Download the vagrant .deb from [http://downloads.vagrantup.com/tags/v1.2.3](http://downloads.vagrantup.com/tags/v1.2.3) and get it onto the box. Then run `sudo dpkg -i <vagrant>.deb`

## Optional: Set up HAL

You can install [HAL](https://github.com/aerofs/aero.hal#deployment-on-ci) to notify users in Slack
when they break tests.

You can also set up an "external speaker" by running HAL on a different host, see
[the related documentation](https://github.com/aerofs/aero.hal/blob/master/README.md#speech-synthesis).
