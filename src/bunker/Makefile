image:
	# make sure all the web assets that we'll be using exist
	$(MAKE) -C ../web/web
	# The Makefile needs to pick up all the files generated from the previous step
	# (i.e. the FILES variable in Makefile.cont), but Makefile can only refer to 
	# file paths that are known in advance. Using a separate Makefile is the only
	# known workaround. "cont" means "continuation".
	# TODO (WW) use the buildroot-syncroot approach instead).
	$(MAKE) -f Makefile.cont
