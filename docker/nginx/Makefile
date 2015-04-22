IMAGE_NAME = aerofs/nginx

image:
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

.PHONY: image
