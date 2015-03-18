# The code is adapted from https://github.com/docker-library/java/blob/master/openjdk-8-jre/Dockerfile.
#
FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

# see https://bugs.debian.org/775775
# and https://github.com/docker-library/java/issues/19#issuecomment-70546872
ENV CA_CERTIFICATES_JAVA_VERSION 20140324

RUN apt-get update && apt-get install -y \
        openjdk-8-jre-headless \
        ca-certificates-java="$CA_CERTIFICATES_JAVA_VERSION" &&\
    rm -rf /var/lib/apt/lists/*

# see CA_CERTIFICATES_JAVA_VERSION notes above
RUN /var/lib/dpkg/info/ca-certificates-java.postinst configure
