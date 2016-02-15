IMAGE_NAME = aerofs/sparta
DST_DIR = buildroot/opt/sparta

image:
	gradle --daemon dist
	rm -rf buildroot
	mkdir -p $(DST_DIR)
	ln ../../out.gradle/sparta/dist/* $(DST_DIR)
	docker build -t $(IMAGE_NAME) .

.PHONY: image 
