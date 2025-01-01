package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Common {
private Common() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public enum PBPermission
implements Internal.EnumLite {
WRITE(0, 0),
MANAGE(1, 1),
;
public static final int WRITE_VALUE = 0;
public static final int MANAGE_VALUE = 1;
public final int getNumber() { return value; }
public static PBPermission valueOf(int value) {
switch (value) {
case 0: return WRITE;
case 1: return MANAGE;
default: return null;
}
}
public static Internal.EnumLiteMap<PBPermission>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<PBPermission>
internalValueMap =
new Internal.EnumLiteMap<PBPermission>() {
public PBPermission findValueByNumber(int number) {
return PBPermission.valueOf(number);
}
};
private final int value;
private PBPermission(int index, int value) {
this.value = value;
}
}
public interface PBPathOrBuilder extends
MessageLiteOrBuilder {
boolean hasSid();
ByteString getSid();
ProtocolStringList
getElemList();
int getElemCount();
String getElem(int index);
ByteString
getElemBytes(int index);
}
public static final class PBPath extends
GeneratedMessageLite implements
PBPathOrBuilder {
private PBPath(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBPath(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBPath defaultInstance;
public static PBPath getDefaultInstance() {
return defaultInstance;
}
public PBPath getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBPath(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
case 10: {
b0_ |= 0x00000001;
sid_ = input.readBytes();
break;
}
case 18: {
ByteString bs = input.readBytes();
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
elem_ = new LazyStringArrayList();
mutable_b0_ |= 0x00000002;
}
elem_.add(bs);
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
if (((mutable_b0_ & 0x00000002) == 0x00000002)) {
elem_ = elem_.getUnmodifiableView();
}
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<PBPath> PARSER =
new AbstractParser<PBPath>() {
public PBPath parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBPath(input, er);
}
};
@Override
public Parser<PBPath> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SID_FIELD_NUMBER = 1;
private ByteString sid_;
public boolean hasSid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getSid() {
return sid_;
}
public static final int ELEM_FIELD_NUMBER = 2;
private LazyStringList elem_;
public ProtocolStringList
getElemList() {
return elem_;
}
public int getElemCount() {
return elem_.size();
}
public String getElem(int index) {
return elem_.get(index);
}
public ByteString
getElemBytes(int index) {
return elem_.getByteString(index);
}
private void initFields() {
sid_ = ByteString.EMPTY;
elem_ = LazyStringArrayList.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasSid()) {
mii = 0;
return false;
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, sid_);
}
for (int i = 0; i < elem_.size(); i++) {
output.writeBytes(2, elem_.getByteString(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeBytesSize(1, sid_);
}
{
int dataSize = 0;
for (int i = 0; i < elem_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(elem_.getByteString(i));
}
size += dataSize;
size += 1 * getElemList().size();
}
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.PBPath parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBPath parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBPath parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBPath parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBPath parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBPath parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.PBPath parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.PBPath parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.PBPath parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBPath parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.PBPath prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.PBPath, Builder>
implements
Common.PBPathOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
sid_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
elem_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.PBPath getDefaultInstanceForType() {
return Common.PBPath.getDefaultInstance();
}
public Common.PBPath build() {
Common.PBPath result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.PBPath buildPartial() {
Common.PBPath result = new Common.PBPath(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sid_ = sid_;
if (((b0_ & 0x00000002) == 0x00000002)) {
elem_ = elem_.getUnmodifiableView();
b0_ = (b0_ & ~0x00000002);
}
result.elem_ = elem_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Common.PBPath other) {
if (other == Common.PBPath.getDefaultInstance()) return this;
if (other.hasSid()) {
setSid(other.getSid());
}
if (!other.elem_.isEmpty()) {
if (elem_.isEmpty()) {
elem_ = other.elem_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureElemIsMutable();
elem_.addAll(other.elem_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSid()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.PBPath pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.PBPath) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString sid_ = ByteString.EMPTY;
public boolean hasSid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getSid() {
return sid_;
}
public Builder setSid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
sid_ = value;
return this;
}
public Builder clearSid() {
b0_ = (b0_ & ~0x00000001);
sid_ = getDefaultInstance().getSid();
return this;
}
private LazyStringList elem_ = LazyStringArrayList.EMPTY;
private void ensureElemIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
elem_ = new LazyStringArrayList(elem_);
b0_ |= 0x00000002;
}
}
public ProtocolStringList
getElemList() {
return elem_.getUnmodifiableView();
}
public int getElemCount() {
return elem_.size();
}
public String getElem(int index) {
return elem_.get(index);
}
public ByteString
getElemBytes(int index) {
return elem_.getByteString(index);
}
public Builder setElem(
int index, String value) {
if (value == null) {
throw new NullPointerException();
}
ensureElemIsMutable();
elem_.set(index, value);
return this;
}
public Builder addElem(
String value) {
if (value == null) {
throw new NullPointerException();
}
ensureElemIsMutable();
elem_.add(value);
return this;
}
public Builder addAllElem(
Iterable<String> values) {
ensureElemIsMutable();
AbstractMessageLite.Builder.addAll(
values, elem_);
return this;
}
public Builder clearElem() {
elem_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder addElemBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureElemIsMutable();
elem_.add(value);
return this;
}
}
static {
defaultInstance = new PBPath(true);
defaultInstance.initFields();
}
}
public interface VoidOrBuilder extends
MessageLiteOrBuilder {
}
public static final class Void extends
GeneratedMessageLite implements
VoidOrBuilder {
private Void(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Void(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final Void defaultInstance;
public static Void getDefaultInstance() {
return defaultInstance;
}
public Void getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private Void(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<Void> PARSER =
new AbstractParser<Void>() {
public Void parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Void(input, er);
}
};
@Override
public Parser<Void> getParserForType() {
return PARSER;
}
private void initFields() {
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.Void parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.Void parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.Void parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.Void parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.Void parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.Void parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.Void parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.Void parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.Void parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.Void parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.Void prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.Void, Builder>
implements
Common.VoidOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.Void getDefaultInstanceForType() {
return Common.Void.getDefaultInstance();
}
public Common.Void build() {
Common.Void result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.Void buildPartial() {
Common.Void result = new Common.Void(this);
return result;
}
public Builder mergeFrom(Common.Void other) {
if (other == Common.Void.getDefaultInstance()) return this;
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.Void pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.Void) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
}
static {
defaultInstance = new Void(true);
defaultInstance.initFields();
}
}
public interface PBExceptionOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Common.PBException.Type getType();
boolean hasMessageDeprecated();
String getMessageDeprecated();
ByteString
getMessageDeprecatedBytes();
boolean hasPlainTextMessageDeprecated();
String getPlainTextMessageDeprecated();
ByteString
getPlainTextMessageDeprecatedBytes();
boolean hasStackTrace();
String getStackTrace();
ByteString
getStackTraceBytes();
boolean hasData();
ByteString getData();
}
public static final class PBException extends
GeneratedMessageLite implements
PBExceptionOrBuilder {
private PBException(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBException(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBException defaultInstance;
public static PBException getDefaultInstance() {
return defaultInstance;
}
public PBException getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBException(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
case 8: {
int rawValue = input.readEnum();
Common.PBException.Type value = Common.PBException.Type.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
b0_ |= 0x00000001;
type_ = value;
}
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000004;
plainTextMessageDeprecated_ = bs;
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000008;
stackTrace_ = bs;
break;
}
case 34: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
messageDeprecated_ = bs;
break;
}
case 42: {
b0_ |= 0x00000010;
data_ = input.readBytes();
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<PBException> PARSER =
new AbstractParser<PBException>() {
public PBException parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBException(input, er);
}
};
@Override
public Parser<PBException> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
INTERNAL_ERROR(0, 0),
EMPTY_EMAIL_ADDRESS(1, 1),
ALREADY_EXIST(2, 3),
BAD_ARGS(3, 4),
NO_PERM(4, 6),
NO_RESOURCE(5, 7),
NOT_FOUND(6, 10),
PROTOCOL_ERROR(7, 12),
TIMEOUT(8, 13),
NO_INVITE(9, 20),
BAD_CREDENTIAL(10, 400),
ABORTED(11, 2),
EXPELLED(12, 201),
NO_AVAIL_DEVICE(13, 15),
NOT_SHARED(14, 200),
OUT_OF_SPACE(15, 11),
UPDATE_IN_PROGRESS(16, 100),
NO_COMPONENT_WITH_SPECIFIED_VERSION(17, 101),
SENDER_HAS_NO_PERM(18, 102),
NOT_DIR(19, 8),
NOT_FILE(20, 9),
DEVICE_OFFLINE(21, 14),
CHILD_ALREADY_SHARED(22, 16),
PARENT_ALREADY_SHARED(23, 17),
INDEXING(24, 202),
UPDATING(25, 203),
LAUNCH_ABORTED(26, 300),
UI_MESSAGE(27, 18),
DEVICE_ID_ALREADY_EXISTS(28, 401),
ALREADY_INVITED(29, 19),
NO_ADMIN_OR_OWNER(30, 402),
NOT_AUTHENTICATED(31, 404),
INVALID_EMAIL_ADDRESS(32, 405),
EXTERNAL_SERVICE_UNAVAILABLE(33, 407),
SHARING_RULES_WARNINGS(34, 409),
CANNOT_RESET_PASSWORD(35, 410),
EXTERNAL_AUTH_FAILURE(36, 411),
LICENSE_LIMIT(37, 412),
RATE_LIMIT_EXCEEDED(38, 413),
SECOND_FACTOR_REQUIRED(39, 414),
WRONG_ORGANIZATION(40, 415),
NOT_LOCALLY_MANAGED(41, 416),
SECOND_FACTOR_SETUP_REQUIRED(42, 417),
MEMBER_LIMIT_EXCEEDED(43, 418),
PASSWORD_EXPIRED(44, 419),
PASSWORD_ALREADY_EXIST(45, 420),
;
public static final int INTERNAL_ERROR_VALUE = 0;
public static final int EMPTY_EMAIL_ADDRESS_VALUE = 1;
public static final int ALREADY_EXIST_VALUE = 3;
public static final int BAD_ARGS_VALUE = 4;
public static final int NO_PERM_VALUE = 6;
public static final int NO_RESOURCE_VALUE = 7;
public static final int NOT_FOUND_VALUE = 10;
public static final int PROTOCOL_ERROR_VALUE = 12;
public static final int TIMEOUT_VALUE = 13;
public static final int NO_INVITE_VALUE = 20;
public static final int BAD_CREDENTIAL_VALUE = 400;
public static final int ABORTED_VALUE = 2;
public static final int EXPELLED_VALUE = 201;
public static final int NO_AVAIL_DEVICE_VALUE = 15;
public static final int NOT_SHARED_VALUE = 200;
public static final int OUT_OF_SPACE_VALUE = 11;
public static final int UPDATE_IN_PROGRESS_VALUE = 100;
public static final int NO_COMPONENT_WITH_SPECIFIED_VERSION_VALUE = 101;
public static final int SENDER_HAS_NO_PERM_VALUE = 102;
public static final int NOT_DIR_VALUE = 8;
public static final int NOT_FILE_VALUE = 9;
public static final int DEVICE_OFFLINE_VALUE = 14;
public static final int CHILD_ALREADY_SHARED_VALUE = 16;
public static final int PARENT_ALREADY_SHARED_VALUE = 17;
public static final int INDEXING_VALUE = 202;
public static final int UPDATING_VALUE = 203;
public static final int LAUNCH_ABORTED_VALUE = 300;
public static final int UI_MESSAGE_VALUE = 18;
public static final int DEVICE_ID_ALREADY_EXISTS_VALUE = 401;
public static final int ALREADY_INVITED_VALUE = 19;
public static final int NO_ADMIN_OR_OWNER_VALUE = 402;
public static final int NOT_AUTHENTICATED_VALUE = 404;
public static final int INVALID_EMAIL_ADDRESS_VALUE = 405;
public static final int EXTERNAL_SERVICE_UNAVAILABLE_VALUE = 407;
public static final int SHARING_RULES_WARNINGS_VALUE = 409;
public static final int CANNOT_RESET_PASSWORD_VALUE = 410;
public static final int EXTERNAL_AUTH_FAILURE_VALUE = 411;
public static final int LICENSE_LIMIT_VALUE = 412;
public static final int RATE_LIMIT_EXCEEDED_VALUE = 413;
public static final int SECOND_FACTOR_REQUIRED_VALUE = 414;
public static final int WRONG_ORGANIZATION_VALUE = 415;
public static final int NOT_LOCALLY_MANAGED_VALUE = 416;
public static final int SECOND_FACTOR_SETUP_REQUIRED_VALUE = 417;
public static final int MEMBER_LIMIT_EXCEEDED_VALUE = 418;
public static final int PASSWORD_EXPIRED_VALUE = 419;
public static final int PASSWORD_ALREADY_EXIST_VALUE = 420;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return INTERNAL_ERROR;
case 1: return EMPTY_EMAIL_ADDRESS;
case 3: return ALREADY_EXIST;
case 4: return BAD_ARGS;
case 6: return NO_PERM;
case 7: return NO_RESOURCE;
case 10: return NOT_FOUND;
case 12: return PROTOCOL_ERROR;
case 13: return TIMEOUT;
case 20: return NO_INVITE;
case 400: return BAD_CREDENTIAL;
case 2: return ABORTED;
case 201: return EXPELLED;
case 15: return NO_AVAIL_DEVICE;
case 200: return NOT_SHARED;
case 11: return OUT_OF_SPACE;
case 100: return UPDATE_IN_PROGRESS;
case 101: return NO_COMPONENT_WITH_SPECIFIED_VERSION;
case 102: return SENDER_HAS_NO_PERM;
case 8: return NOT_DIR;
case 9: return NOT_FILE;
case 14: return DEVICE_OFFLINE;
case 16: return CHILD_ALREADY_SHARED;
case 17: return PARENT_ALREADY_SHARED;
case 202: return INDEXING;
case 203: return UPDATING;
case 300: return LAUNCH_ABORTED;
case 18: return UI_MESSAGE;
case 401: return DEVICE_ID_ALREADY_EXISTS;
case 19: return ALREADY_INVITED;
case 402: return NO_ADMIN_OR_OWNER;
case 404: return NOT_AUTHENTICATED;
case 405: return INVALID_EMAIL_ADDRESS;
case 407: return EXTERNAL_SERVICE_UNAVAILABLE;
case 409: return SHARING_RULES_WARNINGS;
case 410: return CANNOT_RESET_PASSWORD;
case 411: return EXTERNAL_AUTH_FAILURE;
case 412: return LICENSE_LIMIT;
case 413: return RATE_LIMIT_EXCEEDED;
case 414: return SECOND_FACTOR_REQUIRED;
case 415: return WRONG_ORGANIZATION;
case 416: return NOT_LOCALLY_MANAGED;
case 417: return SECOND_FACTOR_SETUP_REQUIRED;
case 418: return MEMBER_LIMIT_EXCEEDED;
case 419: return PASSWORD_EXPIRED;
case 420: return PASSWORD_ALREADY_EXIST;
default: return null;
}
}
public static Internal.EnumLiteMap<Type>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<Type>
internalValueMap =
new Internal.EnumLiteMap<Type>() {
public Type findValueByNumber(int number) {
return Type.valueOf(number);
}
};
private final int value;
private Type(int index, int value) {
this.value = value;
}
}
private int b0_;
public static final int TYPE_FIELD_NUMBER = 1;
private Common.PBException.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBException.Type getType() {
return type_;
}
public static final int MESSAGE_DEPRECATED_FIELD_NUMBER = 4;
private Object messageDeprecated_;
public boolean hasMessageDeprecated() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getMessageDeprecated() {
Object ref = messageDeprecated_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
messageDeprecated_ = s;
}
return s;
}
}
public ByteString
getMessageDeprecatedBytes() {
Object ref = messageDeprecated_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
messageDeprecated_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int PLAIN_TEXT_MESSAGE_DEPRECATED_FIELD_NUMBER = 2;
private Object plainTextMessageDeprecated_;
public boolean hasPlainTextMessageDeprecated() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getPlainTextMessageDeprecated() {
Object ref = plainTextMessageDeprecated_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
plainTextMessageDeprecated_ = s;
}
return s;
}
}
public ByteString
getPlainTextMessageDeprecatedBytes() {
Object ref = plainTextMessageDeprecated_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
plainTextMessageDeprecated_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int STACK_TRACE_FIELD_NUMBER = 3;
private Object stackTrace_;
public boolean hasStackTrace() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public String getStackTrace() {
Object ref = stackTrace_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
stackTrace_ = s;
}
return s;
}
}
public ByteString
getStackTraceBytes() {
Object ref = stackTrace_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
stackTrace_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int DATA_FIELD_NUMBER = 5;
private ByteString data_;
public boolean hasData() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public ByteString getData() {
return data_;
}
private void initFields() {
type_ = Common.PBException.Type.INTERNAL_ERROR;
messageDeprecated_ = "";
plainTextMessageDeprecated_ = "";
stackTrace_ = "";
data_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasType()) {
mii = 0;
return false;
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeEnum(1, type_.getNumber());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(2, getPlainTextMessageDeprecatedBytes());
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeBytes(3, getStackTraceBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(4, getMessageDeprecatedBytes());
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeBytes(5, data_);
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeEnumSize(1, type_.getNumber());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(2, getPlainTextMessageDeprecatedBytes());
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeBytesSize(3, getStackTraceBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(4, getMessageDeprecatedBytes());
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeBytesSize(5, data_);
}
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.PBException parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBException parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBException parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBException parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBException parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBException parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.PBException parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.PBException parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.PBException parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBException parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.PBException prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.PBException, Builder>
implements
Common.PBExceptionOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
type_ = Common.PBException.Type.INTERNAL_ERROR;
b0_ = (b0_ & ~0x00000001);
messageDeprecated_ = "";
b0_ = (b0_ & ~0x00000002);
plainTextMessageDeprecated_ = "";
b0_ = (b0_ & ~0x00000004);
stackTrace_ = "";
b0_ = (b0_ & ~0x00000008);
data_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000010);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.PBException getDefaultInstanceForType() {
return Common.PBException.getDefaultInstance();
}
public Common.PBException build() {
Common.PBException result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.PBException buildPartial() {
Common.PBException result = new Common.PBException(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.messageDeprecated_ = messageDeprecated_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.plainTextMessageDeprecated_ = plainTextMessageDeprecated_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.stackTrace_ = stackTrace_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.data_ = data_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Common.PBException other) {
if (other == Common.PBException.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasMessageDeprecated()) {
b0_ |= 0x00000002;
messageDeprecated_ = other.messageDeprecated_;
}
if (other.hasPlainTextMessageDeprecated()) {
b0_ |= 0x00000004;
plainTextMessageDeprecated_ = other.plainTextMessageDeprecated_;
}
if (other.hasStackTrace()) {
b0_ |= 0x00000008;
stackTrace_ = other.stackTrace_;
}
if (other.hasData()) {
setData(other.getData());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.PBException pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.PBException) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBException.Type type_ = Common.PBException.Type.INTERNAL_ERROR;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBException.Type getType() {
return type_;
}
public Builder setType(Common.PBException.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Common.PBException.Type.INTERNAL_ERROR;
return this;
}
private Object messageDeprecated_ = "";
public boolean hasMessageDeprecated() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getMessageDeprecated() {
Object ref = messageDeprecated_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
messageDeprecated_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getMessageDeprecatedBytes() {
Object ref = messageDeprecated_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
messageDeprecated_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setMessageDeprecated(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
messageDeprecated_ = value;
return this;
}
public Builder clearMessageDeprecated() {
b0_ = (b0_ & ~0x00000002);
messageDeprecated_ = getDefaultInstance().getMessageDeprecated();
return this;
}
public Builder setMessageDeprecatedBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
messageDeprecated_ = value;
return this;
}
private Object plainTextMessageDeprecated_ = "";
public boolean hasPlainTextMessageDeprecated() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getPlainTextMessageDeprecated() {
Object ref = plainTextMessageDeprecated_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
plainTextMessageDeprecated_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getPlainTextMessageDeprecatedBytes() {
Object ref = plainTextMessageDeprecated_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
plainTextMessageDeprecated_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setPlainTextMessageDeprecated(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
plainTextMessageDeprecated_ = value;
return this;
}
public Builder clearPlainTextMessageDeprecated() {
b0_ = (b0_ & ~0x00000004);
plainTextMessageDeprecated_ = getDefaultInstance().getPlainTextMessageDeprecated();
return this;
}
public Builder setPlainTextMessageDeprecatedBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
plainTextMessageDeprecated_ = value;
return this;
}
private Object stackTrace_ = "";
public boolean hasStackTrace() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public String getStackTrace() {
Object ref = stackTrace_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
stackTrace_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getStackTraceBytes() {
Object ref = stackTrace_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
stackTrace_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setStackTrace(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
stackTrace_ = value;
return this;
}
public Builder clearStackTrace() {
b0_ = (b0_ & ~0x00000008);
stackTrace_ = getDefaultInstance().getStackTrace();
return this;
}
public Builder setStackTraceBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
stackTrace_ = value;
return this;
}
private ByteString data_ = ByteString.EMPTY;
public boolean hasData() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public ByteString getData() {
return data_;
}
public Builder setData(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000010;
data_ = value;
return this;
}
public Builder clearData() {
b0_ = (b0_ & ~0x00000010);
data_ = getDefaultInstance().getData();
return this;
}
}
static {
defaultInstance = new PBException(true);
defaultInstance.initFields();
}
}
public interface PBPermissionsOrBuilder extends
MessageLiteOrBuilder {
List<Common.PBPermission> getPermissionList();
int getPermissionCount();
Common.PBPermission getPermission(int index);
}
public static final class PBPermissions extends
GeneratedMessageLite implements
PBPermissionsOrBuilder {
private PBPermissions(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBPermissions(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBPermissions defaultInstance;
public static PBPermissions getDefaultInstance() {
return defaultInstance;
}
public PBPermissions getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBPermissions(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
case 8: {
int rawValue = input.readEnum();
Common.PBPermission value = Common.PBPermission.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
permission_ = new ArrayList<Common.PBPermission>();
mutable_b0_ |= 0x00000001;
}
permission_.add(value);
}
break;
}
case 10: {
int length = input.readRawVarint32();
int oldLimit = input.pushLimit(length);
while(input.getBytesUntilLimit() > 0) {
int rawValue = input.readEnum();
Common.PBPermission value = Common.PBPermission.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
permission_ = new ArrayList<Common.PBPermission>();
mutable_b0_ |= 0x00000001;
}
permission_.add(value);
}
}
input.popLimit(oldLimit);
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
if (((mutable_b0_ & 0x00000001) == 0x00000001)) {
permission_ = Collections.unmodifiableList(permission_);
}
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<PBPermissions> PARSER =
new AbstractParser<PBPermissions>() {
public PBPermissions parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBPermissions(input, er);
}
};
@Override
public Parser<PBPermissions> getParserForType() {
return PARSER;
}
public static final int PERMISSION_FIELD_NUMBER = 1;
private List<Common.PBPermission> permission_;
public List<Common.PBPermission> getPermissionList() {
return permission_;
}
public int getPermissionCount() {
return permission_.size();
}
public Common.PBPermission getPermission(int index) {
return permission_.get(index);
}
private void initFields() {
permission_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
for (int i = 0; i < permission_.size(); i++) {
output.writeEnum(1, permission_.get(i).getNumber());
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
{
int dataSize = 0;
for (int i = 0; i < permission_.size(); i++) {
dataSize += CodedOutputStream
.computeEnumSizeNoTag(permission_.get(i).getNumber());
}
size += dataSize;
size += 1 * permission_.size();
}
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.PBPermissions parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBPermissions parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBPermissions parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBPermissions parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBPermissions parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBPermissions parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.PBPermissions parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.PBPermissions parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.PBPermissions parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBPermissions parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.PBPermissions prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.PBPermissions, Builder>
implements
Common.PBPermissionsOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
permission_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.PBPermissions getDefaultInstanceForType() {
return Common.PBPermissions.getDefaultInstance();
}
public Common.PBPermissions build() {
Common.PBPermissions result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.PBPermissions buildPartial() {
Common.PBPermissions result = new Common.PBPermissions(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
permission_ = Collections.unmodifiableList(permission_);
b0_ = (b0_ & ~0x00000001);
}
result.permission_ = permission_;
return result;
}
public Builder mergeFrom(Common.PBPermissions other) {
if (other == Common.PBPermissions.getDefaultInstance()) return this;
if (!other.permission_.isEmpty()) {
if (permission_.isEmpty()) {
permission_ = other.permission_;
b0_ = (b0_ & ~0x00000001);
} else {
ensurePermissionIsMutable();
permission_.addAll(other.permission_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.PBPermissions pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.PBPermissions) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Common.PBPermission> permission_ =
Collections.emptyList();
private void ensurePermissionIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
permission_ = new ArrayList<Common.PBPermission>(permission_);
b0_ |= 0x00000001;
}
}
public List<Common.PBPermission> getPermissionList() {
return Collections.unmodifiableList(permission_);
}
public int getPermissionCount() {
return permission_.size();
}
public Common.PBPermission getPermission(int index) {
return permission_.get(index);
}
public Builder setPermission(
int index, Common.PBPermission value) {
if (value == null) {
throw new NullPointerException();
}
ensurePermissionIsMutable();
permission_.set(index, value);
return this;
}
public Builder addPermission(Common.PBPermission value) {
if (value == null) {
throw new NullPointerException();
}
ensurePermissionIsMutable();
permission_.add(value);
return this;
}
public Builder addAllPermission(
Iterable<? extends Common.PBPermission> values) {
ensurePermissionIsMutable();
AbstractMessageLite.Builder.addAll(
values, permission_);
return this;
}
public Builder clearPermission() {
permission_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new PBPermissions(true);
defaultInstance.initFields();
}
}
public interface PBSubjectPermissionsOrBuilder extends
MessageLiteOrBuilder {
boolean hasSubject();
String getSubject();
ByteString
getSubjectBytes();
boolean hasPermissions();
Common.PBPermissions getPermissions();
boolean hasExternal();
boolean getExternal();
}
public static final class PBSubjectPermissions extends
GeneratedMessageLite implements
PBSubjectPermissionsOrBuilder {
private PBSubjectPermissions(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBSubjectPermissions(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBSubjectPermissions defaultInstance;
public static PBSubjectPermissions getDefaultInstance() {
return defaultInstance;
}
public PBSubjectPermissions getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBSubjectPermissions(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
case 10: {
ByteString bs = input.readBytes();
b0_ |= 0x00000001;
subject_ = bs;
break;
}
case 18: {
Common.PBPermissions.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = permissions_.toBuilder();
}
permissions_ = input.readMessage(Common.PBPermissions.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(permissions_);
permissions_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
break;
}
case 24: {
b0_ |= 0x00000004;
external_ = input.readBool();
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<PBSubjectPermissions> PARSER =
new AbstractParser<PBSubjectPermissions>() {
public PBSubjectPermissions parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBSubjectPermissions(input, er);
}
};
@Override
public Parser<PBSubjectPermissions> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SUBJECT_FIELD_NUMBER = 1;
private Object subject_;
public boolean hasSubject() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getSubject() {
Object ref = subject_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
subject_ = s;
}
return s;
}
}
public ByteString
getSubjectBytes() {
Object ref = subject_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
subject_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int PERMISSIONS_FIELD_NUMBER = 2;
private Common.PBPermissions permissions_;
public boolean hasPermissions() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Common.PBPermissions getPermissions() {
return permissions_;
}
public static final int EXTERNAL_FIELD_NUMBER = 3;
private boolean external_;
public boolean hasExternal() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public boolean getExternal() {
return external_;
}
private void initFields() {
subject_ = "";
permissions_ = Common.PBPermissions.getDefaultInstance();
external_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasSubject()) {
mii = 0;
return false;
}
if (!hasPermissions()) {
mii = 0;
return false;
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, getSubjectBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeMessage(2, permissions_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBool(3, external_);
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeBytesSize(1, getSubjectBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeMessageSize(2, permissions_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBoolSize(3, external_);
}
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.PBSubjectPermissions parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBSubjectPermissions parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBSubjectPermissions parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBSubjectPermissions parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBSubjectPermissions parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBSubjectPermissions parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.PBSubjectPermissions parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.PBSubjectPermissions parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.PBSubjectPermissions parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBSubjectPermissions parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.PBSubjectPermissions prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.PBSubjectPermissions, Builder>
implements
Common.PBSubjectPermissionsOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
subject_ = "";
b0_ = (b0_ & ~0x00000001);
permissions_ = Common.PBPermissions.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
external_ = false;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.PBSubjectPermissions getDefaultInstanceForType() {
return Common.PBSubjectPermissions.getDefaultInstance();
}
public Common.PBSubjectPermissions build() {
Common.PBSubjectPermissions result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.PBSubjectPermissions buildPartial() {
Common.PBSubjectPermissions result = new Common.PBSubjectPermissions(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.subject_ = subject_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.permissions_ = permissions_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.external_ = external_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Common.PBSubjectPermissions other) {
if (other == Common.PBSubjectPermissions.getDefaultInstance()) return this;
if (other.hasSubject()) {
b0_ |= 0x00000001;
subject_ = other.subject_;
}
if (other.hasPermissions()) {
mergePermissions(other.getPermissions());
}
if (other.hasExternal()) {
setExternal(other.getExternal());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSubject()) {
return false;
}
if (!hasPermissions()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.PBSubjectPermissions pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.PBSubjectPermissions) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object subject_ = "";
public boolean hasSubject() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getSubject() {
Object ref = subject_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
subject_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getSubjectBytes() {
Object ref = subject_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
subject_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setSubject(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
subject_ = value;
return this;
}
public Builder clearSubject() {
b0_ = (b0_ & ~0x00000001);
subject_ = getDefaultInstance().getSubject();
return this;
}
public Builder setSubjectBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
subject_ = value;
return this;
}
private Common.PBPermissions permissions_ = Common.PBPermissions.getDefaultInstance();
public boolean hasPermissions() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Common.PBPermissions getPermissions() {
return permissions_;
}
public Builder setPermissions(Common.PBPermissions value) {
if (value == null) {
throw new NullPointerException();
}
permissions_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setPermissions(
Common.PBPermissions.Builder bdForValue) {
permissions_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergePermissions(Common.PBPermissions value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
permissions_ != Common.PBPermissions.getDefaultInstance()) {
permissions_ =
Common.PBPermissions.newBuilder(permissions_).mergeFrom(value).buildPartial();
} else {
permissions_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearPermissions() {
permissions_ = Common.PBPermissions.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
private boolean external_ ;
public boolean hasExternal() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public boolean getExternal() {
return external_;
}
public Builder setExternal(boolean value) {
b0_ |= 0x00000004;
external_ = value;
return this;
}
public Builder clearExternal() {
b0_ = (b0_ & ~0x00000004);
external_ = false;
return this;
}
}
static {
defaultInstance = new PBSubjectPermissions(true);
defaultInstance.initFields();
}
}
public interface PBFolderInvitationOrBuilder extends
MessageLiteOrBuilder {
boolean hasShareId();
ByteString getShareId();
boolean hasFolderName();
String getFolderName();
ByteString
getFolderNameBytes();
boolean hasSharer();
String getSharer();
ByteString
getSharerBytes();
}
public static final class PBFolderInvitation extends
GeneratedMessageLite implements
PBFolderInvitationOrBuilder {
private PBFolderInvitation(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBFolderInvitation(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBFolderInvitation defaultInstance;
public static PBFolderInvitation getDefaultInstance() {
return defaultInstance;
}
public PBFolderInvitation getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBFolderInvitation(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
case 10: {
b0_ |= 0x00000001;
shareId_ = input.readBytes();
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
folderName_ = bs;
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000004;
sharer_ = bs;
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<PBFolderInvitation> PARSER =
new AbstractParser<PBFolderInvitation>() {
public PBFolderInvitation parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBFolderInvitation(input, er);
}
};
@Override
public Parser<PBFolderInvitation> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SHARE_ID_FIELD_NUMBER = 1;
private ByteString shareId_;
public boolean hasShareId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getShareId() {
return shareId_;
}
public static final int FOLDER_NAME_FIELD_NUMBER = 2;
private Object folderName_;
public boolean hasFolderName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getFolderName() {
Object ref = folderName_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
folderName_ = s;
}
return s;
}
}
public ByteString
getFolderNameBytes() {
Object ref = folderName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
folderName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int SHARER_FIELD_NUMBER = 3;
private Object sharer_;
public boolean hasSharer() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getSharer() {
Object ref = sharer_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
sharer_ = s;
}
return s;
}
}
public ByteString
getSharerBytes() {
Object ref = sharer_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
sharer_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
shareId_ = ByteString.EMPTY;
folderName_ = "";
sharer_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasShareId()) {
mii = 0;
return false;
}
if (!hasFolderName()) {
mii = 0;
return false;
}
if (!hasSharer()) {
mii = 0;
return false;
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, shareId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getFolderNameBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, getSharerBytes());
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeBytesSize(1, shareId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getFolderNameBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, getSharerBytes());
}
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.PBFolderInvitation parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBFolderInvitation parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBFolderInvitation parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.PBFolderInvitation parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.PBFolderInvitation parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBFolderInvitation parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.PBFolderInvitation parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.PBFolderInvitation parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.PBFolderInvitation parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.PBFolderInvitation parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.PBFolderInvitation prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.PBFolderInvitation, Builder>
implements
Common.PBFolderInvitationOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
shareId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
folderName_ = "";
b0_ = (b0_ & ~0x00000002);
sharer_ = "";
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.PBFolderInvitation getDefaultInstanceForType() {
return Common.PBFolderInvitation.getDefaultInstance();
}
public Common.PBFolderInvitation build() {
Common.PBFolderInvitation result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.PBFolderInvitation buildPartial() {
Common.PBFolderInvitation result = new Common.PBFolderInvitation(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.shareId_ = shareId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.folderName_ = folderName_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.sharer_ = sharer_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Common.PBFolderInvitation other) {
if (other == Common.PBFolderInvitation.getDefaultInstance()) return this;
if (other.hasShareId()) {
setShareId(other.getShareId());
}
if (other.hasFolderName()) {
b0_ |= 0x00000002;
folderName_ = other.folderName_;
}
if (other.hasSharer()) {
b0_ |= 0x00000004;
sharer_ = other.sharer_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasShareId()) {
return false;
}
if (!hasFolderName()) {
return false;
}
if (!hasSharer()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.PBFolderInvitation pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.PBFolderInvitation) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString shareId_ = ByteString.EMPTY;
public boolean hasShareId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getShareId() {
return shareId_;
}
public Builder setShareId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
shareId_ = value;
return this;
}
public Builder clearShareId() {
b0_ = (b0_ & ~0x00000001);
shareId_ = getDefaultInstance().getShareId();
return this;
}
private Object folderName_ = "";
public boolean hasFolderName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getFolderName() {
Object ref = folderName_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
folderName_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getFolderNameBytes() {
Object ref = folderName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
folderName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setFolderName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
folderName_ = value;
return this;
}
public Builder clearFolderName() {
b0_ = (b0_ & ~0x00000002);
folderName_ = getDefaultInstance().getFolderName();
return this;
}
public Builder setFolderNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
folderName_ = value;
return this;
}
private Object sharer_ = "";
public boolean hasSharer() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getSharer() {
Object ref = sharer_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
sharer_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getSharerBytes() {
Object ref = sharer_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
sharer_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setSharer(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
sharer_ = value;
return this;
}
public Builder clearSharer() {
b0_ = (b0_ & ~0x00000004);
sharer_ = getDefaultInstance().getSharer();
return this;
}
public Builder setSharerBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
sharer_ = value;
return this;
}
}
static {
defaultInstance = new PBFolderInvitation(true);
defaultInstance.initFields();
}
}
public interface CNameVerificationInfoOrBuilder extends
MessageLiteOrBuilder {
boolean hasUser();
String getUser();
ByteString
getUserBytes();
boolean hasDid();
ByteString getDid();
}
public static final class CNameVerificationInfo extends
GeneratedMessageLite implements
CNameVerificationInfoOrBuilder {
private CNameVerificationInfo(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CNameVerificationInfo(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CNameVerificationInfo defaultInstance;
public static CNameVerificationInfo getDefaultInstance() {
return defaultInstance;
}
public CNameVerificationInfo getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CNameVerificationInfo(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
ByteString.Output unknownFieldsOutput =
ByteString.newOutput();
CodedOutputStream unknownFieldsCodedOutput =
CodedOutputStream.newInstance(
unknownFieldsOutput);
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFieldsCodedOutput,
er, tag)) {
done = true;
}
break;
}
case 10: {
ByteString bs = input.readBytes();
b0_ |= 0x00000001;
user_ = bs;
break;
}
case 18: {
b0_ |= 0x00000002;
did_ = input.readBytes();
break;
}
}
}
} catch (InvalidProtocolBufferException e) {
throw e.setUnfinishedMessage(this);
} catch (IOException e) {
throw new InvalidProtocolBufferException(
e.getMessage()).setUnfinishedMessage(this);
} finally {
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<CNameVerificationInfo> PARSER =
new AbstractParser<CNameVerificationInfo>() {
public CNameVerificationInfo parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CNameVerificationInfo(input, er);
}
};
@Override
public Parser<CNameVerificationInfo> getParserForType() {
return PARSER;
}
private int b0_;
public static final int USER_FIELD_NUMBER = 1;
private Object user_;
public boolean hasUser() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getUser() {
Object ref = user_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
user_ = s;
}
return s;
}
}
public ByteString
getUserBytes() {
Object ref = user_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
user_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int DID_FIELD_NUMBER = 2;
private ByteString did_;
public boolean hasDid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getDid() {
return did_;
}
private void initFields() {
user_ = "";
did_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasUser()) {
mii = 0;
return false;
}
if (!hasDid()) {
mii = 0;
return false;
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, getUserBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, did_);
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeBytesSize(1, getUserBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, did_);
}
size += unknownFields.size();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Common.CNameVerificationInfo parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.CNameVerificationInfo parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.CNameVerificationInfo parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Common.CNameVerificationInfo parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Common.CNameVerificationInfo parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.CNameVerificationInfo parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Common.CNameVerificationInfo parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Common.CNameVerificationInfo parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Common.CNameVerificationInfo parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Common.CNameVerificationInfo parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Common.CNameVerificationInfo prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Common.CNameVerificationInfo, Builder>
implements
Common.CNameVerificationInfoOrBuilder {
private Builder() {
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
user_ = "";
b0_ = (b0_ & ~0x00000001);
did_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Common.CNameVerificationInfo getDefaultInstanceForType() {
return Common.CNameVerificationInfo.getDefaultInstance();
}
public Common.CNameVerificationInfo build() {
Common.CNameVerificationInfo result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Common.CNameVerificationInfo buildPartial() {
Common.CNameVerificationInfo result = new Common.CNameVerificationInfo(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.user_ = user_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.did_ = did_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Common.CNameVerificationInfo other) {
if (other == Common.CNameVerificationInfo.getDefaultInstance()) return this;
if (other.hasUser()) {
b0_ |= 0x00000001;
user_ = other.user_;
}
if (other.hasDid()) {
setDid(other.getDid());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasUser()) {
return false;
}
if (!hasDid()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Common.CNameVerificationInfo pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Common.CNameVerificationInfo) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object user_ = "";
public boolean hasUser() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getUser() {
Object ref = user_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
user_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getUserBytes() {
Object ref = user_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
user_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setUser(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
user_ = value;
return this;
}
public Builder clearUser() {
b0_ = (b0_ & ~0x00000001);
user_ = getDefaultInstance().getUser();
return this;
}
public Builder setUserBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
user_ = value;
return this;
}
private ByteString did_ = ByteString.EMPTY;
public boolean hasDid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getDid() {
return did_;
}
public Builder setDid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
did_ = value;
return this;
}
public Builder clearDid() {
b0_ = (b0_ & ~0x00000002);
did_ = getDefaultInstance().getDid();
return this;
}
}
static {
defaultInstance = new CNameVerificationInfo(true);
defaultInstance.initFields();
}
}
static {
}
}
