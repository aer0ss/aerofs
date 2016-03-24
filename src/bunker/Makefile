include ../../docker/Makefile.base

IMAGE_NAME = aerofs/bunker

PROPERTIES_SRC = $(GIT_ROOT)/packaging/config/root/external.properties.docker.default
PROPERTIES_DEST = buildroot/
VERSION_DEST = buildroot/opt/repackaging/installers/original/
WEB_SRC = $(GIT_ROOT)/src/bunker/web/
WEB_DEST = buildroot/opt/bunker/web/

buildroot: $(VERSION_SRC) $(WEB_SRC)
	mkdir -p $(VERSION_DEST)
	$(RSYNC) $(VERSION_SRC) $(VERSION_DEST)

	$(RSYNC) $(PROPERTIES_SRC) $(PROPERTIES_DEST)

	mkdir -p $(WEB_DEST)
	$(RSYNC) --link-dest=$(WEB_SRC) $(WEB_SRC) $(WEB_DEST)

.PHONY: $(WEB_SRC)
$(WEB_SRC):
	make -C ../web/web

image: build buildroot
	docker build --no-cache -t $(IMAGE_NAME) $(IMAGE_CTX)
	$(call success,"- build $(IMAGE_NAME)")

