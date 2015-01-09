IMAGE_NAME = aerofs/zephyr
DST_DIR = buildroot/opt/zephyr

image: $(DST_DIR)/banner.txt $(DST_DIR)/logback.xml
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/zephyr/dist $(DST_DIR)
	docker build -t $(IMAGE_NAME) .

$(DST_DIR)/%: resources/%
	mkdir -p $(@D)
	cp $< $@
