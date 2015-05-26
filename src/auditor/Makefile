IMAGE_NAME = aerofs/auditor

image:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/auditor/dist buildroot/opt/auditor
	docker build -t $(IMAGE_NAME) .

.PHONY: image
