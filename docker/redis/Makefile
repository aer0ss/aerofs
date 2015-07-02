IMAGE_NAME = aerofs/redis

image:
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

.PHONY: image
