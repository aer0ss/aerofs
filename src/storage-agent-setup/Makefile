include ../../docker/Makefile.base

IMAGE_NAME = aerofs/storage-agent-setup

VERSION_DEST = buildroot/version

buildroot: $(VERSION_SRC)
	mkdir -p $(VERSION_DEST)
	$(RSYNC) $(VERSION_SRC) $(VERSION_DEST)
