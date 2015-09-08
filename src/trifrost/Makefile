include ../../docker/Makefile.base

IMAGE_NAME = aerofs/trifrost

DIST_SRC = $(GIT_ROOT)/out.gradle/trifrost/dist/
DIST_DEST = buildroot/opt/trifrost/

buildroot: dist
	mkdir -p $(DIST_DEST)
	$(RSYNC) --link-dest=$(DIST_SRC) $(DIST_SRC) $(DIST_DEST)
