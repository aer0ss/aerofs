IMAGE_NAME = aerofs/havre
DST_DIR = buildroot/opt/havre

image:
	gradle --daemon dist
	rm -rf buildroot
	mkdir -p $(DST_DIR)
	ln ../../out.gradle/havre/dist/* $(DST_DIR)
	docker build -t $(IMAGE_NAME) .

.PHONY: image
