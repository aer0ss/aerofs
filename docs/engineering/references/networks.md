We use the following networks for the following purposes:

## 172.16.0.0/24 - Public-facing production Services
  - 172.16.0.10  privatecloud
  - 172.16.0.11  charlie
  - 172.16.0.20  polaris
  - 172.16.0.34  rocklog
  - 172.16.0.45  api
  - 172.16.0.65  "NAT" (Apparently this box is responsible for NATing all the other subnets to the outside world.  Don't turn it off.)
  - 172.16.0.73  sv
  - 172.16.0.83  bastion (VPN server, might better belong in 172.16.1.0/24)
  - 172.16.0.111 sp
  - 172.16.0.132 verkehr
  - 172.16.0.177 webadmin
  - 172.16.0.190 dryad
  - 172.16.0.195 config

## 172.16.1.0/24 - internal production services
  - 172.16.1.4   backup
  - 172.16.1.15  puppet
  - 172.16.1.20  lookup
  - 172.16.1.22  apt
  - 172.16.1.34  stagingdb.mysql.aws.aerofs.com (RDS-provided staging database)
  - 172.16.1.43  vpc.mysql.aws.aerofs.com (RDS-provided production database)
  - 172.16.1.45  z
  - 172.16.1.76  nexus/repos
  - 172.16.1.228 teamserver
  - 172.16.1.231 ??? RDS something, but it's not the real RDS privatecloud mysql DB
  - 172.16.1.238 c

## 172.16.2.0/24 - ci servers (including CI puppet)
  - 172.16.2.9   ci.verkehr
  - 172.16.2.20  ci.joan
  - 172.16.2.23  ci.sp
  - 172.16.2.53  ci.webadmin
  - 172.16.2.55  ci RDS database
  - 172.16.2.77  ci.syncdet (syncdet.arrowfs.org ???  idk what this is for)
  - 172.16.2.86  ci.x (shares box with ci.zephyr)
  - 172.16.2.162 ci.zephyr (shares box with ci.x)
  - 172.16.2.170 ci.sv
  - 172.16.2.186 ci.puppet
  - 172.16.2.245 ci.c

## 172.16.5.0/24 - random things?
  - 172.16.5.8/32 - production CA (should probably be moved into .1.0/24 netblock some time)
  - 172.16.5.12/32 - libjingle CI client 1
  - 172.16.5.30/32 - libjingle CI client 2


## 172.19.10.0/24 - VPN endpoints

## Misc (non-VPC)
  - 54.236.64.76 webhooks
  - S3/CDN ( {no,}cache.client{.stg,.safetynet,}.aerofs.com )
  - 107.20.173.137 - build (b.arrowfs.org, build and deploy host)
  - 23.20.80.150 - sentry (sentry.aerofs.com)
  - 54.243.210.100 - blog
  - 54.224.202.31 - old prod/staging CA (should be taken down)
  - 50.19.222.119 - old web (probably not doing anything now)

## Office External IPs
 - 198.0.196.161 - Meraki device
 - 198.0.196.162 - host machine for github/gerrit
 - 198.0.196.163 - github.arrowfs.org vm
 - 198.0.196.164 - gerrit.arrowfs.org vm
 - 198.0.196.165 - cc.aerofs.com host
 - 198.0.196.166 - cc.aerofs.com vm
 - 198.0.196.167 - iosbeta.arrowfs.org vm
 - 198.0.196.168-172 - available, currently unallocated
 - 198.0.196.173 - Science Exchange
 - 198.0.196.174 - Gateway address to Internet
 - comment: netmask is 255.255.255.240
