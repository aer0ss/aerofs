#!/bin/bash
set -eu

cd $(dirname $0)/../../..

echo "Patching various build files..."

set -x

patch -p1 << EOF
diff --git a/docker/ship-aerofs/loader/root/crane.yml.jinja b/docker/ship-aerofs/loader/root/crane.yml.jinja
index 89689f4..1b8a6f5 100644
--- a/docker/ship-aerofs/loader/root/crane.yml.jinja
+++ b/docker/ship-aerofs/loader/root/crane.yml.jinja
@@ -89,6 +89,8 @@ containers:
     run:
       detach: true
       volume:
+        - $(pwd)/src/web/web:/opt/web/web
+        - $(pwd)/src/bunker/web:/opt/bunker/web
         # To collect docker logs
         - /var/run/docker.sock:/var/run/docker.sock
         - /var/lib/docker/containers:/var/lib/docker/containers
EOF

make -C src/bunker && ./docker/dev/dk-reload.sh bunker

set +x

echo
echo "SUCCESS!"
echo
echo "To clean up patched build files, run the following commands:"
echo
echo "    git checkout docker/ship-aerofs/loader/root/crane.yml.jinja"
echo
