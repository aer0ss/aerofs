IMAGE_NAME = aerofs/web

BUILD_ROOT = buildroot
RSYNC = rsync -a --delete --delete-excluded --exclude '*.pyc' --exclude '.*'

image: .make-source .make-dryad
	mkdir -p buildroot/opt/web buildroot/opt/repackaging/installers/original
	$(RSYNC) ../../out.gradle/packages/current.ver buildroot/opt/repackaging/installers/original/
	$(RSYNC) --exclude node_modules --exclude static-src web buildroot/opt/web/
	$(RSYNC) ../../packaging/debs/aerofs-dryad.deb buildroot/opt/web/web/static/
	docker build -t $(IMAGE_NAME) .

.PHONY: image .make-source

.make-source:
	$(MAKE) -C web

.make-dryad:
	$(MAKE) -C ../../packaging dryad