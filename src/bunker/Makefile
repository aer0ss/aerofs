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

# TODO: Docker 1.9.0 has a caching issue that results in the final
# `COPY root buildroot /` line of our Dockerfile sometimes being cached even
# on file changes. This is probably because we use hardlinks in the buildroot
# to save space.
# See https://github.com/docker/docker/issues/17827
image: build buildroot
	docker build --no-cache -t $(IMAGE_NAME) $(IMAGE_CTX)
	$(call success,"- build $(IMAGE_NAME)")
