GIT_ROOT = $(shell git rev-parse --show-toplevel)
include $(GIT_ROOT)/Makefile.common

IMAGES:= \
	aeroim \
	../golang/src/aerofs.com/analytics \
	../golang/src/aerofs.com/auditor \
	../src/blurber \
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
	../golang/src/aerofs.com/sloth \
	../src/sparta \
	../src/spsv \
	../golang/src/aerofs.com/trifrost \
	../golang/src/aerofs.com/ts-probe \
	../golang/src/aerofs.com/valkyrie \
	../src/web \
	ship-aerofs/loader

SA_IMAGES:= \
	../src/storage-agent \
	../src/storage-agent-setup \
	ship-aerofs/sa-loader


all:

images: $(IMAGES)
	make -C ship-aerofs/loader verify
	make clean_docker
sa_images: $(SA_IMAGES)
	make -C ship-aerofs/sa-loader verify
	make clean_docker


.PHONY: $(IMAGES)
$(IMAGES): base
	make -C $@ image

.PHONY: $(SA_IMAGES)
$(SA_IMAGES): base
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
	for img in $(SA_IMAGES); do \
		make -C $$img clean; \
	done
