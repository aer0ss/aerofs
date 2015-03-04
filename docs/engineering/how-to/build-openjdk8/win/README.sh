#
# Instructions to rebuild OpenJDK 8 to be shipped in the Windows clients
#
# NB: almost but not quite a readily runnable script (on OSX, assuming
# all dev tools are installed)
#
# Looking forward we should strongly consider having the infrastructure
# team (whenever we actually get one) automate the creation of build
# machines and the building of the JRE on these machines.
#

# prepare openjdk build environment

pushd ~/repos

hg clone --pull http://hg.openjdk.java.net/jdk8u/jdk8u openjdk8u
pushd openjdk8u

# clone/pull sub-repos
sh get_source.sh

# put all sub-repos at a known-good release tag
sh ./make/scripts/hgforest.sh update -c jdk8u60-b04

# apply Windows-specific patch for File.list
pushd jdk
patch -p1 <../../winntfs_list_trailing_space.patch
popd

popd

##########################################################################
# Now:
#     1. spin up the windows build vm
#     2. make sure the local openjdk checkout is shared with the vm
#        NB: you could conceivably bypass shared folders but I/O
#        performance would take a serious hit which would significantly
#        slow down the overall build time
#     3. launch Cygwin terminal
#     4.   cd  /cygdrive/z/openjdk8u
#     5    bash ./configure --with-freetype=/cygdrive/c/code/freetype-2.4.7 --with-target-bits=32 --with-boot-jdk=/cygdrive/c/code/jdk1.70-55 && make images
#     6. wait ~2h
#     7. profit (assuming nothing broke)
#########################################################################

# Use Greg's tool to remove unnecessary files (shrink by ~5x)
git clone https://github.com/aerofs/openjdk-trim.git

./openjdk-trim/jdk-trim win openjdk8u/build/j2sdk-image/ j2sdk-trim

# move trimmed jre to AeroFS main repo
rm -rf aerofs/resource/client/win/jre/
mv j2sdk-trim/jre/ aerofs/resource/client/win/jre


#######################################################################
#
# IMPORTANT: OpenJDK build scripts generate an empty cacerts file
# This is a problem when contacting a non-AeroFS server over an SSL
# connection which at the time of this writing only happens in the
# Hybrid Cloud updater. The current best partice is to simply keep
# the cacerts from git master but longer term it would probably be
# good to either generate the list of trusted certs ourselves and/or
# setup our own download server.
#
######################################################################

pushd aerofs
git checkout -f resource/client/win/jre/lib/security/cacerts
popd

######################################################################
#
# IMPORTANT: OpenJDK build scripts by default do not enable unlimited
# crypto, which is breaks S3 TeamServer as AES256 is "too strong"
# for the default security profile.
#
# The files currently in master disable all crypto restrictions so
# the simplest approach is to preserve them.
#
# If in doubt, extract and inspect the content of the files (jar xf).
#
######################################################################

pushd aerofs
git checkout -f resource/client/win/jre/lib/security/local_policy.jar
git checkout -f resource/client/win/jre/lib/security/US_export_policy.jar
popd


popd
