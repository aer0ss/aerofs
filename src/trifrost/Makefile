IMAGE_NAME = aerofs/trifrost
DST_DIR = buildroot/opt/trifrost

image:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/trifrost/dist buildroot/opt/trifrost
	docker build -t $(IMAGE_NAME) .

.PHONY: image
