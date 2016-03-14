include ../../docker/Makefile.base

IMAGE_NAME = aerofs/web

VERSION_DEST = buildroot/opt/repackaging/installers/original/
WEB_SRC = $(GIT_ROOT)/src/web/web/
WEB_DEST = buildroot/opt/web/web/
RSYNC += --exclude node_modules --exclude static-src

buildroot: $(VERSION_SRC) $(WEB_SRC) .make-dryad
	mkdir -p $(VERSION_DEST)
	$(RSYNC) $(VERSION_SRC) $(VERSION_DEST)

	mkdir -p $(WEB_DEST)
	$(RSYNC) --link-dest=$(WEB_SRC) $(WEB_SRC) $(WEB_DEST)
	$(RSYNC) ../../packaging/debs/aerofs-dryad.deb buildroot/opt/web/web/static/

.PHONY: $(WEB_SRC)

$(WEB_SRC):
	make -C web

.make-dryad:
	$(MAKE) -C ../../packaging dryad
