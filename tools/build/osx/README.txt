How to update *.dmg.template
------

1. go to disk image utility
2. create a disk image of roughly the size you expect to need (~100mb? it doesn't matter, it's compressed down to the deflated size)
3. name it "AeroFS Installer"
4. mount it
5. arrange it to look the wa you want it to look in finder e.g. backgrounds, layouts, etc.
6. unmount it and never manually touch it again

N.B. The Java code (Launcher.java) assumes the mount point of the dmg contains string "Installer". So be sure to use this string at step 3.

How to update *.app.template
------

They are plain folders. Just update whatever files in it and you are good to go.
