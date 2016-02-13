IMAGE_NAME = aerofs/nginx

image: base
	docker build -t $(IMAGE_NAME) .

base: apk/x86_64/*
	./build.sh

.PHONY: image
