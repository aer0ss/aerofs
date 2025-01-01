package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Core {
private Core() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public interface PBCoreOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Core.PBCore.Type getType();
boolean hasRpcid();
int getRpcid();
boolean hasNewUpdates();
Core.PBNewUpdates getNewUpdates();
boolean hasUpdateSenderFilter();
Core.PBUpdateSenderFilter getUpdateSenderFilter();
boolean hasExceptionResponse();
Common.PBException getExceptionResponse();
boolean hasGetContentRequest();
Core.PBGetContentRequest getGetContentRequest();
boolean hasGetContentResponse();
Core.PBGetContentResponse getGetContentResponse();
boolean hasGetFilterRequest();
Core.PBGetFilterRequest getGetFilterRequest();
boolean hasGetFilterResponse();
Core.PBGetFilterResponse getGetFilterResponse();
}
public static final class PBCore extends
GeneratedMessageLite implements
PBCoreOrBuilder {
private PBCore(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBCore(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBCore defaultInstance;
public static PBCore getDefaultInstance() {
return defaultInstance;
}
public PBCore getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBCore(
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
Core.PBCore.Type value = Core.PBCore.Type.valueOf(rawValue);
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
rpcid_ = input.readInt32();
break;
}
case 26: {
Core.PBNewUpdates.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = newUpdates_.toBuilder();
}
newUpdates_ = input.readMessage(Core.PBNewUpdates.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(newUpdates_);
newUpdates_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
break;
}
case 34: {
Core.PBUpdateSenderFilter.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = updateSenderFilter_.toBuilder();
}
updateSenderFilter_ = input.readMessage(Core.PBUpdateSenderFilter.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(updateSenderFilter_);
updateSenderFilter_ = subBuilder.buildPartial();
}
b0_ |= 0x00000008;
break;
}
case 42: {
Common.PBException.Builder subBuilder = null;
if (((b0_ & 0x00000010) == 0x00000010)) {
subBuilder = exceptionResponse_.toBuilder();
}
exceptionResponse_ = input.readMessage(Common.PBException.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(exceptionResponse_);
exceptionResponse_ = subBuilder.buildPartial();
}
b0_ |= 0x00000010;
break;
}
case 50: {
Core.PBGetContentRequest.Builder subBuilder = null;
if (((b0_ & 0x00000020) == 0x00000020)) {
subBuilder = getContentRequest_.toBuilder();
}
getContentRequest_ = input.readMessage(Core.PBGetContentRequest.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(getContentRequest_);
getContentRequest_ = subBuilder.buildPartial();
}
b0_ |= 0x00000020;
break;
}
case 58: {
Core.PBGetContentResponse.Builder subBuilder = null;
if (((b0_ & 0x00000040) == 0x00000040)) {
subBuilder = getContentResponse_.toBuilder();
}
getContentResponse_ = input.readMessage(Core.PBGetContentResponse.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(getContentResponse_);
getContentResponse_ = subBuilder.buildPartial();
}
b0_ |= 0x00000040;
break;
}
case 66: {
Core.PBGetFilterRequest.Builder subBuilder = null;
if (((b0_ & 0x00000080) == 0x00000080)) {
subBuilder = getFilterRequest_.toBuilder();
}
getFilterRequest_ = input.readMessage(Core.PBGetFilterRequest.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(getFilterRequest_);
getFilterRequest_ = subBuilder.buildPartial();
}
b0_ |= 0x00000080;
break;
}
case 74: {
Core.PBGetFilterResponse.Builder subBuilder = null;
if (((b0_ & 0x00000100) == 0x00000100)) {
subBuilder = getFilterResponse_.toBuilder();
}
getFilterResponse_ = input.readMessage(Core.PBGetFilterResponse.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(getFilterResponse_);
getFilterResponse_ = subBuilder.buildPartial();
}
b0_ |= 0x00000100;
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
public static Parser<PBCore> PARSER =
new AbstractParser<PBCore>() {
public PBCore parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBCore(input, er);
}
};
@Override
public Parser<PBCore> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
REPLY(0, 0),
NEW_UPDATES(1, 1),
UPDATE_SENDER_FILTER(2, 2),
GET_CONTENT_REQUEST(3, 3),
GET_FILTER_REQUEST(4, 4),
RESOLVE_USER_ID_REQUEST(5, 5),
RESOLVE_USER_ID_RESPONSE(6, 6),
;
public static final int REPLY_VALUE = 0;
public static final int NEW_UPDATES_VALUE = 1;
public static final int UPDATE_SENDER_FILTER_VALUE = 2;
public static final int GET_CONTENT_REQUEST_VALUE = 3;
public static final int GET_FILTER_REQUEST_VALUE = 4;
public static final int RESOLVE_USER_ID_REQUEST_VALUE = 5;
public static final int RESOLVE_USER_ID_RESPONSE_VALUE = 6;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return REPLY;
case 1: return NEW_UPDATES;
case 2: return UPDATE_SENDER_FILTER;
case 3: return GET_CONTENT_REQUEST;
case 4: return GET_FILTER_REQUEST;
case 5: return RESOLVE_USER_ID_REQUEST;
case 6: return RESOLVE_USER_ID_RESPONSE;
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
private Core.PBCore.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Core.PBCore.Type getType() {
return type_;
}
public static final int RPCID_FIELD_NUMBER = 2;
private int rpcid_;
public boolean hasRpcid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getRpcid() {
return rpcid_;
}
public static final int NEW_UPDATES_FIELD_NUMBER = 3;
private Core.PBNewUpdates newUpdates_;
public boolean hasNewUpdates() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Core.PBNewUpdates getNewUpdates() {
return newUpdates_;
}
public static final int UPDATE_SENDER_FILTER_FIELD_NUMBER = 4;
private Core.PBUpdateSenderFilter updateSenderFilter_;
public boolean hasUpdateSenderFilter() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Core.PBUpdateSenderFilter getUpdateSenderFilter() {
return updateSenderFilter_;
}
public static final int EXCEPTION_RESPONSE_FIELD_NUMBER = 5;
private Common.PBException exceptionResponse_;
public boolean hasExceptionResponse() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public Common.PBException getExceptionResponse() {
return exceptionResponse_;
}
public static final int GET_CONTENT_REQUEST_FIELD_NUMBER = 6;
private Core.PBGetContentRequest getContentRequest_;
public boolean hasGetContentRequest() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public Core.PBGetContentRequest getGetContentRequest() {
return getContentRequest_;
}
public static final int GET_CONTENT_RESPONSE_FIELD_NUMBER = 7;
private Core.PBGetContentResponse getContentResponse_;
public boolean hasGetContentResponse() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public Core.PBGetContentResponse getGetContentResponse() {
return getContentResponse_;
}
public static final int GET_FILTER_REQUEST_FIELD_NUMBER = 8;
private Core.PBGetFilterRequest getFilterRequest_;
public boolean hasGetFilterRequest() {
return ((b0_ & 0x00000080) == 0x00000080);
}
public Core.PBGetFilterRequest getGetFilterRequest() {
return getFilterRequest_;
}
public static final int GET_FILTER_RESPONSE_FIELD_NUMBER = 9;
private Core.PBGetFilterResponse getFilterResponse_;
public boolean hasGetFilterResponse() {
return ((b0_ & 0x00000100) == 0x00000100);
}
public Core.PBGetFilterResponse getGetFilterResponse() {
return getFilterResponse_;
}
private void initFields() {
type_ = Core.PBCore.Type.REPLY;
rpcid_ = 0;
newUpdates_ = Core.PBNewUpdates.getDefaultInstance();
updateSenderFilter_ = Core.PBUpdateSenderFilter.getDefaultInstance();
exceptionResponse_ = Common.PBException.getDefaultInstance();
getContentRequest_ = Core.PBGetContentRequest.getDefaultInstance();
getContentResponse_ = Core.PBGetContentResponse.getDefaultInstance();
getFilterRequest_ = Core.PBGetFilterRequest.getDefaultInstance();
getFilterResponse_ = Core.PBGetFilterResponse.getDefaultInstance();
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
if (hasNewUpdates()) {
if (!getNewUpdates().isInitialized()) {
mii = 0;
return false;
}
}
if (hasUpdateSenderFilter()) {
if (!getUpdateSenderFilter().isInitialized()) {
mii = 0;
return false;
}
}
if (hasExceptionResponse()) {
if (!getExceptionResponse().isInitialized()) {
mii = 0;
return false;
}
}
if (hasGetContentRequest()) {
if (!getGetContentRequest().isInitialized()) {
mii = 0;
return false;
}
}
if (hasGetContentResponse()) {
if (!getGetContentResponse().isInitialized()) {
mii = 0;
return false;
}
}
if (hasGetFilterRequest()) {
if (!getGetFilterRequest().isInitialized()) {
mii = 0;
return false;
}
}
if (hasGetFilterResponse()) {
if (!getGetFilterResponse().isInitialized()) {
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
output.writeInt32(2, rpcid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, newUpdates_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, updateSenderFilter_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeMessage(5, exceptionResponse_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeMessage(6, getContentRequest_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeMessage(7, getContentResponse_);
}
if (((b0_ & 0x00000080) == 0x00000080)) {
output.writeMessage(8, getFilterRequest_);
}
if (((b0_ & 0x00000100) == 0x00000100)) {
output.writeMessage(9, getFilterResponse_);
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
.computeInt32Size(2, rpcid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, newUpdates_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeMessageSize(4, updateSenderFilter_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeMessageSize(5, exceptionResponse_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeMessageSize(6, getContentRequest_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeMessageSize(7, getContentResponse_);
}
if (((b0_ & 0x00000080) == 0x00000080)) {
size += CodedOutputStream
.computeMessageSize(8, getFilterRequest_);
}
if (((b0_ & 0x00000100) == 0x00000100)) {
size += CodedOutputStream
.computeMessageSize(9, getFilterResponse_);
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
public static Core.PBCore parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBCore parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBCore parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBCore parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBCore parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBCore parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBCore parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBCore parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBCore parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBCore parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBCore prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBCore, Builder>
implements
Core.PBCoreOrBuilder {
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
type_ = Core.PBCore.Type.REPLY;
b0_ = (b0_ & ~0x00000001);
rpcid_ = 0;
b0_ = (b0_ & ~0x00000002);
newUpdates_ = Core.PBNewUpdates.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
updateSenderFilter_ = Core.PBUpdateSenderFilter.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
exceptionResponse_ = Common.PBException.getDefaultInstance();
b0_ = (b0_ & ~0x00000010);
getContentRequest_ = Core.PBGetContentRequest.getDefaultInstance();
b0_ = (b0_ & ~0x00000020);
getContentResponse_ = Core.PBGetContentResponse.getDefaultInstance();
b0_ = (b0_ & ~0x00000040);
getFilterRequest_ = Core.PBGetFilterRequest.getDefaultInstance();
b0_ = (b0_ & ~0x00000080);
getFilterResponse_ = Core.PBGetFilterResponse.getDefaultInstance();
b0_ = (b0_ & ~0x00000100);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBCore getDefaultInstanceForType() {
return Core.PBCore.getDefaultInstance();
}
public Core.PBCore build() {
Core.PBCore result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBCore buildPartial() {
Core.PBCore result = new Core.PBCore(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.rpcid_ = rpcid_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.newUpdates_ = newUpdates_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.updateSenderFilter_ = updateSenderFilter_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.exceptionResponse_ = exceptionResponse_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.getContentRequest_ = getContentRequest_;
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.getContentResponse_ = getContentResponse_;
if (((from_b0_ & 0x00000080) == 0x00000080)) {
to_b0_ |= 0x00000080;
}
result.getFilterRequest_ = getFilterRequest_;
if (((from_b0_ & 0x00000100) == 0x00000100)) {
to_b0_ |= 0x00000100;
}
result.getFilterResponse_ = getFilterResponse_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBCore other) {
if (other == Core.PBCore.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasRpcid()) {
setRpcid(other.getRpcid());
}
if (other.hasNewUpdates()) {
mergeNewUpdates(other.getNewUpdates());
}
if (other.hasUpdateSenderFilter()) {
mergeUpdateSenderFilter(other.getUpdateSenderFilter());
}
if (other.hasExceptionResponse()) {
mergeExceptionResponse(other.getExceptionResponse());
}
if (other.hasGetContentRequest()) {
mergeGetContentRequest(other.getGetContentRequest());
}
if (other.hasGetContentResponse()) {
mergeGetContentResponse(other.getGetContentResponse());
}
if (other.hasGetFilterRequest()) {
mergeGetFilterRequest(other.getGetFilterRequest());
}
if (other.hasGetFilterResponse()) {
mergeGetFilterResponse(other.getGetFilterResponse());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (hasNewUpdates()) {
if (!getNewUpdates().isInitialized()) {
return false;
}
}
if (hasUpdateSenderFilter()) {
if (!getUpdateSenderFilter().isInitialized()) {
return false;
}
}
if (hasExceptionResponse()) {
if (!getExceptionResponse().isInitialized()) {
return false;
}
}
if (hasGetContentRequest()) {
if (!getGetContentRequest().isInitialized()) {
return false;
}
}
if (hasGetContentResponse()) {
if (!getGetContentResponse().isInitialized()) {
return false;
}
}
if (hasGetFilterRequest()) {
if (!getGetFilterRequest().isInitialized()) {
return false;
}
}
if (hasGetFilterResponse()) {
if (!getGetFilterResponse().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBCore pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBCore) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Core.PBCore.Type type_ = Core.PBCore.Type.REPLY;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Core.PBCore.Type getType() {
return type_;
}
public Builder setType(Core.PBCore.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Core.PBCore.Type.REPLY;
return this;
}
private int rpcid_ ;
public boolean hasRpcid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getRpcid() {
return rpcid_;
}
public Builder setRpcid(int value) {
b0_ |= 0x00000002;
rpcid_ = value;
return this;
}
public Builder clearRpcid() {
b0_ = (b0_ & ~0x00000002);
rpcid_ = 0;
return this;
}
private Core.PBNewUpdates newUpdates_ = Core.PBNewUpdates.getDefaultInstance();
public boolean hasNewUpdates() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Core.PBNewUpdates getNewUpdates() {
return newUpdates_;
}
public Builder setNewUpdates(Core.PBNewUpdates value) {
if (value == null) {
throw new NullPointerException();
}
newUpdates_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setNewUpdates(
Core.PBNewUpdates.Builder bdForValue) {
newUpdates_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergeNewUpdates(Core.PBNewUpdates value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
newUpdates_ != Core.PBNewUpdates.getDefaultInstance()) {
newUpdates_ =
Core.PBNewUpdates.newBuilder(newUpdates_).mergeFrom(value).buildPartial();
} else {
newUpdates_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearNewUpdates() {
newUpdates_ = Core.PBNewUpdates.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
private Core.PBUpdateSenderFilter updateSenderFilter_ = Core.PBUpdateSenderFilter.getDefaultInstance();
public boolean hasUpdateSenderFilter() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Core.PBUpdateSenderFilter getUpdateSenderFilter() {
return updateSenderFilter_;
}
public Builder setUpdateSenderFilter(Core.PBUpdateSenderFilter value) {
if (value == null) {
throw new NullPointerException();
}
updateSenderFilter_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setUpdateSenderFilter(
Core.PBUpdateSenderFilter.Builder bdForValue) {
updateSenderFilter_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergeUpdateSenderFilter(Core.PBUpdateSenderFilter value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
updateSenderFilter_ != Core.PBUpdateSenderFilter.getDefaultInstance()) {
updateSenderFilter_ =
Core.PBUpdateSenderFilter.newBuilder(updateSenderFilter_).mergeFrom(value).buildPartial();
} else {
updateSenderFilter_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearUpdateSenderFilter() {
updateSenderFilter_ = Core.PBUpdateSenderFilter.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
private Common.PBException exceptionResponse_ = Common.PBException.getDefaultInstance();
public boolean hasExceptionResponse() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public Common.PBException getExceptionResponse() {
return exceptionResponse_;
}
public Builder setExceptionResponse(Common.PBException value) {
if (value == null) {
throw new NullPointerException();
}
exceptionResponse_ = value;
b0_ |= 0x00000010;
return this;
}
public Builder setExceptionResponse(
Common.PBException.Builder bdForValue) {
exceptionResponse_ = bdForValue.build();
b0_ |= 0x00000010;
return this;
}
public Builder mergeExceptionResponse(Common.PBException value) {
if (((b0_ & 0x00000010) == 0x00000010) &&
exceptionResponse_ != Common.PBException.getDefaultInstance()) {
exceptionResponse_ =
Common.PBException.newBuilder(exceptionResponse_).mergeFrom(value).buildPartial();
} else {
exceptionResponse_ = value;
}
b0_ |= 0x00000010;
return this;
}
public Builder clearExceptionResponse() {
exceptionResponse_ = Common.PBException.getDefaultInstance();
b0_ = (b0_ & ~0x00000010);
return this;
}
private Core.PBGetContentRequest getContentRequest_ = Core.PBGetContentRequest.getDefaultInstance();
public boolean hasGetContentRequest() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public Core.PBGetContentRequest getGetContentRequest() {
return getContentRequest_;
}
public Builder setGetContentRequest(Core.PBGetContentRequest value) {
if (value == null) {
throw new NullPointerException();
}
getContentRequest_ = value;
b0_ |= 0x00000020;
return this;
}
public Builder setGetContentRequest(
Core.PBGetContentRequest.Builder bdForValue) {
getContentRequest_ = bdForValue.build();
b0_ |= 0x00000020;
return this;
}
public Builder mergeGetContentRequest(Core.PBGetContentRequest value) {
if (((b0_ & 0x00000020) == 0x00000020) &&
getContentRequest_ != Core.PBGetContentRequest.getDefaultInstance()) {
getContentRequest_ =
Core.PBGetContentRequest.newBuilder(getContentRequest_).mergeFrom(value).buildPartial();
} else {
getContentRequest_ = value;
}
b0_ |= 0x00000020;
return this;
}
public Builder clearGetContentRequest() {
getContentRequest_ = Core.PBGetContentRequest.getDefaultInstance();
b0_ = (b0_ & ~0x00000020);
return this;
}
private Core.PBGetContentResponse getContentResponse_ = Core.PBGetContentResponse.getDefaultInstance();
public boolean hasGetContentResponse() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public Core.PBGetContentResponse getGetContentResponse() {
return getContentResponse_;
}
public Builder setGetContentResponse(Core.PBGetContentResponse value) {
if (value == null) {
throw new NullPointerException();
}
getContentResponse_ = value;
b0_ |= 0x00000040;
return this;
}
public Builder setGetContentResponse(
Core.PBGetContentResponse.Builder bdForValue) {
getContentResponse_ = bdForValue.build();
b0_ |= 0x00000040;
return this;
}
public Builder mergeGetContentResponse(Core.PBGetContentResponse value) {
if (((b0_ & 0x00000040) == 0x00000040) &&
getContentResponse_ != Core.PBGetContentResponse.getDefaultInstance()) {
getContentResponse_ =
Core.PBGetContentResponse.newBuilder(getContentResponse_).mergeFrom(value).buildPartial();
} else {
getContentResponse_ = value;
}
b0_ |= 0x00000040;
return this;
}
public Builder clearGetContentResponse() {
getContentResponse_ = Core.PBGetContentResponse.getDefaultInstance();
b0_ = (b0_ & ~0x00000040);
return this;
}
private Core.PBGetFilterRequest getFilterRequest_ = Core.PBGetFilterRequest.getDefaultInstance();
public boolean hasGetFilterRequest() {
return ((b0_ & 0x00000080) == 0x00000080);
}
public Core.PBGetFilterRequest getGetFilterRequest() {
return getFilterRequest_;
}
public Builder setGetFilterRequest(Core.PBGetFilterRequest value) {
if (value == null) {
throw new NullPointerException();
}
getFilterRequest_ = value;
b0_ |= 0x00000080;
return this;
}
public Builder setGetFilterRequest(
Core.PBGetFilterRequest.Builder bdForValue) {
getFilterRequest_ = bdForValue.build();
b0_ |= 0x00000080;
return this;
}
public Builder mergeGetFilterRequest(Core.PBGetFilterRequest value) {
if (((b0_ & 0x00000080) == 0x00000080) &&
getFilterRequest_ != Core.PBGetFilterRequest.getDefaultInstance()) {
getFilterRequest_ =
Core.PBGetFilterRequest.newBuilder(getFilterRequest_).mergeFrom(value).buildPartial();
} else {
getFilterRequest_ = value;
}
b0_ |= 0x00000080;
return this;
}
public Builder clearGetFilterRequest() {
getFilterRequest_ = Core.PBGetFilterRequest.getDefaultInstance();
b0_ = (b0_ & ~0x00000080);
return this;
}
private Core.PBGetFilterResponse getFilterResponse_ = Core.PBGetFilterResponse.getDefaultInstance();
public boolean hasGetFilterResponse() {
return ((b0_ & 0x00000100) == 0x00000100);
}
public Core.PBGetFilterResponse getGetFilterResponse() {
return getFilterResponse_;
}
public Builder setGetFilterResponse(Core.PBGetFilterResponse value) {
if (value == null) {
throw new NullPointerException();
}
getFilterResponse_ = value;
b0_ |= 0x00000100;
return this;
}
public Builder setGetFilterResponse(
Core.PBGetFilterResponse.Builder bdForValue) {
getFilterResponse_ = bdForValue.build();
b0_ |= 0x00000100;
return this;
}
public Builder mergeGetFilterResponse(Core.PBGetFilterResponse value) {
if (((b0_ & 0x00000100) == 0x00000100) &&
getFilterResponse_ != Core.PBGetFilterResponse.getDefaultInstance()) {
getFilterResponse_ =
Core.PBGetFilterResponse.newBuilder(getFilterResponse_).mergeFrom(value).buildPartial();
} else {
getFilterResponse_ = value;
}
b0_ |= 0x00000100;
return this;
}
public Builder clearGetFilterResponse() {
getFilterResponse_ = Core.PBGetFilterResponse.getDefaultInstance();
b0_ = (b0_ & ~0x00000100);
return this;
}
}
static {
defaultInstance = new PBCore(true);
defaultInstance.initFields();
}
}
public interface PBUpdateSenderFilterOrBuilder extends
MessageLiteOrBuilder {
boolean hasStoreId();
ByteString getStoreId();
boolean hasSenderFilterIndex();
long getSenderFilterIndex();
boolean hasSenderFilterUpdateSeq();
long getSenderFilterUpdateSeq();
}
public static final class PBUpdateSenderFilter extends
GeneratedMessageLite implements
PBUpdateSenderFilterOrBuilder {
private PBUpdateSenderFilter(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBUpdateSenderFilter(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBUpdateSenderFilter defaultInstance;
public static PBUpdateSenderFilter getDefaultInstance() {
return defaultInstance;
}
public PBUpdateSenderFilter getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBUpdateSenderFilter(
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
storeId_ = input.readBytes();
break;
}
case 16: {
b0_ |= 0x00000002;
senderFilterIndex_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
senderFilterUpdateSeq_ = input.readUInt64();
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
public static Parser<PBUpdateSenderFilter> PARSER =
new AbstractParser<PBUpdateSenderFilter>() {
public PBUpdateSenderFilter parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBUpdateSenderFilter(input, er);
}
};
@Override
public Parser<PBUpdateSenderFilter> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STORE_ID_FIELD_NUMBER = 1;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public static final int SENDER_FILTER_INDEX_FIELD_NUMBER = 2;
private long senderFilterIndex_;
public boolean hasSenderFilterIndex() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getSenderFilterIndex() {
return senderFilterIndex_;
}
public static final int SENDER_FILTER_UPDATE_SEQ_FIELD_NUMBER = 3;
private long senderFilterUpdateSeq_;
public boolean hasSenderFilterUpdateSeq() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getSenderFilterUpdateSeq() {
return senderFilterUpdateSeq_;
}
private void initFields() {
storeId_ = ByteString.EMPTY;
senderFilterIndex_ = 0L;
senderFilterUpdateSeq_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreId()) {
mii = 0;
return false;
}
if (!hasSenderFilterIndex()) {
mii = 0;
return false;
}
if (!hasSenderFilterUpdateSeq()) {
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
output.writeBytes(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, senderFilterIndex_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, senderFilterUpdateSeq_);
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
.computeBytesSize(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, senderFilterIndex_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, senderFilterUpdateSeq_);
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
public static Core.PBUpdateSenderFilter parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBUpdateSenderFilter parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBUpdateSenderFilter parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBUpdateSenderFilter parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBUpdateSenderFilter parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBUpdateSenderFilter parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBUpdateSenderFilter parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBUpdateSenderFilter parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBUpdateSenderFilter parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBUpdateSenderFilter parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBUpdateSenderFilter prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBUpdateSenderFilter, Builder>
implements
Core.PBUpdateSenderFilterOrBuilder {
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
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
senderFilterIndex_ = 0L;
b0_ = (b0_ & ~0x00000002);
senderFilterUpdateSeq_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBUpdateSenderFilter getDefaultInstanceForType() {
return Core.PBUpdateSenderFilter.getDefaultInstance();
}
public Core.PBUpdateSenderFilter build() {
Core.PBUpdateSenderFilter result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBUpdateSenderFilter buildPartial() {
Core.PBUpdateSenderFilter result = new Core.PBUpdateSenderFilter(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeId_ = storeId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.senderFilterIndex_ = senderFilterIndex_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.senderFilterUpdateSeq_ = senderFilterUpdateSeq_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBUpdateSenderFilter other) {
if (other == Core.PBUpdateSenderFilter.getDefaultInstance()) return this;
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
if (other.hasSenderFilterIndex()) {
setSenderFilterIndex(other.getSenderFilterIndex());
}
if (other.hasSenderFilterUpdateSeq()) {
setSenderFilterUpdateSeq(other.getSenderFilterUpdateSeq());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasStoreId()) {
return false;
}
if (!hasSenderFilterIndex()) {
return false;
}
if (!hasSenderFilterUpdateSeq()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBUpdateSenderFilter pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBUpdateSenderFilter) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000001);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
private long senderFilterIndex_ ;
public boolean hasSenderFilterIndex() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getSenderFilterIndex() {
return senderFilterIndex_;
}
public Builder setSenderFilterIndex(long value) {
b0_ |= 0x00000002;
senderFilterIndex_ = value;
return this;
}
public Builder clearSenderFilterIndex() {
b0_ = (b0_ & ~0x00000002);
senderFilterIndex_ = 0L;
return this;
}
private long senderFilterUpdateSeq_ ;
public boolean hasSenderFilterUpdateSeq() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getSenderFilterUpdateSeq() {
return senderFilterUpdateSeq_;
}
public Builder setSenderFilterUpdateSeq(long value) {
b0_ |= 0x00000004;
senderFilterUpdateSeq_ = value;
return this;
}
public Builder clearSenderFilterUpdateSeq() {
b0_ = (b0_ & ~0x00000004);
senderFilterUpdateSeq_ = 0L;
return this;
}
}
static {
defaultInstance = new PBUpdateSenderFilter(true);
defaultInstance.initFields();
}
}
public interface PBNewUpdatesOrBuilder extends
MessageLiteOrBuilder {
boolean hasStoreId();
ByteString getStoreId();
boolean hasChangeEpoch();
long getChangeEpoch();
}
public static final class PBNewUpdates extends
GeneratedMessageLite implements
PBNewUpdatesOrBuilder {
private PBNewUpdates(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBNewUpdates(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBNewUpdates defaultInstance;
public static PBNewUpdates getDefaultInstance() {
return defaultInstance;
}
public PBNewUpdates getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBNewUpdates(
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
storeId_ = input.readBytes();
break;
}
case 16: {
b0_ |= 0x00000002;
changeEpoch_ = input.readUInt64();
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
public static Parser<PBNewUpdates> PARSER =
new AbstractParser<PBNewUpdates>() {
public PBNewUpdates parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBNewUpdates(input, er);
}
};
@Override
public Parser<PBNewUpdates> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STORE_ID_FIELD_NUMBER = 1;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public static final int CHANGE_EPOCH_FIELD_NUMBER = 2;
private long changeEpoch_;
public boolean hasChangeEpoch() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getChangeEpoch() {
return changeEpoch_;
}
private void initFields() {
storeId_ = ByteString.EMPTY;
changeEpoch_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreId()) {
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
output.writeBytes(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, changeEpoch_);
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
.computeBytesSize(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, changeEpoch_);
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
public static Core.PBNewUpdates parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBNewUpdates parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBNewUpdates parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBNewUpdates parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBNewUpdates parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBNewUpdates parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBNewUpdates parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBNewUpdates parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBNewUpdates parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBNewUpdates parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBNewUpdates prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBNewUpdates, Builder>
implements
Core.PBNewUpdatesOrBuilder {
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
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
changeEpoch_ = 0L;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBNewUpdates getDefaultInstanceForType() {
return Core.PBNewUpdates.getDefaultInstance();
}
public Core.PBNewUpdates build() {
Core.PBNewUpdates result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBNewUpdates buildPartial() {
Core.PBNewUpdates result = new Core.PBNewUpdates(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeId_ = storeId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.changeEpoch_ = changeEpoch_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBNewUpdates other) {
if (other == Core.PBNewUpdates.getDefaultInstance()) return this;
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
if (other.hasChangeEpoch()) {
setChangeEpoch(other.getChangeEpoch());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasStoreId()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBNewUpdates pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBNewUpdates) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000001);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
private long changeEpoch_ ;
public boolean hasChangeEpoch() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getChangeEpoch() {
return changeEpoch_;
}
public Builder setChangeEpoch(long value) {
b0_ |= 0x00000002;
changeEpoch_ = value;
return this;
}
public Builder clearChangeEpoch() {
b0_ = (b0_ & ~0x00000002);
changeEpoch_ = 0L;
return this;
}
}
static {
defaultInstance = new PBNewUpdates(true);
defaultInstance.initFields();
}
}
public interface PBGetFilterRequestOrBuilder extends
MessageLiteOrBuilder {
boolean hasCount();
int getCount();
}
public static final class PBGetFilterRequest extends
GeneratedMessageLite implements
PBGetFilterRequestOrBuilder {
private PBGetFilterRequest(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBGetFilterRequest(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBGetFilterRequest defaultInstance;
public static PBGetFilterRequest getDefaultInstance() {
return defaultInstance;
}
public PBGetFilterRequest getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBGetFilterRequest(
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
count_ = input.readInt32();
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
public static Parser<PBGetFilterRequest> PARSER =
new AbstractParser<PBGetFilterRequest>() {
public PBGetFilterRequest parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBGetFilterRequest(input, er);
}
};
@Override
public Parser<PBGetFilterRequest> getParserForType() {
return PARSER;
}
public interface StoreOrBuilder extends
MessageLiteOrBuilder {
boolean hasStoreId();
ByteString getStoreId();
boolean hasFromBase();
boolean getFromBase();
}
public static final class Store extends
GeneratedMessageLite implements
StoreOrBuilder {
private Store(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Store(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final Store defaultInstance;
public static Store getDefaultInstance() {
return defaultInstance;
}
public Store getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private Store(
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
storeId_ = input.readBytes();
break;
}
case 16: {
b0_ |= 0x00000002;
fromBase_ = input.readBool();
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
public static Parser<Store> PARSER =
new AbstractParser<Store>() {
public Store parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Store(input, er);
}
};
@Override
public Parser<Store> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STORE_ID_FIELD_NUMBER = 1;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public static final int FROM_BASE_FIELD_NUMBER = 2;
private boolean fromBase_;
public boolean hasFromBase() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getFromBase() {
return fromBase_;
}
private void initFields() {
storeId_ = ByteString.EMPTY;
fromBase_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreId()) {
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
output.writeBytes(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBool(2, fromBase_);
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
.computeBytesSize(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBoolSize(2, fromBase_);
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
public static Core.PBGetFilterRequest.Store parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterRequest.Store parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterRequest.Store parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterRequest.Store parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterRequest.Store parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterRequest.Store parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetFilterRequest.Store parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetFilterRequest.Store parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetFilterRequest.Store parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterRequest.Store parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetFilterRequest.Store prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetFilterRequest.Store, Builder>
implements
Core.PBGetFilterRequest.StoreOrBuilder {
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
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
fromBase_ = false;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetFilterRequest.Store getDefaultInstanceForType() {
return Core.PBGetFilterRequest.Store.getDefaultInstance();
}
public Core.PBGetFilterRequest.Store build() {
Core.PBGetFilterRequest.Store result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetFilterRequest.Store buildPartial() {
Core.PBGetFilterRequest.Store result = new Core.PBGetFilterRequest.Store(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeId_ = storeId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.fromBase_ = fromBase_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetFilterRequest.Store other) {
if (other == Core.PBGetFilterRequest.Store.getDefaultInstance()) return this;
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
if (other.hasFromBase()) {
setFromBase(other.getFromBase());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasStoreId()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetFilterRequest.Store pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetFilterRequest.Store) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000001);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
private boolean fromBase_ ;
public boolean hasFromBase() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getFromBase() {
return fromBase_;
}
public Builder setFromBase(boolean value) {
b0_ |= 0x00000002;
fromBase_ = value;
return this;
}
public Builder clearFromBase() {
b0_ = (b0_ & ~0x00000002);
fromBase_ = false;
return this;
}
}
static {
defaultInstance = new Store(true);
defaultInstance.initFields();
}
}
private int b0_;
public static final int COUNT_FIELD_NUMBER = 1;
private int count_;
public boolean hasCount() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getCount() {
return count_;
}
private void initFields() {
count_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasCount()) {
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
output.writeInt32(1, count_);
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
.computeInt32Size(1, count_);
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
public static Core.PBGetFilterRequest parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterRequest parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterRequest parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterRequest parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterRequest parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterRequest parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetFilterRequest parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetFilterRequest parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetFilterRequest parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterRequest parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetFilterRequest prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetFilterRequest, Builder>
implements
Core.PBGetFilterRequestOrBuilder {
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
count_ = 0;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetFilterRequest getDefaultInstanceForType() {
return Core.PBGetFilterRequest.getDefaultInstance();
}
public Core.PBGetFilterRequest build() {
Core.PBGetFilterRequest result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetFilterRequest buildPartial() {
Core.PBGetFilterRequest result = new Core.PBGetFilterRequest(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.count_ = count_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetFilterRequest other) {
if (other == Core.PBGetFilterRequest.getDefaultInstance()) return this;
if (other.hasCount()) {
setCount(other.getCount());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasCount()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetFilterRequest pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetFilterRequest) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int count_ ;
public boolean hasCount() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getCount() {
return count_;
}
public Builder setCount(int value) {
b0_ |= 0x00000001;
count_ = value;
return this;
}
public Builder clearCount() {
b0_ = (b0_ & ~0x00000001);
count_ = 0;
return this;
}
}
static {
defaultInstance = new PBGetFilterRequest(true);
defaultInstance.initFields();
}
}
public interface PBGetFilterResponseOrBuilder extends
MessageLiteOrBuilder {
boolean hasCount();
int getCount();
}
public static final class PBGetFilterResponse extends
GeneratedMessageLite implements
PBGetFilterResponseOrBuilder {
private PBGetFilterResponse(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBGetFilterResponse(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBGetFilterResponse defaultInstance;
public static PBGetFilterResponse getDefaultInstance() {
return defaultInstance;
}
public PBGetFilterResponse getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBGetFilterResponse(
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
count_ = input.readInt32();
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
public static Parser<PBGetFilterResponse> PARSER =
new AbstractParser<PBGetFilterResponse>() {
public PBGetFilterResponse parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBGetFilterResponse(input, er);
}
};
@Override
public Parser<PBGetFilterResponse> getParserForType() {
return PARSER;
}
public interface StoreOrBuilder extends
MessageLiteOrBuilder {
boolean hasStoreId();
ByteString getStoreId();
boolean hasSenderFilter();
ByteString getSenderFilter();
boolean hasSenderFilterIndex();
long getSenderFilterIndex();
boolean hasSenderFilterUpdateSeq();
long getSenderFilterUpdateSeq();
boolean hasSenderFilterEpoch();
long getSenderFilterEpoch();
boolean hasFromBase();
boolean getFromBase();
boolean hasEx();
Common.PBException getEx();
}
public static final class Store extends
GeneratedMessageLite implements
StoreOrBuilder {
private Store(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Store(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final Store defaultInstance;
public static Store getDefaultInstance() {
return defaultInstance;
}
public Store getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private Store(
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
storeId_ = input.readBytes();
break;
}
case 18: {
b0_ |= 0x00000002;
senderFilter_ = input.readBytes();
break;
}
case 24: {
b0_ |= 0x00000004;
senderFilterIndex_ = input.readUInt64();
break;
}
case 32: {
b0_ |= 0x00000008;
senderFilterUpdateSeq_ = input.readUInt64();
break;
}
case 40: {
b0_ |= 0x00000010;
senderFilterEpoch_ = input.readUInt64();
break;
}
case 48: {
b0_ |= 0x00000020;
fromBase_ = input.readBool();
break;
}
case 58: {
Common.PBException.Builder subBuilder = null;
if (((b0_ & 0x00000040) == 0x00000040)) {
subBuilder = ex_.toBuilder();
}
ex_ = input.readMessage(Common.PBException.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(ex_);
ex_ = subBuilder.buildPartial();
}
b0_ |= 0x00000040;
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
public static Parser<Store> PARSER =
new AbstractParser<Store>() {
public Store parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Store(input, er);
}
};
@Override
public Parser<Store> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STORE_ID_FIELD_NUMBER = 1;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public static final int SENDER_FILTER_FIELD_NUMBER = 2;
private ByteString senderFilter_;
public boolean hasSenderFilter() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getSenderFilter() {
return senderFilter_;
}
public static final int SENDER_FILTER_INDEX_FIELD_NUMBER = 3;
private long senderFilterIndex_;
public boolean hasSenderFilterIndex() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getSenderFilterIndex() {
return senderFilterIndex_;
}
public static final int SENDER_FILTER_UPDATE_SEQ_FIELD_NUMBER = 4;
private long senderFilterUpdateSeq_;
public boolean hasSenderFilterUpdateSeq() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getSenderFilterUpdateSeq() {
return senderFilterUpdateSeq_;
}
public static final int SENDER_FILTER_EPOCH_FIELD_NUMBER = 5;
private long senderFilterEpoch_;
public boolean hasSenderFilterEpoch() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getSenderFilterEpoch() {
return senderFilterEpoch_;
}
public static final int FROM_BASE_FIELD_NUMBER = 6;
private boolean fromBase_;
public boolean hasFromBase() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public boolean getFromBase() {
return fromBase_;
}
public static final int EX_FIELD_NUMBER = 7;
private Common.PBException ex_;
public boolean hasEx() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public Common.PBException getEx() {
return ex_;
}
private void initFields() {
storeId_ = ByteString.EMPTY;
senderFilter_ = ByteString.EMPTY;
senderFilterIndex_ = 0L;
senderFilterUpdateSeq_ = 0L;
senderFilterEpoch_ = 0L;
fromBase_ = false;
ex_ = Common.PBException.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreId()) {
mii = 0;
return false;
}
if (hasEx()) {
if (!getEx().isInitialized()) {
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
output.writeBytes(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, senderFilter_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, senderFilterIndex_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeUInt64(4, senderFilterUpdateSeq_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeUInt64(5, senderFilterEpoch_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeBool(6, fromBase_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeMessage(7, ex_);
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
.computeBytesSize(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, senderFilter_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, senderFilterIndex_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeUInt64Size(4, senderFilterUpdateSeq_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeUInt64Size(5, senderFilterEpoch_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeBoolSize(6, fromBase_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeMessageSize(7, ex_);
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
public static Core.PBGetFilterResponse.Store parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterResponse.Store parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterResponse.Store parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterResponse.Store parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterResponse.Store parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterResponse.Store parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetFilterResponse.Store parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetFilterResponse.Store parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetFilterResponse.Store parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterResponse.Store parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetFilterResponse.Store prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetFilterResponse.Store, Builder>
implements
Core.PBGetFilterResponse.StoreOrBuilder {
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
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
senderFilter_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
senderFilterIndex_ = 0L;
b0_ = (b0_ & ~0x00000004);
senderFilterUpdateSeq_ = 0L;
b0_ = (b0_ & ~0x00000008);
senderFilterEpoch_ = 0L;
b0_ = (b0_ & ~0x00000010);
fromBase_ = false;
b0_ = (b0_ & ~0x00000020);
ex_ = Common.PBException.getDefaultInstance();
b0_ = (b0_ & ~0x00000040);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetFilterResponse.Store getDefaultInstanceForType() {
return Core.PBGetFilterResponse.Store.getDefaultInstance();
}
public Core.PBGetFilterResponse.Store build() {
Core.PBGetFilterResponse.Store result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetFilterResponse.Store buildPartial() {
Core.PBGetFilterResponse.Store result = new Core.PBGetFilterResponse.Store(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeId_ = storeId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.senderFilter_ = senderFilter_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.senderFilterIndex_ = senderFilterIndex_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.senderFilterUpdateSeq_ = senderFilterUpdateSeq_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.senderFilterEpoch_ = senderFilterEpoch_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.fromBase_ = fromBase_;
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.ex_ = ex_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetFilterResponse.Store other) {
if (other == Core.PBGetFilterResponse.Store.getDefaultInstance()) return this;
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
if (other.hasSenderFilter()) {
setSenderFilter(other.getSenderFilter());
}
if (other.hasSenderFilterIndex()) {
setSenderFilterIndex(other.getSenderFilterIndex());
}
if (other.hasSenderFilterUpdateSeq()) {
setSenderFilterUpdateSeq(other.getSenderFilterUpdateSeq());
}
if (other.hasSenderFilterEpoch()) {
setSenderFilterEpoch(other.getSenderFilterEpoch());
}
if (other.hasFromBase()) {
setFromBase(other.getFromBase());
}
if (other.hasEx()) {
mergeEx(other.getEx());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasStoreId()) {
return false;
}
if (hasEx()) {
if (!getEx().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetFilterResponse.Store pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetFilterResponse.Store) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000001);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
private ByteString senderFilter_ = ByteString.EMPTY;
public boolean hasSenderFilter() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getSenderFilter() {
return senderFilter_;
}
public Builder setSenderFilter(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
senderFilter_ = value;
return this;
}
public Builder clearSenderFilter() {
b0_ = (b0_ & ~0x00000002);
senderFilter_ = getDefaultInstance().getSenderFilter();
return this;
}
private long senderFilterIndex_ ;
public boolean hasSenderFilterIndex() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getSenderFilterIndex() {
return senderFilterIndex_;
}
public Builder setSenderFilterIndex(long value) {
b0_ |= 0x00000004;
senderFilterIndex_ = value;
return this;
}
public Builder clearSenderFilterIndex() {
b0_ = (b0_ & ~0x00000004);
senderFilterIndex_ = 0L;
return this;
}
private long senderFilterUpdateSeq_ ;
public boolean hasSenderFilterUpdateSeq() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getSenderFilterUpdateSeq() {
return senderFilterUpdateSeq_;
}
public Builder setSenderFilterUpdateSeq(long value) {
b0_ |= 0x00000008;
senderFilterUpdateSeq_ = value;
return this;
}
public Builder clearSenderFilterUpdateSeq() {
b0_ = (b0_ & ~0x00000008);
senderFilterUpdateSeq_ = 0L;
return this;
}
private long senderFilterEpoch_ ;
public boolean hasSenderFilterEpoch() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getSenderFilterEpoch() {
return senderFilterEpoch_;
}
public Builder setSenderFilterEpoch(long value) {
b0_ |= 0x00000010;
senderFilterEpoch_ = value;
return this;
}
public Builder clearSenderFilterEpoch() {
b0_ = (b0_ & ~0x00000010);
senderFilterEpoch_ = 0L;
return this;
}
private boolean fromBase_ ;
public boolean hasFromBase() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public boolean getFromBase() {
return fromBase_;
}
public Builder setFromBase(boolean value) {
b0_ |= 0x00000020;
fromBase_ = value;
return this;
}
public Builder clearFromBase() {
b0_ = (b0_ & ~0x00000020);
fromBase_ = false;
return this;
}
private Common.PBException ex_ = Common.PBException.getDefaultInstance();
public boolean hasEx() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public Common.PBException getEx() {
return ex_;
}
public Builder setEx(Common.PBException value) {
if (value == null) {
throw new NullPointerException();
}
ex_ = value;
b0_ |= 0x00000040;
return this;
}
public Builder setEx(
Common.PBException.Builder bdForValue) {
ex_ = bdForValue.build();
b0_ |= 0x00000040;
return this;
}
public Builder mergeEx(Common.PBException value) {
if (((b0_ & 0x00000040) == 0x00000040) &&
ex_ != Common.PBException.getDefaultInstance()) {
ex_ =
Common.PBException.newBuilder(ex_).mergeFrom(value).buildPartial();
} else {
ex_ = value;
}
b0_ |= 0x00000040;
return this;
}
public Builder clearEx() {
ex_ = Common.PBException.getDefaultInstance();
b0_ = (b0_ & ~0x00000040);
return this;
}
}
static {
defaultInstance = new Store(true);
defaultInstance.initFields();
}
}
private int b0_;
public static final int COUNT_FIELD_NUMBER = 1;
private int count_;
public boolean hasCount() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getCount() {
return count_;
}
private void initFields() {
count_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasCount()) {
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
output.writeInt32(1, count_);
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
.computeInt32Size(1, count_);
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
public static Core.PBGetFilterResponse parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterResponse parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterResponse parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetFilterResponse parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetFilterResponse parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterResponse parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetFilterResponse parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetFilterResponse parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetFilterResponse parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetFilterResponse parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetFilterResponse prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetFilterResponse, Builder>
implements
Core.PBGetFilterResponseOrBuilder {
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
count_ = 0;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetFilterResponse getDefaultInstanceForType() {
return Core.PBGetFilterResponse.getDefaultInstance();
}
public Core.PBGetFilterResponse build() {
Core.PBGetFilterResponse result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetFilterResponse buildPartial() {
Core.PBGetFilterResponse result = new Core.PBGetFilterResponse(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.count_ = count_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetFilterResponse other) {
if (other == Core.PBGetFilterResponse.getDefaultInstance()) return this;
if (other.hasCount()) {
setCount(other.getCount());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasCount()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetFilterResponse pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetFilterResponse) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int count_ ;
public boolean hasCount() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getCount() {
return count_;
}
public Builder setCount(int value) {
b0_ |= 0x00000001;
count_ = value;
return this;
}
public Builder clearCount() {
b0_ = (b0_ & ~0x00000001);
count_ = 0;
return this;
}
}
static {
defaultInstance = new PBGetFilterResponse(true);
defaultInstance.initFields();
}
}
public interface PBGetContentRequestOrBuilder extends
MessageLiteOrBuilder {
boolean hasStoreId();
ByteString getStoreId();
boolean hasObjectId();
ByteString getObjectId();
boolean hasLocalVersion();
long getLocalVersion();
boolean hasPrefix();
Core.PBGetContentRequest.Prefix getPrefix();
}
public static final class PBGetContentRequest extends
GeneratedMessageLite implements
PBGetContentRequestOrBuilder {
private PBGetContentRequest(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBGetContentRequest(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBGetContentRequest defaultInstance;
public static PBGetContentRequest getDefaultInstance() {
return defaultInstance;
}
public PBGetContentRequest getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBGetContentRequest(
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
storeId_ = input.readBytes();
break;
}
case 18: {
b0_ |= 0x00000002;
objectId_ = input.readBytes();
break;
}
case 24: {
b0_ |= 0x00000004;
localVersion_ = input.readUInt64();
break;
}
case 34: {
Core.PBGetContentRequest.Prefix.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = prefix_.toBuilder();
}
prefix_ = input.readMessage(Core.PBGetContentRequest.Prefix.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(prefix_);
prefix_ = subBuilder.buildPartial();
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
public static Parser<PBGetContentRequest> PARSER =
new AbstractParser<PBGetContentRequest>() {
public PBGetContentRequest parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBGetContentRequest(input, er);
}
};
@Override
public Parser<PBGetContentRequest> getParserForType() {
return PARSER;
}
public interface PrefixOrBuilder extends
MessageLiteOrBuilder {
boolean hasVersion();
long getVersion();
boolean hasLength();
long getLength();
boolean hasHashState();
ByteString getHashState();
}
public static final class Prefix extends
GeneratedMessageLite implements
PrefixOrBuilder {
private Prefix(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Prefix(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final Prefix defaultInstance;
public static Prefix getDefaultInstance() {
return defaultInstance;
}
public Prefix getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private Prefix(
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
version_ = input.readUInt64();
break;
}
case 16: {
b0_ |= 0x00000002;
length_ = input.readUInt64();
break;
}
case 26: {
b0_ |= 0x00000004;
hashState_ = input.readBytes();
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
public static Parser<Prefix> PARSER =
new AbstractParser<Prefix>() {
public Prefix parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Prefix(input, er);
}
};
@Override
public Parser<Prefix> getParserForType() {
return PARSER;
}
private int b0_;
public static final int VERSION_FIELD_NUMBER = 1;
private long version_;
public boolean hasVersion() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getVersion() {
return version_;
}
public static final int LENGTH_FIELD_NUMBER = 2;
private long length_;
public boolean hasLength() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getLength() {
return length_;
}
public static final int HASH_STATE_FIELD_NUMBER = 3;
private ByteString hashState_;
public boolean hasHashState() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public ByteString getHashState() {
return hashState_;
}
private void initFields() {
version_ = 0L;
length_ = 0L;
hashState_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasVersion()) {
mii = 0;
return false;
}
if (!hasLength()) {
mii = 0;
return false;
}
if (!hasHashState()) {
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
output.writeUInt64(1, version_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, length_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, hashState_);
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
.computeUInt64Size(1, version_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, length_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, hashState_);
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
public static Core.PBGetContentRequest.Prefix parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetContentRequest.Prefix parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetContentRequest.Prefix parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetContentRequest.Prefix parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetContentRequest.Prefix parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetContentRequest.Prefix parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetContentRequest.Prefix parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetContentRequest.Prefix parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetContentRequest.Prefix parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetContentRequest.Prefix parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetContentRequest.Prefix prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetContentRequest.Prefix, Builder>
implements
Core.PBGetContentRequest.PrefixOrBuilder {
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
version_ = 0L;
b0_ = (b0_ & ~0x00000001);
length_ = 0L;
b0_ = (b0_ & ~0x00000002);
hashState_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetContentRequest.Prefix getDefaultInstanceForType() {
return Core.PBGetContentRequest.Prefix.getDefaultInstance();
}
public Core.PBGetContentRequest.Prefix build() {
Core.PBGetContentRequest.Prefix result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetContentRequest.Prefix buildPartial() {
Core.PBGetContentRequest.Prefix result = new Core.PBGetContentRequest.Prefix(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.version_ = version_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.length_ = length_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.hashState_ = hashState_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetContentRequest.Prefix other) {
if (other == Core.PBGetContentRequest.Prefix.getDefaultInstance()) return this;
if (other.hasVersion()) {
setVersion(other.getVersion());
}
if (other.hasLength()) {
setLength(other.getLength());
}
if (other.hasHashState()) {
setHashState(other.getHashState());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasVersion()) {
return false;
}
if (!hasLength()) {
return false;
}
if (!hasHashState()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetContentRequest.Prefix pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetContentRequest.Prefix) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private long version_ ;
public boolean hasVersion() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getVersion() {
return version_;
}
public Builder setVersion(long value) {
b0_ |= 0x00000001;
version_ = value;
return this;
}
public Builder clearVersion() {
b0_ = (b0_ & ~0x00000001);
version_ = 0L;
return this;
}
private long length_ ;
public boolean hasLength() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getLength() {
return length_;
}
public Builder setLength(long value) {
b0_ |= 0x00000002;
length_ = value;
return this;
}
public Builder clearLength() {
b0_ = (b0_ & ~0x00000002);
length_ = 0L;
return this;
}
private ByteString hashState_ = ByteString.EMPTY;
public boolean hasHashState() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public ByteString getHashState() {
return hashState_;
}
public Builder setHashState(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
hashState_ = value;
return this;
}
public Builder clearHashState() {
b0_ = (b0_ & ~0x00000004);
hashState_ = getDefaultInstance().getHashState();
return this;
}
}
static {
defaultInstance = new Prefix(true);
defaultInstance.initFields();
}
}
private int b0_;
public static final int STORE_ID_FIELD_NUMBER = 1;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public static final int OBJECT_ID_FIELD_NUMBER = 2;
private ByteString objectId_;
public boolean hasObjectId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getObjectId() {
return objectId_;
}
public static final int LOCAL_VERSION_FIELD_NUMBER = 3;
private long localVersion_;
public boolean hasLocalVersion() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getLocalVersion() {
return localVersion_;
}
public static final int PREFIX_FIELD_NUMBER = 4;
private Core.PBGetContentRequest.Prefix prefix_;
public boolean hasPrefix() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Core.PBGetContentRequest.Prefix getPrefix() {
return prefix_;
}
private void initFields() {
storeId_ = ByteString.EMPTY;
objectId_ = ByteString.EMPTY;
localVersion_ = 0L;
prefix_ = Core.PBGetContentRequest.Prefix.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreId()) {
mii = 0;
return false;
}
if (!hasObjectId()) {
mii = 0;
return false;
}
if (!hasLocalVersion()) {
mii = 0;
return false;
}
if (hasPrefix()) {
if (!getPrefix().isInitialized()) {
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
output.writeBytes(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, objectId_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, localVersion_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, prefix_);
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
.computeBytesSize(1, storeId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, objectId_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, localVersion_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeMessageSize(4, prefix_);
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
public static Core.PBGetContentRequest parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetContentRequest parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetContentRequest parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetContentRequest parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetContentRequest parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetContentRequest parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetContentRequest parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetContentRequest parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetContentRequest parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetContentRequest parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetContentRequest prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetContentRequest, Builder>
implements
Core.PBGetContentRequestOrBuilder {
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
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
objectId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
localVersion_ = 0L;
b0_ = (b0_ & ~0x00000004);
prefix_ = Core.PBGetContentRequest.Prefix.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetContentRequest getDefaultInstanceForType() {
return Core.PBGetContentRequest.getDefaultInstance();
}
public Core.PBGetContentRequest build() {
Core.PBGetContentRequest result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetContentRequest buildPartial() {
Core.PBGetContentRequest result = new Core.PBGetContentRequest(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeId_ = storeId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.objectId_ = objectId_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.localVersion_ = localVersion_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.prefix_ = prefix_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetContentRequest other) {
if (other == Core.PBGetContentRequest.getDefaultInstance()) return this;
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
if (other.hasObjectId()) {
setObjectId(other.getObjectId());
}
if (other.hasLocalVersion()) {
setLocalVersion(other.getLocalVersion());
}
if (other.hasPrefix()) {
mergePrefix(other.getPrefix());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasStoreId()) {
return false;
}
if (!hasObjectId()) {
return false;
}
if (!hasLocalVersion()) {
return false;
}
if (hasPrefix()) {
if (!getPrefix().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetContentRequest pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetContentRequest) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000001);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
private ByteString objectId_ = ByteString.EMPTY;
public boolean hasObjectId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getObjectId() {
return objectId_;
}
public Builder setObjectId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
objectId_ = value;
return this;
}
public Builder clearObjectId() {
b0_ = (b0_ & ~0x00000002);
objectId_ = getDefaultInstance().getObjectId();
return this;
}
private long localVersion_ ;
public boolean hasLocalVersion() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getLocalVersion() {
return localVersion_;
}
public Builder setLocalVersion(long value) {
b0_ |= 0x00000004;
localVersion_ = value;
return this;
}
public Builder clearLocalVersion() {
b0_ = (b0_ & ~0x00000004);
localVersion_ = 0L;
return this;
}
private Core.PBGetContentRequest.Prefix prefix_ = Core.PBGetContentRequest.Prefix.getDefaultInstance();
public boolean hasPrefix() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Core.PBGetContentRequest.Prefix getPrefix() {
return prefix_;
}
public Builder setPrefix(Core.PBGetContentRequest.Prefix value) {
if (value == null) {
throw new NullPointerException();
}
prefix_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setPrefix(
Core.PBGetContentRequest.Prefix.Builder bdForValue) {
prefix_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergePrefix(Core.PBGetContentRequest.Prefix value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
prefix_ != Core.PBGetContentRequest.Prefix.getDefaultInstance()) {
prefix_ =
Core.PBGetContentRequest.Prefix.newBuilder(prefix_).mergeFrom(value).buildPartial();
} else {
prefix_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearPrefix() {
prefix_ = Core.PBGetContentRequest.Prefix.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
}
static {
defaultInstance = new PBGetContentRequest(true);
defaultInstance.initFields();
}
}
public interface PBGetContentResponseOrBuilder extends
MessageLiteOrBuilder {
boolean hasVersion();
long getVersion();
boolean hasLength();
long getLength();
boolean hasMtime();
long getMtime();
boolean hasHash();
ByteString getHash();
boolean hasPrefixLength();
long getPrefixLength();
boolean hasLts();
long getLts();
}
public static final class PBGetContentResponse extends
GeneratedMessageLite implements
PBGetContentResponseOrBuilder {
private PBGetContentResponse(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBGetContentResponse(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBGetContentResponse defaultInstance;
public static PBGetContentResponse getDefaultInstance() {
return defaultInstance;
}
public PBGetContentResponse getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBGetContentResponse(
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
version_ = input.readUInt64();
break;
}
case 16: {
b0_ |= 0x00000002;
length_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
mtime_ = input.readUInt64();
break;
}
case 34: {
b0_ |= 0x00000008;
hash_ = input.readBytes();
break;
}
case 40: {
b0_ |= 0x00000010;
prefixLength_ = input.readUInt64();
break;
}
case 48: {
b0_ |= 0x00000020;
lts_ = input.readUInt64();
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
public static Parser<PBGetContentResponse> PARSER =
new AbstractParser<PBGetContentResponse>() {
public PBGetContentResponse parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBGetContentResponse(input, er);
}
};
@Override
public Parser<PBGetContentResponse> getParserForType() {
return PARSER;
}
private int b0_;
public static final int VERSION_FIELD_NUMBER = 1;
private long version_;
public boolean hasVersion() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getVersion() {
return version_;
}
public static final int LENGTH_FIELD_NUMBER = 2;
private long length_;
public boolean hasLength() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getLength() {
return length_;
}
public static final int MTIME_FIELD_NUMBER = 3;
private long mtime_;
public boolean hasMtime() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getMtime() {
return mtime_;
}
public static final int HASH_FIELD_NUMBER = 4;
private ByteString hash_;
public boolean hasHash() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public ByteString getHash() {
return hash_;
}
public static final int PREFIX_LENGTH_FIELD_NUMBER = 5;
private long prefixLength_;
public boolean hasPrefixLength() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getPrefixLength() {
return prefixLength_;
}
public static final int LTS_FIELD_NUMBER = 6;
private long lts_;
public boolean hasLts() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getLts() {
return lts_;
}
private void initFields() {
version_ = 0L;
length_ = 0L;
mtime_ = 0L;
hash_ = ByteString.EMPTY;
prefixLength_ = 0L;
lts_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasVersion()) {
mii = 0;
return false;
}
if (!hasLength()) {
mii = 0;
return false;
}
if (!hasMtime()) {
mii = 0;
return false;
}
if (!hasHash()) {
mii = 0;
return false;
}
if (!hasLts()) {
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
output.writeUInt64(1, version_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, length_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, mtime_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeBytes(4, hash_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeUInt64(5, prefixLength_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeUInt64(6, lts_);
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
.computeUInt64Size(1, version_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, length_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, mtime_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeBytesSize(4, hash_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeUInt64Size(5, prefixLength_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeUInt64Size(6, lts_);
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
public static Core.PBGetContentResponse parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetContentResponse parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetContentResponse parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Core.PBGetContentResponse parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Core.PBGetContentResponse parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetContentResponse parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Core.PBGetContentResponse parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Core.PBGetContentResponse parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Core.PBGetContentResponse parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Core.PBGetContentResponse parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Core.PBGetContentResponse prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Core.PBGetContentResponse, Builder>
implements
Core.PBGetContentResponseOrBuilder {
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
version_ = 0L;
b0_ = (b0_ & ~0x00000001);
length_ = 0L;
b0_ = (b0_ & ~0x00000002);
mtime_ = 0L;
b0_ = (b0_ & ~0x00000004);
hash_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000008);
prefixLength_ = 0L;
b0_ = (b0_ & ~0x00000010);
lts_ = 0L;
b0_ = (b0_ & ~0x00000020);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Core.PBGetContentResponse getDefaultInstanceForType() {
return Core.PBGetContentResponse.getDefaultInstance();
}
public Core.PBGetContentResponse build() {
Core.PBGetContentResponse result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Core.PBGetContentResponse buildPartial() {
Core.PBGetContentResponse result = new Core.PBGetContentResponse(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.version_ = version_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.length_ = length_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.mtime_ = mtime_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.hash_ = hash_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.prefixLength_ = prefixLength_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.lts_ = lts_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Core.PBGetContentResponse other) {
if (other == Core.PBGetContentResponse.getDefaultInstance()) return this;
if (other.hasVersion()) {
setVersion(other.getVersion());
}
if (other.hasLength()) {
setLength(other.getLength());
}
if (other.hasMtime()) {
setMtime(other.getMtime());
}
if (other.hasHash()) {
setHash(other.getHash());
}
if (other.hasPrefixLength()) {
setPrefixLength(other.getPrefixLength());
}
if (other.hasLts()) {
setLts(other.getLts());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasVersion()) {
return false;
}
if (!hasLength()) {
return false;
}
if (!hasMtime()) {
return false;
}
if (!hasHash()) {
return false;
}
if (!hasLts()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Core.PBGetContentResponse pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Core.PBGetContentResponse) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private long version_ ;
public boolean hasVersion() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getVersion() {
return version_;
}
public Builder setVersion(long value) {
b0_ |= 0x00000001;
version_ = value;
return this;
}
public Builder clearVersion() {
b0_ = (b0_ & ~0x00000001);
version_ = 0L;
return this;
}
private long length_ ;
public boolean hasLength() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getLength() {
return length_;
}
public Builder setLength(long value) {
b0_ |= 0x00000002;
length_ = value;
return this;
}
public Builder clearLength() {
b0_ = (b0_ & ~0x00000002);
length_ = 0L;
return this;
}
private long mtime_ ;
public boolean hasMtime() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getMtime() {
return mtime_;
}
public Builder setMtime(long value) {
b0_ |= 0x00000004;
mtime_ = value;
return this;
}
public Builder clearMtime() {
b0_ = (b0_ & ~0x00000004);
mtime_ = 0L;
return this;
}
private ByteString hash_ = ByteString.EMPTY;
public boolean hasHash() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public ByteString getHash() {
return hash_;
}
public Builder setHash(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
hash_ = value;
return this;
}
public Builder clearHash() {
b0_ = (b0_ & ~0x00000008);
hash_ = getDefaultInstance().getHash();
return this;
}
private long prefixLength_ ;
public boolean hasPrefixLength() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getPrefixLength() {
return prefixLength_;
}
public Builder setPrefixLength(long value) {
b0_ |= 0x00000010;
prefixLength_ = value;
return this;
}
public Builder clearPrefixLength() {
b0_ = (b0_ & ~0x00000010);
prefixLength_ = 0L;
return this;
}
private long lts_ ;
public boolean hasLts() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getLts() {
return lts_;
}
public Builder setLts(long value) {
b0_ |= 0x00000020;
lts_ = value;
return this;
}
public Builder clearLts() {
b0_ = (b0_ & ~0x00000020);
lts_ = 0L;
return this;
}
}
static {
defaultInstance = new PBGetContentResponse(true);
defaultInstance.initFields();
}
}
static {
}
}
