#!/bin/bash
set -eu

cd $(dirname $0)/../../..

echo "Patching various build files..."

set -x

patch -p1 << EOF
diff --git a/docker/ship-aerofs/loader/root/crane.yml.jinja b/docker/ship-aerofs/loader/root/crane.yml.jinja
index e39e7d0..bb645d2 100644
--- a/docker/ship-aerofs/loader/root/crane.yml.jinja
+++ b/docker/ship-aerofs/loader/root/crane.yml.jinja
@@ -149,6 +149,8 @@ containers:
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
index 192aefc..4c76094 100755
--- a/src/web/root/run.sh
+++ b/src/web/root/run.sh
@@ -88,4 +88,4 @@ export PYTHONPATH=/opt/web
 export STRIPE_PUBLISHABLE_KEY=dummy.stripe.key
 export STRIPE_SECRET_KEY=dummy.stripe.secret
 
-/container-scripts/restart-on-error pserve /opt/web/production.ini
+/container-scripts/restart-on-error pserve --reload /opt/web/production.ini
EOF

patch -p1 << EOF
diff --git a/src/web/root/opt/web/production.ini.template b/src/web/root/opt/web/production.ini.template
index 4fb5913..9921f9b 100644
--- a/src/web/root/opt/web/production.ini.template
+++ b/src/web/root/opt/web/production.ini.template
@@ -13,7 +13,7 @@ deployment.sp_server_uri = http://sp.service:8080
 sp.version = 22
 
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
echo "    git checkout docker/ship-aerofs/loader/root/crane.yml.jinja"
echo "    git checkout src/web/root/run.sh"
echo "    git checkout src/web/root/opt/web/production.ini.template"
echo
