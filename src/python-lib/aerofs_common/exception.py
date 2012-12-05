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
        return self.reply.type

    def __str__(self):
        description = "{0}".format(_PBEXCEPTION_TYPE.values_by_number[self.reply.type].name)
        if self.reply.HasField('plain_text_message'):
            description += u": {0}".format(self.reply.plain_text_message)
        elif self.reply.HasField('message'):
            description += u": {0}".format(self.reply.message)
        return description
