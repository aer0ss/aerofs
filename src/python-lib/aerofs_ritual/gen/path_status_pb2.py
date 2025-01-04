# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: path_status.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
from google.protobuf import descriptor_pb2
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor.FileDescriptor(
  name='path_status.proto',
  package='',
  serialized_pb=_b('\n\x11path_status.proto\"\xb7\x01\n\x0cPBPathStatus\x12 \n\x04sync\x18\x01 \x02(\x0e\x32\x12.PBPathStatus.Sync\x12\r\n\x05\x66lags\x18\x02 \x02(\x05\"@\n\x04Sync\x12\x0b\n\x07UNKNOWN\x10\x00\x12\x0c\n\x08OUT_SYNC\x10\x01\x12\x10\n\x0cPARTIAL_SYNC\x10\x02\x12\x0b\n\x07IN_SYNC\x10\x03\"4\n\x04\x46lag\x12\x0f\n\x0b\x44OWNLOADING\x10\x01\x12\r\n\tUPLOADING\x10\x02\x12\x0c\n\x08\x43ONFLICT\x10\x04\x42\x14\n\x10\x63om.aerofs.protoH\x03')
)
_sym_db.RegisterFileDescriptor(DESCRIPTOR)



_PBPATHSTATUS_SYNC = _descriptor.EnumDescriptor(
  name='Sync',
  full_name='PBPathStatus.Sync',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='UNKNOWN', index=0, number=0,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='OUT_SYNC', index=1, number=1,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='PARTIAL_SYNC', index=2, number=2,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='IN_SYNC', index=3, number=3,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=87,
  serialized_end=151,
)
_sym_db.RegisterEnumDescriptor(_PBPATHSTATUS_SYNC)

_PBPATHSTATUS_FLAG = _descriptor.EnumDescriptor(
  name='Flag',
  full_name='PBPathStatus.Flag',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='DOWNLOADING', index=0, number=1,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UPLOADING', index=1, number=2,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='CONFLICT', index=2, number=4,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=153,
  serialized_end=205,
)
_sym_db.RegisterEnumDescriptor(_PBPATHSTATUS_FLAG)


_PBPATHSTATUS = _descriptor.Descriptor(
  name='PBPathStatus',
  full_name='PBPathStatus',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='sync', full_name='PBPathStatus.sync', index=0,
      number=1, type=14, cpp_type=8, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='flags', full_name='PBPathStatus.flags', index=1,
      number=2, type=5, cpp_type=1, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
    _PBPATHSTATUS_SYNC,
    _PBPATHSTATUS_FLAG,
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=22,
  serialized_end=205,
)

_PBPATHSTATUS.fields_by_name['sync'].enum_type = _PBPATHSTATUS_SYNC
_PBPATHSTATUS_SYNC.containing_type = _PBPATHSTATUS
_PBPATHSTATUS_FLAG.containing_type = _PBPATHSTATUS
DESCRIPTOR.message_types_by_name['PBPathStatus'] = _PBPATHSTATUS

PBPathStatus = _reflection.GeneratedProtocolMessageType('PBPathStatus', (_message.Message,), dict(
  DESCRIPTOR = _PBPATHSTATUS,
  __module__ = 'path_status_pb2'
  # @@protoc_insertion_point(class_scope:PBPathStatus)
  ))
_sym_db.RegisterMessage(PBPathStatus)


DESCRIPTOR.has_options = True
DESCRIPTOR._options = _descriptor._ParseOptions(descriptor_pb2.FileOptions(), _b('\n\020com.aerofs.protoH\003'))
# @@protoc_insertion_point(module_scope)