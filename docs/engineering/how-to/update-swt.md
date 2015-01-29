The SWT libraries (libswt-*.so files) needs to be removed from the lib/swt.jar and moved under APPROOT
to fix an unsatisfied link error on looking up the libraries for certain users.

See http://askubuntu.com/questions/125150/unsatisfied-link-error-and-missing-so-files-when-starting-eclipse

To problem is solved by setting the java.library.path property to the absolute path of APPROOT.

The following steps shows how to remove the libraries from the jar:

For Linux32/64 and OSX:
    unzip swt-3.7.jar libswt-*
    mv libswt-* ../
    zip -d swt-3.7.jar libswt-*
For Windows:
    unzip swt-3.7.jar swt*
    mv *.dll ../
    zip -d swt-3.7.jar swt*
