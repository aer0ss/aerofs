IMAGE_NAME = aerofs/probe

WEB_INF_DIR = buildroot/usr/share/tomcat6/webapps/ROOT/WEB-INF

image: $(WEB_INF_DIR)/web.xml
	docker build -t $(IMAGE_NAME) .

$(WEB_INF_DIR)/%: %
	mkdir -p $(@D)
	cp $< $@

.PHONY: image
