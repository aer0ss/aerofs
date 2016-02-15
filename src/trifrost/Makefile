IMAGE_NAME = aerofs/trifrost
DST_DIR = buildroot/opt/trifrost

image:
	gradle --daemon dist
	rm -rf buildroot
	mkdir -p $(DST_DIR)
	ln ../../out.gradle/trifrost/dist/* $(DST_DIR)
	docker build -t $(IMAGE_NAME) .

.PHONY: image
