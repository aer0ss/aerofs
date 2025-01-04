# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: common.proto

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
  name='common.proto',
  package='',
  serialized_pb=_b('\n\x0c\x63ommon.proto\"#\n\x06PBPath\x12\x0b\n\x03sid\x18\x01 \x02(\x0c\x12\x0c\n\x04\x65lem\x18\x02 \x03(\t\"\x06\n\x04Void\"\x9a\t\n\x0bPBException\x12\x1f\n\x04type\x18\x01 \x02(\x0e\x32\x11.PBException.Type\x12\x1a\n\x12message_deprecated\x18\x04 \x01(\t\x12%\n\x1dplain_text_message_deprecated\x18\x02 \x01(\t\x12\x13\n\x0bstack_trace\x18\x03 \x01(\t\x12\x0c\n\x04\x64\x61ta\x18\x05 \x01(\x0c\"\x83\x08\n\x04Type\x12\x12\n\x0eINTERNAL_ERROR\x10\x00\x12\x17\n\x13\x45MPTY_EMAIL_ADDRESS\x10\x01\x12\x11\n\rALREADY_EXIST\x10\x03\x12\x0c\n\x08\x42\x41\x44_ARGS\x10\x04\x12\x0b\n\x07NO_PERM\x10\x06\x12\x0f\n\x0bNO_RESOURCE\x10\x07\x12\r\n\tNOT_FOUND\x10\n\x12\x12\n\x0ePROTOCOL_ERROR\x10\x0c\x12\x0b\n\x07TIMEOUT\x10\r\x12\r\n\tNO_INVITE\x10\x14\x12\x13\n\x0e\x42\x41\x44_CREDENTIAL\x10\x90\x03\x12\x0b\n\x07\x41\x42ORTED\x10\x02\x12\r\n\x08\x45XPELLED\x10\xc9\x01\x12\x13\n\x0fNO_AVAIL_DEVICE\x10\x0f\x12\x0f\n\nNOT_SHARED\x10\xc8\x01\x12\x10\n\x0cOUT_OF_SPACE\x10\x0b\x12\x16\n\x12UPDATE_IN_PROGRESS\x10\x64\x12\'\n#NO_COMPONENT_WITH_SPECIFIED_VERSION\x10\x65\x12\x16\n\x12SENDER_HAS_NO_PERM\x10\x66\x12\x0b\n\x07NOT_DIR\x10\x08\x12\x0c\n\x08NOT_FILE\x10\t\x12\x12\n\x0e\x44\x45VICE_OFFLINE\x10\x0e\x12\x18\n\x14\x43HILD_ALREADY_SHARED\x10\x10\x12\x19\n\x15PARENT_ALREADY_SHARED\x10\x11\x12\r\n\x08INDEXING\x10\xca\x01\x12\r\n\x08UPDATING\x10\xcb\x01\x12\x13\n\x0eLAUNCH_ABORTED\x10\xac\x02\x12\x0e\n\nUI_MESSAGE\x10\x12\x12\x1d\n\x18\x44\x45VICE_ID_ALREADY_EXISTS\x10\x91\x03\x12\x13\n\x0f\x41LREADY_INVITED\x10\x13\x12\x16\n\x11NO_ADMIN_OR_OWNER\x10\x92\x03\x12\x16\n\x11NOT_AUTHENTICATED\x10\x94\x03\x12\x1a\n\x15INVALID_EMAIL_ADDRESS\x10\x95\x03\x12!\n\x1c\x45XTERNAL_SERVICE_UNAVAILABLE\x10\x97\x03\x12\x1b\n\x16SHARING_RULES_WARNINGS\x10\x99\x03\x12\x1a\n\x15\x43\x41NNOT_RESET_PASSWORD\x10\x9a\x03\x12\x1a\n\x15\x45XTERNAL_AUTH_FAILURE\x10\x9b\x03\x12\x12\n\rLICENSE_LIMIT\x10\x9c\x03\x12\x18\n\x13RATE_LIMIT_EXCEEDED\x10\x9d\x03\x12\x1b\n\x16SECOND_FACTOR_REQUIRED\x10\x9e\x03\x12\x17\n\x12WRONG_ORGANIZATION\x10\x9f\x03\x12\x18\n\x13NOT_LOCALLY_MANAGED\x10\xa0\x03\x12!\n\x1cSECOND_FACTOR_SETUP_REQUIRED\x10\xa1\x03\x12\x1a\n\x15MEMBER_LIMIT_EXCEEDED\x10\xa2\x03\x12\x15\n\x10PASSWORD_EXPIRED\x10\xa3\x03\x12\x1b\n\x16PASSWORD_ALREADY_EXIST\x10\xa4\x03\"2\n\rPBPermissions\x12!\n\npermission\x18\x01 \x03(\x0e\x32\r.PBPermission\"^\n\x14PBSubjectPermissions\x12\x0f\n\x07subject\x18\x01 \x02(\t\x12#\n\x0bpermissions\x18\x02 \x02(\x0b\x32\x0e.PBPermissions\x12\x10\n\x08\x65xternal\x18\x03 \x01(\x08\"K\n\x12PBFolderInvitation\x12\x10\n\x08share_id\x18\x01 \x02(\x0c\x12\x13\n\x0b\x66older_name\x18\x02 \x02(\t\x12\x0e\n\x06sharer\x18\x03 \x02(\t\"2\n\x15\x43NameVerificationInfo\x12\x0c\n\x04user\x18\x01 \x02(\t\x12\x0b\n\x03\x64id\x18\x02 \x02(\x0c*%\n\x0cPBPermission\x12\t\n\x05WRITE\x10\x00\x12\n\n\x06MANAGE\x10\x01\x42\x14\n\x10\x63om.aerofs.protoH\x03')
)
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

