IMAGE_NAME = aerofs/base.ubuntu14.04

image:
	$(MAKE) -f ../../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

.PHONY: image
