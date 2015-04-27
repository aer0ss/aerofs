#!/bin/bash
set -eu

cd $(dirname $0)/../../..

echo "Patching various build files..."

set -x

patch -p1 << EOF
diff --git a/docker/ship-aerofs/loader/root/crane.yml b/docker/ship-aerofs/loader/root/crane.yml
index e39e7d0..bb645d2 100644
--- a/docker/ship-aerofs/loader/root/crane.yml
+++ b/docker/ship-aerofs/loader/root/crane.yml
@@ -148,6 +148,8 @@ containers:
     image: aerofs/web
     run:
       detach: true
+      volume:
+       - $(pwd)/src/web/web:/opt/web/web
       volumes-from:
         - data
       link:
EOF

patch -p1 << EOF
diff --git a/src/web/root/run.sh b/src/web/root/run.sh
index e37b789..74b0d0b 100755
--- a/src/web/root/run.sh
+++ b/src/web/root/run.sh
@@ -93,4 +93,4 @@ export PYTHONPATH=/opt/web
 export STRIPE_PUBLISHABLE_KEY=dummy.stripe.key
 export STRIPE_SECRET_KEY=dummy.stripe.secret

-pserve /opt/web/production.ini
+pserve --reload /opt/web/production.ini
EOF

patch -p1 << EOF
diff --git a/puppetmaster/modules/unified/files/production.ini.template b/puppetmaster/modules/unified/files/produ
index e1bdbad..eccdf00 100644
--- a/puppetmaster/modules/unified/files/production.ini.template
+++ b/puppetmaster/modules/unified/files/production.ini.template
@@ -13,7 +13,7 @@ deployment.sp_server_uri = http://localhost:8080/sp
 sp.version = 21

 # Pyramid settings
-pyramid.reload_templates = false
+pyramid.reload_templates = true
 pyramid.debug_authorization = false
 pyramid.debug_notfound = false
 pyramid.debug_routematch = false
EOF

make -C src/web && ./docker/dev/dk-reload.sh web

set +x

echo
echo "SUCCESS!"
echo
echo "To clean up patched build files, run the following commands:"
echo
echo "    git checkout docker/ship-aerofs/loader/root/crane.yml"
echo "    git checkout src/web/root/run.sh"
echo "    git checkout puppetmaster/modules/unified/files/production.ini.template"
echo