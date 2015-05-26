IMAGE_NAME = aerofs/havre

image:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/havre/dist buildroot/opt/havre
	docker build -t $(IMAGE_NAME) .

.PHONY: image
