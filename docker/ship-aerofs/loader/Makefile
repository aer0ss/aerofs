IMAGE_NAME = aerofs/loader

TAG = buildroot/tag

image: $(TAG)
	../../ship/vm/loader/build.sh
	docker build -t $(IMAGE_NAME) .
	docker run --rm $(IMAGE_NAME) verify $(IMAGE_NAME)

$(TAG): ../../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	sed -e 's/.*=//' $< > $@

../../../out.gradle/packages/current.ver:
	../../../invoke write_version

.PHONY: image
