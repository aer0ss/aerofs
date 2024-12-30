GIT_ROOT = $(shell git rev-parse --show-toplevel)
include $(GIT_ROOT)/Makefile.common

IMAGES:= \
	../golang/src/aerofs.com/auditor \
	../src/bunker \
	../golang/src/aerofs.com/ca-server \
	../packaging/config \
	../golang/src/aerofs.com/charlie \
	data-container \
	enforcer \
	../src/havre \
	../golang/src/github.com/aerofs/lipwig \
	logrotator \
	maintenance-nginx \
	../src/maintenance-web \
	mysql \
	nginx \
	ntp \
	postfix \
	../src/polaris \
	redis \
	../packaging/repackaging \
	../packaging/sanity \
	../src/sparta \
	../src/spsv \
	../golang/src/aerofs.com/ts-probe \
	../golang/src/aerofs.com/valkyrie \
	../golang/src/aerofs.com/waldo \
	../src/web \
	webdriver-python \
	ship-aerofs/loader

all:

images: $(IMAGES)
	make -C ship-aerofs/loader verify
	make clean_docker

.PHONY: $(IMAGES)
$(IMAGES): base
	make -C $@ image

.PHONY: base
base: cache
	make -C base image

cache:
	$(GIT_ROOT)/tools/cache/start.sh

clean_docker:
	docker rmi `docker images --no-trunc | grep '^<none>' | awk '{print $$3}'` 2>/dev/null ||:


clean:
	make -C base clean
	for img in $(IMAGES); do \
		make -C $$img clean; \
	done
