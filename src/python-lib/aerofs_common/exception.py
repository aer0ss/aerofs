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

    def __str__(self):
        description = self.get_type_name()
        if self.reply.HasField('plain_text_message'):
            description += u": {0}".format(self.reply.plain_text_message)
        elif self.reply.HasField('message'):
            description += u": {0}".format(self.reply.message)
        return description
