OH SHIT!
========

Is it not June 2016 yet?
Is HC borked in a way that requires a hotfix?

Get ready for some time travel!


Server maintenance
------------------

Server packages are stored on apt.aerofs.com

Build your hotfix on top of the `servers-hotfix` branch on gerrit, which was
forked from `8336dc581c6cb3e5bdfce48b6c0faf94759e8c33`, the commit that finally
removed the Java 6 build dependency.

Once you've applied your hotfix and tested it to your satisfaction, build new
deb packages for the affected services:


    ./invoke clean proto build_servers
    pushd packaging
    BIN=PUBLIC make <services...> upload
    popd


Most HC server are puppetized so you should in theory be able to deploy these
packages via a puppet kick but that puppetry is putrescent and untrustworthy
so you're probably better off ssh'ing into the appropriate server and running
`apt-get` manually (don't forget to restart services as needed)

For instance, to deploy on sp:

    ssh sp.aerofs.com
    sudo apt-get update
    sudo apt-get install aerofs-sp
    sudo service tomcat6 restart


Use `apt-cache policy <package>` to check the current version of a package.


DB migrations affecting spdb/bifrost may take some effort to backport as these
two databases were merged in PC but remain separate in HC. Databases in HC are
hosted on AWS so you'll need to look at service configuration to retrieve the
appropriate credentials if you need to perform a manual migration.


NB: HC havre was deployed once before this guide was written. Thankfully, this
service has been remarkably stable so, in the unlikely event that you need to
hotfix it, you should be able to apply your change directly on top of the last
deployed commit: `0d26346a9e7fea969852502ad3f7fa2293c91f50`.


Client maintenance
------------------

Forget it.

Safetynet and the rest of the client deploy infrastructure have been burned a
while back, there is no going back.

If you somehow manage to get the release machinery in a good enough shape for a
push (good luck), apply your fix on top of the `public-maintenance` branch on
gerrit which was forked from `6e58e2135b00a738fa346c52d5a68c4dd3759e14`, the
very last commit before the removal `PUBLIC` from the build scripts.

