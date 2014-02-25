import socket

class RetraceClient:
    def __init__(self, port, version):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(3) # 3 seconds timeout
        s.connect(("127.0.0.1", port))
        self.server = s.makefile()
        self.version = version

    def retrace(self, classname, methodname=None, linenumber=-1):
        """
        Un-obfuscates a classname and an optional methodname
        Returns a dict mapping the keys 'classname' and 'methodname' with the un-obfuscated values
        """

        # Send the request
        request = self.version + ' ' + classname
        if methodname:
            request += ' ' + methodname + ' ' + str(linenumber)
        request += '\n'
        self.server.write(request)
        self.server.flush()

        # Read and parse the response
        try:
            response = self.server.readline().strip()
        except:
            response = "timed out"

        if response.startswith('OK: '):
            response = response[4:].split(' ', 1)
            return dict(classname = response[0],
                        methodname = len(response) > 1 and response[1] or None)
        else:
            # Return the obfuscated classname and methodname
            return dict(classname = classname, methodname = methodname)

    def close(self):
        self.server.close()
