# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: cmd.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf.internal import enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
from google.protobuf import descriptor_pb2
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor.FileDescriptor(
  name='cmd.proto',
  package='',
  serialized_pb=_b('\n\tcmd.proto\"_\n\x07\x43ommand\x12\r\n\x05\x65poch\x18\x01 \x02(\x04\x12\x1a\n\x04type\x18\x02 \x02(\x0e\x32\x0c.CommandType\x12)\n\x10upload_logs_args\x18\x03 \x01(\x0b\x32\x0f.UploadLogsArgs\"e\n\x0eUploadLogsArgs\x12\x11\n\tdefect_id\x18\x01 \x02(\t\x12\x13\n\x0b\x65xpiry_time\x18\x02 \x02(\x04\x12+\n\x0b\x64\x65stination\x18\x03 \x01(\x0b\x32\x16.UploadLogsDestination\"E\n\x15UploadLogsDestination\x12\x10\n\x08hostname\x18\x01 \x02(\t\x12\x0c\n\x04port\x18\x02 \x02(\x05\x12\x0c\n\x04\x63\x65rt\x18\x03 \x02(\t*\x8a\x02\n\x0b\x43ommandType\x12 \n\x1cINVALIDATE_DEVICE_NAME_CACHE\x10\x00\x12\x1e\n\x1aINVALIDATE_USER_NAME_CACHE\x10\x01\x12\x0f\n\x0bUNLINK_SELF\x10\x02\x12\x18\n\x14UNLINK_AND_WIPE_SELF\x10\x03\x12\x0f\n\x0bREFRESH_CRL\x10\x04\x12#\n\x1fOBSOLETE_WAS_CLEAN_SSS_DATABASE\x10\x05\x12\x13\n\x0fUPLOAD_DATABASE\x10\x06\x12\x10\n\x0c\x43HECK_UPDATE\x10\x07\x12\x0f\n\x0bSEND_DEFECT\x10\x08\x12\x0f\n\x0bLOG_THREADS\x10\t\x12\x0f\n\x0bUPLOAD_LOGS\x10\nB\x14\n\x10\x63om.aerofs.protoH\x03')
)
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

_COMMANDTYPE = _descriptor.EnumDescriptor(
  name='CommandType',
  full_name='CommandType',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='INVALIDATE_DEVICE_NAME_CACHE', index=0, number=0,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='INVALIDATE_USER_NAME_CACHE', index=1, number=1,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UNLINK_SELF', index=2, number=2,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UNLINK_AND_WIPE_SELF', index=3, number=3,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='REFRESH_CRL', index=4, number=4,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='OBSOLETE_WAS_CLEAN_SSS_DATABASE', index=5, number=5,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UPLOAD_DATABASE', index=6, number=6,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='CHECK_UPDATE', index=7, number=7,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='SEND_DEFECT', index=8, number=8,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='LOG_THREADS', index=9, number=9,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UPLOAD_LOGS', index=10, number=10,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=285,
  serialized_end=551,
)
_sym_db.RegisterEnumDescriptor(_COMMANDTYPE)

CommandType = enum_type_wrapper.EnumTypeWrapper(_COMMANDTYPE)
INVALIDATE_DEVICE_NAME_CACHE = 0
INVALIDATE_USER_NAME_CACHE = 1
UNLINK_SELF = 2
UNLINK_AND_WIPE_SELF = 3
REFRESH_CRL = 4
OBSOLETE_WAS_CLEAN_SSS_DATABASE = 5
UPLOAD_DATABASE = 6
CHECK_UPDATE = 7
SEND_DEFECT = 8
LOG_THREADS = 9
UPLOAD_LOGS = 10



_COMMAND = _descriptor.Descriptor(
  name='Command',
  full_name='Command',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='epoch', full_name='Command.epoch', index=0,
      number=1, type=4, cpp_type=4, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='type', full_name='Command.type', index=1,
      number=2, type=14, cpp_type=8, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='upload_logs_args', full_name='Command.upload_logs_args', index=2,
      number=3, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=13,
  serialized_end=108,
)


_UPLOADLOGSARGS = _descriptor.Descriptor(
  name='UploadLogsArgs',
  full_name='UploadLogsArgs',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='defect_id', full_name='UploadLogsArgs.defect_id', index=0,
      number=1, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='expiry_time', full_name='UploadLogsArgs.expiry_time', index=1,
      number=2, type=4, cpp_type=4, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='destination', full_name='UploadLogsArgs.destination', index=2,
      number=3, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=110,
  serialized_end=211,
)


_UPLOADLOGSDESTINATION = _descriptor.Descriptor(
  name='UploadLogsDestination',
  full_name='UploadLogsDestination',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='hostname', full_name='UploadLogsDestination.hostname', index=0,
      number=1, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='port', full_name='UploadLogsDestination.port', index=1,
      number=2, type=5, cpp_type=1, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='cert', full_name='UploadLogsDestination.cert', index=2,
      number=3, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=213,
  serialized_end=282,
)

_COMMAND.fields_by_name['type'].enum_type = _COMMANDTYPE
_COMMAND.fields_by_name['upload_logs_args'].message_type = _UPLOADLOGSARGS
_UPLOADLOGSARGS.fields_by_name['destination'].message_type = _UPLOADLOGSDESTINATION
DESCRIPTOR.message_types_by_name['Command'] = _COMMAND
DESCRIPTOR.message_types_by_name['UploadLogsArgs'] = _UPLOADLOGSARGS
DESCRIPTOR.message_types_by_name['UploadLogsDestination'] = _UPLOADLOGSDESTINATION
DESCRIPTOR.enum_types_by_name['CommandType'] = _COMMANDTYPE

Command = _reflection.GeneratedProtocolMessageType('Command', (_message.Message,), dict(
  DESCRIPTOR = _COMMAND,
  __module__ = 'cmd_pb2'
  # @@protoc_insertion_point(class_scope:Command)
  ))
_sym_db.RegisterMessage(Command)

UploadLogsArgs = _reflection.GeneratedProtocolMessageType('UploadLogsArgs', (_message.Message,), dict(
  DESCRIPTOR = _UPLOADLOGSARGS,
  __module__ = 'cmd_pb2'
  # @@protoc_insertion_point(class_scope:UploadLogsArgs)
  ))
_sym_db.RegisterMessage(UploadLogsArgs)

UploadLogsDestination = _reflection.GeneratedProtocolMessageType('UploadLogsDestination', (_message.Message,), dict(
  DESCRIPTOR = _UPLOADLOGSDESTINATION,
  __module__ = 'cmd_pb2'
  # @@protoc_insertion_point(class_scope:UploadLogsDestination)
  ))
_sym_db.RegisterMessage(UploadLogsDestination)


DESCRIPTOR.has_options = True
DESCRIPTOR._options = _descriptor._ParseOptions(descriptor_pb2.FileOptions(), _b('\n\020com.aerofs.protoH\003'))
# @@protoc_insertion_point(module_scope)
