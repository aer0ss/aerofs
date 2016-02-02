IMAGE_NAME = aerofs/web

BUILD_ROOT = buildroot
RSYNC = rsync -a --delete --delete-excluded --exclude '*.pyc' --exclude '.*'

image: .make-source
	mkdir -p buildroot/opt/web buildroot/opt/repackaging/installers/original
	$(RSYNC) ../../out.gradle/packages/current.ver buildroot/opt/repackaging/installers/original/
	$(RSYNC) --exclude node_modules --exclude static-src web buildroot/opt/web/
	docker build -t $(IMAGE_NAME) .

.PHONY: image base lib proto .make-source

base: lib
	$(MAKE) -C ../../docker/base/python2.7
lib: proto
	$(MAKE) -C ../python-lib
proto:
	../../invoke proto

.make-source:
	$(MAKE) -C web

