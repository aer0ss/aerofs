# A large part of this file was adapted from https://github.com/docker-library/python/blob/master/2.7/Dockerfile
#
# I (WW) didn't use system python as it doesn't work very well, e.g. Bunker ran into:
#       AttributeError: 'module' object has no attribute 'PROTOCOL_SSLv3'
# when launching.
#
FROM aerofs/base

MAINTAINER Weihan Wang "weihan@aerofs.com"

# remove several traces of debian python
RUN apt-get purge -y python.*

# http://bugs.python.org/issue19846
# > At the moment, setting "LANG=C" on a Linux system *fundamentally breaks Python 3*, and that's not OK.
ENV LANG C.UTF-8

ENV PYTHON_VERSION 2.7.9

ENV BUILD_PKGS xz-utils gcc make

# gpg: key 18ADD4FF: public key "Benjamin Peterson <benjamin@python.org>" imported
RUN gpg --keyserver pool.sks-keyservers.net --recv-keys C01E1CAD5EA2C4F0B8E3571504C367C218ADD4FF

RUN set -x \
    && apt-get update && apt-get install -y zlib1g-dev libssl-dev libbz2-dev $BUILD_PKGS \
	&& mkdir -p /usr/src/python \
	&& curl -SL "https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tar.xz" -o python.tar.xz \
	&& curl -SL "https://www.python.org/ftp/python/$PYTHON_VERSION/Python-$PYTHON_VERSION.tar.xz.asc" -o python.tar.xz.asc \
	&& gpg --verify python.tar.xz.asc \
	&& tar -xJC /usr/src/python --strip-components=1 -f python.tar.xz \
	&& rm python.tar.xz* \
	&& cd /usr/src/python \
	&& ./configure --enable-shared --enable-unicode=ucs4 \
	&& make -j$(nproc) \
	&& make install \
	&& ldconfig \
	&& curl -SL 'https://bootstrap.pypa.io/get-pip.py' | python2 \
	&& find /usr/local \
		\( -type d -a -name test -o -name tests \) \
		-o \( -type f -a -name '*.pyc' -o -name '*.pyo' \) \
		-exec rm -rf '{}' + \
	&& rm -rf /usr/src/python \
	&& apt-get purge -y $BUILD_PKGS \
	&& rm -rf /var/lib/apt/lists/*

# requests is needed by aerofs_common.configuration
RUN pip install requests

COPY buildroot /

RUN pip install /sdist/* && rm -rf /sdist
