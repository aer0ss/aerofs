IMAGE_NAME = aerofs/loader

TAG = buildroot/tag

image: $(TAG)
	docker build -t $(IMAGE_NAME) .
	docker run --rm $(IMAGE_NAME) verify $(IMAGE_NAME)

$(TAG): ../../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	sed -e 's/.*=//' $< > $@
