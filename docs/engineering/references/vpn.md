# Overview
We run our own Virtual Private Network to give us a separate, trusted network
fabric, so we can minimize our exposure to the outside world for internal
services.

In practice, we use Amazon's Virtual Private Cloud (VPC) for (virtually) all of
our services hosted on EC2, and we give each engineer (and other boxes which
need access to the private fabric) access to this fabric via an instance of
OpenVPN which runs on a box within the VPC and routes traffic between the VPN
network and the VPC network.

You can see who is currently on the VPN at
[http://vpn-status.arrowfs.org:8000/](http://vpn-status.arrowfs.org:8000/)

# Network layout

* The VPC owns `172.16.0.0/16`. All AeroFS services on the trusted fabric will
  live within this class B.
* Production servers generally go in `172.16.0.0/24` e.g. sp.aerofs.com
* Internal-only services generally go in `172.16.1.0/24` e.g.
  puppet.arrowfs.org, c.aerofs.com
* CI servers go in `172.16.2.0/24`
* The VPN itself assigns addresses from `172.19.10.0/24`, though this may need
  to grow with additional clients.
* The VPN bridge address is `172.19.10.1/24` and `172.16.0.83/24`

The OpenVPN host needs to MASQUERADE to the VPC:

    iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE

Lastly, the VPN overrides DNS using dnsmasq. Just add entries to `/etc/hosts`
and restart dnsmasq, and VPN clients will see them.

# Setting up a new engineer/box

* ssh vpn.arrowfs.org
* become root
* run `/root/make_vpn_client <username> <CNAME>`, where `<username>` just
  affects the output .tgz filename, and `<CNAME>` follows this convention:
  * for employee machines, use the employee's email address
  * for servers, use the hostname
  * try to avoid weird characters like spaces
* retrieve <username>.tgz with scp or some other trusted mechanism
* extract the config from the .tgz
* Set up the OpenVPN client with your freshly-minted config (see below)

# Setting up a new OpenVPN certificate
The certificate for the OpenVPN server expires every 1000 days, currently set to expire on Jun 10 2018.
To refresh it:

* ssh vpn.arrowfs.org
* become root
* archive the old key/csr/crt files in `/root/`, `/etc/ssl/certs/openvpn.crt`, `/etc/ssl/private/openvpn.key`
* run `openssl req -newkey rsa:2048 -keyout ~/openvpn.key -out ~/openvpn.csr -nodes -subj "/O=aerofs.com/OU=OpenVPN/CN=vpn.aerofs.com"` which will save new files to `/root/`
* run `openssl ca -config /etc/openvpn/ca/openssl.cnf  -days 1000 -in ~/openvpn.csr -out ~/openvpn.crt` which will sign the csr and save the new crt under `/root/`
* make sure permissions on these files make sense, and then replace the old versions with them
* run `service openvpn restart`

### OSX

Install
[Tunnelblick](https://code.google.com/p/tunnelblick/wiki/DownloadsEntry?tm=2#Tunnelblick_Beta_Releases).

Then, double click your `AeroFSVPN.tblk` folder to install the config. Make sure Tunnelblick isn't already running otherwise the config file won't load.

### Linux 

Install openvpn, then place the four files from the .tgz (`aerofs-vpn.conf`,
`ca.crt`, `client.crt`, and `client.key`) in `/etc/openvpn/`. Set the
permissions on `client.key` to 400, and set the daemon to autostart.

Or in short:

    sudo -i # you'll need to be root
    apt-get install openvpn
    cd /root/
    tar xzvf newserver.tgz
    mv /root/AeroFSVPN.tblk/* /etc/openvpn/
    chmod 400 /etc/openvpn/client.key
    vim /etc/default/openvpn # add a line AUTOSTART="aerofs-vpn"
    /etc/init.d/openvpn start

### Windows

* Install [OpenVPN](http://openvpn.net/index.php/open-source/downloads.html),
  then place the four files from the .tgz (`aerofs-vpn.conf`, `ca.crt`,
  `client.crt`, and `client.key`) in `C:\Program Files\OpenVPN\config\`. 
* Rename `aerofs-vpn.conf` to `aerofs-vpn.ovpn`
* To configure OpenVPN to connect at system startup, open Control Panel >
  Administrator Tools > Services, and set the "OpenVPN Service" startup type to
  "Automatic".
* If OpenVPN GUI fails to start on the first attempt, run OpenVPN GUI once as
  administrator.