_PBPERMISSION = _descriptor.EnumDescriptor(
  name='PBPermission',
  full_name='PBPermission',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='WRITE', index=0, number=0,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='MANAGE', index=1, number=1,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=1519,
  serialized_end=1556,
)
_sym_db.RegisterEnumDescriptor(_PBPERMISSION)

PBPermission = enum_type_wrapper.EnumTypeWrapper(_PBPERMISSION)
WRITE = 0
MANAGE = 1


_PBEXCEPTION_TYPE = _descriptor.EnumDescriptor(
  name='Type',
  full_name='PBException.Type',
  filename=None,
  file=DESCRIPTOR,
  values=[
    _descriptor.EnumValueDescriptor(
      name='INTERNAL_ERROR', index=0, number=0,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='EMPTY_EMAIL_ADDRESS', index=1, number=1,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='ALREADY_EXIST', index=2, number=3,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='BAD_ARGS', index=3, number=4,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NO_PERM', index=4, number=6,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NO_RESOURCE', index=5, number=7,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NOT_FOUND', index=6, number=10,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='PROTOCOL_ERROR', index=7, number=12,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='TIMEOUT', index=8, number=13,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NO_INVITE', index=9, number=20,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='BAD_CREDENTIAL', index=10, number=400,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='ABORTED', index=11, number=2,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='EXPELLED', index=12, number=201,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NO_AVAIL_DEVICE', index=13, number=15,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NOT_SHARED', index=14, number=200,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='OUT_OF_SPACE', index=15, number=11,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UPDATE_IN_PROGRESS', index=16, number=100,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NO_COMPONENT_WITH_SPECIFIED_VERSION', index=17, number=101,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='SENDER_HAS_NO_PERM', index=18, number=102,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NOT_DIR', index=19, number=8,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NOT_FILE', index=20, number=9,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='DEVICE_OFFLINE', index=21, number=14,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='CHILD_ALREADY_SHARED', index=22, number=16,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='PARENT_ALREADY_SHARED', index=23, number=17,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='INDEXING', index=24, number=202,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UPDATING', index=25, number=203,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='LAUNCH_ABORTED', index=26, number=300,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='UI_MESSAGE', index=27, number=18,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='DEVICE_ID_ALREADY_EXISTS', index=28, number=401,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='ALREADY_INVITED', index=29, number=19,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NO_ADMIN_OR_OWNER', index=30, number=402,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NOT_AUTHENTICATED', index=31, number=404,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='INVALID_EMAIL_ADDRESS', index=32, number=405,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='EXTERNAL_SERVICE_UNAVAILABLE', index=33, number=407,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='SHARING_RULES_WARNINGS', index=34, number=409,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='CANNOT_RESET_PASSWORD', index=35, number=410,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='EXTERNAL_AUTH_FAILURE', index=36, number=411,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='LICENSE_LIMIT', index=37, number=412,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='RATE_LIMIT_EXCEEDED', index=38, number=413,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='SECOND_FACTOR_REQUIRED', index=39, number=414,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='WRONG_ORGANIZATION', index=40, number=415,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='NOT_LOCALLY_MANAGED', index=41, number=416,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='SECOND_FACTOR_SETUP_REQUIRED', index=42, number=417,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='MEMBER_LIMIT_EXCEEDED', index=43, number=418,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='PASSWORD_EXPIRED', index=44, number=419,
      options=None,
      type=None),
    _descriptor.EnumValueDescriptor(
      name='PASSWORD_ALREADY_EXIST', index=45, number=420,
      options=None,
      type=None),
  ],
  containing_type=None,
  options=None,
  serialized_start=213,
  serialized_end=1240,
)
_sym_db.RegisterEnumDescriptor(_PBEXCEPTION_TYPE)


