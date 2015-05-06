# The code is adapted from https://github.com/docker-library/java/blob/master/openjdk-8-jre/Dockerfile.
#
FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

# see https://bugs.debian.org/775775
# and https://github.com/docker-library/java/issues/19#issuecomment-70546872
# Note that these issues may apply only to Debian and not Ubuntu. TODO: remove it?
ENV CA_CERTIFICATES_JAVA_VERSION 20140324

RUN echo 'Acquire::http::Proxy "http://10.0.2.2:3142";' > /etc/apt/apt.conf.d/80httpproxy &&\
    apt-get update && apt-get install -y \
        openjdk-8-jre-headless \
        ca-certificates-java="$CA_CERTIFICATES_JAVA_VERSION" &&\
    rm -rf /var/lib/apt/lists/* &&\
    rm /etc/apt/apt.conf.d/80httpproxy &&\
    # see CA_CERTIFICATES_JAVA_VERSION notes above
    /var/lib/dpkg/info/ca-certificates.postinst configure
