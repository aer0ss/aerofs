IMAGE_NAME = aerofs/bunker

RSYNC = rsync -a --delete --delete-excluded --exclude '*.pyc' --exclude '.*'

image:
	$(MAKE) -C ../web/web
	mkdir -p buildroot/opt/bunker buildroot/opt/repackaging/installers/original
	$(RSYNC) ../../out.gradle/packages/current.ver buildroot/opt/repackaging/installers/original/
	$(RSYNC) ../../packaging/config/root/external.properties.docker.default buildroot/
	$(RSYNC) web buildroot/opt/bunker/
	docker build -t $(IMAGE_NAME) .

.PHONY: image

