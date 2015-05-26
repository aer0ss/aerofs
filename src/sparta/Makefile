IMAGE_NAME = aerofs/sparta
DST_DIR = buildroot/opt/sparta

image:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/sparta/dist buildroot/opt/sparta
	docker build -t $(IMAGE_NAME) .

.PHONY: image 
