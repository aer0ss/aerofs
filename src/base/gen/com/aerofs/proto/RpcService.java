package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class RpcService {
private RpcService() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public interface PayloadOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
int getType();
boolean hasPayloadData();
ByteString getPayloadData();
}
public static final class Payload extends
GeneratedMessageLite implements
PayloadOrBuilder {
private Payload(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Payload(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final Payload defaultInstance;
public static Payload getDefaultInstance() {
return defaultInstance;
}
public Payload getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private Payload(
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
type_ = input.readInt32();
break;
}
case 82: {
b0_ |= 0x00000002;
payloadData_ = input.readBytes();
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
public static Parser<Payload> PARSER =
new AbstractParser<Payload>() {
public Payload parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Payload(input, er);
}
};
@Override
public Parser<Payload> getParserForType() {
return PARSER;
}
private int b0_;
public static final int TYPE_FIELD_NUMBER = 1;
private int type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getType() {
return type_;
}
public static final int PAYLOAD_DATA_FIELD_NUMBER = 10;
private ByteString payloadData_;
public boolean hasPayloadData() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getPayloadData() {
return payloadData_;
}
private void initFields() {
type_ = 0;
payloadData_ = ByteString.EMPTY;
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
if (!hasPayloadData()) {
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
output.writeInt32(1, type_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(10, payloadData_);
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
.computeInt32Size(1, type_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(10, payloadData_);
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
public static RpcService.Payload parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RpcService.Payload parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RpcService.Payload parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static RpcService.Payload parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static RpcService.Payload parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RpcService.Payload parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static RpcService.Payload parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static RpcService.Payload parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static RpcService.Payload parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static RpcService.Payload parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(RpcService.Payload prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
RpcService.Payload, Builder>
implements
RpcService.PayloadOrBuilder {
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
type_ = 0;
b0_ = (b0_ & ~0x00000001);
payloadData_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public RpcService.Payload getDefaultInstanceForType() {
return RpcService.Payload.getDefaultInstance();
}
public RpcService.Payload build() {
RpcService.Payload result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public RpcService.Payload buildPartial() {
RpcService.Payload result = new RpcService.Payload(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.payloadData_ = payloadData_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(RpcService.Payload other) {
if (other == RpcService.Payload.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasPayloadData()) {
setPayloadData(other.getPayloadData());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (!hasPayloadData()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
RpcService.Payload pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (RpcService.Payload) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int type_ ;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getType() {
return type_;
}
public Builder setType(int value) {
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = 0;
return this;
}
private ByteString payloadData_ = ByteString.EMPTY;
public boolean hasPayloadData() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getPayloadData() {
return payloadData_;
}
public Builder setPayloadData(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
payloadData_ = value;
return this;
}
public Builder clearPayloadData() {
b0_ = (b0_ & ~0x00000002);
payloadData_ = getDefaultInstance().getPayloadData();
return this;
}
}
static {
defaultInstance = new Payload(true);
defaultInstance.initFields();
}
}
static {
}
}
