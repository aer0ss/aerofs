package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class RitualNotifications {
private RitualNotifications() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public enum PBTransportMethod
implements Internal.EnumLite {
UNKNOWN(0, -1),
NOT_AVAILABLE(1, 0),
TCP(2, 1),
ZEPHYR(3, 2),
;
public static final int UNKNOWN_VALUE = -1;
public static final int NOT_AVAILABLE_VALUE = 0;
public static final int TCP_VALUE = 1;
public static final int ZEPHYR_VALUE = 2;
public final int getNumber() { return value; }
public static PBTransportMethod valueOf(int value) {
switch (value) {
case -1: return UNKNOWN;
case 0: return NOT_AVAILABLE;
case 1: return TCP;
case 2: return ZEPHYR;
default: return null;
}
}
public static Internal.EnumLiteMap<PBTransportMethod>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<PBTransportMethod>
internalValueMap =
new Internal.EnumLiteMap<PBTransportMethod>() {
public PBTransportMethod findValueByNumber(int number) {
return PBTransportMethod.valueOf(number);
}
};
private final int value;
private PBTransportMethod(int index, int value) {
this.value = value;
}
}
public interface PBNotificationOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
RitualNotifications.PBNotification.Type getType();
boolean hasTransfer();
RitualNotifications.PBTransferEvent getTransfer();
boolean hasPathStatus();
RitualNotifications.PBPathStatusEvent getPathStatus();
boolean hasCount();
int getCount();
boolean hasPath();
Common.PBPath getPath();
boolean hasIndexingProgress();
RitualNotifications.PBIndexingProgress getIndexingProgress();
boolean hasOnlineStatus();
boolean getOnlineStatus();
}
public static final class PBNotification extends
GeneratedMessageLite implements
PBNotificationOrBuilder {
private PBNotification(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBNotification(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBNotification defaultInstance;
public static PBNotification getDefaultInstance() {
return defaultInstance;
}
public PBNotification getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBNotification(
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
RitualNotifications.PBNotification.Type value = RitualNotifications.PBNotification.Type.valueOf(rawValue);
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
RitualNotifications.PBTransferEvent.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = transfer_.toBuilder();
}
transfer_ = input.readMessage(RitualNotifications.PBTransferEvent.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(transfer_);
transfer_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
break;
}
case 34: {
RitualNotifications.PBPathStatusEvent.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = pathStatus_.toBuilder();
}
pathStatus_ = input.readMessage(RitualNotifications.PBPathStatusEvent.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(pathStatus_);
pathStatus_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
break;
}
case 40: {
b0_ |= 0x00000008;
count_ = input.readInt32();
break;
}
case 50: {
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000010) == 0x00000010)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000010;
break;
}
case 58: {
RitualNotifications.PBIndexingProgress.Builder subBuilder = null;
if (((b0_ & 0x00000020) == 0x00000020)) {
subBuilder = indexingProgress_.toBuilder();
}
indexingProgress_ = input.readMessage(RitualNotifications.PBIndexingProgress.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(indexingProgress_);
indexingProgress_ = subBuilder.buildPartial();
}
b0_ |= 0x00000020;
break;
}
case 64: {
b0_ |= 0x00000040;
onlineStatus_ = input.readBool();
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
public static Parser<PBNotification> PARSER =
new AbstractParser<PBNotification>() {
public PBNotification parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBNotification(input, er);
}
};
@Override
public Parser<PBNotification> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
TRANSFER(0, 0),
PATH_STATUS(1, 2),
PATH_STATUS_OUT_OF_DATE(2, 3),
CONFLICT_COUNT(3, 4),
SHARED_FOLDER_JOIN(4, 5),
SHARED_FOLDER_KICKOUT(5, 6),
INDEXING_PROGRESS(6, 7),
SHARED_FOLDER_PENDING(7, 8),
ROOTS_CHANGED(8, 9),
ONLINE_STATUS_CHANGED(9, 10),
NRO_COUNT(10, 11),
;
public static final int TRANSFER_VALUE = 0;
public static final int PATH_STATUS_VALUE = 2;
public static final int PATH_STATUS_OUT_OF_DATE_VALUE = 3;
public static final int CONFLICT_COUNT_VALUE = 4;
public static final int SHARED_FOLDER_JOIN_VALUE = 5;
public static final int SHARED_FOLDER_KICKOUT_VALUE = 6;
public static final int INDEXING_PROGRESS_VALUE = 7;
public static final int SHARED_FOLDER_PENDING_VALUE = 8;
public static final int ROOTS_CHANGED_VALUE = 9;
public static final int ONLINE_STATUS_CHANGED_VALUE = 10;
public static final int NRO_COUNT_VALUE = 11;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return TRANSFER;
case 2: return PATH_STATUS;
case 3: return PATH_STATUS_OUT_OF_DATE;
case 4: return CONFLICT_COUNT;
case 5: return SHARED_FOLDER_JOIN;
case 6: return SHARED_FOLDER_KICKOUT;
case 7: return INDEXING_PROGRESS;
case 8: return SHARED_FOLDER_PENDING;
case 9: return ROOTS_CHANGED;
case 10: return ONLINE_STATUS_CHANGED;
case 11: return NRO_COUNT;
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
private RitualNotifications.PBNotification.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public RitualNotifications.PBNotification.Type getType() {
return type_;
}
public static final int TRANSFER_FIELD_NUMBER = 2;
private RitualNotifications.PBTransferEvent transfer_;
public boolean hasTransfer() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public RitualNotifications.PBTransferEvent getTransfer() {
return transfer_;
}
public static final int PATH_STATUS_FIELD_NUMBER = 4;
private RitualNotifications.PBPathStatusEvent pathStatus_;
public boolean hasPathStatus() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public RitualNotifications.PBPathStatusEvent getPathStatus() {
return pathStatus_;
}
public static final int COUNT_FIELD_NUMBER = 5;
private int count_;
public boolean hasCount() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public int getCount() {
return count_;
}
public static final int PATH_FIELD_NUMBER = 6;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public Common.PBPath getPath() {
return path_;
}
public static final int INDEXING_PROGRESS_FIELD_NUMBER = 7;
private RitualNotifications.PBIndexingProgress indexingProgress_;
public boolean hasIndexingProgress() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public RitualNotifications.PBIndexingProgress getIndexingProgress() {
return indexingProgress_;
}
public static final int ONLINE_STATUS_FIELD_NUMBER = 8;
private boolean onlineStatus_;
public boolean hasOnlineStatus() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public boolean getOnlineStatus() {
return onlineStatus_;
}
private void initFields() {
type_ = RitualNotifications.PBNotification.Type.TRANSFER;
transfer_ = RitualNotifications.PBTransferEvent.getDefaultInstance();
pathStatus_ = RitualNotifications.PBPathStatusEvent.getDefaultInstance();
count_ = 0;
path_ = Common.PBPath.getDefaultInstance();
indexingProgress_ = RitualNotifications.PBIndexingProgress.getDefaultInstance();
onlineStatus_ = false;
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
if (hasTransfer()) {
if (!getTransfer().isInitialized()) {
mii = 0;
return false;
}
}
if (hasPathStatus()) {
if (!getPathStatus().isInitialized()) {
mii = 0;
return false;
}
}
if (hasPath()) {
if (!getPath().isInitialized()) {
mii = 0;
return false;
}
}
if (hasIndexingProgress()) {
if (!getIndexingProgress().isInitialized()) {
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
output.writeEnum(1, type_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeMessage(2, transfer_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(4, pathStatus_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeInt32(5, count_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeMessage(6, path_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeMessage(7, indexingProgress_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeBool(8, onlineStatus_);
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
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeMessageSize(2, transfer_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(4, pathStatus_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeInt32Size(5, count_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeMessageSize(6, path_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeMessageSize(7, indexingProgress_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeBoolSize(8, onlineStatus_);
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
public static RitualNotifications.PBNotification parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBNotification parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBNotification parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBNotification parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBNotification parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBNotification parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static RitualNotifications.PBNotification parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static RitualNotifications.PBNotification parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static RitualNotifications.PBNotification parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBNotification parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(RitualNotifications.PBNotification prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
RitualNotifications.PBNotification, Builder>
implements
RitualNotifications.PBNotificationOrBuilder {
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
type_ = RitualNotifications.PBNotification.Type.TRANSFER;
b0_ = (b0_ & ~0x00000001);
transfer_ = RitualNotifications.PBTransferEvent.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
pathStatus_ = RitualNotifications.PBPathStatusEvent.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
count_ = 0;
b0_ = (b0_ & ~0x00000008);
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000010);
indexingProgress_ = RitualNotifications.PBIndexingProgress.getDefaultInstance();
b0_ = (b0_ & ~0x00000020);
onlineStatus_ = false;
b0_ = (b0_ & ~0x00000040);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public RitualNotifications.PBNotification getDefaultInstanceForType() {
return RitualNotifications.PBNotification.getDefaultInstance();
}
public RitualNotifications.PBNotification build() {
RitualNotifications.PBNotification result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public RitualNotifications.PBNotification buildPartial() {
RitualNotifications.PBNotification result = new RitualNotifications.PBNotification(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.transfer_ = transfer_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.pathStatus_ = pathStatus_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.count_ = count_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.path_ = path_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.indexingProgress_ = indexingProgress_;
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.onlineStatus_ = onlineStatus_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(RitualNotifications.PBNotification other) {
if (other == RitualNotifications.PBNotification.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasTransfer()) {
mergeTransfer(other.getTransfer());
}
if (other.hasPathStatus()) {
mergePathStatus(other.getPathStatus());
}
if (other.hasCount()) {
setCount(other.getCount());
}
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasIndexingProgress()) {
mergeIndexingProgress(other.getIndexingProgress());
}
if (other.hasOnlineStatus()) {
setOnlineStatus(other.getOnlineStatus());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (hasTransfer()) {
if (!getTransfer().isInitialized()) {
return false;
}
}
if (hasPathStatus()) {
if (!getPathStatus().isInitialized()) {
return false;
}
}
if (hasPath()) {
if (!getPath().isInitialized()) {
return false;
}
}
if (hasIndexingProgress()) {
if (!getIndexingProgress().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
RitualNotifications.PBNotification pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (RitualNotifications.PBNotification) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private RitualNotifications.PBNotification.Type type_ = RitualNotifications.PBNotification.Type.TRANSFER;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public RitualNotifications.PBNotification.Type getType() {
return type_;
}
public Builder setType(RitualNotifications.PBNotification.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = RitualNotifications.PBNotification.Type.TRANSFER;
return this;
}
private RitualNotifications.PBTransferEvent transfer_ = RitualNotifications.PBTransferEvent.getDefaultInstance();
public boolean hasTransfer() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public RitualNotifications.PBTransferEvent getTransfer() {
return transfer_;
}
public Builder setTransfer(RitualNotifications.PBTransferEvent value) {
if (value == null) {
throw new NullPointerException();
}
transfer_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setTransfer(
RitualNotifications.PBTransferEvent.Builder bdForValue) {
transfer_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeTransfer(RitualNotifications.PBTransferEvent value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
transfer_ != RitualNotifications.PBTransferEvent.getDefaultInstance()) {
transfer_ =
RitualNotifications.PBTransferEvent.newBuilder(transfer_).mergeFrom(value).buildPartial();
} else {
transfer_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearTransfer() {
transfer_ = RitualNotifications.PBTransferEvent.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
private RitualNotifications.PBPathStatusEvent pathStatus_ = RitualNotifications.PBPathStatusEvent.getDefaultInstance();
public boolean hasPathStatus() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public RitualNotifications.PBPathStatusEvent getPathStatus() {
return pathStatus_;
}
public Builder setPathStatus(RitualNotifications.PBPathStatusEvent value) {
if (value == null) {
throw new NullPointerException();
}
pathStatus_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setPathStatus(
RitualNotifications.PBPathStatusEvent.Builder bdForValue) {
pathStatus_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergePathStatus(RitualNotifications.PBPathStatusEvent value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
pathStatus_ != RitualNotifications.PBPathStatusEvent.getDefaultInstance()) {
pathStatus_ =
RitualNotifications.PBPathStatusEvent.newBuilder(pathStatus_).mergeFrom(value).buildPartial();
} else {
pathStatus_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearPathStatus() {
pathStatus_ = RitualNotifications.PBPathStatusEvent.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
private int count_ ;
public boolean hasCount() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public int getCount() {
return count_;
}
public Builder setCount(int value) {
b0_ |= 0x00000008;
count_ = value;
return this;
}
public Builder clearCount() {
b0_ = (b0_ & ~0x00000008);
count_ = 0;
return this;
}
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000010;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000010;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000010) == 0x00000010) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000010;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000010);
return this;
}
private RitualNotifications.PBIndexingProgress indexingProgress_ = RitualNotifications.PBIndexingProgress.getDefaultInstance();
public boolean hasIndexingProgress() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public RitualNotifications.PBIndexingProgress getIndexingProgress() {
return indexingProgress_;
}
public Builder setIndexingProgress(RitualNotifications.PBIndexingProgress value) {
if (value == null) {
throw new NullPointerException();
}
indexingProgress_ = value;
b0_ |= 0x00000020;
return this;
}
public Builder setIndexingProgress(
RitualNotifications.PBIndexingProgress.Builder bdForValue) {
indexingProgress_ = bdForValue.build();
b0_ |= 0x00000020;
return this;
}
public Builder mergeIndexingProgress(RitualNotifications.PBIndexingProgress value) {
if (((b0_ & 0x00000020) == 0x00000020) &&
indexingProgress_ != RitualNotifications.PBIndexingProgress.getDefaultInstance()) {
indexingProgress_ =
RitualNotifications.PBIndexingProgress.newBuilder(indexingProgress_).mergeFrom(value).buildPartial();
} else {
indexingProgress_ = value;
}
b0_ |= 0x00000020;
return this;
}
public Builder clearIndexingProgress() {
indexingProgress_ = RitualNotifications.PBIndexingProgress.getDefaultInstance();
b0_ = (b0_ & ~0x00000020);
return this;
}
private boolean onlineStatus_ ;
public boolean hasOnlineStatus() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public boolean getOnlineStatus() {
return onlineStatus_;
}
public Builder setOnlineStatus(boolean value) {
b0_ |= 0x00000040;
onlineStatus_ = value;
return this;
}
public Builder clearOnlineStatus() {
b0_ = (b0_ & ~0x00000040);
onlineStatus_ = false;
return this;
}
}
static {
defaultInstance = new PBNotification(true);
defaultInstance.initFields();
}
}
public interface PBTransferEventOrBuilder extends
MessageLiteOrBuilder {
boolean hasUpload();
boolean getUpload();
boolean hasSocid();
RitualNotifications.PBSOCID getSocid();
boolean hasPath();
Common.PBPath getPath();
boolean hasDeviceId();
ByteString getDeviceId();
boolean hasDone();
long getDone();
boolean hasTotal();
long getTotal();
boolean hasFailed();
boolean getFailed();
boolean hasDisplayName();
String getDisplayName();
ByteString
getDisplayNameBytes();
boolean hasTransport();
RitualNotifications.PBTransportMethod getTransport();
}
public static final class PBTransferEvent extends
GeneratedMessageLite implements
PBTransferEventOrBuilder {
private PBTransferEvent(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBTransferEvent(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBTransferEvent defaultInstance;
public static PBTransferEvent getDefaultInstance() {
return defaultInstance;
}
public PBTransferEvent getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBTransferEvent(
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
upload_ = input.readBool();
break;
}
case 18: {
RitualNotifications.PBSOCID.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = socid_.toBuilder();
}
socid_ = input.readMessage(RitualNotifications.PBSOCID.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(socid_);
socid_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
break;
}
case 26: {
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
break;
}
case 34: {
b0_ |= 0x00000008;
deviceId_ = input.readBytes();
break;
}
case 40: {
b0_ |= 0x00000010;
done_ = input.readUInt64();
break;
}
case 48: {
b0_ |= 0x00000020;
total_ = input.readUInt64();
break;
}
case 56: {
b0_ |= 0x00000040;
failed_ = input.readBool();
break;
}
case 66: {
ByteString bs = input.readBytes();
b0_ |= 0x00000080;
displayName_ = bs;
break;
}
case 72: {
int rawValue = input.readEnum();
RitualNotifications.PBTransportMethod value = RitualNotifications.PBTransportMethod.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
b0_ |= 0x00000100;
transport_ = value;
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
public static Parser<PBTransferEvent> PARSER =
new AbstractParser<PBTransferEvent>() {
public PBTransferEvent parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBTransferEvent(input, er);
}
};
@Override
public Parser<PBTransferEvent> getParserForType() {
return PARSER;
}
private int b0_;
public static final int UPLOAD_FIELD_NUMBER = 1;
private boolean upload_;
public boolean hasUpload() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getUpload() {
return upload_;
}
public static final int SOCID_FIELD_NUMBER = 2;
private RitualNotifications.PBSOCID socid_;
public boolean hasSocid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public RitualNotifications.PBSOCID getSocid() {
return socid_;
}
public static final int PATH_FIELD_NUMBER = 3;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Common.PBPath getPath() {
return path_;
}
public static final int DEVICE_ID_FIELD_NUMBER = 4;
private ByteString deviceId_;
public boolean hasDeviceId() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public ByteString getDeviceId() {
return deviceId_;
}
public static final int DONE_FIELD_NUMBER = 5;
private long done_;
public boolean hasDone() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getDone() {
return done_;
}
public static final int TOTAL_FIELD_NUMBER = 6;
private long total_;
public boolean hasTotal() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getTotal() {
return total_;
}
public static final int FAILED_FIELD_NUMBER = 7;
private boolean failed_;
public boolean hasFailed() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public boolean getFailed() {
return failed_;
}
public static final int DISPLAY_NAME_FIELD_NUMBER = 8;
private Object displayName_;
public boolean hasDisplayName() {
return ((b0_ & 0x00000080) == 0x00000080);
}
public String getDisplayName() {
Object ref = displayName_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
displayName_ = s;
}
return s;
}
}
public ByteString
getDisplayNameBytes() {
Object ref = displayName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
displayName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int TRANSPORT_FIELD_NUMBER = 9;
private RitualNotifications.PBTransportMethod transport_;
public boolean hasTransport() {
return ((b0_ & 0x00000100) == 0x00000100);
}
public RitualNotifications.PBTransportMethod getTransport() {
return transport_;
}
private void initFields() {
upload_ = false;
socid_ = RitualNotifications.PBSOCID.getDefaultInstance();
path_ = Common.PBPath.getDefaultInstance();
deviceId_ = ByteString.EMPTY;
done_ = 0L;
total_ = 0L;
failed_ = false;
displayName_ = "";
transport_ = RitualNotifications.PBTransportMethod.UNKNOWN;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasUpload()) {
mii = 0;
return false;
}
if (!hasSocid()) {
mii = 0;
return false;
}
if (!hasDeviceId()) {
mii = 0;
return false;
}
if (!hasDone()) {
mii = 0;
return false;
}
if (!hasTotal()) {
mii = 0;
return false;
}
if (!getSocid().isInitialized()) {
mii = 0;
return false;
}
if (hasPath()) {
if (!getPath().isInitialized()) {
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
output.writeBool(1, upload_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeMessage(2, socid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, path_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeBytes(4, deviceId_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeUInt64(5, done_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeUInt64(6, total_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeBool(7, failed_);
}
if (((b0_ & 0x00000080) == 0x00000080)) {
output.writeBytes(8, getDisplayNameBytes());
}
if (((b0_ & 0x00000100) == 0x00000100)) {
output.writeEnum(9, transport_.getNumber());
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
.computeBoolSize(1, upload_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeMessageSize(2, socid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, path_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeBytesSize(4, deviceId_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeUInt64Size(5, done_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeUInt64Size(6, total_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeBoolSize(7, failed_);
}
if (((b0_ & 0x00000080) == 0x00000080)) {
size += CodedOutputStream
.computeBytesSize(8, getDisplayNameBytes());
}
if (((b0_ & 0x00000100) == 0x00000100)) {
size += CodedOutputStream
.computeEnumSize(9, transport_.getNumber());
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
public static RitualNotifications.PBTransferEvent parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBTransferEvent parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBTransferEvent parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBTransferEvent parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBTransferEvent parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBTransferEvent parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static RitualNotifications.PBTransferEvent parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static RitualNotifications.PBTransferEvent parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static RitualNotifications.PBTransferEvent parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBTransferEvent parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(RitualNotifications.PBTransferEvent prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
RitualNotifications.PBTransferEvent, Builder>
implements
RitualNotifications.PBTransferEventOrBuilder {
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
upload_ = false;
b0_ = (b0_ & ~0x00000001);
socid_ = RitualNotifications.PBSOCID.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
deviceId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000008);
done_ = 0L;
b0_ = (b0_ & ~0x00000010);
total_ = 0L;
b0_ = (b0_ & ~0x00000020);
failed_ = false;
b0_ = (b0_ & ~0x00000040);
displayName_ = "";
b0_ = (b0_ & ~0x00000080);
transport_ = RitualNotifications.PBTransportMethod.UNKNOWN;
b0_ = (b0_ & ~0x00000100);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public RitualNotifications.PBTransferEvent getDefaultInstanceForType() {
return RitualNotifications.PBTransferEvent.getDefaultInstance();
}
public RitualNotifications.PBTransferEvent build() {
RitualNotifications.PBTransferEvent result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public RitualNotifications.PBTransferEvent buildPartial() {
RitualNotifications.PBTransferEvent result = new RitualNotifications.PBTransferEvent(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.upload_ = upload_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.socid_ = socid_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.path_ = path_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.deviceId_ = deviceId_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.done_ = done_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.total_ = total_;
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.failed_ = failed_;
if (((from_b0_ & 0x00000080) == 0x00000080)) {
to_b0_ |= 0x00000080;
}
result.displayName_ = displayName_;
if (((from_b0_ & 0x00000100) == 0x00000100)) {
to_b0_ |= 0x00000100;
}
result.transport_ = transport_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(RitualNotifications.PBTransferEvent other) {
if (other == RitualNotifications.PBTransferEvent.getDefaultInstance()) return this;
if (other.hasUpload()) {
setUpload(other.getUpload());
}
if (other.hasSocid()) {
mergeSocid(other.getSocid());
}
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasDeviceId()) {
setDeviceId(other.getDeviceId());
}
if (other.hasDone()) {
setDone(other.getDone());
}
if (other.hasTotal()) {
setTotal(other.getTotal());
}
if (other.hasFailed()) {
setFailed(other.getFailed());
}
if (other.hasDisplayName()) {
b0_ |= 0x00000080;
displayName_ = other.displayName_;
}
if (other.hasTransport()) {
setTransport(other.getTransport());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasUpload()) {
return false;
}
if (!hasSocid()) {
return false;
}
if (!hasDeviceId()) {
return false;
}
if (!hasDone()) {
return false;
}
if (!hasTotal()) {
return false;
}
if (!getSocid().isInitialized()) {
return false;
}
if (hasPath()) {
if (!getPath().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
RitualNotifications.PBTransferEvent pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (RitualNotifications.PBTransferEvent) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private boolean upload_ ;
public boolean hasUpload() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getUpload() {
return upload_;
}
public Builder setUpload(boolean value) {
b0_ |= 0x00000001;
upload_ = value;
return this;
}
public Builder clearUpload() {
b0_ = (b0_ & ~0x00000001);
upload_ = false;
return this;
}
private RitualNotifications.PBSOCID socid_ = RitualNotifications.PBSOCID.getDefaultInstance();
public boolean hasSocid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public RitualNotifications.PBSOCID getSocid() {
return socid_;
}
public Builder setSocid(RitualNotifications.PBSOCID value) {
if (value == null) {
throw new NullPointerException();
}
socid_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setSocid(
RitualNotifications.PBSOCID.Builder bdForValue) {
socid_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeSocid(RitualNotifications.PBSOCID value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
socid_ != RitualNotifications.PBSOCID.getDefaultInstance()) {
socid_ =
RitualNotifications.PBSOCID.newBuilder(socid_).mergeFrom(value).buildPartial();
} else {
socid_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearSocid() {
socid_ = RitualNotifications.PBSOCID.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
private ByteString deviceId_ = ByteString.EMPTY;
public boolean hasDeviceId() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public ByteString getDeviceId() {
return deviceId_;
}
public Builder setDeviceId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
deviceId_ = value;
return this;
}
public Builder clearDeviceId() {
b0_ = (b0_ & ~0x00000008);
deviceId_ = getDefaultInstance().getDeviceId();
return this;
}
private long done_ ;
public boolean hasDone() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getDone() {
return done_;
}
public Builder setDone(long value) {
b0_ |= 0x00000010;
done_ = value;
return this;
}
public Builder clearDone() {
b0_ = (b0_ & ~0x00000010);
done_ = 0L;
return this;
}
private long total_ ;
public boolean hasTotal() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getTotal() {
return total_;
}
public Builder setTotal(long value) {
b0_ |= 0x00000020;
total_ = value;
return this;
}
public Builder clearTotal() {
b0_ = (b0_ & ~0x00000020);
total_ = 0L;
return this;
}
private boolean failed_ ;
public boolean hasFailed() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public boolean getFailed() {
return failed_;
}
public Builder setFailed(boolean value) {
b0_ |= 0x00000040;
failed_ = value;
return this;
}
public Builder clearFailed() {
b0_ = (b0_ & ~0x00000040);
failed_ = false;
return this;
}
private Object displayName_ = "";
public boolean hasDisplayName() {
return ((b0_ & 0x00000080) == 0x00000080);
}
public String getDisplayName() {
Object ref = displayName_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
displayName_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDisplayNameBytes() {
Object ref = displayName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
displayName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDisplayName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000080;
displayName_ = value;
return this;
}
public Builder clearDisplayName() {
b0_ = (b0_ & ~0x00000080);
displayName_ = getDefaultInstance().getDisplayName();
return this;
}
public Builder setDisplayNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000080;
displayName_ = value;
return this;
}
private RitualNotifications.PBTransportMethod transport_ = RitualNotifications.PBTransportMethod.UNKNOWN;
public boolean hasTransport() {
return ((b0_ & 0x00000100) == 0x00000100);
}
public RitualNotifications.PBTransportMethod getTransport() {
return transport_;
}
public Builder setTransport(RitualNotifications.PBTransportMethod value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000100;
transport_ = value;
return this;
}
public Builder clearTransport() {
b0_ = (b0_ & ~0x00000100);
transport_ = RitualNotifications.PBTransportMethod.UNKNOWN;
return this;
}
}
static {
defaultInstance = new PBTransferEvent(true);
defaultInstance.initFields();
}
}
public interface PBPathStatusEventOrBuilder extends
MessageLiteOrBuilder {
List<Common.PBPath> 
getPathList();
Common.PBPath getPath(int index);
int getPathCount();
List<PathStatus.PBPathStatus> 
getStatusList();
PathStatus.PBPathStatus getStatus(int index);
int getStatusCount();
}
public static final class PBPathStatusEvent extends
GeneratedMessageLite implements
PBPathStatusEventOrBuilder {
private PBPathStatusEvent(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBPathStatusEvent(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBPathStatusEvent defaultInstance;
public static PBPathStatusEvent getDefaultInstance() {
return defaultInstance;
}
public PBPathStatusEvent getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBPathStatusEvent(
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
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
path_ = new ArrayList<Common.PBPath>();
mutable_b0_ |= 0x00000001;
}
path_.add(input.readMessage(Common.PBPath.PARSER, er));
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
status_ = new ArrayList<PathStatus.PBPathStatus>();
mutable_b0_ |= 0x00000002;
}
status_.add(input.readMessage(PathStatus.PBPathStatus.PARSER, er));
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
path_ = Collections.unmodifiableList(path_);
}
if (((mutable_b0_ & 0x00000002) == 0x00000002)) {
status_ = Collections.unmodifiableList(status_);
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
public static Parser<PBPathStatusEvent> PARSER =
new AbstractParser<PBPathStatusEvent>() {
public PBPathStatusEvent parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBPathStatusEvent(input, er);
}
};
@Override
public Parser<PBPathStatusEvent> getParserForType() {
return PARSER;
}
public static final int PATH_FIELD_NUMBER = 1;
private List<Common.PBPath> path_;
public List<Common.PBPath> getPathList() {
return path_;
}
public List<? extends Common.PBPathOrBuilder> 
getPathOrBuilderList() {
return path_;
}
public int getPathCount() {
return path_.size();
}
public Common.PBPath getPath(int index) {
return path_.get(index);
}
public Common.PBPathOrBuilder getPathOrBuilder(
int index) {
return path_.get(index);
}
public static final int STATUS_FIELD_NUMBER = 2;
private List<PathStatus.PBPathStatus> status_;
public List<PathStatus.PBPathStatus> getStatusList() {
return status_;
}
public List<? extends PathStatus.PBPathStatusOrBuilder> 
getStatusOrBuilderList() {
return status_;
}
public int getStatusCount() {
return status_.size();
}
public PathStatus.PBPathStatus getStatus(int index) {
return status_.get(index);
}
public PathStatus.PBPathStatusOrBuilder getStatusOrBuilder(
int index) {
return status_.get(index);
}
private void initFields() {
path_ = Collections.emptyList();
status_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getPathCount(); i++) {
if (!getPath(i).isInitialized()) {
mii = 0;
return false;
}
}
for (int i = 0; i < getStatusCount(); i++) {
if (!getStatus(i).isInitialized()) {
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
for (int i = 0; i < path_.size(); i++) {
output.writeMessage(1, path_.get(i));
}
for (int i = 0; i < status_.size(); i++) {
output.writeMessage(2, status_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < path_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, path_.get(i));
}
for (int i = 0; i < status_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, status_.get(i));
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
public static RitualNotifications.PBPathStatusEvent parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static RitualNotifications.PBPathStatusEvent parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static RitualNotifications.PBPathStatusEvent parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBPathStatusEvent parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(RitualNotifications.PBPathStatusEvent prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
RitualNotifications.PBPathStatusEvent, Builder>
implements
RitualNotifications.PBPathStatusEventOrBuilder {
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
path_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
status_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public RitualNotifications.PBPathStatusEvent getDefaultInstanceForType() {
return RitualNotifications.PBPathStatusEvent.getDefaultInstance();
}
public RitualNotifications.PBPathStatusEvent build() {
RitualNotifications.PBPathStatusEvent result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public RitualNotifications.PBPathStatusEvent buildPartial() {
RitualNotifications.PBPathStatusEvent result = new RitualNotifications.PBPathStatusEvent(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
path_ = Collections.unmodifiableList(path_);
b0_ = (b0_ & ~0x00000001);
}
result.path_ = path_;
if (((b0_ & 0x00000002) == 0x00000002)) {
status_ = Collections.unmodifiableList(status_);
b0_ = (b0_ & ~0x00000002);
}
result.status_ = status_;
return result;
}
public Builder mergeFrom(RitualNotifications.PBPathStatusEvent other) {
if (other == RitualNotifications.PBPathStatusEvent.getDefaultInstance()) return this;
if (!other.path_.isEmpty()) {
if (path_.isEmpty()) {
path_ = other.path_;
b0_ = (b0_ & ~0x00000001);
} else {
ensurePathIsMutable();
path_.addAll(other.path_);
}
}
if (!other.status_.isEmpty()) {
if (status_.isEmpty()) {
status_ = other.status_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureStatusIsMutable();
status_.addAll(other.status_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getPathCount(); i++) {
if (!getPath(i).isInitialized()) {
return false;
}
}
for (int i = 0; i < getStatusCount(); i++) {
if (!getStatus(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
RitualNotifications.PBPathStatusEvent pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (RitualNotifications.PBPathStatusEvent) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Common.PBPath> path_ =
Collections.emptyList();
private void ensurePathIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
path_ = new ArrayList<Common.PBPath>(path_);
b0_ |= 0x00000001;
}
}
public List<Common.PBPath> getPathList() {
return Collections.unmodifiableList(path_);
}
public int getPathCount() {
return path_.size();
}
public Common.PBPath getPath(int index) {
return path_.get(index);
}
public Builder setPath(
int index, Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
ensurePathIsMutable();
path_.set(index, value);
return this;
}
public Builder setPath(
int index, Common.PBPath.Builder bdForValue) {
ensurePathIsMutable();
path_.set(index, bdForValue.build());
return this;
}
public Builder addPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
ensurePathIsMutable();
path_.add(value);
return this;
}
public Builder addPath(
int index, Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
ensurePathIsMutable();
path_.add(index, value);
return this;
}
public Builder addPath(
Common.PBPath.Builder bdForValue) {
ensurePathIsMutable();
path_.add(bdForValue.build());
return this;
}
public Builder addPath(
int index, Common.PBPath.Builder bdForValue) {
ensurePathIsMutable();
path_.add(index, bdForValue.build());
return this;
}
public Builder addAllPath(
Iterable<? extends Common.PBPath> values) {
ensurePathIsMutable();
AbstractMessageLite.Builder.addAll(
values, path_);
return this;
}
public Builder clearPath() {
path_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removePath(int index) {
ensurePathIsMutable();
path_.remove(index);
return this;
}
private List<PathStatus.PBPathStatus> status_ =
Collections.emptyList();
private void ensureStatusIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
status_ = new ArrayList<PathStatus.PBPathStatus>(status_);
b0_ |= 0x00000002;
}
}
public List<PathStatus.PBPathStatus> getStatusList() {
return Collections.unmodifiableList(status_);
}
public int getStatusCount() {
return status_.size();
}
public PathStatus.PBPathStatus getStatus(int index) {
return status_.get(index);
}
public Builder setStatus(
int index, PathStatus.PBPathStatus value) {
if (value == null) {
throw new NullPointerException();
}
ensureStatusIsMutable();
status_.set(index, value);
return this;
}
public Builder setStatus(
int index, PathStatus.PBPathStatus.Builder bdForValue) {
ensureStatusIsMutable();
status_.set(index, bdForValue.build());
return this;
}
public Builder addStatus(PathStatus.PBPathStatus value) {
if (value == null) {
throw new NullPointerException();
}
ensureStatusIsMutable();
status_.add(value);
return this;
}
public Builder addStatus(
int index, PathStatus.PBPathStatus value) {
if (value == null) {
throw new NullPointerException();
}
ensureStatusIsMutable();
status_.add(index, value);
return this;
}
public Builder addStatus(
PathStatus.PBPathStatus.Builder bdForValue) {
ensureStatusIsMutable();
status_.add(bdForValue.build());
return this;
}
public Builder addStatus(
int index, PathStatus.PBPathStatus.Builder bdForValue) {
ensureStatusIsMutable();
status_.add(index, bdForValue.build());
return this;
}
public Builder addAllStatus(
Iterable<? extends PathStatus.PBPathStatus> values) {
ensureStatusIsMutable();
AbstractMessageLite.Builder.addAll(
values, status_);
return this;
}
public Builder clearStatus() {
status_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder removeStatus(int index) {
ensureStatusIsMutable();
status_.remove(index);
return this;
}
}
static {
defaultInstance = new PBPathStatusEvent(true);
defaultInstance.initFields();
}
}
public interface PBIndexingProgressOrBuilder extends
MessageLiteOrBuilder {
boolean hasFiles();
int getFiles();
boolean hasFolders();
int getFolders();
}
public static final class PBIndexingProgress extends
GeneratedMessageLite implements
PBIndexingProgressOrBuilder {
private PBIndexingProgress(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBIndexingProgress(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBIndexingProgress defaultInstance;
public static PBIndexingProgress getDefaultInstance() {
return defaultInstance;
}
public PBIndexingProgress getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBIndexingProgress(
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
files_ = input.readInt32();
break;
}
case 16: {
b0_ |= 0x00000002;
folders_ = input.readInt32();
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
public static Parser<PBIndexingProgress> PARSER =
new AbstractParser<PBIndexingProgress>() {
public PBIndexingProgress parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBIndexingProgress(input, er);
}
};
@Override
public Parser<PBIndexingProgress> getParserForType() {
return PARSER;
}
private int b0_;
public static final int FILES_FIELD_NUMBER = 1;
private int files_;
public boolean hasFiles() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getFiles() {
return files_;
}
public static final int FOLDERS_FIELD_NUMBER = 2;
private int folders_;
public boolean hasFolders() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getFolders() {
return folders_;
}
private void initFields() {
files_ = 0;
folders_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasFiles()) {
mii = 0;
return false;
}
if (!hasFolders()) {
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
output.writeInt32(1, files_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeInt32(2, folders_);
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
.computeInt32Size(1, files_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeInt32Size(2, folders_);
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
public static RitualNotifications.PBIndexingProgress parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBIndexingProgress parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBIndexingProgress parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBIndexingProgress parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBIndexingProgress parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBIndexingProgress parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static RitualNotifications.PBIndexingProgress parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static RitualNotifications.PBIndexingProgress parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static RitualNotifications.PBIndexingProgress parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBIndexingProgress parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(RitualNotifications.PBIndexingProgress prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
RitualNotifications.PBIndexingProgress, Builder>
implements
RitualNotifications.PBIndexingProgressOrBuilder {
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
files_ = 0;
b0_ = (b0_ & ~0x00000001);
folders_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public RitualNotifications.PBIndexingProgress getDefaultInstanceForType() {
return RitualNotifications.PBIndexingProgress.getDefaultInstance();
}
public RitualNotifications.PBIndexingProgress build() {
RitualNotifications.PBIndexingProgress result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public RitualNotifications.PBIndexingProgress buildPartial() {
RitualNotifications.PBIndexingProgress result = new RitualNotifications.PBIndexingProgress(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.files_ = files_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.folders_ = folders_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(RitualNotifications.PBIndexingProgress other) {
if (other == RitualNotifications.PBIndexingProgress.getDefaultInstance()) return this;
if (other.hasFiles()) {
setFiles(other.getFiles());
}
if (other.hasFolders()) {
setFolders(other.getFolders());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasFiles()) {
return false;
}
if (!hasFolders()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
RitualNotifications.PBIndexingProgress pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (RitualNotifications.PBIndexingProgress) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int files_ ;
public boolean hasFiles() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getFiles() {
return files_;
}
public Builder setFiles(int value) {
b0_ |= 0x00000001;
files_ = value;
return this;
}
public Builder clearFiles() {
b0_ = (b0_ & ~0x00000001);
files_ = 0;
return this;
}
private int folders_ ;
public boolean hasFolders() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getFolders() {
return folders_;
}
public Builder setFolders(int value) {
b0_ |= 0x00000002;
folders_ = value;
return this;
}
public Builder clearFolders() {
b0_ = (b0_ & ~0x00000002);
folders_ = 0;
return this;
}
}
static {
defaultInstance = new PBIndexingProgress(true);
defaultInstance.initFields();
}
}
public interface PBSOCIDOrBuilder extends
MessageLiteOrBuilder {
boolean hasSidx();
int getSidx();
boolean hasOid();
ByteString getOid();
boolean hasCid();
int getCid();
}
public static final class PBSOCID extends
GeneratedMessageLite implements
PBSOCIDOrBuilder {
private PBSOCID(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBSOCID(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBSOCID defaultInstance;
public static PBSOCID getDefaultInstance() {
return defaultInstance;
}
public PBSOCID getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBSOCID(
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
sidx_ = input.readInt32();
break;
}
case 18: {
b0_ |= 0x00000002;
oid_ = input.readBytes();
break;
}
case 24: {
b0_ |= 0x00000004;
cid_ = input.readInt32();
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
public static Parser<PBSOCID> PARSER =
new AbstractParser<PBSOCID>() {
public PBSOCID parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBSOCID(input, er);
}
};
@Override
public Parser<PBSOCID> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SIDX_FIELD_NUMBER = 1;
private int sidx_;
public boolean hasSidx() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getSidx() {
return sidx_;
}
public static final int OID_FIELD_NUMBER = 2;
private ByteString oid_;
public boolean hasOid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getOid() {
return oid_;
}
public static final int CID_FIELD_NUMBER = 3;
private int cid_;
public boolean hasCid() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public int getCid() {
return cid_;
}
private void initFields() {
sidx_ = 0;
oid_ = ByteString.EMPTY;
cid_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasSidx()) {
mii = 0;
return false;
}
if (!hasOid()) {
mii = 0;
return false;
}
if (!hasCid()) {
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
output.writeInt32(1, sidx_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, oid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeInt32(3, cid_);
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
.computeInt32Size(1, sidx_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, oid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeInt32Size(3, cid_);
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
public static RitualNotifications.PBSOCID parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBSOCID parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBSOCID parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RitualNotifications.PBSOCID parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RitualNotifications.PBSOCID parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBSOCID parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static RitualNotifications.PBSOCID parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static RitualNotifications.PBSOCID parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static RitualNotifications.PBSOCID parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RitualNotifications.PBSOCID parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(RitualNotifications.PBSOCID prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
RitualNotifications.PBSOCID, Builder>
implements
RitualNotifications.PBSOCIDOrBuilder {
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
sidx_ = 0;
b0_ = (b0_ & ~0x00000001);
oid_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
cid_ = 0;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public RitualNotifications.PBSOCID getDefaultInstanceForType() {
return RitualNotifications.PBSOCID.getDefaultInstance();
}
public RitualNotifications.PBSOCID build() {
RitualNotifications.PBSOCID result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public RitualNotifications.PBSOCID buildPartial() {
RitualNotifications.PBSOCID result = new RitualNotifications.PBSOCID(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sidx_ = sidx_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.oid_ = oid_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.cid_ = cid_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(RitualNotifications.PBSOCID other) {
if (other == RitualNotifications.PBSOCID.getDefaultInstance()) return this;
if (other.hasSidx()) {
setSidx(other.getSidx());
}
if (other.hasOid()) {
setOid(other.getOid());
}
if (other.hasCid()) {
setCid(other.getCid());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSidx()) {
return false;
}
if (!hasOid()) {
return false;
}
if (!hasCid()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
RitualNotifications.PBSOCID pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (RitualNotifications.PBSOCID) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int sidx_ ;
public boolean hasSidx() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getSidx() {
return sidx_;
}
public Builder setSidx(int value) {
b0_ |= 0x00000001;
sidx_ = value;
return this;
}
public Builder clearSidx() {
b0_ = (b0_ & ~0x00000001);
sidx_ = 0;
return this;
}
private ByteString oid_ = ByteString.EMPTY;
public boolean hasOid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getOid() {
return oid_;
}
public Builder setOid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
oid_ = value;
return this;
}
public Builder clearOid() {
b0_ = (b0_ & ~0x00000002);
oid_ = getDefaultInstance().getOid();
return this;
}
private int cid_ ;
public boolean hasCid() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public int getCid() {
return cid_;
}
public Builder setCid(int value) {
b0_ |= 0x00000004;
cid_ = value;
return this;
}
public Builder clearCid() {
b0_ = (b0_ & ~0x00000004);
cid_ = 0;
return this;
}
}
static {
defaultInstance = new PBSOCID(true);
defaultInstance.initFields();
}
}
static {
}
}
