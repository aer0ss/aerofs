IMAGE_NAME = aerofs/config
ROOT = buildroot

LICENSING_LIB_DIR = ../../src/licensing
RSYNC = rsync -a --delete --delete-excluded --exclude '*.pyc' --exclude '.*'

image:
	mkdir -p buildroot
	$(RSYNC) $(LICENSING_LIB_DIR) buildroot/
	docker build -t $(IMAGE_NAME) .

.PHONY: image
