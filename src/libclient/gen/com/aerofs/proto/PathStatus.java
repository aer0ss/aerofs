package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class PathStatus {
private PathStatus() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public interface PBPathStatusOrBuilder extends
MessageLiteOrBuilder {
boolean hasSync();
PathStatus.PBPathStatus.Sync getSync();
boolean hasFlags();
int getFlags();
}
public static final class PBPathStatus extends
GeneratedMessageLite implements
PBPathStatusOrBuilder {
private PBPathStatus(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBPathStatus(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBPathStatus defaultInstance;
public static PBPathStatus getDefaultInstance() {
return defaultInstance;
}
public PBPathStatus getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBPathStatus(
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
PathStatus.PBPathStatus.Sync value = PathStatus.PBPathStatus.Sync.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
b0_ |= 0x00000001;
sync_ = value;
}
break;
}
case 16: {
b0_ |= 0x00000002;
flags_ = input.readInt32();
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
public static Parser<PBPathStatus> PARSER =
new AbstractParser<PBPathStatus>() {
public PBPathStatus parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBPathStatus(input, er);
}
};
@Override
public Parser<PBPathStatus> getParserForType() {
return PARSER;
}
public enum Sync
implements Internal.EnumLite {
UNKNOWN(0, 0),
OUT_SYNC(1, 1),
PARTIAL_SYNC(2, 2),
IN_SYNC(3, 3),
;
public static final int UNKNOWN_VALUE = 0;
public static final int OUT_SYNC_VALUE = 1;
public static final int PARTIAL_SYNC_VALUE = 2;
public static final int IN_SYNC_VALUE = 3;
public final int getNumber() { return value; }
public static Sync valueOf(int value) {
switch (value) {
case 0: return UNKNOWN;
case 1: return OUT_SYNC;
case 2: return PARTIAL_SYNC;
case 3: return IN_SYNC;
default: return null;
}
}
public static Internal.EnumLiteMap<Sync>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<Sync>
internalValueMap =
new Internal.EnumLiteMap<Sync>() {
public Sync findValueByNumber(int number) {
return Sync.valueOf(number);
}
};
private final int value;
private Sync(int index, int value) {
this.value = value;
}
}
public enum Flag
implements Internal.EnumLite {
DOWNLOADING(0, 1),
UPLOADING(1, 2),
CONFLICT(2, 4),
;
public static final int DOWNLOADING_VALUE = 1;
public static final int UPLOADING_VALUE = 2;
public static final int CONFLICT_VALUE = 4;
public final int getNumber() { return value; }
public static Flag valueOf(int value) {
switch (value) {
case 1: return DOWNLOADING;
case 2: return UPLOADING;
case 4: return CONFLICT;
default: return null;
}
}
public static Internal.EnumLiteMap<Flag>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<Flag>
internalValueMap =
new Internal.EnumLiteMap<Flag>() {
public Flag findValueByNumber(int number) {
return Flag.valueOf(number);
}
};
private final int value;
private Flag(int index, int value) {
this.value = value;
}
}
private int b0_;
public static final int SYNC_FIELD_NUMBER = 1;
private PathStatus.PBPathStatus.Sync sync_;
public boolean hasSync() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public PathStatus.PBPathStatus.Sync getSync() {
return sync_;
}
public static final int FLAGS_FIELD_NUMBER = 2;
private int flags_;
public boolean hasFlags() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getFlags() {
return flags_;
}
private void initFields() {
sync_ = PathStatus.PBPathStatus.Sync.UNKNOWN;
flags_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasSync()) {
mii = 0;
return false;
}
if (!hasFlags()) {
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
output.writeEnum(1, sync_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeInt32(2, flags_);
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
.computeEnumSize(1, sync_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeInt32Size(2, flags_);
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
public static PathStatus.PBPathStatus parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static PathStatus.PBPathStatus parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static PathStatus.PBPathStatus parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static PathStatus.PBPathStatus parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static PathStatus.PBPathStatus parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static PathStatus.PBPathStatus parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static PathStatus.PBPathStatus parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static PathStatus.PBPathStatus parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static PathStatus.PBPathStatus parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static PathStatus.PBPathStatus parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(PathStatus.PBPathStatus prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
PathStatus.PBPathStatus, Builder>
implements
PathStatus.PBPathStatusOrBuilder {
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
sync_ = PathStatus.PBPathStatus.Sync.UNKNOWN;
b0_ = (b0_ & ~0x00000001);
flags_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public PathStatus.PBPathStatus getDefaultInstanceForType() {
return PathStatus.PBPathStatus.getDefaultInstance();
}
public PathStatus.PBPathStatus build() {
PathStatus.PBPathStatus result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public PathStatus.PBPathStatus buildPartial() {
PathStatus.PBPathStatus result = new PathStatus.PBPathStatus(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sync_ = sync_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.flags_ = flags_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(PathStatus.PBPathStatus other) {
if (other == PathStatus.PBPathStatus.getDefaultInstance()) return this;
if (other.hasSync()) {
setSync(other.getSync());
}
if (other.hasFlags()) {
setFlags(other.getFlags());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSync()) {
return false;
}
if (!hasFlags()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
PathStatus.PBPathStatus pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (PathStatus.PBPathStatus) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private PathStatus.PBPathStatus.Sync sync_ = PathStatus.PBPathStatus.Sync.UNKNOWN;
public boolean hasSync() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public PathStatus.PBPathStatus.Sync getSync() {
return sync_;
}
public Builder setSync(PathStatus.PBPathStatus.Sync value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
sync_ = value;
return this;
}
public Builder clearSync() {
b0_ = (b0_ & ~0x00000001);
sync_ = PathStatus.PBPathStatus.Sync.UNKNOWN;
return this;
}
private int flags_ ;
public boolean hasFlags() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getFlags() {
return flags_;
}
public Builder setFlags(int value) {
b0_ |= 0x00000002;
flags_ = value;
return this;
}
public Builder clearFlags() {
b0_ = (b0_ & ~0x00000002);
flags_ = 0;
return this;
}
}
static {
defaultInstance = new PBPathStatus(true);
defaultInstance.initFields();
}
}
static {
}
}
