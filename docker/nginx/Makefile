include ../Makefile.base

IMAGE_NAME = aerofs/nginx

UPDATES_DEST = buildroot/opt/updates/

build:
	docker build -t aerofs/nginx -f ../base/base/Dockerfile.nginx ../base/base

buildroot:
	mkdir -p $(UPDATES_DEST)
	$(RSYNC) --link-dest=$(UPDATES_SRC) $(UPDATES_SRC) $(UPDATES_DEST)