_PBPATH = _descriptor.Descriptor(
  name='PBPath',
  full_name='PBPath',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='sid', full_name='PBPath.sid', index=0,
      number=1, type=12, cpp_type=9, label=2,
      has_default_value=False, default_value=_b(""),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='elem', full_name='PBPath.elem', index=1,
      number=2, type=9, cpp_type=9, label=3,
      has_default_value=False, default_value=[],
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
  serialized_start=16,
  serialized_end=51,
)


_VOID = _descriptor.Descriptor(
  name='Void',
  full_name='Void',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
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
  serialized_start=53,
  serialized_end=59,
)


_PBEXCEPTION = _descriptor.Descriptor(
  name='PBException',
  full_name='PBException',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='type', full_name='PBException.type', index=0,
      number=1, type=14, cpp_type=8, label=2,
      has_default_value=False, default_value=0,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='message_deprecated', full_name='PBException.message_deprecated', index=1,
      number=4, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='plain_text_message_deprecated', full_name='PBException.plain_text_message_deprecated', index=2,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='stack_trace', full_name='PBException.stack_trace', index=3,
      number=3, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='data', full_name='PBException.data', index=4,
      number=5, type=12, cpp_type=9, label=1,
      has_default_value=False, default_value=_b(""),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
    _PBEXCEPTION_TYPE,
  ],
  options=None,
  is_extendable=False,
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=62,
  serialized_end=1240,
)


_PBPERMISSIONS = _descriptor.Descriptor(
  name='PBPermissions',
  full_name='PBPermissions',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='permission', full_name='PBPermissions.permission', index=0,
      number=1, type=14, cpp_type=8, label=3,
      has_default_value=False, default_value=[],
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
  serialized_start=1242,
  serialized_end=1292,
)


_PBSUBJECTPERMISSIONS = _descriptor.Descriptor(
  name='PBSubjectPermissions',
  full_name='PBSubjectPermissions',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='subject', full_name='PBSubjectPermissions.subject', index=0,
      number=1, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='permissions', full_name='PBSubjectPermissions.permissions', index=1,
      number=2, type=11, cpp_type=10, label=2,
      has_default_value=False, default_value=None,
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='external', full_name='PBSubjectPermissions.external', index=2,
      number=3, type=8, cpp_type=7, label=1,
      has_default_value=False, default_value=False,
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
  serialized_start=1294,
  serialized_end=1388,
)


_PBFOLDERINVITATION = _descriptor.Descriptor(
  name='PBFolderInvitation',
  full_name='PBFolderInvitation',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='share_id', full_name='PBFolderInvitation.share_id', index=0,
      number=1, type=12, cpp_type=9, label=2,
      has_default_value=False, default_value=_b(""),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='folder_name', full_name='PBFolderInvitation.folder_name', index=1,
      number=2, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='sharer', full_name='PBFolderInvitation.sharer', index=2,
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
  serialized_start=1390,
  serialized_end=1465,
)


