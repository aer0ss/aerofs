include ../../docker/Makefile.base

IMAGE_NAME = aerofs/havre

DIST_SRC = $(GIT_ROOT)/out.gradle/havre/dist/
DIST_DEST = buildroot/opt/havre/

buildroot: dist
	mkdir -p $(DIST_DEST)
	$(RSYNC) --link-dest=$(DIST_SRC) $(DIST_SRC) $(DIST_DEST)
