BASE_DIRS = \
	base/base \
	base/ubuntu14.04 \
	base/ubuntu15.04 \
	base/jre8 \
	base/jre8-and-mysql-client \
	base/python2.7

# Sort image names alphabetically to help users observe build progress
DIRS = \
	../src/auditor \
	../src/bifrost \
	../src/bunker \
	../src/ca-server \
	../packaging/config \
	../packaging/charlie \
	data-container \
	enforcer \
	ejabberd \
	../src/havre \
	maintenance-nginx \
	mysql \
	nginx \
	ntp \
	postfix \
	../src/polaris \
	redis \
	../packaging/repackaging \
	../packaging/sanity \
	../src/sparta \
	\
	../src/spsv \
	../src/spsv/resources/identity \
	../src/spsv/resources/log-collection \
	../src/spsv/resources/probe \
	../src/spsv/resources/sp \
	../src/spsv/resources/verification \
	\
	../src/verkehr \
	../src/web \
	../src/zephyr \
	\
	ship/vm/loader \
	ship-aerofs/loader

all: images gc

DOCKERIZE_BASE_DIRS=$(BASE_DIRS:%=dockerize-%)
DOCKERIZE_DIRS=$(DIRS:%=dockerize-%)

base: $(DOCKERIZE_BASE_DIRS)
images: base $(DOCKERIZE_DIRS)

$(DOCKERIZE_BASE_DIRS) $(DOCKERIZE_DIRS):
	@echo "========================================"
	@echo "  Building Docker image $(@:dockerize-%=%)"
	@echo "========================================"
	$(MAKE) -C $(@:dockerize-%=%) image

ship: make-ship gc

make-ship:
	$(MAKE) -C ship-aerofs

gc:
	@echo Removing untagged images to save space...
	docker rmi `docker images --no-trunc | grep '^<none>' | awk '{print $$3}'` 2>/dev/null || true

clean:
	# TODO (WW) clean buildroot?
	$(MAKE) -C ship-aerofs clean

.PHONY: ship