_CNAMEVERIFICATIONINFO = _descriptor.Descriptor(
  name='CNameVerificationInfo',
  full_name='CNameVerificationInfo',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='user', full_name='CNameVerificationInfo.user', index=0,
      number=1, type=9, cpp_type=9, label=2,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      options=None),
    _descriptor.FieldDescriptor(
      name='did', full_name='CNameVerificationInfo.did', index=1,
      number=2, type=12, cpp_type=9, label=2,
      has_default_value=False, default_value=_b(""),
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
  serialized_start=1467,
  serialized_end=1517,
)

_PBEXCEPTION.fields_by_name['type'].enum_type = _PBEXCEPTION_TYPE
_PBEXCEPTION_TYPE.containing_type = _PBEXCEPTION
_PBPERMISSIONS.fields_by_name['permission'].enum_type = _PBPERMISSION
_PBSUBJECTPERMISSIONS.fields_by_name['permissions'].message_type = _PBPERMISSIONS
DESCRIPTOR.message_types_by_name['PBPath'] = _PBPATH
DESCRIPTOR.message_types_by_name['Void'] = _VOID
DESCRIPTOR.message_types_by_name['PBException'] = _PBEXCEPTION
DESCRIPTOR.message_types_by_name['PBPermissions'] = _PBPERMISSIONS
DESCRIPTOR.message_types_by_name['PBSubjectPermissions'] = _PBSUBJECTPERMISSIONS
DESCRIPTOR.message_types_by_name['PBFolderInvitation'] = _PBFOLDERINVITATION
DESCRIPTOR.message_types_by_name['CNameVerificationInfo'] = _CNAMEVERIFICATIONINFO
DESCRIPTOR.enum_types_by_name['PBPermission'] = _PBPERMISSION

PBPath = _reflection.GeneratedProtocolMessageType('PBPath', (_message.Message,), dict(
  DESCRIPTOR = _PBPATH,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:PBPath)
  ))
_sym_db.RegisterMessage(PBPath)

Void = _reflection.GeneratedProtocolMessageType('Void', (_message.Message,), dict(
  DESCRIPTOR = _VOID,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:Void)
  ))
_sym_db.RegisterMessage(Void)

PBException = _reflection.GeneratedProtocolMessageType('PBException', (_message.Message,), dict(
  DESCRIPTOR = _PBEXCEPTION,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:PBException)
  ))
_sym_db.RegisterMessage(PBException)

PBPermissions = _reflection.GeneratedProtocolMessageType('PBPermissions', (_message.Message,), dict(
  DESCRIPTOR = _PBPERMISSIONS,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:PBPermissions)
  ))
_sym_db.RegisterMessage(PBPermissions)

PBSubjectPermissions = _reflection.GeneratedProtocolMessageType('PBSubjectPermissions', (_message.Message,), dict(
  DESCRIPTOR = _PBSUBJECTPERMISSIONS,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:PBSubjectPermissions)
  ))
_sym_db.RegisterMessage(PBSubjectPermissions)

PBFolderInvitation = _reflection.GeneratedProtocolMessageType('PBFolderInvitation', (_message.Message,), dict(
  DESCRIPTOR = _PBFOLDERINVITATION,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:PBFolderInvitation)
  ))
_sym_db.RegisterMessage(PBFolderInvitation)

CNameVerificationInfo = _reflection.GeneratedProtocolMessageType('CNameVerificationInfo', (_message.Message,), dict(
  DESCRIPTOR = _CNAMEVERIFICATIONINFO,
  __module__ = 'common_pb2'
  # @@protoc_insertion_point(class_scope:CNameVerificationInfo)
  ))
_sym_db.RegisterMessage(CNameVerificationInfo)


DESCRIPTOR.has_options = True
DESCRIPTOR._options = _descriptor._ParseOptions(descriptor_pb2.FileOptions(), _b('\n\020com.aerofs.protoH\003'))
# @@protoc_insertion_point(module_scope)