include ../../docker/Makefile.base

IMAGE_NAME = aerofs/repackaging

AEROFS_COMMON_SRC = $(GIT_ROOT)/src/python-lib/aerofs_common/
AEROFS_COMMON_DEST = buildroot/opt/repackaging/api/aerofs_common/
CONTAINER_SCRIPTS_SRC = $(GIT_ROOT)/docker/base/base/root/container-scripts/
CONTAINER_SCRIPTS_DEST = buildroot/container-scripts/
PACKAGES_DEST = buildroot/opt/repackaging/installers/original/
RSYNC += --exclude '_gen'

PACKAGES = $(shell ls $(PACKAGES_SRC))

build:
	make -C base image

buildroot:
ifeq (,$(filter 17 18, $(words $(PACKAGES))))
	$(error $(words $(PACKAGES)) files were found in $(PACKAGES_SRC). I expect 17 or 18(syncdet only))
endif
	mkdir -p $(PACKAGES_DEST)
	$(RSYNC) --link-dest=$(PACKAGES_SRC) $(PACKAGES_SRC) $(PACKAGES_DEST)

	mkdir -p $(AEROFS_COMMON_DEST)
	$(RSYNC) --link-dest=$(AEROFS_COMMON_SRC) $(AEROFS_COMMON_SRC) $(AEROFS_COMMON_DEST)

	mkdir -p $(CONTAINER_SCRIPTS_DEST)
	$(RSYNC) --link-dest=$(CONTAINER_SCRIPTS_SRC) $(CONTAINER_SCRIPTS_SRC) $(CONTAINER_SCRIPTS_DEST)
