######################################################################
#
# This server is to be run on a SafetyNet machine running
# Windows Vista or higher. Windows services, such as sshd on cygwin,
# are not allowed to spawn applications that have a GUI. That means
# AeroFS can not be launched from SyncDET, at least not directly.
# This server should be run on the physical machine from the cygwin
# terminal (not through ssh). By deploying SyncDET for the first time,
# this server is copied to the Windows machine.
#
# Once the server is running, SyncDET will make an HTTP request from
# within the Windows machine to this server and give it the path to
# the AeroFS executable. This server will launch it, and since it's
# already running in GUI land, AeroFS will start properly.
#
######################################################################
import os
import BaseHTTPServer
import subprocess
import urllib2

class MyHTTPServer(BaseHTTPServer.HTTPServer):
    # Allows the server to be quickly restarted (won't wait for TCP port timeout)
    allow_reuse_address = True

class WindowsAeroFSGUILauncher(BaseHTTPServer.BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            path = urllib2.unquote(self.path)

            # Since we are trying to launch AeroFS on Windows, do a sanity check.
            if path.endswith("aerofs.exe"):
                # Since SyncDET runs under cygwin on Windows, we will assume the path
                # is a cygwin path. We need to convert it.

                # Strip the leading // that will exist due to there being a separator between
                # the host and the path in the URL, as well as the path being absolute
                path = path.strip().replace("//", "/")

                # Convert the path from a cygwin path to a Windows path, if needed
                aerofs_approot = os.path.dirname(path).replace("/cygdrive/c", "C:").replace("/", "\\")
                program_name = os.path.basename(path)

                # Start the Windows Command Prompt and use 'start' to asynchronously launch
                # the aerofs GUI. Hide the stdout logging.
                # NOTE: Be careful here, as the start command will take anything in quotes to be
                # the title of the new command prompt to open. If the path contains spaces, then quoting
                # it will not launch the correct program. Instead, we use the /D switch to set the
                # working directory for the program. This way 'start' won't get confused about titles and
                # we can successfully use paths with spaces!!
                with open(os.devnull, 'w') as dev_null:
                    cmd = ["cmd.exe", "/c", "start", "/D", aerofs_approot, program_name]
                    print "Executing command: {0}".format(cmd)
                    subprocess.check_call(cmd, stderr=subprocess.STDOUT, stdout=dev_null)

                # Respond
                self.send_response(200)
                self.end_headers()
                self.wfile.write("launched daemon\n")
                self.wfile.flush()
            else:
                self.send_error(404)

        except Exception as e:
            self.send_error(500, str(e))

if __name__ == '__main__':
    server_address = ('localhost', 8000)
    httpd = MyHTTPServer(server_address, WindowsAeroFSGUILauncher)
    print "AeroFS launch server running on {0}...".format(server_address)
    httpd.serve_forever()
