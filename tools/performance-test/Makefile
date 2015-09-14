APPLIANCE := `ansible/hosts/appliance | jq ' .appliance[0] ' | tr -d '"'`
METRICS := ~/metrics


$(METRICS):
	rsync -avz -e 'ssh -i ansible/aws.pem' core@$(APPLIANCE):/home/core/metrics/ $(METRICS)


metrics.png: http_successes.png logical_objects.png transform.png transform_slope.png
	convert \( logical_objects.png transform.png -append \) \( http_successes.png transform_slope.png -append \) +append large.png

http_successes.png: $(METRICS)
	./plotter.py --metrics=$< --output=$@ http.request.success.1min-rate http.request.success.5min-rate http.request.success.15min-rate

logical_objects.png: $(METRICS)
	./plotter.py --metrics=$< --output=$@ com.aerofs.polaris.dao.LogicalObjects.add.1min-rate com.aerofs.polaris.dao.LogicalObjects.update.1min-rate com.aerofs.polaris.dao.LogicalObjects.get.1min-rate com.aerofs.polaris.dao.LogicalObjects.getStore.1min-rate

transform.png: $(METRICS)
	./plotter.py --metrics=$< --output=$@ http.request.success.count com.aerofs.polaris.dao.Transforms.add.count

transform_slope.png: $(METRICS)
	./plotter.py --metrics=$< --output=$@ --slope http.request.success.count com.aerofs.polaris.dao.Transforms.add.count


clean:
	rm -rf $(METRICS)
	rm -f metrics.png
	rm -f http_successes.png
	rm -f logical_objects.png
	rm -f transform.png
	rm -f transform_slope.png
