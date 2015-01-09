IMAGE_NAME = aerofs/sparta
DST_DIR = buildroot/opt/sparta

image: .gradle $(DST_DIR)/logback.xml $(DST_DIR)/sparta.properties
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/sparta/dist $(DST_DIR)

$(DST_DIR)/%: resources/%
	cp $< $@

$(DST_DIR)/sparta.properties: resources/sparta.properties
	sed -e 's|mysql://localhost/aerofs_sp|mysql://mysql.service/aerofs_sp|' \
		-e 's|http://localhost:8700/tokeninfo|http://bifrost.service:8700/tokeninfo|' \
		-e 's/127.0.0.1/0.0.0.0/' $< > $@
