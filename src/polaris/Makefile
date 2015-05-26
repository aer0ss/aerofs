IMAGE_NAME = aerofs/polaris

image:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/polaris/dist buildroot/opt/polaris
	docker build -t $(IMAGE_NAME) .

.PHONY: image
