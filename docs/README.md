New to AeroFS?
---

Welcome! Follow these steps to get started:

    $ cd ~/repos/aerofs
    $ ./invoke markdown
    
This will compile all the .md files into .html files. Then,

	$ open ~/repos/aerofs/shell.out/docs/engineering/how-to/get-started.html
	
or click [here](engineering/how-to/get-started.html) to browse the getting started guide.

	$ ./invoke markdown_watch
	
continuously monitors changes and compiles modified files in the doc folder.

Name convention
---

All file in this folder should follow the naming convention of
`lower-case-letters-with-dashes.ext`.
