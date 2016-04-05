include ../../Makefile.base

IMAGE_NAME = aerofs/analytics-pull
PACKAGE_NAME = aerofs.com/analytics-pull

VERSION_DEST = buildroot/opt/repackaging/installers/original/

GOCKERIZE_CTX = ../..

buildroot: $(VERSION_SRC)
	mkdir -p $(VERSION_DEST)
	$(RSYNC) $(VERSION_SRC) $(VERSION_DEST)
