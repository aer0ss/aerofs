include ../../docker/Makefile.base

IMAGE_NAME = aerofs/polaris

DIST_SRC = $(GIT_ROOT)/out.gradle/polaris/dist/
DIST_DEST = buildroot/opt/polaris/

buildroot: dist
	mkdir -p $(DIST_DEST)
	$(RSYNC) --link-dest=$(DIST_SRC) $(DIST_SRC) $(DIST_DEST)
