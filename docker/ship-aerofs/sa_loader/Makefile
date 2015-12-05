IMAGE_NAME = aerofs/sa-loader

TAG = buildroot/tag

image: $(TAG)
	../../ship/vm/loader/build.sh
	docker build -t $(IMAGE_NAME) .

	docker run --rm \
		-v /var/run/docker.sock:/var/run/docker.sock \
		$(IMAGE_NAME) \
		verify $(IMAGE_NAME)

$(TAG): ../../../out.gradle/packages/current.ver
	mkdir -p $(@D)
	sed -e 's/.*=//' $< > $@

../../../out.gradle/packages/current.ver:
	../../../invoke write_version

.PHONY: image
