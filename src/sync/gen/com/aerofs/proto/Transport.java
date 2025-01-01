package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Transport {
private Transport() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public interface PBTPHeaderOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Transport.PBTPHeader.Type getType();
boolean hasStream();
Transport.PBStream getStream();
boolean hasMcastId();
int getMcastId();
boolean hasHeartbeat();
Transport.PBHeartbeat getHeartbeat();
}
public static final class PBTPHeader extends
GeneratedMessageLite implements
PBTPHeaderOrBuilder {
private PBTPHeader(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBTPHeader(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBTPHeader defaultInstance;
public static PBTPHeader getDefaultInstance() {
return defaultInstance;
}
public PBTPHeader getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBTPHeader(
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
Transport.PBTPHeader.Type value = Transport.PBTPHeader.Type.valueOf(rawValue);
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
Transport.PBStream.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = stream_.toBuilder();
}
stream_ = input.readMessage(Transport.PBStream.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(stream_);
stream_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
break;
}
case 24: {
b0_ |= 0x00000004;
mcastId_ = input.readUInt32();
break;
}
case 34: {
Transport.PBHeartbeat.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = heartbeat_.toBuilder();
}
heartbeat_ = input.readMessage(Transport.PBHeartbeat.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(heartbeat_);
heartbeat_ = subBuilder.buildPartial();
}
b0_ |= 0x00000008;
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
public static Parser<PBTPHeader> PARSER =
new AbstractParser<PBTPHeader>() {
public PBTPHeader parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBTPHeader(input, er);
}
};
@Override
public Parser<PBTPHeader> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
DATAGRAM(0, 0),
STREAM(1, 1),
HEARTBEAT_CALL(2, 2),
HEARTBEAT_REPLY(3, 3),
;
public static final int DATAGRAM_VALUE = 0;
public static final int STREAM_VALUE = 1;
public static final int HEARTBEAT_CALL_VALUE = 2;
public static final int HEARTBEAT_REPLY_VALUE = 3;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return DATAGRAM;
case 1: return STREAM;
case 2: return HEARTBEAT_CALL;
case 3: return HEARTBEAT_REPLY;
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
private Transport.PBTPHeader.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Transport.PBTPHeader.Type getType() {
return type_;
}
public static final int STREAM_FIELD_NUMBER = 2;
private Transport.PBStream stream_;
public boolean hasStream() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Transport.PBStream getStream() {
return stream_;
}
public static final int MCAST_ID_FIELD_NUMBER = 3;
private int mcastId_;
public boolean hasMcastId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public int getMcastId() {
return mcastId_;
}
public static final int HEARTBEAT_FIELD_NUMBER = 4;
private Transport.PBHeartbeat heartbeat_;
public boolean hasHeartbeat() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Transport.PBHeartbeat getHeartbeat() {
return heartbeat_;
}
private void initFields() {
type_ = Transport.PBTPHeader.Type.DATAGRAM;
stream_ = Transport.PBStream.getDefaultInstance();
mcastId_ = 0;
heartbeat_ = Transport.PBHeartbeat.getDefaultInstance();
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
if (hasStream()) {
if (!getStream().isInitialized()) {
mii = 0;
return false;
}
}
if (hasHeartbeat()) {
if (!getHeartbeat().isInitialized()) {
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
output.writeMessage(2, stream_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt32(3, mcastId_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, heartbeat_);
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
.computeMessageSize(2, stream_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt32Size(3, mcastId_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeMessageSize(4, heartbeat_);
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
public static Transport.PBTPHeader parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Transport.PBTPHeader parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Transport.PBTPHeader parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Transport.PBTPHeader parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Transport.PBTPHeader parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Transport.PBTPHeader parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Transport.PBTPHeader parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Transport.PBTPHeader parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Transport.PBTPHeader parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Transport.PBTPHeader parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Transport.PBTPHeader prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Transport.PBTPHeader, Builder>
implements
Transport.PBTPHeaderOrBuilder {
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
type_ = Transport.PBTPHeader.Type.DATAGRAM;
b0_ = (b0_ & ~0x00000001);
stream_ = Transport.PBStream.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
mcastId_ = 0;
b0_ = (b0_ & ~0x00000004);
heartbeat_ = Transport.PBHeartbeat.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Transport.PBTPHeader getDefaultInstanceForType() {
return Transport.PBTPHeader.getDefaultInstance();
}
public Transport.PBTPHeader build() {
Transport.PBTPHeader result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Transport.PBTPHeader buildPartial() {
Transport.PBTPHeader result = new Transport.PBTPHeader(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.stream_ = stream_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.mcastId_ = mcastId_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.heartbeat_ = heartbeat_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Transport.PBTPHeader other) {
if (other == Transport.PBTPHeader.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasStream()) {
mergeStream(other.getStream());
}
if (other.hasMcastId()) {
setMcastId(other.getMcastId());
}
if (other.hasHeartbeat()) {
mergeHeartbeat(other.getHeartbeat());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (hasStream()) {
if (!getStream().isInitialized()) {
return false;
}
}
if (hasHeartbeat()) {
if (!getHeartbeat().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Transport.PBTPHeader pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Transport.PBTPHeader) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Transport.PBTPHeader.Type type_ = Transport.PBTPHeader.Type.DATAGRAM;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Transport.PBTPHeader.Type getType() {
return type_;
}
public Builder setType(Transport.PBTPHeader.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Transport.PBTPHeader.Type.DATAGRAM;
return this;
}
private Transport.PBStream stream_ = Transport.PBStream.getDefaultInstance();
public boolean hasStream() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Transport.PBStream getStream() {
return stream_;
}
public Builder setStream(Transport.PBStream value) {
if (value == null) {
throw new NullPointerException();
}
stream_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setStream(
Transport.PBStream.Builder bdForValue) {
stream_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeStream(Transport.PBStream value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
stream_ != Transport.PBStream.getDefaultInstance()) {
stream_ =
Transport.PBStream.newBuilder(stream_).mergeFrom(value).buildPartial();
} else {
stream_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearStream() {
stream_ = Transport.PBStream.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
private int mcastId_ ;
public boolean hasMcastId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public int getMcastId() {
return mcastId_;
}
public Builder setMcastId(int value) {
b0_ |= 0x00000004;
mcastId_ = value;
return this;
}
public Builder clearMcastId() {
b0_ = (b0_ & ~0x00000004);
mcastId_ = 0;
return this;
}
private Transport.PBHeartbeat heartbeat_ = Transport.PBHeartbeat.getDefaultInstance();
public boolean hasHeartbeat() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Transport.PBHeartbeat getHeartbeat() {
return heartbeat_;
}
public Builder setHeartbeat(Transport.PBHeartbeat value) {
if (value == null) {
throw new NullPointerException();
}
heartbeat_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setHeartbeat(
Transport.PBHeartbeat.Builder bdForValue) {
heartbeat_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergeHeartbeat(Transport.PBHeartbeat value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
heartbeat_ != Transport.PBHeartbeat.getDefaultInstance()) {
heartbeat_ =
Transport.PBHeartbeat.newBuilder(heartbeat_).mergeFrom(value).buildPartial();
} else {
heartbeat_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearHeartbeat() {
heartbeat_ = Transport.PBHeartbeat.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
}
static {
defaultInstance = new PBTPHeader(true);
defaultInstance.initFields();
}
}
public interface PBStreamOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Transport.PBStream.Type getType();
boolean hasStreamId();
int getStreamId();
boolean hasSeqNum();
int getSeqNum();
boolean hasReason();
Transport.PBStream.InvalidationReason getReason();
}
public static final class PBStream extends
GeneratedMessageLite implements
PBStreamOrBuilder {
private PBStream(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBStream(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBStream defaultInstance;
public static PBStream getDefaultInstance() {
return defaultInstance;
}
public PBStream getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBStream(
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
Transport.PBStream.Type value = Transport.PBStream.Type.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
b0_ |= 0x00000001;
type_ = value;
}
break;
}
case 16: {
b0_ |= 0x00000002;
streamId_ = input.readUInt32();
break;
}
case 24: {
b0_ |= 0x00000004;
seqNum_ = input.readUInt32();
break;
}
case 32: {
int rawValue = input.readEnum();
Transport.PBStream.InvalidationReason value = Transport.PBStream.InvalidationReason.valueOf(rawValue);
if (value == null) {
unknownFieldsCodedOutput.writeRawVarint32(tag);
unknownFieldsCodedOutput.writeRawVarint32(rawValue);
} else {
b0_ |= 0x00000008;
reason_ = value;
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
public static Parser<PBStream> PARSER =
new AbstractParser<PBStream>() {
public PBStream parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBStream(input, er);
}
};
@Override
public Parser<PBStream> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
PAYLOAD(0, 0),
BEGIN_STREAM(1, 1),
TX_ABORT_STREAM(2, 2),
RX_ABORT_STREAM(3, 3),
PAUSE_STREAM(4, 4),
RESUME_STREAM(5, 5),
;
public static final int PAYLOAD_VALUE = 0;
public static final int BEGIN_STREAM_VALUE = 1;
public static final int TX_ABORT_STREAM_VALUE = 2;
public static final int RX_ABORT_STREAM_VALUE = 3;
public static final int PAUSE_STREAM_VALUE = 4;
public static final int RESUME_STREAM_VALUE = 5;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return PAYLOAD;
case 1: return BEGIN_STREAM;
case 2: return TX_ABORT_STREAM;
case 3: return RX_ABORT_STREAM;
case 4: return PAUSE_STREAM;
case 5: return RESUME_STREAM;
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
public enum InvalidationReason
implements Internal.EnumLite {
UPDATE_IN_PROGRESS(0, 0),
ENDED(1, 1),
STREAM_NOT_FOUND(2, 2),
INTERNAL_ERROR(3, 3),
CHOKE_ERROR(4, 5),
OUT_OF_ORDER(5, 6),
STORE_NOT_FOUND(6, 7),
;
public static final int UPDATE_IN_PROGRESS_VALUE = 0;
public static final int ENDED_VALUE = 1;
public static final int STREAM_NOT_FOUND_VALUE = 2;
public static final int INTERNAL_ERROR_VALUE = 3;
public static final int CHOKE_ERROR_VALUE = 5;
public static final int OUT_OF_ORDER_VALUE = 6;
public static final int STORE_NOT_FOUND_VALUE = 7;
public final int getNumber() { return value; }
public static InvalidationReason valueOf(int value) {
switch (value) {
case 0: return UPDATE_IN_PROGRESS;
case 1: return ENDED;
case 2: return STREAM_NOT_FOUND;
case 3: return INTERNAL_ERROR;
case 5: return CHOKE_ERROR;
case 6: return OUT_OF_ORDER;
case 7: return STORE_NOT_FOUND;
default: return null;
}
}
public static Internal.EnumLiteMap<InvalidationReason>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<InvalidationReason>
internalValueMap =
new Internal.EnumLiteMap<InvalidationReason>() {
public InvalidationReason findValueByNumber(int number) {
return InvalidationReason.valueOf(number);
}
};
private final int value;
private InvalidationReason(int index, int value) {
this.value = value;
}
}
private int b0_;
public static final int TYPE_FIELD_NUMBER = 1;
private Transport.PBStream.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Transport.PBStream.Type getType() {
return type_;
}
public static final int STREAM_ID_FIELD_NUMBER = 2;
private int streamId_;
public boolean hasStreamId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getStreamId() {
return streamId_;
}
public static final int SEQ_NUM_FIELD_NUMBER = 3;
private int seqNum_;
public boolean hasSeqNum() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public int getSeqNum() {
return seqNum_;
}
public static final int REASON_FIELD_NUMBER = 4;
private Transport.PBStream.InvalidationReason reason_;
public boolean hasReason() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Transport.PBStream.InvalidationReason getReason() {
return reason_;
}
private void initFields() {
type_ = Transport.PBStream.Type.PAYLOAD;
streamId_ = 0;
seqNum_ = 0;
reason_ = Transport.PBStream.InvalidationReason.UPDATE_IN_PROGRESS;
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
if (!hasStreamId()) {
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
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt32(2, streamId_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt32(3, seqNum_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeEnum(4, reason_.getNumber());
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
.computeUInt32Size(2, streamId_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt32Size(3, seqNum_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeEnumSize(4, reason_.getNumber());
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
public static Transport.PBStream parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Transport.PBStream parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Transport.PBStream parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Transport.PBStream parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Transport.PBStream parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Transport.PBStream parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Transport.PBStream parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Transport.PBStream parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Transport.PBStream parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Transport.PBStream parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Transport.PBStream prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Transport.PBStream, Builder>
implements
Transport.PBStreamOrBuilder {
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
type_ = Transport.PBStream.Type.PAYLOAD;
b0_ = (b0_ & ~0x00000001);
streamId_ = 0;
b0_ = (b0_ & ~0x00000002);
seqNum_ = 0;
b0_ = (b0_ & ~0x00000004);
reason_ = Transport.PBStream.InvalidationReason.UPDATE_IN_PROGRESS;
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Transport.PBStream getDefaultInstanceForType() {
return Transport.PBStream.getDefaultInstance();
}
public Transport.PBStream build() {
Transport.PBStream result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Transport.PBStream buildPartial() {
Transport.PBStream result = new Transport.PBStream(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.streamId_ = streamId_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.seqNum_ = seqNum_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.reason_ = reason_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Transport.PBStream other) {
if (other == Transport.PBStream.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasStreamId()) {
setStreamId(other.getStreamId());
}
if (other.hasSeqNum()) {
setSeqNum(other.getSeqNum());
}
if (other.hasReason()) {
setReason(other.getReason());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (!hasStreamId()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Transport.PBStream pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Transport.PBStream) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Transport.PBStream.Type type_ = Transport.PBStream.Type.PAYLOAD;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Transport.PBStream.Type getType() {
return type_;
}
public Builder setType(Transport.PBStream.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Transport.PBStream.Type.PAYLOAD;
return this;
}
private int streamId_ ;
public boolean hasStreamId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getStreamId() {
return streamId_;
}
public Builder setStreamId(int value) {
b0_ |= 0x00000002;
streamId_ = value;
return this;
}
public Builder clearStreamId() {
b0_ = (b0_ & ~0x00000002);
streamId_ = 0;
return this;
}
private int seqNum_ ;
public boolean hasSeqNum() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public int getSeqNum() {
return seqNum_;
}
public Builder setSeqNum(int value) {
b0_ |= 0x00000004;
seqNum_ = value;
return this;
}
public Builder clearSeqNum() {
b0_ = (b0_ & ~0x00000004);
seqNum_ = 0;
return this;
}
private Transport.PBStream.InvalidationReason reason_ = Transport.PBStream.InvalidationReason.UPDATE_IN_PROGRESS;
public boolean hasReason() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Transport.PBStream.InvalidationReason getReason() {
return reason_;
}
public Builder setReason(Transport.PBStream.InvalidationReason value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
reason_ = value;
return this;
}
public Builder clearReason() {
b0_ = (b0_ & ~0x00000008);
reason_ = Transport.PBStream.InvalidationReason.UPDATE_IN_PROGRESS;
return this;
}
}
static {
defaultInstance = new PBStream(true);
defaultInstance.initFields();
}
}
public interface PBHeartbeatOrBuilder extends
MessageLiteOrBuilder {
boolean hasHeartbeatId();
int getHeartbeatId();
boolean hasSentTime();
long getSentTime();
}
public static final class PBHeartbeat extends
GeneratedMessageLite implements
PBHeartbeatOrBuilder {
private PBHeartbeat(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBHeartbeat(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBHeartbeat defaultInstance;
public static PBHeartbeat getDefaultInstance() {
return defaultInstance;
}
public PBHeartbeat getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBHeartbeat(
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
heartbeatId_ = input.readInt32();
break;
}
case 16: {
b0_ |= 0x00000002;
sentTime_ = input.readUInt64();
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
public static Parser<PBHeartbeat> PARSER =
new AbstractParser<PBHeartbeat>() {
public PBHeartbeat parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBHeartbeat(input, er);
}
};
@Override
public Parser<PBHeartbeat> getParserForType() {
return PARSER;
}
private int b0_;
public static final int HEARTBEAT_ID_FIELD_NUMBER = 1;
private int heartbeatId_;
public boolean hasHeartbeatId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getHeartbeatId() {
return heartbeatId_;
}
public static final int SENT_TIME_FIELD_NUMBER = 2;
private long sentTime_;
public boolean hasSentTime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getSentTime() {
return sentTime_;
}
private void initFields() {
heartbeatId_ = 0;
sentTime_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasHeartbeatId()) {
mii = 0;
return false;
}
if (!hasSentTime()) {
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
output.writeInt32(1, heartbeatId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, sentTime_);
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
.computeInt32Size(1, heartbeatId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, sentTime_);
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
public static Transport.PBHeartbeat parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Transport.PBHeartbeat parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Transport.PBHeartbeat parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Transport.PBHeartbeat parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Transport.PBHeartbeat parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Transport.PBHeartbeat parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Transport.PBHeartbeat parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Transport.PBHeartbeat parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Transport.PBHeartbeat parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Transport.PBHeartbeat parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Transport.PBHeartbeat prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Transport.PBHeartbeat, Builder>
implements
Transport.PBHeartbeatOrBuilder {
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
heartbeatId_ = 0;
b0_ = (b0_ & ~0x00000001);
sentTime_ = 0L;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Transport.PBHeartbeat getDefaultInstanceForType() {
return Transport.PBHeartbeat.getDefaultInstance();
}
public Transport.PBHeartbeat build() {
Transport.PBHeartbeat result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Transport.PBHeartbeat buildPartial() {
Transport.PBHeartbeat result = new Transport.PBHeartbeat(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.heartbeatId_ = heartbeatId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.sentTime_ = sentTime_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Transport.PBHeartbeat other) {
if (other == Transport.PBHeartbeat.getDefaultInstance()) return this;
if (other.hasHeartbeatId()) {
setHeartbeatId(other.getHeartbeatId());
}
if (other.hasSentTime()) {
setSentTime(other.getSentTime());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasHeartbeatId()) {
return false;
}
if (!hasSentTime()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Transport.PBHeartbeat pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Transport.PBHeartbeat) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int heartbeatId_ ;
public boolean hasHeartbeatId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getHeartbeatId() {
return heartbeatId_;
}
public Builder setHeartbeatId(int value) {
b0_ |= 0x00000001;
heartbeatId_ = value;
return this;
}
public Builder clearHeartbeatId() {
b0_ = (b0_ & ~0x00000001);
heartbeatId_ = 0;
return this;
}
private long sentTime_ ;
public boolean hasSentTime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getSentTime() {
return sentTime_;
}
public Builder setSentTime(long value) {
b0_ |= 0x00000002;
sentTime_ = value;
return this;
}
public Builder clearSentTime() {
b0_ = (b0_ & ~0x00000002);
sentTime_ = 0L;
return this;
}
}
static {
defaultInstance = new PBHeartbeat(true);
defaultInstance.initFields();
}
}
static {
}
}
