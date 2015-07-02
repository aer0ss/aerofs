IMAGE_NAME = aerofs/mysql

image:
	$(MAKE) -f ../container-scripts/Makefile
	docker build -t $(IMAGE_NAME) .

.PHONY: image
