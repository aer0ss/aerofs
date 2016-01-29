from _gen.common_pb2 import _PBEXCEPTION_TYPE

"""
An Exception class that wraps a PBException.
"""
class ExceptionReply(Exception):

    def __init__(self, reply):
        """
        @param reply type PBException
        """
        super(ExceptionReply, self).__init__()
        self.reply = reply

    def get_type(self):
        """
        @return the numeric type value
        """
        return self.reply.type

    def get_type_name(self):
        """
        @return the type string, e.g. "NOT_FOUND"
        """
        return _PBEXCEPTION_TYPE.values_by_number[self.reply.type].name

    def get_data(self):
        """
        @return the data field of the exception
        """
        return self.reply.data

    def get_message(self):
        description = ""
        if self.reply.HasField('plain_text_message_deprecated'):
            description = u"{0}".format(self.reply.plain_text_message_deprecated)
        elif self.reply.HasField('message_deprecated'):
            description = u"{0}".format(self.reply.message_deprecated)
        return description

    def __str__(self):
        return self.get_type_name() + u": {0}".format(self.get_message())
