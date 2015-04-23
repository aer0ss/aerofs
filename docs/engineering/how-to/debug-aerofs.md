[ Debuggery; a WIP ]

Debugging client programs
--

The 'rundebug' script offers an easy way to start the gui or daemon with a remote debugger port by using the `-G` or `-D` parameters (case-sensitive). The JNDI service will listen on the following default ports:

 - `portbase + 5` : daemon (default 5005)

 - `portbase + 10` : GUI (default 5010)

To access the script, go in `tools` first.

When you start the debug-enabled program, rundebug will print the port number in use.

    $ ./rundebug -G
    Started GUI with RTROOT /Users/jon/repos/aerofs/approot/jon
    Output redirected to /Users/jon/repos/aerofs/approot/jon/gui.out
    DEBUGGER: running at port 5010.

In IDEA, create a Remote run configuration with the hostname set to "localhost" and the port number set the the debugger port. I recommend keeping a Debug_GUI and Debug_Daemon configuration pointing to port 5005 and 5010, respectively. Less inertia means more debugging.

rundebug starts these processes in "suspended" mode - the JVM will not kick off the main() method until you attach a debugger.

See the "Using IDEA..." section below.

Debugging SP and other server instances
--

The glorious future we call "local prod" makes it very easy to attach a Java debugger to servlets. Let's use SP as an example. 

We want to make the Tomcat application server start up with a remote debugger port open. To do so, probably the easiest way is to modify the JAVA_OPTS variable in `/etc/default/tomcat6`. The string we want to add to JAVA_OPTS is:

    -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=9090

Two notes: Replace 9090 with any other port number if you prefer. Also note the "suspend" parameter; if you set this to "y", the VM will block at startup until a debugger attaches. This is extremely useful for debugging configuration-loading or other startup tasks...

Alternatively, you can comment out the JAVA_OPTS line from this file and pass it in via an exported environment variable.

Either way, restart tomcat:

    sudo service tomcat6 restart

...and now we should be able to connect from your dev machine. See the "Using IDEA..." section below for attaching your debugger to a waiting JVM.


Using IDEA as your debugger
--

The IDEA debugger is pretty great. To create a remote-debugging configuration in IDEA:

- Click Run

- Click Edit Configurations

- Click the "+" on the top left

- choose "Remote" from the drop-down list

- change the name to indicate the purpose ("Local-SP debug", "Debug_GUI", etc.)

- change the Host: setting as appropriate. localhost, admin.syncfs.com, etc.

- change the Port: setting as appropriate. 5005 for GUI, 5010 for daemon, 9090 for tomcat, etc.

- click Ok.

![screenshot of remote-debug config](http://i.imgur.com/42PmozE.png)

To use it, choose 'Local SP debug' in the configuration chooser and hit the little bug icon. Set some breakpoints and go to town!

Troubleshooting
--

1. To test the GUI you should specify the RTROOT:

        ./rundebug -G -r ~/rtroot/

2. If the debugger doesn't run and

        $ cat ~/rtroot/{USER}/gui.out
        Error occurred during initialization of VM
        Could not find agent library jdwp on the library path, with error: dlopen(libjdwp.dylib, 1): image not found

You should probably do this:

    cp /Library/Java/JavaVirtualMachines/jdk1.8.0_45.jdk/Contents/Home/jre/lib/libjdwp.dylib ~/repos/aerofs/approot/

[ note to self; the GUI_debug and daemon_debug configurations should be checked in; must be in an .iml doc, right? ]