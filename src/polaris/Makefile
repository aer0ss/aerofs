IMAGE_NAME = aerofs/polaris
DST_DIR = buildroot/opt/polaris

image:
	gradle --daemon dist
	rm -rf buildroot
	mkdir -p $(DST_DIR)
	ln ../../out.gradle/polaris/dist/* $(DST_DIR)
	docker build -t $(IMAGE_NAME) .

.PHONY: image
