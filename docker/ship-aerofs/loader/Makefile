IMAGE_NAME = aerofs/loader

TAG = buildroot/tag

image: $(TAG)
	make -C ../../ship/vm/loader
	docker build -t $(IMAGE_NAME) .
	docker run --rm $(IMAGE_NAME) verify $(IMAGE_NAME)

$(TAG): ../../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	sed -e 's/.*=//' $< > $@

.PHONY: image
