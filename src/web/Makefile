include ../../docker/Makefile.base

IMAGE_NAME = aerofs/web

DRYAD_DEST = buildroot/opt/web/web/static/
VERSION_DEST = buildroot/opt/repackaging/installers/original/
WEB_SRC = $(GIT_ROOT)/src/web/web/
WEB_DEST = buildroot/opt/web/web/
RSYNC += --exclude node_modules --exclude static-src

buildroot: $(DRYAD_SRC) $(VERSION_SRC) $(WEB_SRC)
	mkdir -p $(VERSION_DEST)
	$(RSYNC) $(VERSION_SRC) $(VERSION_DEST)

	mkdir -p $(WEB_DEST)
	$(RSYNC) --link-dest=$(WEB_SRC) $(WEB_SRC) $(WEB_DEST)

	mkdir -p $(DRYAD_DEST)
#	$(RSYNC) --link-dest=$(PACKAGING_DEBS_SRC) $(DRYAD_SRC) $(DRYAD_DEST)

.PHONY: $(WEB_SRC)

$(WEB_SRC):
	make -C web
