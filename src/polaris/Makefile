IMAGE_NAME = aerofs/polaris

DST_DIR = buildroot/opt/polaris

image: .gradle $(DST_DIR)/polaris.yml.tmplt
	docker build -t $(IMAGE_NAME) .

.gradle:
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/polaris/dist $(DST_DIR)

$(DST_DIR)/polaris.yml.tmplt: src/dist/polaris.yml.tmplt
	sed -e 's|url: http://localhost:25234|url: http://verkehr.service:25234|' \
		-e 's|url: http://localhost:8085|url: http://sparta.service:8085|' \
		-e 's/localhost/0.0.0.0/' $< > $@

.PHONY: image .gradle
