package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Cmd {
private Cmd() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public enum CommandType
implements Internal.EnumLite {
INVALIDATE_DEVICE_NAME_CACHE(0, 0),
INVALIDATE_USER_NAME_CACHE(1, 1),
UNLINK_SELF(2, 2),
UNLINK_AND_WIPE_SELF(3, 3),
REFRESH_CRL(4, 4),
OBSOLETE_WAS_CLEAN_SSS_DATABASE(5, 5),
UPLOAD_DATABASE(6, 6),
CHECK_UPDATE(7, 7),
SEND_DEFECT(8, 8),
LOG_THREADS(9, 9),
UPLOAD_LOGS(10, 10),
;
public static final int INVALIDATE_DEVICE_NAME_CACHE_VALUE = 0;
public static final int INVALIDATE_USER_NAME_CACHE_VALUE = 1;
public static final int UNLINK_SELF_VALUE = 2;
public static final int UNLINK_AND_WIPE_SELF_VALUE = 3;
public static final int REFRESH_CRL_VALUE = 4;
public static final int OBSOLETE_WAS_CLEAN_SSS_DATABASE_VALUE = 5;
public static final int UPLOAD_DATABASE_VALUE = 6;
public static final int CHECK_UPDATE_VALUE = 7;
public static final int SEND_DEFECT_VALUE = 8;
public static final int LOG_THREADS_VALUE = 9;
public static final int UPLOAD_LOGS_VALUE = 10;
public final int getNumber() { return value; }
public static CommandType valueOf(int value) {
switch (value) {
case 0: return INVALIDATE_DEVICE_NAME_CACHE;
case 1: return INVALIDATE_USER_NAME_CACHE;
case 2: return UNLINK_SELF;
case 3: return UNLINK_AND_WIPE_SELF;
case 4: return REFRESH_CRL;
case 5: return OBSOLETE_WAS_CLEAN_SSS_DATABASE;
case 6: return UPLOAD_DATABASE;
case 7: return CHECK_UPDATE;
case 8: return SEND_DEFECT;
case 9: return LOG_THREADS;
case 10: return UPLOAD_LOGS;
default: return null;
}
}
public static Internal.EnumLiteMap<CommandType>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<CommandType>
internalValueMap =
new Internal.EnumLiteMap<CommandType>() {
public CommandType findValueByNumber(int number) {
return CommandType.valueOf(number);
}
};
private final int value;
private CommandType(int index, int value) {
this.value = value;
}
}
public interface CommandOrBuilder extends
MessageLiteOrBuilder {
boolean hasEpoch();
long getEpoch();
boolean hasType();
Cmd.CommandType getType();
boolean hasUploadLogsArgs();
Cmd.UploadLogsArgs getUploadLogsArgs();
}
public static final class Command extends
GeneratedMessageLite implements
CommandOrBuilder {
private Command(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Command(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final Command defaultInstance;
public static Command getDefaultInstance() {
return defaultInstance;
}
public Command getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private Command(
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
b0_ |= 0x00000001;
epoch_ = input.readUInt64();
break;
}
case 16: {
int rawValue = input.readEnum();
Cmd.CommandType value = Cmd.CommandType.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
b0_ |= 0x00000002;
type_ = value;
}
break;
}
case 26: {
Cmd.UploadLogsArgs.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = uploadLogsArgs_.toBuilder();
}
uploadLogsArgs_ = input.readMessage(Cmd.UploadLogsArgs.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(uploadLogsArgs_);
uploadLogsArgs_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
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
public static Parser<Command> PARSER =
new AbstractParser<Command>() {
public Command parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Command(input, er);
}
};
@Override
public Parser<Command> getParserForType() {
return PARSER;
}
private int b0_;
public static final int EPOCH_FIELD_NUMBER = 1;
private long epoch_;
public boolean hasEpoch() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getEpoch() {
return epoch_;
}
public static final int TYPE_FIELD_NUMBER = 2;
private Cmd.CommandType type_;
public boolean hasType() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Cmd.CommandType getType() {
return type_;
}
public static final int UPLOAD_LOGS_ARGS_FIELD_NUMBER = 3;
private Cmd.UploadLogsArgs uploadLogsArgs_;
public boolean hasUploadLogsArgs() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Cmd.UploadLogsArgs getUploadLogsArgs() {
return uploadLogsArgs_;
}
private void initFields() {
epoch_ = 0L;
type_ = Cmd.CommandType.INVALIDATE_DEVICE_NAME_CACHE;
uploadLogsArgs_ = Cmd.UploadLogsArgs.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasEpoch()) {
mii = 0;
return false;
}
if (!hasType()) {
mii = 0;
return false;
}
if (hasUploadLogsArgs()) {
if (!getUploadLogsArgs().isInitialized()) {
mii = 0;
return false;
}
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeUInt64(1, epoch_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeEnum(2, type_.getNumber());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, uploadLogsArgs_);
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
.computeUInt64Size(1, epoch_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeEnumSize(2, type_.getNumber());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, uploadLogsArgs_);
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
public static Cmd.Command parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Cmd.Command parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Cmd.Command parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Cmd.Command parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Cmd.Command parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Cmd.Command parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Cmd.Command parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Cmd.Command parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Cmd.Command parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Cmd.Command parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Cmd.Command prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Cmd.Command, Builder>
implements
Cmd.CommandOrBuilder {
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
epoch_ = 0L;
b0_ = (b0_ & ~0x00000001);
type_ = Cmd.CommandType.INVALIDATE_DEVICE_NAME_CACHE;
b0_ = (b0_ & ~0x00000002);
uploadLogsArgs_ = Cmd.UploadLogsArgs.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Cmd.Command getDefaultInstanceForType() {
return Cmd.Command.getDefaultInstance();
}
public Cmd.Command build() {
Cmd.Command result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Cmd.Command buildPartial() {
Cmd.Command result = new Cmd.Command(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.epoch_ = epoch_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.type_ = type_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.uploadLogsArgs_ = uploadLogsArgs_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Cmd.Command other) {
if (other == Cmd.Command.getDefaultInstance()) return this;
if (other.hasEpoch()) {
setEpoch(other.getEpoch());
}
if (other.hasType()) {
setType(other.getType());
}
if (other.hasUploadLogsArgs()) {
mergeUploadLogsArgs(other.getUploadLogsArgs());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasEpoch()) {
return false;
}
if (!hasType()) {
return false;
}
if (hasUploadLogsArgs()) {
if (!getUploadLogsArgs().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Cmd.Command pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Cmd.Command) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private long epoch_ ;
public boolean hasEpoch() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getEpoch() {
return epoch_;
}
public Builder setEpoch(long value) {
b0_ |= 0x00000001;
epoch_ = value;
return this;
}
public Builder clearEpoch() {
b0_ = (b0_ & ~0x00000001);
epoch_ = 0L;
return this;
}
private Cmd.CommandType type_ = Cmd.CommandType.INVALIDATE_DEVICE_NAME_CACHE;
public boolean hasType() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Cmd.CommandType getType() {
return type_;
}
public Builder setType(Cmd.CommandType value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000002);
type_ = Cmd.CommandType.INVALIDATE_DEVICE_NAME_CACHE;
return this;
}
private Cmd.UploadLogsArgs uploadLogsArgs_ = Cmd.UploadLogsArgs.getDefaultInstance();
public boolean hasUploadLogsArgs() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Cmd.UploadLogsArgs getUploadLogsArgs() {
return uploadLogsArgs_;
}
public Builder setUploadLogsArgs(Cmd.UploadLogsArgs value) {
if (value == null) {
throw new NullPointerException();
}
uploadLogsArgs_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setUploadLogsArgs(
Cmd.UploadLogsArgs.Builder bdForValue) {
uploadLogsArgs_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergeUploadLogsArgs(Cmd.UploadLogsArgs value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
uploadLogsArgs_ != Cmd.UploadLogsArgs.getDefaultInstance()) {
uploadLogsArgs_ =
Cmd.UploadLogsArgs.newBuilder(uploadLogsArgs_).mergeFrom(value).buildPartial();
} else {
uploadLogsArgs_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearUploadLogsArgs() {
uploadLogsArgs_ = Cmd.UploadLogsArgs.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
}
static {
defaultInstance = new Command(true);
defaultInstance.initFields();
}
}
public interface UploadLogsArgsOrBuilder extends
MessageLiteOrBuilder {
boolean hasDefectId();
String getDefectId();
ByteString
getDefectIdBytes();
boolean hasExpiryTime();
long getExpiryTime();
boolean hasDestination();
Cmd.UploadLogsDestination getDestination();
}
public static final class UploadLogsArgs extends
GeneratedMessageLite implements
UploadLogsArgsOrBuilder {
private UploadLogsArgs(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UploadLogsArgs(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UploadLogsArgs defaultInstance;
public static UploadLogsArgs getDefaultInstance() {
return defaultInstance;
}
public UploadLogsArgs getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UploadLogsArgs(
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
defectId_ = bs;
break;
}
case 16: {
b0_ |= 0x00000002;
expiryTime_ = input.readUInt64();
break;
}
case 26: {
Cmd.UploadLogsDestination.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = destination_.toBuilder();
}
destination_ = input.readMessage(Cmd.UploadLogsDestination.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(destination_);
destination_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
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
public static Parser<UploadLogsArgs> PARSER =
new AbstractParser<UploadLogsArgs>() {
public UploadLogsArgs parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UploadLogsArgs(input, er);
}
};
@Override
public Parser<UploadLogsArgs> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DEFECT_ID_FIELD_NUMBER = 1;
private Object defectId_;
public boolean hasDefectId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDefectId() {
Object ref = defectId_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
defectId_ = s;
}
return s;
}
}
public ByteString
getDefectIdBytes() {
Object ref = defectId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
defectId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int EXPIRY_TIME_FIELD_NUMBER = 2;
private long expiryTime_;
public boolean hasExpiryTime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getExpiryTime() {
return expiryTime_;
}
public static final int DESTINATION_FIELD_NUMBER = 3;
private Cmd.UploadLogsDestination destination_;
public boolean hasDestination() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Cmd.UploadLogsDestination getDestination() {
return destination_;
}
private void initFields() {
defectId_ = "";
expiryTime_ = 0L;
destination_ = Cmd.UploadLogsDestination.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasDefectId()) {
mii = 0;
return false;
}
if (!hasExpiryTime()) {
mii = 0;
return false;
}
if (hasDestination()) {
if (!getDestination().isInitialized()) {
mii = 0;
return false;
}
}
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, getDefectIdBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, expiryTime_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, destination_);
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
.computeBytesSize(1, getDefectIdBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, expiryTime_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, destination_);
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
public static Cmd.UploadLogsArgs parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Cmd.UploadLogsArgs parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Cmd.UploadLogsArgs parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Cmd.UploadLogsArgs parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Cmd.UploadLogsArgs parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Cmd.UploadLogsArgs parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Cmd.UploadLogsArgs parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Cmd.UploadLogsArgs parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Cmd.UploadLogsArgs parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Cmd.UploadLogsArgs parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Cmd.UploadLogsArgs prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Cmd.UploadLogsArgs, Builder>
implements
Cmd.UploadLogsArgsOrBuilder {
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
defectId_ = "";
b0_ = (b0_ & ~0x00000001);
expiryTime_ = 0L;
b0_ = (b0_ & ~0x00000002);
destination_ = Cmd.UploadLogsDestination.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Cmd.UploadLogsArgs getDefaultInstanceForType() {
return Cmd.UploadLogsArgs.getDefaultInstance();
}
public Cmd.UploadLogsArgs build() {
Cmd.UploadLogsArgs result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Cmd.UploadLogsArgs buildPartial() {
Cmd.UploadLogsArgs result = new Cmd.UploadLogsArgs(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.defectId_ = defectId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.expiryTime_ = expiryTime_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.destination_ = destination_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Cmd.UploadLogsArgs other) {
if (other == Cmd.UploadLogsArgs.getDefaultInstance()) return this;
if (other.hasDefectId()) {
b0_ |= 0x00000001;
defectId_ = other.defectId_;
}
if (other.hasExpiryTime()) {
setExpiryTime(other.getExpiryTime());
}
if (other.hasDestination()) {
mergeDestination(other.getDestination());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasDefectId()) {
return false;
}
if (!hasExpiryTime()) {
return false;
}
if (hasDestination()) {
if (!getDestination().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Cmd.UploadLogsArgs pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Cmd.UploadLogsArgs) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object defectId_ = "";
public boolean hasDefectId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDefectId() {
Object ref = defectId_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
defectId_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDefectIdBytes() {
Object ref = defectId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
defectId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDefectId(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
defectId_ = value;
return this;
}
public Builder clearDefectId() {
b0_ = (b0_ & ~0x00000001);
defectId_ = getDefaultInstance().getDefectId();
return this;
}
public Builder setDefectIdBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
defectId_ = value;
return this;
}
private long expiryTime_ ;
public boolean hasExpiryTime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getExpiryTime() {
return expiryTime_;
}
public Builder setExpiryTime(long value) {
b0_ |= 0x00000002;
expiryTime_ = value;
return this;
}
public Builder clearExpiryTime() {
b0_ = (b0_ & ~0x00000002);
expiryTime_ = 0L;
return this;
}
private Cmd.UploadLogsDestination destination_ = Cmd.UploadLogsDestination.getDefaultInstance();
public boolean hasDestination() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Cmd.UploadLogsDestination getDestination() {
return destination_;
}
public Builder setDestination(Cmd.UploadLogsDestination value) {
if (value == null) {
throw new NullPointerException();
}
destination_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setDestination(
Cmd.UploadLogsDestination.Builder bdForValue) {
destination_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergeDestination(Cmd.UploadLogsDestination value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
destination_ != Cmd.UploadLogsDestination.getDefaultInstance()) {
destination_ =
Cmd.UploadLogsDestination.newBuilder(destination_).mergeFrom(value).buildPartial();
} else {
destination_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearDestination() {
destination_ = Cmd.UploadLogsDestination.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
}
static {
defaultInstance = new UploadLogsArgs(true);
defaultInstance.initFields();
}
}
public interface UploadLogsDestinationOrBuilder extends
MessageLiteOrBuilder {
boolean hasHostname();
String getHostname();
ByteString
getHostnameBytes();
boolean hasPort();
int getPort();
boolean hasCert();
String getCert();
ByteString
getCertBytes();
}
public static final class UploadLogsDestination extends
GeneratedMessageLite implements
UploadLogsDestinationOrBuilder {
private UploadLogsDestination(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UploadLogsDestination(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UploadLogsDestination defaultInstance;
public static UploadLogsDestination getDefaultInstance() {
return defaultInstance;
}
public UploadLogsDestination getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UploadLogsDestination(
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
hostname_ = bs;
break;
}
case 16: {
b0_ |= 0x00000002;
port_ = input.readInt32();
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000004;
cert_ = bs;
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
public static Parser<UploadLogsDestination> PARSER =
new AbstractParser<UploadLogsDestination>() {
public UploadLogsDestination parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UploadLogsDestination(input, er);
}
};
@Override
public Parser<UploadLogsDestination> getParserForType() {
return PARSER;
}
private int b0_;
public static final int HOSTNAME_FIELD_NUMBER = 1;
private Object hostname_;
public boolean hasHostname() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getHostname() {
Object ref = hostname_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
hostname_ = s;
}
return s;
}
}
public ByteString
getHostnameBytes() {
Object ref = hostname_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
hostname_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int PORT_FIELD_NUMBER = 2;
private int port_;
public boolean hasPort() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getPort() {
return port_;
}
public static final int CERT_FIELD_NUMBER = 3;
private Object cert_;
public boolean hasCert() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getCert() {
Object ref = cert_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
cert_ = s;
}
return s;
}
}
public ByteString
getCertBytes() {
Object ref = cert_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
cert_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
hostname_ = "";
port_ = 0;
cert_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasHostname()) {
mii = 0;
return false;
}
if (!hasPort()) {
mii = 0;
return false;
}
if (!hasCert()) {
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
output.writeBytes(1, getHostnameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeInt32(2, port_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, getCertBytes());
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
.computeBytesSize(1, getHostnameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeInt32Size(2, port_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, getCertBytes());
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
public static Cmd.UploadLogsDestination parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Cmd.UploadLogsDestination parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Cmd.UploadLogsDestination parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Cmd.UploadLogsDestination parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Cmd.UploadLogsDestination parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Cmd.UploadLogsDestination parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Cmd.UploadLogsDestination parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Cmd.UploadLogsDestination parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Cmd.UploadLogsDestination parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Cmd.UploadLogsDestination parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Cmd.UploadLogsDestination prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Cmd.UploadLogsDestination, Builder>
implements
Cmd.UploadLogsDestinationOrBuilder {
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
hostname_ = "";
b0_ = (b0_ & ~0x00000001);
port_ = 0;
b0_ = (b0_ & ~0x00000002);
cert_ = "";
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Cmd.UploadLogsDestination getDefaultInstanceForType() {
return Cmd.UploadLogsDestination.getDefaultInstance();
}
public Cmd.UploadLogsDestination build() {
Cmd.UploadLogsDestination result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Cmd.UploadLogsDestination buildPartial() {
Cmd.UploadLogsDestination result = new Cmd.UploadLogsDestination(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.hostname_ = hostname_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.port_ = port_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.cert_ = cert_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Cmd.UploadLogsDestination other) {
if (other == Cmd.UploadLogsDestination.getDefaultInstance()) return this;
if (other.hasHostname()) {
b0_ |= 0x00000001;
hostname_ = other.hostname_;
}
if (other.hasPort()) {
setPort(other.getPort());
}
if (other.hasCert()) {
b0_ |= 0x00000004;
cert_ = other.cert_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasHostname()) {
return false;
}
if (!hasPort()) {
return false;
}
if (!hasCert()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Cmd.UploadLogsDestination pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Cmd.UploadLogsDestination) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object hostname_ = "";
public boolean hasHostname() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getHostname() {
Object ref = hostname_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
hostname_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getHostnameBytes() {
Object ref = hostname_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
hostname_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setHostname(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
hostname_ = value;
return this;
}
public Builder clearHostname() {
b0_ = (b0_ & ~0x00000001);
hostname_ = getDefaultInstance().getHostname();
return this;
}
public Builder setHostnameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
hostname_ = value;
return this;
}
private int port_ ;
public boolean hasPort() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getPort() {
return port_;
}
public Builder setPort(int value) {
b0_ |= 0x00000002;
port_ = value;
return this;
}
public Builder clearPort() {
b0_ = (b0_ & ~0x00000002);
port_ = 0;
return this;
}
private Object cert_ = "";
public boolean hasCert() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getCert() {
Object ref = cert_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
cert_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getCertBytes() {
Object ref = cert_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
cert_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setCert(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
cert_ = value;
return this;
}
public Builder clearCert() {
b0_ = (b0_ & ~0x00000004);
cert_ = getDefaultInstance().getCert();
return this;
}
public Builder setCertBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
cert_ = value;
return this;
}
}
static {
defaultInstance = new UploadLogsDestination(true);
defaultInstance.initFields();
}
}
static {
}
}
