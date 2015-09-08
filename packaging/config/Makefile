include ../../docker/Makefile.base

IMAGE_NAME = aerofs/config

LICENSING_DEST = buildroot/licensing/
RSYNC += --exclude build --exclude unittests

buildroot: $(LICENSING_SRC)
	mkdir -p $(LICENSING_DEST)
	$(RSYNC) --link-dest=$(LICENSING_SRC) $(LICENSING_SRC) $(LICENSING_DEST)
