include ../../docker/Makefile.base

IMAGE_NAME = aerofs/sparta

DIST_SRC = $(GIT_ROOT)/out.gradle/sparta/dist/
DIST_DEST = buildroot/opt/sparta/

buildroot: dist
	mkdir -p $(DIST_DEST)
	$(RSYNC) --link-dest=$(DIST_SRC) $(DIST_SRC) $(DIST_DEST)
