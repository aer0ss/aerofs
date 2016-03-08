#!/bin/bash
#
# Generates base DEBIAN package layout in a $SERVICE subdir of the pwd:
#   - create /var/log/$SERVICE
#   - generate control file describing the package
#   - generate preinst script that adds a system user for service
#   - generate postinst script that makes sure the service's log folder is
#     owned by the service user
#   - generate prerm script that ensures the service is stopped
#   - generate postrm script the remove /opt/$SERVICE if purge is specified
#   - generate conffiles file
#   - generate upstart service configuration
#   - copy Java build artifacts from ant output dir
#

set -ue

if [[ $# -lt 1 || $# -gt 4 || "x$1" == "x" ]]
then
    echo "$@"
    echo "usage: $0 <service_name> [conf_files] [java_args] [service_args]"
    echo
    echo "example: $0 verkehr"
    exit 2
fi

SERVICE="$1"
USER="$SERVICE"
GROUP="$USER"

CONF_FILES=""
if [[ $# -gt 1 ]]
then
    CONF_FILES="$2"
fi

JAVA_ARGS=""
if [[ $# -gt 2 ]]
then
    JAVA_ARGS="$3"
fi

SERVICE_ARGS=""
if [[ $# -gt 3 ]]
then
    SERVICE_ARGS="$4"
fi

OUTPUT_DIR=build/$SERVICE

DEBIAN="$OUTPUT_DIR/DEBIAN"

mkdir -p "$DEBIAN"
mkdir -p "$OUTPUT_DIR/var/log/$SERVICE"

cat << EOF >> "$DEBIAN"/control
Package: aerofs-$SERVICE
Section: java
Priority: required
Maintainer: AeroFS <team@aerofs.com>
Description: AeroFS $SERVICE package.
Architecture: all
Depends: java8-jdk
EOF

cat << EOF >> "$DEBIAN"/preinst
#!/bin/bash
# first stop service if it is running
service $SERVICE stop || true

# create the required user, if it hasn't been created yet.
info=\$(id $USER 2>/dev/null)
if [ "\$info" = "" ]
then
    useradd                 \
        --system            \
        --create-home       \
        --user-group        \
        --shell /bin/bash   \
        $USER
fi
EOF

cat << EOF >> "$DEBIAN"/postinst
#!/bin/bash
chown $USER:$GROUP /var/log/$SERVICE -R
EOF

cat << EOF >> "$DEBIAN"/prerm
#!/bin/bash
service $SERVICE stop || true
EOF

cat << EOF >> "$DEBIAN"/postrm
#!/bin/bash
if [ \$1 == purge ]
then
    rm -rf /opt/$SERVICE
fi
EOF

for f in preinst postinst prerm postrm
do
    chmod 755 "$DEBIAN"/$f
done

mkdir -p $OUTPUT_DIR/etc/init

cat << EOF >> "$OUTPUT_DIR"/etc/init/$SERVICE.conf
# $SERVICE service script

description "$SERVICE server"
author "AeroFS <team@aerofs.com>"

# when to start the service
start on runlevel [2345]

# when to stop the service
stop on runlevel [016]

# automatically restart $SERVICE if it crashes
# up to 10 times in a 60 second period
respawn
respawn limit 10 60

# Drop privileges
setuid $USER
setgid $GROUP

# Change directories before spawning child
chdir /opt/$SERVICE

exec /usr/bin/java -XX:+HeapDumpOnOutOfMemoryError      \\
        -XX:HeapDumpPath=/var/log/$SERVICE              \\
        $JAVA_ARGS                                      \\
        -jar /opt/$SERVICE/aerofs-$SERVICE.jar $SERVICE_ARGS

EOF

OPT="$OUTPUT_DIR/opt/$SERVICE"
mkdir -p $OPT

# conf files
for f in $CONF_FILES
do
    cp $f $OPT/
    echo "/opt/$SERVICE/$(basename $f)" >> $DEBIAN/conffiles
done

# Java-related file copies.
cp ../out.gradle/$SERVICE/dist/*.jar $OPT/