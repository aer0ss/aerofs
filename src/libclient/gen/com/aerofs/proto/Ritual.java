package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Ritual {
private Ritual() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public interface GetObjectAttributesCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class GetObjectAttributesCall extends
GeneratedMessageLite implements
GetObjectAttributesCallOrBuilder {
private GetObjectAttributesCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetObjectAttributesCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetObjectAttributesCall defaultInstance;
public static GetObjectAttributesCall getDefaultInstance() {
return defaultInstance;
}
public GetObjectAttributesCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetObjectAttributesCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<GetObjectAttributesCall> PARSER =
new AbstractParser<GetObjectAttributesCall>() {
public GetObjectAttributesCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetObjectAttributesCall(input, er);
}
};
@Override
public Parser<GetObjectAttributesCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.GetObjectAttributesCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetObjectAttributesCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetObjectAttributesCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetObjectAttributesCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetObjectAttributesCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetObjectAttributesCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetObjectAttributesCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetObjectAttributesCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetObjectAttributesCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetObjectAttributesCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetObjectAttributesCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetObjectAttributesCall, Builder>
implements
Ritual.GetObjectAttributesCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetObjectAttributesCall getDefaultInstanceForType() {
return Ritual.GetObjectAttributesCall.getDefaultInstance();
}
public Ritual.GetObjectAttributesCall build() {
Ritual.GetObjectAttributesCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetObjectAttributesCall buildPartial() {
Ritual.GetObjectAttributesCall result = new Ritual.GetObjectAttributesCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetObjectAttributesCall other) {
if (other == Ritual.GetObjectAttributesCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetObjectAttributesCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetObjectAttributesCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new GetObjectAttributesCall(true);
defaultInstance.initFields();
}
}
public interface GetObjectAttributesReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasObjectAttributes();
Ritual.PBObjectAttributes getObjectAttributes();
}
public static final class GetObjectAttributesReply extends
GeneratedMessageLite implements
GetObjectAttributesReplyOrBuilder {
private GetObjectAttributesReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetObjectAttributesReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetObjectAttributesReply defaultInstance;
public static GetObjectAttributesReply getDefaultInstance() {
return defaultInstance;
}
public GetObjectAttributesReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetObjectAttributesReply(
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
Ritual.PBObjectAttributes.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = objectAttributes_.toBuilder();
}
objectAttributes_ = input.readMessage(Ritual.PBObjectAttributes.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(objectAttributes_);
objectAttributes_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<GetObjectAttributesReply> PARSER =
new AbstractParser<GetObjectAttributesReply>() {
public GetObjectAttributesReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetObjectAttributesReply(input, er);
}
};
@Override
public Parser<GetObjectAttributesReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int OBJECT_ATTRIBUTES_FIELD_NUMBER = 1;
private Ritual.PBObjectAttributes objectAttributes_;
public boolean hasObjectAttributes() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Ritual.PBObjectAttributes getObjectAttributes() {
return objectAttributes_;
}
private void initFields() {
objectAttributes_ = Ritual.PBObjectAttributes.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasObjectAttributes()) {
mii = 0;
return false;
}
if (!getObjectAttributes().isInitialized()) {
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
output.writeMessage(1, objectAttributes_);
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
.computeMessageSize(1, objectAttributes_);
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
public static Ritual.GetObjectAttributesReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetObjectAttributesReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetObjectAttributesReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetObjectAttributesReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetObjectAttributesReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetObjectAttributesReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetObjectAttributesReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetObjectAttributesReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetObjectAttributesReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetObjectAttributesReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetObjectAttributesReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetObjectAttributesReply, Builder>
implements
Ritual.GetObjectAttributesReplyOrBuilder {
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
objectAttributes_ = Ritual.PBObjectAttributes.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetObjectAttributesReply getDefaultInstanceForType() {
return Ritual.GetObjectAttributesReply.getDefaultInstance();
}
public Ritual.GetObjectAttributesReply build() {
Ritual.GetObjectAttributesReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetObjectAttributesReply buildPartial() {
Ritual.GetObjectAttributesReply result = new Ritual.GetObjectAttributesReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.objectAttributes_ = objectAttributes_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetObjectAttributesReply other) {
if (other == Ritual.GetObjectAttributesReply.getDefaultInstance()) return this;
if (other.hasObjectAttributes()) {
mergeObjectAttributes(other.getObjectAttributes());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasObjectAttributes()) {
return false;
}
if (!getObjectAttributes().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetObjectAttributesReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetObjectAttributesReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Ritual.PBObjectAttributes objectAttributes_ = Ritual.PBObjectAttributes.getDefaultInstance();
public boolean hasObjectAttributes() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Ritual.PBObjectAttributes getObjectAttributes() {
return objectAttributes_;
}
public Builder setObjectAttributes(Ritual.PBObjectAttributes value) {
if (value == null) {
throw new NullPointerException();
}
objectAttributes_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setObjectAttributes(
Ritual.PBObjectAttributes.Builder bdForValue) {
objectAttributes_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergeObjectAttributes(Ritual.PBObjectAttributes value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
objectAttributes_ != Ritual.PBObjectAttributes.getDefaultInstance()) {
objectAttributes_ =
Ritual.PBObjectAttributes.newBuilder(objectAttributes_).mergeFrom(value).buildPartial();
} else {
objectAttributes_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearObjectAttributes() {
objectAttributes_ = Ritual.PBObjectAttributes.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new GetObjectAttributesReply(true);
defaultInstance.initFields();
}
}
public interface GetChildrenAttributesCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class GetChildrenAttributesCall extends
GeneratedMessageLite implements
GetChildrenAttributesCallOrBuilder {
private GetChildrenAttributesCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetChildrenAttributesCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetChildrenAttributesCall defaultInstance;
public static GetChildrenAttributesCall getDefaultInstance() {
return defaultInstance;
}
public GetChildrenAttributesCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetChildrenAttributesCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<GetChildrenAttributesCall> PARSER =
new AbstractParser<GetChildrenAttributesCall>() {
public GetChildrenAttributesCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetChildrenAttributesCall(input, er);
}
};
@Override
public Parser<GetChildrenAttributesCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.GetChildrenAttributesCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetChildrenAttributesCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetChildrenAttributesCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetChildrenAttributesCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetChildrenAttributesCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetChildrenAttributesCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetChildrenAttributesCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetChildrenAttributesCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetChildrenAttributesCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetChildrenAttributesCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetChildrenAttributesCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetChildrenAttributesCall, Builder>
implements
Ritual.GetChildrenAttributesCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetChildrenAttributesCall getDefaultInstanceForType() {
return Ritual.GetChildrenAttributesCall.getDefaultInstance();
}
public Ritual.GetChildrenAttributesCall build() {
Ritual.GetChildrenAttributesCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetChildrenAttributesCall buildPartial() {
Ritual.GetChildrenAttributesCall result = new Ritual.GetChildrenAttributesCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetChildrenAttributesCall other) {
if (other == Ritual.GetChildrenAttributesCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetChildrenAttributesCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetChildrenAttributesCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new GetChildrenAttributesCall(true);
defaultInstance.initFields();
}
}
public interface GetChildrenAttributesReplyOrBuilder extends
MessageLiteOrBuilder {
ProtocolStringList
getChildrenNameList();
int getChildrenNameCount();
String getChildrenName(int index);
ByteString
getChildrenNameBytes(int index);
List<Ritual.PBObjectAttributes> 
getChildrenAttributesList();
Ritual.PBObjectAttributes getChildrenAttributes(int index);
int getChildrenAttributesCount();
}
public static final class GetChildrenAttributesReply extends
GeneratedMessageLite implements
GetChildrenAttributesReplyOrBuilder {
private GetChildrenAttributesReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetChildrenAttributesReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetChildrenAttributesReply defaultInstance;
public static GetChildrenAttributesReply getDefaultInstance() {
return defaultInstance;
}
public GetChildrenAttributesReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetChildrenAttributesReply(
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
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
childrenName_ = new LazyStringArrayList();
mutable_b0_ |= 0x00000001;
}
childrenName_.add(bs);
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
childrenAttributes_ = new ArrayList<Ritual.PBObjectAttributes>();
mutable_b0_ |= 0x00000002;
}
childrenAttributes_.add(input.readMessage(Ritual.PBObjectAttributes.PARSER, er));
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
childrenName_ = childrenName_.getUnmodifiableView();
}
if (((mutable_b0_ & 0x00000002) == 0x00000002)) {
childrenAttributes_ = Collections.unmodifiableList(childrenAttributes_);
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
public static Parser<GetChildrenAttributesReply> PARSER =
new AbstractParser<GetChildrenAttributesReply>() {
public GetChildrenAttributesReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetChildrenAttributesReply(input, er);
}
};
@Override
public Parser<GetChildrenAttributesReply> getParserForType() {
return PARSER;
}
public static final int CHILDREN_NAME_FIELD_NUMBER = 1;
private LazyStringList childrenName_;
public ProtocolStringList
getChildrenNameList() {
return childrenName_;
}
public int getChildrenNameCount() {
return childrenName_.size();
}
public String getChildrenName(int index) {
return childrenName_.get(index);
}
public ByteString
getChildrenNameBytes(int index) {
return childrenName_.getByteString(index);
}
public static final int CHILDREN_ATTRIBUTES_FIELD_NUMBER = 2;
private List<Ritual.PBObjectAttributes> childrenAttributes_;
public List<Ritual.PBObjectAttributes> getChildrenAttributesList() {
return childrenAttributes_;
}
public List<? extends Ritual.PBObjectAttributesOrBuilder> 
getChildrenAttributesOrBuilderList() {
return childrenAttributes_;
}
public int getChildrenAttributesCount() {
return childrenAttributes_.size();
}
public Ritual.PBObjectAttributes getChildrenAttributes(int index) {
return childrenAttributes_.get(index);
}
public Ritual.PBObjectAttributesOrBuilder getChildrenAttributesOrBuilder(
int index) {
return childrenAttributes_.get(index);
}
private void initFields() {
childrenName_ = LazyStringArrayList.EMPTY;
childrenAttributes_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getChildrenAttributesCount(); i++) {
if (!getChildrenAttributes(i).isInitialized()) {
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
for (int i = 0; i < childrenName_.size(); i++) {
output.writeBytes(1, childrenName_.getByteString(i));
}
for (int i = 0; i < childrenAttributes_.size(); i++) {
output.writeMessage(2, childrenAttributes_.get(i));
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
for (int i = 0; i < childrenName_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(childrenName_.getByteString(i));
}
size += dataSize;
size += 1 * getChildrenNameList().size();
}
for (int i = 0; i < childrenAttributes_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, childrenAttributes_.get(i));
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
public static Ritual.GetChildrenAttributesReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetChildrenAttributesReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetChildrenAttributesReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetChildrenAttributesReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetChildrenAttributesReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetChildrenAttributesReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetChildrenAttributesReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetChildrenAttributesReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetChildrenAttributesReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetChildrenAttributesReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetChildrenAttributesReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetChildrenAttributesReply, Builder>
implements
Ritual.GetChildrenAttributesReplyOrBuilder {
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
childrenName_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000001);
childrenAttributes_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetChildrenAttributesReply getDefaultInstanceForType() {
return Ritual.GetChildrenAttributesReply.getDefaultInstance();
}
public Ritual.GetChildrenAttributesReply build() {
Ritual.GetChildrenAttributesReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetChildrenAttributesReply buildPartial() {
Ritual.GetChildrenAttributesReply result = new Ritual.GetChildrenAttributesReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
childrenName_ = childrenName_.getUnmodifiableView();
b0_ = (b0_ & ~0x00000001);
}
result.childrenName_ = childrenName_;
if (((b0_ & 0x00000002) == 0x00000002)) {
childrenAttributes_ = Collections.unmodifiableList(childrenAttributes_);
b0_ = (b0_ & ~0x00000002);
}
result.childrenAttributes_ = childrenAttributes_;
return result;
}
public Builder mergeFrom(Ritual.GetChildrenAttributesReply other) {
if (other == Ritual.GetChildrenAttributesReply.getDefaultInstance()) return this;
if (!other.childrenName_.isEmpty()) {
if (childrenName_.isEmpty()) {
childrenName_ = other.childrenName_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureChildrenNameIsMutable();
childrenName_.addAll(other.childrenName_);
}
}
if (!other.childrenAttributes_.isEmpty()) {
if (childrenAttributes_.isEmpty()) {
childrenAttributes_ = other.childrenAttributes_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureChildrenAttributesIsMutable();
childrenAttributes_.addAll(other.childrenAttributes_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getChildrenAttributesCount(); i++) {
if (!getChildrenAttributes(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetChildrenAttributesReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetChildrenAttributesReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private LazyStringList childrenName_ = LazyStringArrayList.EMPTY;
private void ensureChildrenNameIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
childrenName_ = new LazyStringArrayList(childrenName_);
b0_ |= 0x00000001;
}
}
public ProtocolStringList
getChildrenNameList() {
return childrenName_.getUnmodifiableView();
}
public int getChildrenNameCount() {
return childrenName_.size();
}
public String getChildrenName(int index) {
return childrenName_.get(index);
}
public ByteString
getChildrenNameBytes(int index) {
return childrenName_.getByteString(index);
}
public Builder setChildrenName(
int index, String value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildrenNameIsMutable();
childrenName_.set(index, value);
return this;
}
public Builder addChildrenName(
String value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildrenNameIsMutable();
childrenName_.add(value);
return this;
}
public Builder addAllChildrenName(
Iterable<String> values) {
ensureChildrenNameIsMutable();
AbstractMessageLite.Builder.addAll(
values, childrenName_);
return this;
}
public Builder clearChildrenName() {
childrenName_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder addChildrenNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildrenNameIsMutable();
childrenName_.add(value);
return this;
}
private List<Ritual.PBObjectAttributes> childrenAttributes_ =
Collections.emptyList();
private void ensureChildrenAttributesIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
childrenAttributes_ = new ArrayList<Ritual.PBObjectAttributes>(childrenAttributes_);
b0_ |= 0x00000002;
}
}
public List<Ritual.PBObjectAttributes> getChildrenAttributesList() {
return Collections.unmodifiableList(childrenAttributes_);
}
public int getChildrenAttributesCount() {
return childrenAttributes_.size();
}
public Ritual.PBObjectAttributes getChildrenAttributes(int index) {
return childrenAttributes_.get(index);
}
public Builder setChildrenAttributes(
int index, Ritual.PBObjectAttributes value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildrenAttributesIsMutable();
childrenAttributes_.set(index, value);
return this;
}
public Builder setChildrenAttributes(
int index, Ritual.PBObjectAttributes.Builder bdForValue) {
ensureChildrenAttributesIsMutable();
childrenAttributes_.set(index, bdForValue.build());
return this;
}
public Builder addChildrenAttributes(Ritual.PBObjectAttributes value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildrenAttributesIsMutable();
childrenAttributes_.add(value);
return this;
}
public Builder addChildrenAttributes(
int index, Ritual.PBObjectAttributes value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildrenAttributesIsMutable();
childrenAttributes_.add(index, value);
return this;
}
public Builder addChildrenAttributes(
Ritual.PBObjectAttributes.Builder bdForValue) {
ensureChildrenAttributesIsMutable();
childrenAttributes_.add(bdForValue.build());
return this;
}
public Builder addChildrenAttributes(
int index, Ritual.PBObjectAttributes.Builder bdForValue) {
ensureChildrenAttributesIsMutable();
childrenAttributes_.add(index, bdForValue.build());
return this;
}
public Builder addAllChildrenAttributes(
Iterable<? extends Ritual.PBObjectAttributes> values) {
ensureChildrenAttributesIsMutable();
AbstractMessageLite.Builder.addAll(
values, childrenAttributes_);
return this;
}
public Builder clearChildrenAttributes() {
childrenAttributes_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder removeChildrenAttributes(int index) {
ensureChildrenAttributesIsMutable();
childrenAttributes_.remove(index);
return this;
}
}
static {
defaultInstance = new GetChildrenAttributesReply(true);
defaultInstance.initFields();
}
}
public interface PBObjectAttributesOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Ritual.PBObjectAttributes.Type getType();
List<Ritual.PBBranch> 
getBranchList();
Ritual.PBBranch getBranch(int index);
int getBranchCount();
boolean hasExcluded();
boolean getExcluded();
}
public static final class PBObjectAttributes extends
GeneratedMessageLite implements
PBObjectAttributesOrBuilder {
private PBObjectAttributes(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBObjectAttributes(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBObjectAttributes defaultInstance;
public static PBObjectAttributes getDefaultInstance() {
return defaultInstance;
}
public PBObjectAttributes getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBObjectAttributes(
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
Ritual.PBObjectAttributes.Type value = Ritual.PBObjectAttributes.Type.valueOf(rawValue);
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
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
branch_ = new ArrayList<Ritual.PBBranch>();
mutable_b0_ |= 0x00000002;
}
branch_.add(input.readMessage(Ritual.PBBranch.PARSER, er));
break;
}
case 24: {
b0_ |= 0x00000002;
excluded_ = input.readBool();
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
branch_ = Collections.unmodifiableList(branch_);
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
public static Parser<PBObjectAttributes> PARSER =
new AbstractParser<PBObjectAttributes>() {
public PBObjectAttributes parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBObjectAttributes(input, er);
}
};
@Override
public Parser<PBObjectAttributes> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
FILE(0, 0),
FOLDER(1, 1),
SHARED_FOLDER(2, 2),
;
public static final int FILE_VALUE = 0;
public static final int FOLDER_VALUE = 1;
public static final int SHARED_FOLDER_VALUE = 2;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return FILE;
case 1: return FOLDER;
case 2: return SHARED_FOLDER;
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
private Ritual.PBObjectAttributes.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Ritual.PBObjectAttributes.Type getType() {
return type_;
}
public static final int BRANCH_FIELD_NUMBER = 2;
private List<Ritual.PBBranch> branch_;
public List<Ritual.PBBranch> getBranchList() {
return branch_;
}
public List<? extends Ritual.PBBranchOrBuilder> 
getBranchOrBuilderList() {
return branch_;
}
public int getBranchCount() {
return branch_.size();
}
public Ritual.PBBranch getBranch(int index) {
return branch_.get(index);
}
public Ritual.PBBranchOrBuilder getBranchOrBuilder(
int index) {
return branch_.get(index);
}
public static final int EXCLUDED_FIELD_NUMBER = 3;
private boolean excluded_;
public boolean hasExcluded() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getExcluded() {
return excluded_;
}
private void initFields() {
type_ = Ritual.PBObjectAttributes.Type.FILE;
branch_ = Collections.emptyList();
excluded_ = false;
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
if (!hasExcluded()) {
mii = 0;
return false;
}
for (int i = 0; i < getBranchCount(); i++) {
if (!getBranch(i).isInitialized()) {
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
for (int i = 0; i < branch_.size(); i++) {
output.writeMessage(2, branch_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBool(3, excluded_);
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
for (int i = 0; i < branch_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, branch_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBoolSize(3, excluded_);
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
public static Ritual.PBObjectAttributes parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBObjectAttributes parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBObjectAttributes parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBObjectAttributes parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBObjectAttributes parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBObjectAttributes parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.PBObjectAttributes parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.PBObjectAttributes parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.PBObjectAttributes parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBObjectAttributes parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.PBObjectAttributes prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.PBObjectAttributes, Builder>
implements
Ritual.PBObjectAttributesOrBuilder {
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
type_ = Ritual.PBObjectAttributes.Type.FILE;
b0_ = (b0_ & ~0x00000001);
branch_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
excluded_ = false;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.PBObjectAttributes getDefaultInstanceForType() {
return Ritual.PBObjectAttributes.getDefaultInstance();
}
public Ritual.PBObjectAttributes build() {
Ritual.PBObjectAttributes result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.PBObjectAttributes buildPartial() {
Ritual.PBObjectAttributes result = new Ritual.PBObjectAttributes(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((b0_ & 0x00000002) == 0x00000002)) {
branch_ = Collections.unmodifiableList(branch_);
b0_ = (b0_ & ~0x00000002);
}
result.branch_ = branch_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000002;
}
result.excluded_ = excluded_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.PBObjectAttributes other) {
if (other == Ritual.PBObjectAttributes.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (!other.branch_.isEmpty()) {
if (branch_.isEmpty()) {
branch_ = other.branch_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureBranchIsMutable();
branch_.addAll(other.branch_);
}
}
if (other.hasExcluded()) {
setExcluded(other.getExcluded());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (!hasExcluded()) {
return false;
}
for (int i = 0; i < getBranchCount(); i++) {
if (!getBranch(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.PBObjectAttributes pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.PBObjectAttributes) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Ritual.PBObjectAttributes.Type type_ = Ritual.PBObjectAttributes.Type.FILE;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Ritual.PBObjectAttributes.Type getType() {
return type_;
}
public Builder setType(Ritual.PBObjectAttributes.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Ritual.PBObjectAttributes.Type.FILE;
return this;
}
private List<Ritual.PBBranch> branch_ =
Collections.emptyList();
private void ensureBranchIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
branch_ = new ArrayList<Ritual.PBBranch>(branch_);
b0_ |= 0x00000002;
}
}
public List<Ritual.PBBranch> getBranchList() {
return Collections.unmodifiableList(branch_);
}
public int getBranchCount() {
return branch_.size();
}
public Ritual.PBBranch getBranch(int index) {
return branch_.get(index);
}
public Builder setBranch(
int index, Ritual.PBBranch value) {
if (value == null) {
throw new NullPointerException();
}
ensureBranchIsMutable();
branch_.set(index, value);
return this;
}
public Builder setBranch(
int index, Ritual.PBBranch.Builder bdForValue) {
ensureBranchIsMutable();
branch_.set(index, bdForValue.build());
return this;
}
public Builder addBranch(Ritual.PBBranch value) {
if (value == null) {
throw new NullPointerException();
}
ensureBranchIsMutable();
branch_.add(value);
return this;
}
public Builder addBranch(
int index, Ritual.PBBranch value) {
if (value == null) {
throw new NullPointerException();
}
ensureBranchIsMutable();
branch_.add(index, value);
return this;
}
public Builder addBranch(
Ritual.PBBranch.Builder bdForValue) {
ensureBranchIsMutable();
branch_.add(bdForValue.build());
return this;
}
public Builder addBranch(
int index, Ritual.PBBranch.Builder bdForValue) {
ensureBranchIsMutable();
branch_.add(index, bdForValue.build());
return this;
}
public Builder addAllBranch(
Iterable<? extends Ritual.PBBranch> values) {
ensureBranchIsMutable();
AbstractMessageLite.Builder.addAll(
values, branch_);
return this;
}
public Builder clearBranch() {
branch_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder removeBranch(int index) {
ensureBranchIsMutable();
branch_.remove(index);
return this;
}
private boolean excluded_ ;
public boolean hasExcluded() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public boolean getExcluded() {
return excluded_;
}
public Builder setExcluded(boolean value) {
b0_ |= 0x00000004;
excluded_ = value;
return this;
}
public Builder clearExcluded() {
b0_ = (b0_ & ~0x00000004);
excluded_ = false;
return this;
}
}
static {
defaultInstance = new PBObjectAttributes(true);
defaultInstance.initFields();
}
}
public interface PBBranchOrBuilder extends
MessageLiteOrBuilder {
boolean hasKidx();
int getKidx();
boolean hasLength();
long getLength();
boolean hasMtime();
long getMtime();
boolean hasContributor();
Ritual.PBBranch.PBPeer getContributor();
}
public static final class PBBranch extends
GeneratedMessageLite implements
PBBranchOrBuilder {
private PBBranch(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBBranch(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBBranch defaultInstance;
public static PBBranch getDefaultInstance() {
return defaultInstance;
}
public PBBranch getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBBranch(
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
kidx_ = input.readUInt32();
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
Ritual.PBBranch.PBPeer.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = contributor_.toBuilder();
}
contributor_ = input.readMessage(Ritual.PBBranch.PBPeer.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(contributor_);
contributor_ = subBuilder.buildPartial();
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
public static Parser<PBBranch> PARSER =
new AbstractParser<PBBranch>() {
public PBBranch parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBBranch(input, er);
}
};
@Override
public Parser<PBBranch> getParserForType() {
return PARSER;
}
public interface PBPeerOrBuilder extends
MessageLiteOrBuilder {
boolean hasUserName();
String getUserName();
ByteString
getUserNameBytes();
boolean hasDeviceName();
String getDeviceName();
ByteString
getDeviceNameBytes();
}
public static final class PBPeer extends
GeneratedMessageLite implements
PBPeerOrBuilder {
private PBPeer(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBPeer(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBPeer defaultInstance;
public static PBPeer getDefaultInstance() {
return defaultInstance;
}
public PBPeer getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBPeer(
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
userName_ = bs;
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
deviceName_ = bs;
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
public static Parser<PBPeer> PARSER =
new AbstractParser<PBPeer>() {
public PBPeer parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBPeer(input, er);
}
};
@Override
public Parser<PBPeer> getParserForType() {
return PARSER;
}
private int b0_;
public static final int USER_NAME_FIELD_NUMBER = 1;
private Object userName_;
public boolean hasUserName() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getUserName() {
Object ref = userName_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
userName_ = s;
}
return s;
}
}
public ByteString
getUserNameBytes() {
Object ref = userName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
userName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int DEVICE_NAME_FIELD_NUMBER = 2;
private Object deviceName_;
public boolean hasDeviceName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getDeviceName() {
Object ref = deviceName_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
deviceName_ = s;
}
return s;
}
}
public ByteString
getDeviceNameBytes() {
Object ref = deviceName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
deviceName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
userName_ = "";
deviceName_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasUserName()) {
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
output.writeBytes(1, getUserNameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getDeviceNameBytes());
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
.computeBytesSize(1, getUserNameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getDeviceNameBytes());
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
public static Ritual.PBBranch.PBPeer parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBBranch.PBPeer parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBBranch.PBPeer parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBBranch.PBPeer parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBBranch.PBPeer parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBBranch.PBPeer parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.PBBranch.PBPeer parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.PBBranch.PBPeer parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.PBBranch.PBPeer parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBBranch.PBPeer parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.PBBranch.PBPeer prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.PBBranch.PBPeer, Builder>
implements
Ritual.PBBranch.PBPeerOrBuilder {
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
userName_ = "";
b0_ = (b0_ & ~0x00000001);
deviceName_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.PBBranch.PBPeer getDefaultInstanceForType() {
return Ritual.PBBranch.PBPeer.getDefaultInstance();
}
public Ritual.PBBranch.PBPeer build() {
Ritual.PBBranch.PBPeer result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.PBBranch.PBPeer buildPartial() {
Ritual.PBBranch.PBPeer result = new Ritual.PBBranch.PBPeer(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.userName_ = userName_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.deviceName_ = deviceName_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.PBBranch.PBPeer other) {
if (other == Ritual.PBBranch.PBPeer.getDefaultInstance()) return this;
if (other.hasUserName()) {
b0_ |= 0x00000001;
userName_ = other.userName_;
}
if (other.hasDeviceName()) {
b0_ |= 0x00000002;
deviceName_ = other.deviceName_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasUserName()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.PBBranch.PBPeer pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.PBBranch.PBPeer) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object userName_ = "";
public boolean hasUserName() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getUserName() {
Object ref = userName_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
userName_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getUserNameBytes() {
Object ref = userName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
userName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setUserName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
userName_ = value;
return this;
}
public Builder clearUserName() {
b0_ = (b0_ & ~0x00000001);
userName_ = getDefaultInstance().getUserName();
return this;
}
public Builder setUserNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
userName_ = value;
return this;
}
private Object deviceName_ = "";
public boolean hasDeviceName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getDeviceName() {
Object ref = deviceName_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
deviceName_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDeviceNameBytes() {
Object ref = deviceName_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
deviceName_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDeviceName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
deviceName_ = value;
return this;
}
public Builder clearDeviceName() {
b0_ = (b0_ & ~0x00000002);
deviceName_ = getDefaultInstance().getDeviceName();
return this;
}
public Builder setDeviceNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
deviceName_ = value;
return this;
}
}
static {
defaultInstance = new PBPeer(true);
defaultInstance.initFields();
}
}
private int b0_;
public static final int KIDX_FIELD_NUMBER = 1;
private int kidx_;
public boolean hasKidx() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getKidx() {
return kidx_;
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
public static final int CONTRIBUTOR_FIELD_NUMBER = 4;
private Ritual.PBBranch.PBPeer contributor_;
public boolean hasContributor() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Ritual.PBBranch.PBPeer getContributor() {
return contributor_;
}
private void initFields() {
kidx_ = 0;
length_ = 0L;
mtime_ = 0L;
contributor_ = Ritual.PBBranch.PBPeer.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasKidx()) {
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
if (hasContributor()) {
if (!getContributor().isInitialized()) {
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
output.writeUInt32(1, kidx_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, length_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, mtime_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, contributor_);
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
.computeUInt32Size(1, kidx_);
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
.computeMessageSize(4, contributor_);
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
public static Ritual.PBBranch parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBBranch parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBBranch parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBBranch parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBBranch parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBBranch parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.PBBranch parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.PBBranch parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.PBBranch parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBBranch parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.PBBranch prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.PBBranch, Builder>
implements
Ritual.PBBranchOrBuilder {
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
kidx_ = 0;
b0_ = (b0_ & ~0x00000001);
length_ = 0L;
b0_ = (b0_ & ~0x00000002);
mtime_ = 0L;
b0_ = (b0_ & ~0x00000004);
contributor_ = Ritual.PBBranch.PBPeer.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.PBBranch getDefaultInstanceForType() {
return Ritual.PBBranch.getDefaultInstance();
}
public Ritual.PBBranch build() {
Ritual.PBBranch result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.PBBranch buildPartial() {
Ritual.PBBranch result = new Ritual.PBBranch(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.kidx_ = kidx_;
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
result.contributor_ = contributor_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.PBBranch other) {
if (other == Ritual.PBBranch.getDefaultInstance()) return this;
if (other.hasKidx()) {
setKidx(other.getKidx());
}
if (other.hasLength()) {
setLength(other.getLength());
}
if (other.hasMtime()) {
setMtime(other.getMtime());
}
if (other.hasContributor()) {
mergeContributor(other.getContributor());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasKidx()) {
return false;
}
if (!hasLength()) {
return false;
}
if (!hasMtime()) {
return false;
}
if (hasContributor()) {
if (!getContributor().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.PBBranch pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.PBBranch) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int kidx_ ;
public boolean hasKidx() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getKidx() {
return kidx_;
}
public Builder setKidx(int value) {
b0_ |= 0x00000001;
kidx_ = value;
return this;
}
public Builder clearKidx() {
b0_ = (b0_ & ~0x00000001);
kidx_ = 0;
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
private Ritual.PBBranch.PBPeer contributor_ = Ritual.PBBranch.PBPeer.getDefaultInstance();
public boolean hasContributor() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Ritual.PBBranch.PBPeer getContributor() {
return contributor_;
}
public Builder setContributor(Ritual.PBBranch.PBPeer value) {
if (value == null) {
throw new NullPointerException();
}
contributor_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setContributor(
Ritual.PBBranch.PBPeer.Builder bdForValue) {
contributor_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergeContributor(Ritual.PBBranch.PBPeer value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
contributor_ != Ritual.PBBranch.PBPeer.getDefaultInstance()) {
contributor_ =
Ritual.PBBranch.PBPeer.newBuilder(contributor_).mergeFrom(value).buildPartial();
} else {
contributor_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearContributor() {
contributor_ = Ritual.PBBranch.PBPeer.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
}
static {
defaultInstance = new PBBranch(true);
defaultInstance.initFields();
}
}
public interface ListNonRepresentableObjectsReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject> 
getObjectsList();
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject getObjects(int index);
int getObjectsCount();
}
public static final class ListNonRepresentableObjectsReply extends
GeneratedMessageLite implements
ListNonRepresentableObjectsReplyOrBuilder {
private ListNonRepresentableObjectsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListNonRepresentableObjectsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListNonRepresentableObjectsReply defaultInstance;
public static ListNonRepresentableObjectsReply getDefaultInstance() {
return defaultInstance;
}
public ListNonRepresentableObjectsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListNonRepresentableObjectsReply(
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
objects_ = new ArrayList<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject>();
mutable_b0_ |= 0x00000001;
}
objects_.add(input.readMessage(Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject.PARSER, er));
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
objects_ = Collections.unmodifiableList(objects_);
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
public static Parser<ListNonRepresentableObjectsReply> PARSER =
new AbstractParser<ListNonRepresentableObjectsReply>() {
public ListNonRepresentableObjectsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListNonRepresentableObjectsReply(input, er);
}
};
@Override
public Parser<ListNonRepresentableObjectsReply> getParserForType() {
return PARSER;
}
public interface PBNonRepresentableObjectOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasReason();
String getReason();
ByteString
getReasonBytes();
}
public static final class PBNonRepresentableObject extends
GeneratedMessageLite implements
PBNonRepresentableObjectOrBuilder {
private PBNonRepresentableObject(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBNonRepresentableObject(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBNonRepresentableObject defaultInstance;
public static PBNonRepresentableObject getDefaultInstance() {
return defaultInstance;
}
public PBNonRepresentableObject getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBNonRepresentableObject(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
reason_ = bs;
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
public static Parser<PBNonRepresentableObject> PARSER =
new AbstractParser<PBNonRepresentableObject>() {
public PBNonRepresentableObject parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBNonRepresentableObject(input, er);
}
};
@Override
public Parser<PBNonRepresentableObject> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int REASON_FIELD_NUMBER = 2;
private Object reason_;
public boolean hasReason() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getReason() {
Object ref = reason_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
reason_ = s;
}
return s;
}
}
public ByteString
getReasonBytes() {
Object ref = reason_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
reason_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
reason_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasReason()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getReasonBytes());
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getReasonBytes());
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
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject, Builder>
implements
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObjectOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
reason_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject getDefaultInstanceForType() {
return Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject.getDefaultInstance();
}
public Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject build() {
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject buildPartial() {
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject result = new Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.reason_ = reason_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject other) {
if (other == Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasReason()) {
b0_ |= 0x00000002;
reason_ = other.reason_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasReason()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Object reason_ = "";
public boolean hasReason() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getReason() {
Object ref = reason_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
reason_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getReasonBytes() {
Object ref = reason_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
reason_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setReason(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
reason_ = value;
return this;
}
public Builder clearReason() {
b0_ = (b0_ & ~0x00000002);
reason_ = getDefaultInstance().getReason();
return this;
}
public Builder setReasonBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
reason_ = value;
return this;
}
}
static {
defaultInstance = new PBNonRepresentableObject(true);
defaultInstance.initFields();
}
}
public static final int OBJECTS_FIELD_NUMBER = 1;
private List<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject> objects_;
public List<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject> getObjectsList() {
return objects_;
}
public List<? extends Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObjectOrBuilder> 
getObjectsOrBuilderList() {
return objects_;
}
public int getObjectsCount() {
return objects_.size();
}
public Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject getObjects(int index) {
return objects_.get(index);
}
public Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObjectOrBuilder getObjectsOrBuilder(
int index) {
return objects_.get(index);
}
private void initFields() {
objects_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getObjectsCount(); i++) {
if (!getObjects(i).isInitialized()) {
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
for (int i = 0; i < objects_.size(); i++) {
output.writeMessage(1, objects_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < objects_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, objects_.get(i));
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
public static Ritual.ListNonRepresentableObjectsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListNonRepresentableObjectsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListNonRepresentableObjectsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListNonRepresentableObjectsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListNonRepresentableObjectsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListNonRepresentableObjectsReply, Builder>
implements
Ritual.ListNonRepresentableObjectsReplyOrBuilder {
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
objects_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListNonRepresentableObjectsReply getDefaultInstanceForType() {
return Ritual.ListNonRepresentableObjectsReply.getDefaultInstance();
}
public Ritual.ListNonRepresentableObjectsReply build() {
Ritual.ListNonRepresentableObjectsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListNonRepresentableObjectsReply buildPartial() {
Ritual.ListNonRepresentableObjectsReply result = new Ritual.ListNonRepresentableObjectsReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
objects_ = Collections.unmodifiableList(objects_);
b0_ = (b0_ & ~0x00000001);
}
result.objects_ = objects_;
return result;
}
public Builder mergeFrom(Ritual.ListNonRepresentableObjectsReply other) {
if (other == Ritual.ListNonRepresentableObjectsReply.getDefaultInstance()) return this;
if (!other.objects_.isEmpty()) {
if (objects_.isEmpty()) {
objects_ = other.objects_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureObjectsIsMutable();
objects_.addAll(other.objects_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getObjectsCount(); i++) {
if (!getObjects(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListNonRepresentableObjectsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListNonRepresentableObjectsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject> objects_ =
Collections.emptyList();
private void ensureObjectsIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
objects_ = new ArrayList<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject>(objects_);
b0_ |= 0x00000001;
}
}
public List<Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject> getObjectsList() {
return Collections.unmodifiableList(objects_);
}
public int getObjectsCount() {
return objects_.size();
}
public Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject getObjects(int index) {
return objects_.get(index);
}
public Builder setObjects(
int index, Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject value) {
if (value == null) {
throw new NullPointerException();
}
ensureObjectsIsMutable();
objects_.set(index, value);
return this;
}
public Builder setObjects(
int index, Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject.Builder bdForValue) {
ensureObjectsIsMutable();
objects_.set(index, bdForValue.build());
return this;
}
public Builder addObjects(Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject value) {
if (value == null) {
throw new NullPointerException();
}
ensureObjectsIsMutable();
objects_.add(value);
return this;
}
public Builder addObjects(
int index, Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject value) {
if (value == null) {
throw new NullPointerException();
}
ensureObjectsIsMutable();
objects_.add(index, value);
return this;
}
public Builder addObjects(
Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject.Builder bdForValue) {
ensureObjectsIsMutable();
objects_.add(bdForValue.build());
return this;
}
public Builder addObjects(
int index, Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject.Builder bdForValue) {
ensureObjectsIsMutable();
objects_.add(index, bdForValue.build());
return this;
}
public Builder addAllObjects(
Iterable<? extends Ritual.ListNonRepresentableObjectsReply.PBNonRepresentableObject> values) {
ensureObjectsIsMutable();
AbstractMessageLite.Builder.addAll(
values, objects_);
return this;
}
public Builder clearObjects() {
objects_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeObjects(int index) {
ensureObjectsIsMutable();
objects_.remove(index);
return this;
}
}
static {
defaultInstance = new ListNonRepresentableObjectsReply(true);
defaultInstance.initFields();
}
}
public interface CreateObjectCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasDir();
boolean getDir();
}
public static final class CreateObjectCall extends
GeneratedMessageLite implements
CreateObjectCallOrBuilder {
private CreateObjectCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateObjectCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateObjectCall defaultInstance;
public static CreateObjectCall getDefaultInstance() {
return defaultInstance;
}
public CreateObjectCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateObjectCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 16: {
b0_ |= 0x00000002;
dir_ = input.readBool();
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
public static Parser<CreateObjectCall> PARSER =
new AbstractParser<CreateObjectCall>() {
public CreateObjectCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateObjectCall(input, er);
}
};
@Override
public Parser<CreateObjectCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int DIR_FIELD_NUMBER = 2;
private boolean dir_;
public boolean hasDir() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getDir() {
return dir_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
dir_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasDir()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBool(2, dir_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBoolSize(2, dir_);
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
public static Ritual.CreateObjectCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateObjectCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateObjectCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateObjectCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateObjectCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateObjectCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateObjectCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateObjectCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateObjectCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateObjectCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateObjectCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateObjectCall, Builder>
implements
Ritual.CreateObjectCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
dir_ = false;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateObjectCall getDefaultInstanceForType() {
return Ritual.CreateObjectCall.getDefaultInstance();
}
public Ritual.CreateObjectCall build() {
Ritual.CreateObjectCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateObjectCall buildPartial() {
Ritual.CreateObjectCall result = new Ritual.CreateObjectCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.dir_ = dir_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateObjectCall other) {
if (other == Ritual.CreateObjectCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasDir()) {
setDir(other.getDir());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasDir()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.CreateObjectCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateObjectCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private boolean dir_ ;
public boolean hasDir() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getDir() {
return dir_;
}
public Builder setDir(boolean value) {
b0_ |= 0x00000002;
dir_ = value;
return this;
}
public Builder clearDir() {
b0_ = (b0_ & ~0x00000002);
dir_ = false;
return this;
}
}
static {
defaultInstance = new CreateObjectCall(true);
defaultInstance.initFields();
}
}
public interface DeleteObjectCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class DeleteObjectCall extends
GeneratedMessageLite implements
DeleteObjectCallOrBuilder {
private DeleteObjectCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DeleteObjectCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final DeleteObjectCall defaultInstance;
public static DeleteObjectCall getDefaultInstance() {
return defaultInstance;
}
public DeleteObjectCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private DeleteObjectCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<DeleteObjectCall> PARSER =
new AbstractParser<DeleteObjectCall>() {
public DeleteObjectCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DeleteObjectCall(input, er);
}
};
@Override
public Parser<DeleteObjectCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.DeleteObjectCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteObjectCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteObjectCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteObjectCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteObjectCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteObjectCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.DeleteObjectCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.DeleteObjectCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.DeleteObjectCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteObjectCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.DeleteObjectCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.DeleteObjectCall, Builder>
implements
Ritual.DeleteObjectCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.DeleteObjectCall getDefaultInstanceForType() {
return Ritual.DeleteObjectCall.getDefaultInstance();
}
public Ritual.DeleteObjectCall build() {
Ritual.DeleteObjectCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.DeleteObjectCall buildPartial() {
Ritual.DeleteObjectCall result = new Ritual.DeleteObjectCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.DeleteObjectCall other) {
if (other == Ritual.DeleteObjectCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.DeleteObjectCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.DeleteObjectCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new DeleteObjectCall(true);
defaultInstance.initFields();
}
}
public interface MoveObjectCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPathFrom();
Common.PBPath getPathFrom();
boolean hasPathTo();
Common.PBPath getPathTo();
}
public static final class MoveObjectCall extends
GeneratedMessageLite implements
MoveObjectCallOrBuilder {
private MoveObjectCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private MoveObjectCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final MoveObjectCall defaultInstance;
public static MoveObjectCall getDefaultInstance() {
return defaultInstance;
}
public MoveObjectCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private MoveObjectCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = pathFrom_.toBuilder();
}
pathFrom_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(pathFrom_);
pathFrom_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = pathTo_.toBuilder();
}
pathTo_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(pathTo_);
pathTo_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
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
public static Parser<MoveObjectCall> PARSER =
new AbstractParser<MoveObjectCall>() {
public MoveObjectCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new MoveObjectCall(input, er);
}
};
@Override
public Parser<MoveObjectCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATHFROM_FIELD_NUMBER = 1;
private Common.PBPath pathFrom_;
public boolean hasPathFrom() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPathFrom() {
return pathFrom_;
}
public static final int PATHTO_FIELD_NUMBER = 2;
private Common.PBPath pathTo_;
public boolean hasPathTo() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Common.PBPath getPathTo() {
return pathTo_;
}
private void initFields() {
pathFrom_ = Common.PBPath.getDefaultInstance();
pathTo_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPathFrom()) {
mii = 0;
return false;
}
if (!hasPathTo()) {
mii = 0;
return false;
}
if (!getPathFrom().isInitialized()) {
mii = 0;
return false;
}
if (!getPathTo().isInitialized()) {
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
output.writeMessage(1, pathFrom_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeMessage(2, pathTo_);
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
.computeMessageSize(1, pathFrom_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeMessageSize(2, pathTo_);
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
public static Ritual.MoveObjectCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.MoveObjectCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.MoveObjectCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.MoveObjectCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.MoveObjectCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.MoveObjectCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.MoveObjectCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.MoveObjectCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.MoveObjectCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.MoveObjectCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.MoveObjectCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.MoveObjectCall, Builder>
implements
Ritual.MoveObjectCallOrBuilder {
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
pathFrom_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
pathTo_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.MoveObjectCall getDefaultInstanceForType() {
return Ritual.MoveObjectCall.getDefaultInstance();
}
public Ritual.MoveObjectCall build() {
Ritual.MoveObjectCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.MoveObjectCall buildPartial() {
Ritual.MoveObjectCall result = new Ritual.MoveObjectCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.pathFrom_ = pathFrom_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.pathTo_ = pathTo_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.MoveObjectCall other) {
if (other == Ritual.MoveObjectCall.getDefaultInstance()) return this;
if (other.hasPathFrom()) {
mergePathFrom(other.getPathFrom());
}
if (other.hasPathTo()) {
mergePathTo(other.getPathTo());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPathFrom()) {
return false;
}
if (!hasPathTo()) {
return false;
}
if (!getPathFrom().isInitialized()) {
return false;
}
if (!getPathTo().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.MoveObjectCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.MoveObjectCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath pathFrom_ = Common.PBPath.getDefaultInstance();
public boolean hasPathFrom() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPathFrom() {
return pathFrom_;
}
public Builder setPathFrom(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
pathFrom_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPathFrom(
Common.PBPath.Builder bdForValue) {
pathFrom_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePathFrom(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
pathFrom_ != Common.PBPath.getDefaultInstance()) {
pathFrom_ =
Common.PBPath.newBuilder(pathFrom_).mergeFrom(value).buildPartial();
} else {
pathFrom_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPathFrom() {
pathFrom_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Common.PBPath pathTo_ = Common.PBPath.getDefaultInstance();
public boolean hasPathTo() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Common.PBPath getPathTo() {
return pathTo_;
}
public Builder setPathTo(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
pathTo_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setPathTo(
Common.PBPath.Builder bdForValue) {
pathTo_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergePathTo(Common.PBPath value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
pathTo_ != Common.PBPath.getDefaultInstance()) {
pathTo_ =
Common.PBPath.newBuilder(pathTo_).mergeFrom(value).buildPartial();
} else {
pathTo_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearPathTo() {
pathTo_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
}
static {
defaultInstance = new MoveObjectCall(true);
defaultInstance.initFields();
}
}
public interface DumpStatsCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasTemplate();
Diagnostics.PBDumpStat getTemplate();
}
public static final class DumpStatsCall extends
GeneratedMessageLite implements
DumpStatsCallOrBuilder {
private DumpStatsCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DumpStatsCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final DumpStatsCall defaultInstance;
public static DumpStatsCall getDefaultInstance() {
return defaultInstance;
}
public DumpStatsCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private DumpStatsCall(
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
Diagnostics.PBDumpStat.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = template_.toBuilder();
}
template_ = input.readMessage(Diagnostics.PBDumpStat.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(template_);
template_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<DumpStatsCall> PARSER =
new AbstractParser<DumpStatsCall>() {
public DumpStatsCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DumpStatsCall(input, er);
}
};
@Override
public Parser<DumpStatsCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int TEMPLATE_FIELD_NUMBER = 1;
private Diagnostics.PBDumpStat template_;
public boolean hasTemplate() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBDumpStat getTemplate() {
return template_;
}
private void initFields() {
template_ = Diagnostics.PBDumpStat.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasTemplate()) {
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
output.writeMessage(1, template_);
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
.computeMessageSize(1, template_);
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
public static Ritual.DumpStatsCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DumpStatsCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DumpStatsCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DumpStatsCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DumpStatsCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DumpStatsCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.DumpStatsCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.DumpStatsCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.DumpStatsCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DumpStatsCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.DumpStatsCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.DumpStatsCall, Builder>
implements
Ritual.DumpStatsCallOrBuilder {
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
template_ = Diagnostics.PBDumpStat.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.DumpStatsCall getDefaultInstanceForType() {
return Ritual.DumpStatsCall.getDefaultInstance();
}
public Ritual.DumpStatsCall build() {
Ritual.DumpStatsCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.DumpStatsCall buildPartial() {
Ritual.DumpStatsCall result = new Ritual.DumpStatsCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.template_ = template_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.DumpStatsCall other) {
if (other == Ritual.DumpStatsCall.getDefaultInstance()) return this;
if (other.hasTemplate()) {
mergeTemplate(other.getTemplate());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasTemplate()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.DumpStatsCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.DumpStatsCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.PBDumpStat template_ = Diagnostics.PBDumpStat.getDefaultInstance();
public boolean hasTemplate() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBDumpStat getTemplate() {
return template_;
}
public Builder setTemplate(Diagnostics.PBDumpStat value) {
if (value == null) {
throw new NullPointerException();
}
template_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setTemplate(
Diagnostics.PBDumpStat.Builder bdForValue) {
template_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergeTemplate(Diagnostics.PBDumpStat value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
template_ != Diagnostics.PBDumpStat.getDefaultInstance()) {
template_ =
Diagnostics.PBDumpStat.newBuilder(template_).mergeFrom(value).buildPartial();
} else {
template_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearTemplate() {
template_ = Diagnostics.PBDumpStat.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new DumpStatsCall(true);
defaultInstance.initFields();
}
}
public interface DumpStatsReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasStats();
Diagnostics.PBDumpStat getStats();
}
public static final class DumpStatsReply extends
GeneratedMessageLite implements
DumpStatsReplyOrBuilder {
private DumpStatsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DumpStatsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final DumpStatsReply defaultInstance;
public static DumpStatsReply getDefaultInstance() {
return defaultInstance;
}
public DumpStatsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private DumpStatsReply(
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
Diagnostics.PBDumpStat.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = stats_.toBuilder();
}
stats_ = input.readMessage(Diagnostics.PBDumpStat.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(stats_);
stats_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<DumpStatsReply> PARSER =
new AbstractParser<DumpStatsReply>() {
public DumpStatsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DumpStatsReply(input, er);
}
};
@Override
public Parser<DumpStatsReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STATS_FIELD_NUMBER = 1;
private Diagnostics.PBDumpStat stats_;
public boolean hasStats() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBDumpStat getStats() {
return stats_;
}
private void initFields() {
stats_ = Diagnostics.PBDumpStat.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStats()) {
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
output.writeMessage(1, stats_);
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
.computeMessageSize(1, stats_);
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
public static Ritual.DumpStatsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DumpStatsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DumpStatsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DumpStatsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DumpStatsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DumpStatsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.DumpStatsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.DumpStatsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.DumpStatsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DumpStatsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.DumpStatsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.DumpStatsReply, Builder>
implements
Ritual.DumpStatsReplyOrBuilder {
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
stats_ = Diagnostics.PBDumpStat.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.DumpStatsReply getDefaultInstanceForType() {
return Ritual.DumpStatsReply.getDefaultInstance();
}
public Ritual.DumpStatsReply build() {
Ritual.DumpStatsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.DumpStatsReply buildPartial() {
Ritual.DumpStatsReply result = new Ritual.DumpStatsReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.stats_ = stats_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.DumpStatsReply other) {
if (other == Ritual.DumpStatsReply.getDefaultInstance()) return this;
if (other.hasStats()) {
mergeStats(other.getStats());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasStats()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.DumpStatsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.DumpStatsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.PBDumpStat stats_ = Diagnostics.PBDumpStat.getDefaultInstance();
public boolean hasStats() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBDumpStat getStats() {
return stats_;
}
public Builder setStats(Diagnostics.PBDumpStat value) {
if (value == null) {
throw new NullPointerException();
}
stats_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setStats(
Diagnostics.PBDumpStat.Builder bdForValue) {
stats_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergeStats(Diagnostics.PBDumpStat value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
stats_ != Diagnostics.PBDumpStat.getDefaultInstance()) {
stats_ =
Diagnostics.PBDumpStat.newBuilder(stats_).mergeFrom(value).buildPartial();
} else {
stats_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearStats() {
stats_ = Diagnostics.PBDumpStat.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new DumpStatsReply(true);
defaultInstance.initFields();
}
}
public interface GetExcludedFoldersCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class GetExcludedFoldersCall extends
GeneratedMessageLite implements
GetExcludedFoldersCallOrBuilder {
private GetExcludedFoldersCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetExcludedFoldersCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetExcludedFoldersCall defaultInstance;
public static GetExcludedFoldersCall getDefaultInstance() {
return defaultInstance;
}
public GetExcludedFoldersCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetExcludedFoldersCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<GetExcludedFoldersCall> PARSER =
new AbstractParser<GetExcludedFoldersCall>() {
public GetExcludedFoldersCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetExcludedFoldersCall(input, er);
}
};
@Override
public Parser<GetExcludedFoldersCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.GetExcludedFoldersCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetExcludedFoldersCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetExcludedFoldersCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetExcludedFoldersCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetExcludedFoldersCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetExcludedFoldersCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetExcludedFoldersCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetExcludedFoldersCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetExcludedFoldersCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetExcludedFoldersCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetExcludedFoldersCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetExcludedFoldersCall, Builder>
implements
Ritual.GetExcludedFoldersCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetExcludedFoldersCall getDefaultInstanceForType() {
return Ritual.GetExcludedFoldersCall.getDefaultInstance();
}
public Ritual.GetExcludedFoldersCall build() {
Ritual.GetExcludedFoldersCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetExcludedFoldersCall buildPartial() {
Ritual.GetExcludedFoldersCall result = new Ritual.GetExcludedFoldersCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetExcludedFoldersCall other) {
if (other == Ritual.GetExcludedFoldersCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetExcludedFoldersCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetExcludedFoldersCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new GetExcludedFoldersCall(true);
defaultInstance.initFields();
}
}
public interface GetExcludedFoldersReplyOrBuilder extends
MessageLiteOrBuilder {
ProtocolStringList
getNameList();
int getNameCount();
String getName(int index);
ByteString
getNameBytes(int index);
}
public static final class GetExcludedFoldersReply extends
GeneratedMessageLite implements
GetExcludedFoldersReplyOrBuilder {
private GetExcludedFoldersReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetExcludedFoldersReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetExcludedFoldersReply defaultInstance;
public static GetExcludedFoldersReply getDefaultInstance() {
return defaultInstance;
}
public GetExcludedFoldersReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetExcludedFoldersReply(
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
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
name_ = new LazyStringArrayList();
mutable_b0_ |= 0x00000001;
}
name_.add(bs);
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
name_ = name_.getUnmodifiableView();
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
public static Parser<GetExcludedFoldersReply> PARSER =
new AbstractParser<GetExcludedFoldersReply>() {
public GetExcludedFoldersReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetExcludedFoldersReply(input, er);
}
};
@Override
public Parser<GetExcludedFoldersReply> getParserForType() {
return PARSER;
}
public static final int NAME_FIELD_NUMBER = 1;
private LazyStringList name_;
public ProtocolStringList
getNameList() {
return name_;
}
public int getNameCount() {
return name_.size();
}
public String getName(int index) {
return name_.get(index);
}
public ByteString
getNameBytes(int index) {
return name_.getByteString(index);
}
private void initFields() {
name_ = LazyStringArrayList.EMPTY;
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
for (int i = 0; i < name_.size(); i++) {
output.writeBytes(1, name_.getByteString(i));
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
for (int i = 0; i < name_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(name_.getByteString(i));
}
size += dataSize;
size += 1 * getNameList().size();
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
public static Ritual.GetExcludedFoldersReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetExcludedFoldersReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetExcludedFoldersReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetExcludedFoldersReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetExcludedFoldersReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetExcludedFoldersReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetExcludedFoldersReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetExcludedFoldersReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetExcludedFoldersReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetExcludedFoldersReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetExcludedFoldersReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetExcludedFoldersReply, Builder>
implements
Ritual.GetExcludedFoldersReplyOrBuilder {
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
name_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetExcludedFoldersReply getDefaultInstanceForType() {
return Ritual.GetExcludedFoldersReply.getDefaultInstance();
}
public Ritual.GetExcludedFoldersReply build() {
Ritual.GetExcludedFoldersReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetExcludedFoldersReply buildPartial() {
Ritual.GetExcludedFoldersReply result = new Ritual.GetExcludedFoldersReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
name_ = name_.getUnmodifiableView();
b0_ = (b0_ & ~0x00000001);
}
result.name_ = name_;
return result;
}
public Builder mergeFrom(Ritual.GetExcludedFoldersReply other) {
if (other == Ritual.GetExcludedFoldersReply.getDefaultInstance()) return this;
if (!other.name_.isEmpty()) {
if (name_.isEmpty()) {
name_ = other.name_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureNameIsMutable();
name_.addAll(other.name_);
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
Ritual.GetExcludedFoldersReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetExcludedFoldersReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private LazyStringList name_ = LazyStringArrayList.EMPTY;
private void ensureNameIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
name_ = new LazyStringArrayList(name_);
b0_ |= 0x00000001;
}
}
public ProtocolStringList
getNameList() {
return name_.getUnmodifiableView();
}
public int getNameCount() {
return name_.size();
}
public String getName(int index) {
return name_.get(index);
}
public ByteString
getNameBytes(int index) {
return name_.getByteString(index);
}
public Builder setName(
int index, String value) {
if (value == null) {
throw new NullPointerException();
}
ensureNameIsMutable();
name_.set(index, value);
return this;
}
public Builder addName(
String value) {
if (value == null) {
throw new NullPointerException();
}
ensureNameIsMutable();
name_.add(value);
return this;
}
public Builder addAllName(
Iterable<String> values) {
ensureNameIsMutable();
AbstractMessageLite.Builder.addAll(
values, name_);
return this;
}
public Builder clearName() {
name_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder addNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureNameIsMutable();
name_.add(value);
return this;
}
}
static {
defaultInstance = new GetExcludedFoldersReply(true);
defaultInstance.initFields();
}
}
public interface CreateRootCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
}
public static final class CreateRootCall extends
GeneratedMessageLite implements
CreateRootCallOrBuilder {
private CreateRootCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateRootCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateRootCall defaultInstance;
public static CreateRootCall getDefaultInstance() {
return defaultInstance;
}
public CreateRootCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateRootCall(
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
path_ = bs;
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
public static Parser<CreateRootCall> PARSER =
new AbstractParser<CreateRootCall>() {
public CreateRootCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateRootCall(input, er);
}
};
@Override
public Parser<CreateRootCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Object path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getPath() {
Object ref = path_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
path_ = s;
}
return s;
}
}
public ByteString
getPathBytes() {
Object ref = path_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
path_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
path_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
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
output.writeBytes(1, getPathBytes());
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
.computeBytesSize(1, getPathBytes());
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
public static Ritual.CreateRootCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateRootCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateRootCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateRootCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateRootCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateRootCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateRootCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateRootCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateRootCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateRootCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateRootCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateRootCall, Builder>
implements
Ritual.CreateRootCallOrBuilder {
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
path_ = "";
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateRootCall getDefaultInstanceForType() {
return Ritual.CreateRootCall.getDefaultInstance();
}
public Ritual.CreateRootCall build() {
Ritual.CreateRootCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateRootCall buildPartial() {
Ritual.CreateRootCall result = new Ritual.CreateRootCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateRootCall other) {
if (other == Ritual.CreateRootCall.getDefaultInstance()) return this;
if (other.hasPath()) {
b0_ |= 0x00000001;
path_ = other.path_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.CreateRootCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateRootCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object path_ = "";
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getPath() {
Object ref = path_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
path_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getPathBytes() {
Object ref = path_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
path_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setPath(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
path_ = value;
return this;
}
public Builder clearPath() {
b0_ = (b0_ & ~0x00000001);
path_ = getDefaultInstance().getPath();
return this;
}
public Builder setPathBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
path_ = value;
return this;
}
}
static {
defaultInstance = new CreateRootCall(true);
defaultInstance.initFields();
}
}
public interface CreateRootReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasSid();
ByteString getSid();
}
public static final class CreateRootReply extends
GeneratedMessageLite implements
CreateRootReplyOrBuilder {
private CreateRootReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateRootReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateRootReply defaultInstance;
public static CreateRootReply getDefaultInstance() {
return defaultInstance;
}
public CreateRootReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateRootReply(
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
public static Parser<CreateRootReply> PARSER =
new AbstractParser<CreateRootReply>() {
public CreateRootReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateRootReply(input, er);
}
};
@Override
public Parser<CreateRootReply> getParserForType() {
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
private void initFields() {
sid_ = ByteString.EMPTY;
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
public static Ritual.CreateRootReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateRootReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateRootReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateRootReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateRootReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateRootReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateRootReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateRootReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateRootReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateRootReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateRootReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateRootReply, Builder>
implements
Ritual.CreateRootReplyOrBuilder {
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
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateRootReply getDefaultInstanceForType() {
return Ritual.CreateRootReply.getDefaultInstance();
}
public Ritual.CreateRootReply build() {
Ritual.CreateRootReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateRootReply buildPartial() {
Ritual.CreateRootReply result = new Ritual.CreateRootReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sid_ = sid_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateRootReply other) {
if (other == Ritual.CreateRootReply.getDefaultInstance()) return this;
if (other.hasSid()) {
setSid(other.getSid());
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
Ritual.CreateRootReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateRootReply) e.getUnfinishedMessage();
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
}
static {
defaultInstance = new CreateRootReply(true);
defaultInstance.initFields();
}
}
public interface UnlinkRootCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasSid();
ByteString getSid();
}
public static final class UnlinkRootCall extends
GeneratedMessageLite implements
UnlinkRootCallOrBuilder {
private UnlinkRootCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UnlinkRootCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UnlinkRootCall defaultInstance;
public static UnlinkRootCall getDefaultInstance() {
return defaultInstance;
}
public UnlinkRootCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UnlinkRootCall(
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
public static Parser<UnlinkRootCall> PARSER =
new AbstractParser<UnlinkRootCall>() {
public UnlinkRootCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UnlinkRootCall(input, er);
}
};
@Override
public Parser<UnlinkRootCall> getParserForType() {
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
private void initFields() {
sid_ = ByteString.EMPTY;
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
public static Ritual.UnlinkRootCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.UnlinkRootCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.UnlinkRootCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.UnlinkRootCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.UnlinkRootCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.UnlinkRootCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.UnlinkRootCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.UnlinkRootCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.UnlinkRootCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.UnlinkRootCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.UnlinkRootCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.UnlinkRootCall, Builder>
implements
Ritual.UnlinkRootCallOrBuilder {
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
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.UnlinkRootCall getDefaultInstanceForType() {
return Ritual.UnlinkRootCall.getDefaultInstance();
}
public Ritual.UnlinkRootCall build() {
Ritual.UnlinkRootCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.UnlinkRootCall buildPartial() {
Ritual.UnlinkRootCall result = new Ritual.UnlinkRootCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sid_ = sid_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.UnlinkRootCall other) {
if (other == Ritual.UnlinkRootCall.getDefaultInstance()) return this;
if (other.hasSid()) {
setSid(other.getSid());
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
Ritual.UnlinkRootCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.UnlinkRootCall) e.getUnfinishedMessage();
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
}
static {
defaultInstance = new UnlinkRootCall(true);
defaultInstance.initFields();
}
}
public interface LinkRootCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
boolean hasSid();
ByteString getSid();
}
public static final class LinkRootCall extends
GeneratedMessageLite implements
LinkRootCallOrBuilder {
private LinkRootCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private LinkRootCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final LinkRootCall defaultInstance;
public static LinkRootCall getDefaultInstance() {
return defaultInstance;
}
public LinkRootCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private LinkRootCall(
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
path_ = bs;
break;
}
case 18: {
b0_ |= 0x00000002;
sid_ = input.readBytes();
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
public static Parser<LinkRootCall> PARSER =
new AbstractParser<LinkRootCall>() {
public LinkRootCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new LinkRootCall(input, er);
}
};
@Override
public Parser<LinkRootCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Object path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getPath() {
Object ref = path_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
path_ = s;
}
return s;
}
}
public ByteString
getPathBytes() {
Object ref = path_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
path_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int SID_FIELD_NUMBER = 2;
private ByteString sid_;
public boolean hasSid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getSid() {
return sid_;
}
private void initFields() {
path_ = "";
sid_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
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
output.writeBytes(1, getPathBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, sid_);
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
.computeBytesSize(1, getPathBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, sid_);
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
public static Ritual.LinkRootCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.LinkRootCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.LinkRootCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.LinkRootCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.LinkRootCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.LinkRootCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.LinkRootCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.LinkRootCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.LinkRootCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.LinkRootCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.LinkRootCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.LinkRootCall, Builder>
implements
Ritual.LinkRootCallOrBuilder {
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
path_ = "";
b0_ = (b0_ & ~0x00000001);
sid_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.LinkRootCall getDefaultInstanceForType() {
return Ritual.LinkRootCall.getDefaultInstance();
}
public Ritual.LinkRootCall build() {
Ritual.LinkRootCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.LinkRootCall buildPartial() {
Ritual.LinkRootCall result = new Ritual.LinkRootCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.sid_ = sid_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.LinkRootCall other) {
if (other == Ritual.LinkRootCall.getDefaultInstance()) return this;
if (other.hasPath()) {
b0_ |= 0x00000001;
path_ = other.path_;
}
if (other.hasSid()) {
setSid(other.getSid());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasSid()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.LinkRootCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.LinkRootCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object path_ = "";
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getPath() {
Object ref = path_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
path_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getPathBytes() {
Object ref = path_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
path_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setPath(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
path_ = value;
return this;
}
public Builder clearPath() {
b0_ = (b0_ & ~0x00000001);
path_ = getDefaultInstance().getPath();
return this;
}
public Builder setPathBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
path_ = value;
return this;
}
private ByteString sid_ = ByteString.EMPTY;
public boolean hasSid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getSid() {
return sid_;
}
public Builder setSid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
sid_ = value;
return this;
}
public Builder clearSid() {
b0_ = (b0_ & ~0x00000002);
sid_ = getDefaultInstance().getSid();
return this;
}
}
static {
defaultInstance = new LinkRootCall(true);
defaultInstance.initFields();
}
}
public interface ListUnlinkedRootsReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.ListUnlinkedRootsReply.UnlinkedRoot> 
getRootList();
Ritual.ListUnlinkedRootsReply.UnlinkedRoot getRoot(int index);
int getRootCount();
}
public static final class ListUnlinkedRootsReply extends
GeneratedMessageLite implements
ListUnlinkedRootsReplyOrBuilder {
private ListUnlinkedRootsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListUnlinkedRootsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListUnlinkedRootsReply defaultInstance;
public static ListUnlinkedRootsReply getDefaultInstance() {
return defaultInstance;
}
public ListUnlinkedRootsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListUnlinkedRootsReply(
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
root_ = new ArrayList<Ritual.ListUnlinkedRootsReply.UnlinkedRoot>();
mutable_b0_ |= 0x00000001;
}
root_.add(input.readMessage(Ritual.ListUnlinkedRootsReply.UnlinkedRoot.PARSER, er));
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
root_ = Collections.unmodifiableList(root_);
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
public static Parser<ListUnlinkedRootsReply> PARSER =
new AbstractParser<ListUnlinkedRootsReply>() {
public ListUnlinkedRootsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListUnlinkedRootsReply(input, er);
}
};
@Override
public Parser<ListUnlinkedRootsReply> getParserForType() {
return PARSER;
}
public interface UnlinkedRootOrBuilder extends
MessageLiteOrBuilder {
boolean hasSid();
ByteString getSid();
boolean hasName();
String getName();
ByteString
getNameBytes();
}
public static final class UnlinkedRoot extends
GeneratedMessageLite implements
UnlinkedRootOrBuilder {
private UnlinkedRoot(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UnlinkedRoot(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UnlinkedRoot defaultInstance;
public static UnlinkedRoot getDefaultInstance() {
return defaultInstance;
}
public UnlinkedRoot getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UnlinkedRoot(
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
b0_ |= 0x00000002;
name_ = bs;
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
public static Parser<UnlinkedRoot> PARSER =
new AbstractParser<UnlinkedRoot>() {
public UnlinkedRoot parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UnlinkedRoot(input, er);
}
};
@Override
public Parser<UnlinkedRoot> getParserForType() {
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
public static final int NAME_FIELD_NUMBER = 2;
private Object name_;
public boolean hasName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getName() {
Object ref = name_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
sid_ = ByteString.EMPTY;
name_ = "";
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
if (!hasName()) {
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
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getNameBytes());
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
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getNameBytes());
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
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUnlinkedRootsReply.UnlinkedRoot parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListUnlinkedRootsReply.UnlinkedRoot prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListUnlinkedRootsReply.UnlinkedRoot, Builder>
implements
Ritual.ListUnlinkedRootsReply.UnlinkedRootOrBuilder {
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
name_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListUnlinkedRootsReply.UnlinkedRoot getDefaultInstanceForType() {
return Ritual.ListUnlinkedRootsReply.UnlinkedRoot.getDefaultInstance();
}
public Ritual.ListUnlinkedRootsReply.UnlinkedRoot build() {
Ritual.ListUnlinkedRootsReply.UnlinkedRoot result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListUnlinkedRootsReply.UnlinkedRoot buildPartial() {
Ritual.ListUnlinkedRootsReply.UnlinkedRoot result = new Ritual.ListUnlinkedRootsReply.UnlinkedRoot(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sid_ = sid_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.name_ = name_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ListUnlinkedRootsReply.UnlinkedRoot other) {
if (other == Ritual.ListUnlinkedRootsReply.UnlinkedRoot.getDefaultInstance()) return this;
if (other.hasSid()) {
setSid(other.getSid());
}
if (other.hasName()) {
b0_ |= 0x00000002;
name_ = other.name_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSid()) {
return false;
}
if (!hasName()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListUnlinkedRootsReply.UnlinkedRoot pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListUnlinkedRootsReply.UnlinkedRoot) e.getUnfinishedMessage();
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
private Object name_ = "";
public boolean hasName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getName() {
Object ref = name_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
name_ = value;
return this;
}
public Builder clearName() {
b0_ = (b0_ & ~0x00000002);
name_ = getDefaultInstance().getName();
return this;
}
public Builder setNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
name_ = value;
return this;
}
}
static {
defaultInstance = new UnlinkedRoot(true);
defaultInstance.initFields();
}
}
public static final int ROOT_FIELD_NUMBER = 1;
private List<Ritual.ListUnlinkedRootsReply.UnlinkedRoot> root_;
public List<Ritual.ListUnlinkedRootsReply.UnlinkedRoot> getRootList() {
return root_;
}
public List<? extends Ritual.ListUnlinkedRootsReply.UnlinkedRootOrBuilder> 
getRootOrBuilderList() {
return root_;
}
public int getRootCount() {
return root_.size();
}
public Ritual.ListUnlinkedRootsReply.UnlinkedRoot getRoot(int index) {
return root_.get(index);
}
public Ritual.ListUnlinkedRootsReply.UnlinkedRootOrBuilder getRootOrBuilder(
int index) {
return root_.get(index);
}
private void initFields() {
root_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getRootCount(); i++) {
if (!getRoot(i).isInitialized()) {
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
for (int i = 0; i < root_.size(); i++) {
output.writeMessage(1, root_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < root_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, root_.get(i));
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
public static Ritual.ListUnlinkedRootsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListUnlinkedRootsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListUnlinkedRootsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUnlinkedRootsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListUnlinkedRootsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListUnlinkedRootsReply, Builder>
implements
Ritual.ListUnlinkedRootsReplyOrBuilder {
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
root_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListUnlinkedRootsReply getDefaultInstanceForType() {
return Ritual.ListUnlinkedRootsReply.getDefaultInstance();
}
public Ritual.ListUnlinkedRootsReply build() {
Ritual.ListUnlinkedRootsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListUnlinkedRootsReply buildPartial() {
Ritual.ListUnlinkedRootsReply result = new Ritual.ListUnlinkedRootsReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
root_ = Collections.unmodifiableList(root_);
b0_ = (b0_ & ~0x00000001);
}
result.root_ = root_;
return result;
}
public Builder mergeFrom(Ritual.ListUnlinkedRootsReply other) {
if (other == Ritual.ListUnlinkedRootsReply.getDefaultInstance()) return this;
if (!other.root_.isEmpty()) {
if (root_.isEmpty()) {
root_ = other.root_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureRootIsMutable();
root_.addAll(other.root_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getRootCount(); i++) {
if (!getRoot(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListUnlinkedRootsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListUnlinkedRootsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.ListUnlinkedRootsReply.UnlinkedRoot> root_ =
Collections.emptyList();
private void ensureRootIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
root_ = new ArrayList<Ritual.ListUnlinkedRootsReply.UnlinkedRoot>(root_);
b0_ |= 0x00000001;
}
}
public List<Ritual.ListUnlinkedRootsReply.UnlinkedRoot> getRootList() {
return Collections.unmodifiableList(root_);
}
public int getRootCount() {
return root_.size();
}
public Ritual.ListUnlinkedRootsReply.UnlinkedRoot getRoot(int index) {
return root_.get(index);
}
public Builder setRoot(
int index, Ritual.ListUnlinkedRootsReply.UnlinkedRoot value) {
if (value == null) {
throw new NullPointerException();
}
ensureRootIsMutable();
root_.set(index, value);
return this;
}
public Builder setRoot(
int index, Ritual.ListUnlinkedRootsReply.UnlinkedRoot.Builder bdForValue) {
ensureRootIsMutable();
root_.set(index, bdForValue.build());
return this;
}
public Builder addRoot(Ritual.ListUnlinkedRootsReply.UnlinkedRoot value) {
if (value == null) {
throw new NullPointerException();
}
ensureRootIsMutable();
root_.add(value);
return this;
}
public Builder addRoot(
int index, Ritual.ListUnlinkedRootsReply.UnlinkedRoot value) {
if (value == null) {
throw new NullPointerException();
}
ensureRootIsMutable();
root_.add(index, value);
return this;
}
public Builder addRoot(
Ritual.ListUnlinkedRootsReply.UnlinkedRoot.Builder bdForValue) {
ensureRootIsMutable();
root_.add(bdForValue.build());
return this;
}
public Builder addRoot(
int index, Ritual.ListUnlinkedRootsReply.UnlinkedRoot.Builder bdForValue) {
ensureRootIsMutable();
root_.add(index, bdForValue.build());
return this;
}
public Builder addAllRoot(
Iterable<? extends Ritual.ListUnlinkedRootsReply.UnlinkedRoot> values) {
ensureRootIsMutable();
AbstractMessageLite.Builder.addAll(
values, root_);
return this;
}
public Builder clearRoot() {
root_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeRoot(int index) {
ensureRootIsMutable();
root_.remove(index);
return this;
}
}
static {
defaultInstance = new ListUnlinkedRootsReply(true);
defaultInstance.initFields();
}
}
public interface CreateUrlCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class CreateUrlCall extends
GeneratedMessageLite implements
CreateUrlCallOrBuilder {
private CreateUrlCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateUrlCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateUrlCall defaultInstance;
public static CreateUrlCall getDefaultInstance() {
return defaultInstance;
}
public CreateUrlCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateUrlCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<CreateUrlCall> PARSER =
new AbstractParser<CreateUrlCall>() {
public CreateUrlCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateUrlCall(input, er);
}
};
@Override
public Parser<CreateUrlCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.CreateUrlCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateUrlCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateUrlCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateUrlCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateUrlCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateUrlCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateUrlCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateUrlCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateUrlCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateUrlCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateUrlCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateUrlCall, Builder>
implements
Ritual.CreateUrlCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateUrlCall getDefaultInstanceForType() {
return Ritual.CreateUrlCall.getDefaultInstance();
}
public Ritual.CreateUrlCall build() {
Ritual.CreateUrlCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateUrlCall buildPartial() {
Ritual.CreateUrlCall result = new Ritual.CreateUrlCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateUrlCall other) {
if (other == Ritual.CreateUrlCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.CreateUrlCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateUrlCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new CreateUrlCall(true);
defaultInstance.initFields();
}
}
public interface CreateUrlReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasLink();
String getLink();
ByteString
getLinkBytes();
}
public static final class CreateUrlReply extends
GeneratedMessageLite implements
CreateUrlReplyOrBuilder {
private CreateUrlReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateUrlReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateUrlReply defaultInstance;
public static CreateUrlReply getDefaultInstance() {
return defaultInstance;
}
public CreateUrlReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateUrlReply(
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
link_ = bs;
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
public static Parser<CreateUrlReply> PARSER =
new AbstractParser<CreateUrlReply>() {
public CreateUrlReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateUrlReply(input, er);
}
};
@Override
public Parser<CreateUrlReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int LINK_FIELD_NUMBER = 1;
private Object link_;
public boolean hasLink() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getLink() {
Object ref = link_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
link_ = s;
}
return s;
}
}
public ByteString
getLinkBytes() {
Object ref = link_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
link_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
link_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasLink()) {
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
output.writeBytes(1, getLinkBytes());
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
.computeBytesSize(1, getLinkBytes());
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
public static Ritual.CreateUrlReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateUrlReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateUrlReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateUrlReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateUrlReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateUrlReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateUrlReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateUrlReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateUrlReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateUrlReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateUrlReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateUrlReply, Builder>
implements
Ritual.CreateUrlReplyOrBuilder {
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
link_ = "";
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateUrlReply getDefaultInstanceForType() {
return Ritual.CreateUrlReply.getDefaultInstance();
}
public Ritual.CreateUrlReply build() {
Ritual.CreateUrlReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateUrlReply buildPartial() {
Ritual.CreateUrlReply result = new Ritual.CreateUrlReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.link_ = link_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateUrlReply other) {
if (other == Ritual.CreateUrlReply.getDefaultInstance()) return this;
if (other.hasLink()) {
b0_ |= 0x00000001;
link_ = other.link_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasLink()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.CreateUrlReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateUrlReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object link_ = "";
public boolean hasLink() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getLink() {
Object ref = link_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
link_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getLinkBytes() {
Object ref = link_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
link_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setLink(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
link_ = value;
return this;
}
public Builder clearLink() {
b0_ = (b0_ & ~0x00000001);
link_ = getDefaultInstance().getLink();
return this;
}
public Builder setLinkBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
link_ = value;
return this;
}
}
static {
defaultInstance = new CreateUrlReply(true);
defaultInstance.initFields();
}
}
public interface ShareFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
List<Common.PBSubjectPermissions> 
getSubjectPermissionsList();
Common.PBSubjectPermissions getSubjectPermissions(int index);
int getSubjectPermissionsCount();
boolean hasNote();
String getNote();
ByteString
getNoteBytes();
boolean hasSuppressSharingRulesWarnings();
boolean getSuppressSharingRulesWarnings();
}
public static final class ShareFolderCall extends
GeneratedMessageLite implements
ShareFolderCallOrBuilder {
private ShareFolderCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ShareFolderCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ShareFolderCall defaultInstance;
public static ShareFolderCall getDefaultInstance() {
return defaultInstance;
}
public ShareFolderCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ShareFolderCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
subjectPermissions_ = new ArrayList<Common.PBSubjectPermissions>();
mutable_b0_ |= 0x00000002;
}
subjectPermissions_.add(input.readMessage(Common.PBSubjectPermissions.PARSER, er));
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
note_ = bs;
break;
}
case 32: {
b0_ |= 0x00000004;
suppressSharingRulesWarnings_ = input.readBool();
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
subjectPermissions_ = Collections.unmodifiableList(subjectPermissions_);
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
public static Parser<ShareFolderCall> PARSER =
new AbstractParser<ShareFolderCall>() {
public ShareFolderCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ShareFolderCall(input, er);
}
};
@Override
public Parser<ShareFolderCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int SUBJECT_PERMISSIONS_FIELD_NUMBER = 2;
private List<Common.PBSubjectPermissions> subjectPermissions_;
public List<Common.PBSubjectPermissions> getSubjectPermissionsList() {
return subjectPermissions_;
}
public List<? extends Common.PBSubjectPermissionsOrBuilder> 
getSubjectPermissionsOrBuilderList() {
return subjectPermissions_;
}
public int getSubjectPermissionsCount() {
return subjectPermissions_.size();
}
public Common.PBSubjectPermissions getSubjectPermissions(int index) {
return subjectPermissions_.get(index);
}
public Common.PBSubjectPermissionsOrBuilder getSubjectPermissionsOrBuilder(
int index) {
return subjectPermissions_.get(index);
}
public static final int NOTE_FIELD_NUMBER = 3;
private Object note_;
public boolean hasNote() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getNote() {
Object ref = note_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
note_ = s;
}
return s;
}
}
public ByteString
getNoteBytes() {
Object ref = note_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
note_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int SUPPRESS_SHARING_RULES_WARNINGS_FIELD_NUMBER = 4;
private boolean suppressSharingRulesWarnings_;
public boolean hasSuppressSharingRulesWarnings() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public boolean getSuppressSharingRulesWarnings() {
return suppressSharingRulesWarnings_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
subjectPermissions_ = Collections.emptyList();
note_ = "";
suppressSharingRulesWarnings_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasNote()) {
mii = 0;
return false;
}
if (!hasSuppressSharingRulesWarnings()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
mii = 0;
return false;
}
for (int i = 0; i < getSubjectPermissionsCount(); i++) {
if (!getSubjectPermissions(i).isInitialized()) {
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
output.writeMessage(1, path_);
}
for (int i = 0; i < subjectPermissions_.size(); i++) {
output.writeMessage(2, subjectPermissions_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(3, getNoteBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBool(4, suppressSharingRulesWarnings_);
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
.computeMessageSize(1, path_);
}
for (int i = 0; i < subjectPermissions_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, subjectPermissions_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(3, getNoteBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBoolSize(4, suppressSharingRulesWarnings_);
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
public static Ritual.ShareFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ShareFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ShareFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ShareFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ShareFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ShareFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ShareFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ShareFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ShareFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ShareFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ShareFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ShareFolderCall, Builder>
implements
Ritual.ShareFolderCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
subjectPermissions_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
note_ = "";
b0_ = (b0_ & ~0x00000004);
suppressSharingRulesWarnings_ = false;
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ShareFolderCall getDefaultInstanceForType() {
return Ritual.ShareFolderCall.getDefaultInstance();
}
public Ritual.ShareFolderCall build() {
Ritual.ShareFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ShareFolderCall buildPartial() {
Ritual.ShareFolderCall result = new Ritual.ShareFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((b0_ & 0x00000002) == 0x00000002)) {
subjectPermissions_ = Collections.unmodifiableList(subjectPermissions_);
b0_ = (b0_ & ~0x00000002);
}
result.subjectPermissions_ = subjectPermissions_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000002;
}
result.note_ = note_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000004;
}
result.suppressSharingRulesWarnings_ = suppressSharingRulesWarnings_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ShareFolderCall other) {
if (other == Ritual.ShareFolderCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (!other.subjectPermissions_.isEmpty()) {
if (subjectPermissions_.isEmpty()) {
subjectPermissions_ = other.subjectPermissions_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureSubjectPermissionsIsMutable();
subjectPermissions_.addAll(other.subjectPermissions_);
}
}
if (other.hasNote()) {
b0_ |= 0x00000004;
note_ = other.note_;
}
if (other.hasSuppressSharingRulesWarnings()) {
setSuppressSharingRulesWarnings(other.getSuppressSharingRulesWarnings());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasNote()) {
return false;
}
if (!hasSuppressSharingRulesWarnings()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
for (int i = 0; i < getSubjectPermissionsCount(); i++) {
if (!getSubjectPermissions(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ShareFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ShareFolderCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private List<Common.PBSubjectPermissions> subjectPermissions_ =
Collections.emptyList();
private void ensureSubjectPermissionsIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
subjectPermissions_ = new ArrayList<Common.PBSubjectPermissions>(subjectPermissions_);
b0_ |= 0x00000002;
}
}
public List<Common.PBSubjectPermissions> getSubjectPermissionsList() {
return Collections.unmodifiableList(subjectPermissions_);
}
public int getSubjectPermissionsCount() {
return subjectPermissions_.size();
}
public Common.PBSubjectPermissions getSubjectPermissions(int index) {
return subjectPermissions_.get(index);
}
public Builder setSubjectPermissions(
int index, Common.PBSubjectPermissions value) {
if (value == null) {
throw new NullPointerException();
}
ensureSubjectPermissionsIsMutable();
subjectPermissions_.set(index, value);
return this;
}
public Builder setSubjectPermissions(
int index, Common.PBSubjectPermissions.Builder bdForValue) {
ensureSubjectPermissionsIsMutable();
subjectPermissions_.set(index, bdForValue.build());
return this;
}
public Builder addSubjectPermissions(Common.PBSubjectPermissions value) {
if (value == null) {
throw new NullPointerException();
}
ensureSubjectPermissionsIsMutable();
subjectPermissions_.add(value);
return this;
}
public Builder addSubjectPermissions(
int index, Common.PBSubjectPermissions value) {
if (value == null) {
throw new NullPointerException();
}
ensureSubjectPermissionsIsMutable();
subjectPermissions_.add(index, value);
return this;
}
public Builder addSubjectPermissions(
Common.PBSubjectPermissions.Builder bdForValue) {
ensureSubjectPermissionsIsMutable();
subjectPermissions_.add(bdForValue.build());
return this;
}
public Builder addSubjectPermissions(
int index, Common.PBSubjectPermissions.Builder bdForValue) {
ensureSubjectPermissionsIsMutable();
subjectPermissions_.add(index, bdForValue.build());
return this;
}
public Builder addAllSubjectPermissions(
Iterable<? extends Common.PBSubjectPermissions> values) {
ensureSubjectPermissionsIsMutable();
AbstractMessageLite.Builder.addAll(
values, subjectPermissions_);
return this;
}
public Builder clearSubjectPermissions() {
subjectPermissions_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder removeSubjectPermissions(int index) {
ensureSubjectPermissionsIsMutable();
subjectPermissions_.remove(index);
return this;
}
private Object note_ = "";
public boolean hasNote() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getNote() {
Object ref = note_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
note_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getNoteBytes() {
Object ref = note_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
note_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setNote(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
note_ = value;
return this;
}
public Builder clearNote() {
b0_ = (b0_ & ~0x00000004);
note_ = getDefaultInstance().getNote();
return this;
}
public Builder setNoteBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
note_ = value;
return this;
}
private boolean suppressSharingRulesWarnings_ ;
public boolean hasSuppressSharingRulesWarnings() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public boolean getSuppressSharingRulesWarnings() {
return suppressSharingRulesWarnings_;
}
public Builder setSuppressSharingRulesWarnings(boolean value) {
b0_ |= 0x00000008;
suppressSharingRulesWarnings_ = value;
return this;
}
public Builder clearSuppressSharingRulesWarnings() {
b0_ = (b0_ & ~0x00000008);
suppressSharingRulesWarnings_ = false;
return this;
}
}
static {
defaultInstance = new ShareFolderCall(true);
defaultInstance.initFields();
}
}
public interface UnshareFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class UnshareFolderCall extends
GeneratedMessageLite implements
UnshareFolderCallOrBuilder {
private UnshareFolderCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UnshareFolderCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UnshareFolderCall defaultInstance;
public static UnshareFolderCall getDefaultInstance() {
return defaultInstance;
}
public UnshareFolderCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UnshareFolderCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<UnshareFolderCall> PARSER =
new AbstractParser<UnshareFolderCall>() {
public UnshareFolderCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UnshareFolderCall(input, er);
}
};
@Override
public Parser<UnshareFolderCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.UnshareFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.UnshareFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.UnshareFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.UnshareFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.UnshareFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.UnshareFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.UnshareFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.UnshareFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.UnshareFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.UnshareFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.UnshareFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.UnshareFolderCall, Builder>
implements
Ritual.UnshareFolderCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.UnshareFolderCall getDefaultInstanceForType() {
return Ritual.UnshareFolderCall.getDefaultInstance();
}
public Ritual.UnshareFolderCall build() {
Ritual.UnshareFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.UnshareFolderCall buildPartial() {
Ritual.UnshareFolderCall result = new Ritual.UnshareFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.UnshareFolderCall other) {
if (other == Ritual.UnshareFolderCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.UnshareFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.UnshareFolderCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new UnshareFolderCall(true);
defaultInstance.initFields();
}
}
public interface ListSharedFolderInvitationsReplyOrBuilder extends
MessageLiteOrBuilder {
List<Common.PBFolderInvitation> 
getInvitationList();
Common.PBFolderInvitation getInvitation(int index);
int getInvitationCount();
}
public static final class ListSharedFolderInvitationsReply extends
GeneratedMessageLite implements
ListSharedFolderInvitationsReplyOrBuilder {
private ListSharedFolderInvitationsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListSharedFolderInvitationsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListSharedFolderInvitationsReply defaultInstance;
public static ListSharedFolderInvitationsReply getDefaultInstance() {
return defaultInstance;
}
public ListSharedFolderInvitationsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListSharedFolderInvitationsReply(
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
invitation_ = new ArrayList<Common.PBFolderInvitation>();
mutable_b0_ |= 0x00000001;
}
invitation_.add(input.readMessage(Common.PBFolderInvitation.PARSER, er));
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
invitation_ = Collections.unmodifiableList(invitation_);
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
public static Parser<ListSharedFolderInvitationsReply> PARSER =
new AbstractParser<ListSharedFolderInvitationsReply>() {
public ListSharedFolderInvitationsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListSharedFolderInvitationsReply(input, er);
}
};
@Override
public Parser<ListSharedFolderInvitationsReply> getParserForType() {
return PARSER;
}
public static final int INVITATION_FIELD_NUMBER = 1;
private List<Common.PBFolderInvitation> invitation_;
public List<Common.PBFolderInvitation> getInvitationList() {
return invitation_;
}
public List<? extends Common.PBFolderInvitationOrBuilder> 
getInvitationOrBuilderList() {
return invitation_;
}
public int getInvitationCount() {
return invitation_.size();
}
public Common.PBFolderInvitation getInvitation(int index) {
return invitation_.get(index);
}
public Common.PBFolderInvitationOrBuilder getInvitationOrBuilder(
int index) {
return invitation_.get(index);
}
private void initFields() {
invitation_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getInvitationCount(); i++) {
if (!getInvitation(i).isInitialized()) {
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
for (int i = 0; i < invitation_.size(); i++) {
output.writeMessage(1, invitation_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < invitation_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, invitation_.get(i));
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
public static Ritual.ListSharedFolderInvitationsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListSharedFolderInvitationsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListSharedFolderInvitationsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListSharedFolderInvitationsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListSharedFolderInvitationsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListSharedFolderInvitationsReply, Builder>
implements
Ritual.ListSharedFolderInvitationsReplyOrBuilder {
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
invitation_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListSharedFolderInvitationsReply getDefaultInstanceForType() {
return Ritual.ListSharedFolderInvitationsReply.getDefaultInstance();
}
public Ritual.ListSharedFolderInvitationsReply build() {
Ritual.ListSharedFolderInvitationsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListSharedFolderInvitationsReply buildPartial() {
Ritual.ListSharedFolderInvitationsReply result = new Ritual.ListSharedFolderInvitationsReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
invitation_ = Collections.unmodifiableList(invitation_);
b0_ = (b0_ & ~0x00000001);
}
result.invitation_ = invitation_;
return result;
}
public Builder mergeFrom(Ritual.ListSharedFolderInvitationsReply other) {
if (other == Ritual.ListSharedFolderInvitationsReply.getDefaultInstance()) return this;
if (!other.invitation_.isEmpty()) {
if (invitation_.isEmpty()) {
invitation_ = other.invitation_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureInvitationIsMutable();
invitation_.addAll(other.invitation_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getInvitationCount(); i++) {
if (!getInvitation(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListSharedFolderInvitationsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListSharedFolderInvitationsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Common.PBFolderInvitation> invitation_ =
Collections.emptyList();
private void ensureInvitationIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
invitation_ = new ArrayList<Common.PBFolderInvitation>(invitation_);
b0_ |= 0x00000001;
}
}
public List<Common.PBFolderInvitation> getInvitationList() {
return Collections.unmodifiableList(invitation_);
}
public int getInvitationCount() {
return invitation_.size();
}
public Common.PBFolderInvitation getInvitation(int index) {
return invitation_.get(index);
}
public Builder setInvitation(
int index, Common.PBFolderInvitation value) {
if (value == null) {
throw new NullPointerException();
}
ensureInvitationIsMutable();
invitation_.set(index, value);
return this;
}
public Builder setInvitation(
int index, Common.PBFolderInvitation.Builder bdForValue) {
ensureInvitationIsMutable();
invitation_.set(index, bdForValue.build());
return this;
}
public Builder addInvitation(Common.PBFolderInvitation value) {
if (value == null) {
throw new NullPointerException();
}
ensureInvitationIsMutable();
invitation_.add(value);
return this;
}
public Builder addInvitation(
int index, Common.PBFolderInvitation value) {
if (value == null) {
throw new NullPointerException();
}
ensureInvitationIsMutable();
invitation_.add(index, value);
return this;
}
public Builder addInvitation(
Common.PBFolderInvitation.Builder bdForValue) {
ensureInvitationIsMutable();
invitation_.add(bdForValue.build());
return this;
}
public Builder addInvitation(
int index, Common.PBFolderInvitation.Builder bdForValue) {
ensureInvitationIsMutable();
invitation_.add(index, bdForValue.build());
return this;
}
public Builder addAllInvitation(
Iterable<? extends Common.PBFolderInvitation> values) {
ensureInvitationIsMutable();
AbstractMessageLite.Builder.addAll(
values, invitation_);
return this;
}
public Builder clearInvitation() {
invitation_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeInvitation(int index) {
ensureInvitationIsMutable();
invitation_.remove(index);
return this;
}
}
static {
defaultInstance = new ListSharedFolderInvitationsReply(true);
defaultInstance.initFields();
}
}
public interface JoinSharedFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasId();
ByteString getId();
}
public static final class JoinSharedFolderCall extends
GeneratedMessageLite implements
JoinSharedFolderCallOrBuilder {
private JoinSharedFolderCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private JoinSharedFolderCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final JoinSharedFolderCall defaultInstance;
public static JoinSharedFolderCall getDefaultInstance() {
return defaultInstance;
}
public JoinSharedFolderCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private JoinSharedFolderCall(
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
id_ = input.readBytes();
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
public static Parser<JoinSharedFolderCall> PARSER =
new AbstractParser<JoinSharedFolderCall>() {
public JoinSharedFolderCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new JoinSharedFolderCall(input, er);
}
};
@Override
public Parser<JoinSharedFolderCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int ID_FIELD_NUMBER = 1;
private ByteString id_;
public boolean hasId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getId() {
return id_;
}
private void initFields() {
id_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasId()) {
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
output.writeBytes(1, id_);
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
.computeBytesSize(1, id_);
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
public static Ritual.JoinSharedFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.JoinSharedFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.JoinSharedFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.JoinSharedFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.JoinSharedFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.JoinSharedFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.JoinSharedFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.JoinSharedFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.JoinSharedFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.JoinSharedFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.JoinSharedFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.JoinSharedFolderCall, Builder>
implements
Ritual.JoinSharedFolderCallOrBuilder {
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
id_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.JoinSharedFolderCall getDefaultInstanceForType() {
return Ritual.JoinSharedFolderCall.getDefaultInstance();
}
public Ritual.JoinSharedFolderCall build() {
Ritual.JoinSharedFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.JoinSharedFolderCall buildPartial() {
Ritual.JoinSharedFolderCall result = new Ritual.JoinSharedFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.id_ = id_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.JoinSharedFolderCall other) {
if (other == Ritual.JoinSharedFolderCall.getDefaultInstance()) return this;
if (other.hasId()) {
setId(other.getId());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasId()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.JoinSharedFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.JoinSharedFolderCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString id_ = ByteString.EMPTY;
public boolean hasId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getId() {
return id_;
}
public Builder setId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
id_ = value;
return this;
}
public Builder clearId() {
b0_ = (b0_ & ~0x00000001);
id_ = getDefaultInstance().getId();
return this;
}
}
static {
defaultInstance = new JoinSharedFolderCall(true);
defaultInstance.initFields();
}
}
public interface LeaveSharedFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class LeaveSharedFolderCall extends
GeneratedMessageLite implements
LeaveSharedFolderCallOrBuilder {
private LeaveSharedFolderCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private LeaveSharedFolderCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final LeaveSharedFolderCall defaultInstance;
public static LeaveSharedFolderCall getDefaultInstance() {
return defaultInstance;
}
public LeaveSharedFolderCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private LeaveSharedFolderCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<LeaveSharedFolderCall> PARSER =
new AbstractParser<LeaveSharedFolderCall>() {
public LeaveSharedFolderCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new LeaveSharedFolderCall(input, er);
}
};
@Override
public Parser<LeaveSharedFolderCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.LeaveSharedFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.LeaveSharedFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.LeaveSharedFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.LeaveSharedFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.LeaveSharedFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.LeaveSharedFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.LeaveSharedFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.LeaveSharedFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.LeaveSharedFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.LeaveSharedFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.LeaveSharedFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.LeaveSharedFolderCall, Builder>
implements
Ritual.LeaveSharedFolderCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.LeaveSharedFolderCall getDefaultInstanceForType() {
return Ritual.LeaveSharedFolderCall.getDefaultInstance();
}
public Ritual.LeaveSharedFolderCall build() {
Ritual.LeaveSharedFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.LeaveSharedFolderCall buildPartial() {
Ritual.LeaveSharedFolderCall result = new Ritual.LeaveSharedFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.LeaveSharedFolderCall other) {
if (other == Ritual.LeaveSharedFolderCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.LeaveSharedFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.LeaveSharedFolderCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new LeaveSharedFolderCall(true);
defaultInstance.initFields();
}
}
public interface PBSharedFolderOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasName();
String getName();
ByteString
getNameBytes();
boolean hasStoreId();
ByteString getStoreId();
boolean hasAdmittedOrLinked();
boolean getAdmittedOrLinked();
}
public static final class PBSharedFolder extends
GeneratedMessageLite implements
PBSharedFolderOrBuilder {
private PBSharedFolder(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBSharedFolder(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBSharedFolder defaultInstance;
public static PBSharedFolder getDefaultInstance() {
return defaultInstance;
}
public PBSharedFolder getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBSharedFolder(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
name_ = bs;
break;
}
case 26: {
b0_ |= 0x00000004;
storeId_ = input.readBytes();
break;
}
case 32: {
b0_ |= 0x00000008;
admittedOrLinked_ = input.readBool();
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
public static Parser<PBSharedFolder> PARSER =
new AbstractParser<PBSharedFolder>() {
public PBSharedFolder parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBSharedFolder(input, er);
}
};
@Override
public Parser<PBSharedFolder> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int NAME_FIELD_NUMBER = 2;
private Object name_;
public boolean hasName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getName() {
Object ref = name_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int STORE_ID_FIELD_NUMBER = 3;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public ByteString getStoreId() {
return storeId_;
}
public static final int ADMITTED_OR_LINKED_FIELD_NUMBER = 4;
private boolean admittedOrLinked_;
public boolean hasAdmittedOrLinked() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public boolean getAdmittedOrLinked() {
return admittedOrLinked_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
name_ = "";
storeId_ = ByteString.EMPTY;
admittedOrLinked_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasName()) {
mii = 0;
return false;
}
if (!hasStoreId()) {
mii = 0;
return false;
}
if (!hasAdmittedOrLinked()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getNameBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, storeId_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeBool(4, admittedOrLinked_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getNameBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, storeId_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeBoolSize(4, admittedOrLinked_);
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
public static Ritual.PBSharedFolder parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBSharedFolder parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBSharedFolder parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBSharedFolder parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBSharedFolder parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBSharedFolder parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.PBSharedFolder parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.PBSharedFolder parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.PBSharedFolder parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBSharedFolder parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.PBSharedFolder prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.PBSharedFolder, Builder>
implements
Ritual.PBSharedFolderOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
name_ = "";
b0_ = (b0_ & ~0x00000002);
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000004);
admittedOrLinked_ = false;
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.PBSharedFolder getDefaultInstanceForType() {
return Ritual.PBSharedFolder.getDefaultInstance();
}
public Ritual.PBSharedFolder build() {
Ritual.PBSharedFolder result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.PBSharedFolder buildPartial() {
Ritual.PBSharedFolder result = new Ritual.PBSharedFolder(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.name_ = name_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.storeId_ = storeId_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.admittedOrLinked_ = admittedOrLinked_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.PBSharedFolder other) {
if (other == Ritual.PBSharedFolder.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasName()) {
b0_ |= 0x00000002;
name_ = other.name_;
}
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
if (other.hasAdmittedOrLinked()) {
setAdmittedOrLinked(other.getAdmittedOrLinked());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasName()) {
return false;
}
if (!hasStoreId()) {
return false;
}
if (!hasAdmittedOrLinked()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.PBSharedFolder pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.PBSharedFolder) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Object name_ = "";
public boolean hasName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getName() {
Object ref = name_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
name_ = value;
return this;
}
public Builder clearName() {
b0_ = (b0_ & ~0x00000002);
name_ = getDefaultInstance().getName();
return this;
}
public Builder setNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
name_ = value;
return this;
}
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000004);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
private boolean admittedOrLinked_ ;
public boolean hasAdmittedOrLinked() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public boolean getAdmittedOrLinked() {
return admittedOrLinked_;
}
public Builder setAdmittedOrLinked(boolean value) {
b0_ |= 0x00000008;
admittedOrLinked_ = value;
return this;
}
public Builder clearAdmittedOrLinked() {
b0_ = (b0_ & ~0x00000008);
admittedOrLinked_ = false;
return this;
}
}
static {
defaultInstance = new PBSharedFolder(true);
defaultInstance.initFields();
}
}
public interface ListUserRootsReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.ListUserRootsReply.UserRoot> 
getRootList();
Ritual.ListUserRootsReply.UserRoot getRoot(int index);
int getRootCount();
}
public static final class ListUserRootsReply extends
GeneratedMessageLite implements
ListUserRootsReplyOrBuilder {
private ListUserRootsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListUserRootsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListUserRootsReply defaultInstance;
public static ListUserRootsReply getDefaultInstance() {
return defaultInstance;
}
public ListUserRootsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListUserRootsReply(
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
root_ = new ArrayList<Ritual.ListUserRootsReply.UserRoot>();
mutable_b0_ |= 0x00000001;
}
root_.add(input.readMessage(Ritual.ListUserRootsReply.UserRoot.PARSER, er));
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
root_ = Collections.unmodifiableList(root_);
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
public static Parser<ListUserRootsReply> PARSER =
new AbstractParser<ListUserRootsReply>() {
public ListUserRootsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListUserRootsReply(input, er);
}
};
@Override
public Parser<ListUserRootsReply> getParserForType() {
return PARSER;
}
public interface UserRootOrBuilder extends
MessageLiteOrBuilder {
boolean hasSid();
ByteString getSid();
boolean hasName();
String getName();
ByteString
getNameBytes();
}
public static final class UserRoot extends
GeneratedMessageLite implements
UserRootOrBuilder {
private UserRoot(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UserRoot(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UserRoot defaultInstance;
public static UserRoot getDefaultInstance() {
return defaultInstance;
}
public UserRoot getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UserRoot(
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
b0_ |= 0x00000002;
name_ = bs;
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
public static Parser<UserRoot> PARSER =
new AbstractParser<UserRoot>() {
public UserRoot parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UserRoot(input, er);
}
};
@Override
public Parser<UserRoot> getParserForType() {
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
public static final int NAME_FIELD_NUMBER = 2;
private Object name_;
public boolean hasName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getName() {
Object ref = name_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
sid_ = ByteString.EMPTY;
name_ = "";
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
if (!hasName()) {
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
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getNameBytes());
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
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getNameBytes());
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
public static Ritual.ListUserRootsReply.UserRoot parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListUserRootsReply.UserRoot parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListUserRootsReply.UserRoot parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUserRootsReply.UserRoot parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListUserRootsReply.UserRoot prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListUserRootsReply.UserRoot, Builder>
implements
Ritual.ListUserRootsReply.UserRootOrBuilder {
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
name_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListUserRootsReply.UserRoot getDefaultInstanceForType() {
return Ritual.ListUserRootsReply.UserRoot.getDefaultInstance();
}
public Ritual.ListUserRootsReply.UserRoot build() {
Ritual.ListUserRootsReply.UserRoot result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListUserRootsReply.UserRoot buildPartial() {
Ritual.ListUserRootsReply.UserRoot result = new Ritual.ListUserRootsReply.UserRoot(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sid_ = sid_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.name_ = name_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ListUserRootsReply.UserRoot other) {
if (other == Ritual.ListUserRootsReply.UserRoot.getDefaultInstance()) return this;
if (other.hasSid()) {
setSid(other.getSid());
}
if (other.hasName()) {
b0_ |= 0x00000002;
name_ = other.name_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSid()) {
return false;
}
if (!hasName()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListUserRootsReply.UserRoot pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListUserRootsReply.UserRoot) e.getUnfinishedMessage();
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
private Object name_ = "";
public boolean hasName() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getName() {
Object ref = name_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
name_ = value;
return this;
}
public Builder clearName() {
b0_ = (b0_ & ~0x00000002);
name_ = getDefaultInstance().getName();
return this;
}
public Builder setNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
name_ = value;
return this;
}
}
static {
defaultInstance = new UserRoot(true);
defaultInstance.initFields();
}
}
public static final int ROOT_FIELD_NUMBER = 1;
private List<Ritual.ListUserRootsReply.UserRoot> root_;
public List<Ritual.ListUserRootsReply.UserRoot> getRootList() {
return root_;
}
public List<? extends Ritual.ListUserRootsReply.UserRootOrBuilder> 
getRootOrBuilderList() {
return root_;
}
public int getRootCount() {
return root_.size();
}
public Ritual.ListUserRootsReply.UserRoot getRoot(int index) {
return root_.get(index);
}
public Ritual.ListUserRootsReply.UserRootOrBuilder getRootOrBuilder(
int index) {
return root_.get(index);
}
private void initFields() {
root_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getRootCount(); i++) {
if (!getRoot(i).isInitialized()) {
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
for (int i = 0; i < root_.size(); i++) {
output.writeMessage(1, root_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < root_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, root_.get(i));
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
public static Ritual.ListUserRootsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUserRootsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUserRootsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListUserRootsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListUserRootsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUserRootsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListUserRootsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListUserRootsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListUserRootsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListUserRootsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListUserRootsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListUserRootsReply, Builder>
implements
Ritual.ListUserRootsReplyOrBuilder {
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
root_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListUserRootsReply getDefaultInstanceForType() {
return Ritual.ListUserRootsReply.getDefaultInstance();
}
public Ritual.ListUserRootsReply build() {
Ritual.ListUserRootsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListUserRootsReply buildPartial() {
Ritual.ListUserRootsReply result = new Ritual.ListUserRootsReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
root_ = Collections.unmodifiableList(root_);
b0_ = (b0_ & ~0x00000001);
}
result.root_ = root_;
return result;
}
public Builder mergeFrom(Ritual.ListUserRootsReply other) {
if (other == Ritual.ListUserRootsReply.getDefaultInstance()) return this;
if (!other.root_.isEmpty()) {
if (root_.isEmpty()) {
root_ = other.root_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureRootIsMutable();
root_.addAll(other.root_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getRootCount(); i++) {
if (!getRoot(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListUserRootsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListUserRootsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.ListUserRootsReply.UserRoot> root_ =
Collections.emptyList();
private void ensureRootIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
root_ = new ArrayList<Ritual.ListUserRootsReply.UserRoot>(root_);
b0_ |= 0x00000001;
}
}
public List<Ritual.ListUserRootsReply.UserRoot> getRootList() {
return Collections.unmodifiableList(root_);
}
public int getRootCount() {
return root_.size();
}
public Ritual.ListUserRootsReply.UserRoot getRoot(int index) {
return root_.get(index);
}
public Builder setRoot(
int index, Ritual.ListUserRootsReply.UserRoot value) {
if (value == null) {
throw new NullPointerException();
}
ensureRootIsMutable();
root_.set(index, value);
return this;
}
public Builder setRoot(
int index, Ritual.ListUserRootsReply.UserRoot.Builder bdForValue) {
ensureRootIsMutable();
root_.set(index, bdForValue.build());
return this;
}
public Builder addRoot(Ritual.ListUserRootsReply.UserRoot value) {
if (value == null) {
throw new NullPointerException();
}
ensureRootIsMutable();
root_.add(value);
return this;
}
public Builder addRoot(
int index, Ritual.ListUserRootsReply.UserRoot value) {
if (value == null) {
throw new NullPointerException();
}
ensureRootIsMutable();
root_.add(index, value);
return this;
}
public Builder addRoot(
Ritual.ListUserRootsReply.UserRoot.Builder bdForValue) {
ensureRootIsMutable();
root_.add(bdForValue.build());
return this;
}
public Builder addRoot(
int index, Ritual.ListUserRootsReply.UserRoot.Builder bdForValue) {
ensureRootIsMutable();
root_.add(index, bdForValue.build());
return this;
}
public Builder addAllRoot(
Iterable<? extends Ritual.ListUserRootsReply.UserRoot> values) {
ensureRootIsMutable();
AbstractMessageLite.Builder.addAll(
values, root_);
return this;
}
public Builder clearRoot() {
root_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeRoot(int index) {
ensureRootIsMutable();
root_.remove(index);
return this;
}
}
static {
defaultInstance = new ListUserRootsReply(true);
defaultInstance.initFields();
}
}
public interface ListSharedFoldersReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.PBSharedFolder> 
getSharedFolderList();
Ritual.PBSharedFolder getSharedFolder(int index);
int getSharedFolderCount();
}
public static final class ListSharedFoldersReply extends
GeneratedMessageLite implements
ListSharedFoldersReplyOrBuilder {
private ListSharedFoldersReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListSharedFoldersReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListSharedFoldersReply defaultInstance;
public static ListSharedFoldersReply getDefaultInstance() {
return defaultInstance;
}
public ListSharedFoldersReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListSharedFoldersReply(
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
sharedFolder_ = new ArrayList<Ritual.PBSharedFolder>();
mutable_b0_ |= 0x00000001;
}
sharedFolder_.add(input.readMessage(Ritual.PBSharedFolder.PARSER, er));
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
sharedFolder_ = Collections.unmodifiableList(sharedFolder_);
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
public static Parser<ListSharedFoldersReply> PARSER =
new AbstractParser<ListSharedFoldersReply>() {
public ListSharedFoldersReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListSharedFoldersReply(input, er);
}
};
@Override
public Parser<ListSharedFoldersReply> getParserForType() {
return PARSER;
}
public static final int SHARED_FOLDER_FIELD_NUMBER = 1;
private List<Ritual.PBSharedFolder> sharedFolder_;
public List<Ritual.PBSharedFolder> getSharedFolderList() {
return sharedFolder_;
}
public List<? extends Ritual.PBSharedFolderOrBuilder> 
getSharedFolderOrBuilderList() {
return sharedFolder_;
}
public int getSharedFolderCount() {
return sharedFolder_.size();
}
public Ritual.PBSharedFolder getSharedFolder(int index) {
return sharedFolder_.get(index);
}
public Ritual.PBSharedFolderOrBuilder getSharedFolderOrBuilder(
int index) {
return sharedFolder_.get(index);
}
private void initFields() {
sharedFolder_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getSharedFolderCount(); i++) {
if (!getSharedFolder(i).isInitialized()) {
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
for (int i = 0; i < sharedFolder_.size(); i++) {
output.writeMessage(1, sharedFolder_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < sharedFolder_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, sharedFolder_.get(i));
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
public static Ritual.ListSharedFoldersReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListSharedFoldersReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListSharedFoldersReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListSharedFoldersReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListSharedFoldersReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListSharedFoldersReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListSharedFoldersReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListSharedFoldersReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListSharedFoldersReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListSharedFoldersReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListSharedFoldersReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListSharedFoldersReply, Builder>
implements
Ritual.ListSharedFoldersReplyOrBuilder {
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
sharedFolder_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListSharedFoldersReply getDefaultInstanceForType() {
return Ritual.ListSharedFoldersReply.getDefaultInstance();
}
public Ritual.ListSharedFoldersReply build() {
Ritual.ListSharedFoldersReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListSharedFoldersReply buildPartial() {
Ritual.ListSharedFoldersReply result = new Ritual.ListSharedFoldersReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
sharedFolder_ = Collections.unmodifiableList(sharedFolder_);
b0_ = (b0_ & ~0x00000001);
}
result.sharedFolder_ = sharedFolder_;
return result;
}
public Builder mergeFrom(Ritual.ListSharedFoldersReply other) {
if (other == Ritual.ListSharedFoldersReply.getDefaultInstance()) return this;
if (!other.sharedFolder_.isEmpty()) {
if (sharedFolder_.isEmpty()) {
sharedFolder_ = other.sharedFolder_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureSharedFolderIsMutable();
sharedFolder_.addAll(other.sharedFolder_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getSharedFolderCount(); i++) {
if (!getSharedFolder(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListSharedFoldersReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListSharedFoldersReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.PBSharedFolder> sharedFolder_ =
Collections.emptyList();
private void ensureSharedFolderIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
sharedFolder_ = new ArrayList<Ritual.PBSharedFolder>(sharedFolder_);
b0_ |= 0x00000001;
}
}
public List<Ritual.PBSharedFolder> getSharedFolderList() {
return Collections.unmodifiableList(sharedFolder_);
}
public int getSharedFolderCount() {
return sharedFolder_.size();
}
public Ritual.PBSharedFolder getSharedFolder(int index) {
return sharedFolder_.get(index);
}
public Builder setSharedFolder(
int index, Ritual.PBSharedFolder value) {
if (value == null) {
throw new NullPointerException();
}
ensureSharedFolderIsMutable();
sharedFolder_.set(index, value);
return this;
}
public Builder setSharedFolder(
int index, Ritual.PBSharedFolder.Builder bdForValue) {
ensureSharedFolderIsMutable();
sharedFolder_.set(index, bdForValue.build());
return this;
}
public Builder addSharedFolder(Ritual.PBSharedFolder value) {
if (value == null) {
throw new NullPointerException();
}
ensureSharedFolderIsMutable();
sharedFolder_.add(value);
return this;
}
public Builder addSharedFolder(
int index, Ritual.PBSharedFolder value) {
if (value == null) {
throw new NullPointerException();
}
ensureSharedFolderIsMutable();
sharedFolder_.add(index, value);
return this;
}
public Builder addSharedFolder(
Ritual.PBSharedFolder.Builder bdForValue) {
ensureSharedFolderIsMutable();
sharedFolder_.add(bdForValue.build());
return this;
}
public Builder addSharedFolder(
int index, Ritual.PBSharedFolder.Builder bdForValue) {
ensureSharedFolderIsMutable();
sharedFolder_.add(index, bdForValue.build());
return this;
}
public Builder addAllSharedFolder(
Iterable<? extends Ritual.PBSharedFolder> values) {
ensureSharedFolderIsMutable();
AbstractMessageLite.Builder.addAll(
values, sharedFolder_);
return this;
}
public Builder clearSharedFolder() {
sharedFolder_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeSharedFolder(int index) {
ensureSharedFolderIsMutable();
sharedFolder_.remove(index);
return this;
}
}
static {
defaultInstance = new ListSharedFoldersReply(true);
defaultInstance.initFields();
}
}
public interface ExcludeFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class ExcludeFolderCall extends
GeneratedMessageLite implements
ExcludeFolderCallOrBuilder {
private ExcludeFolderCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExcludeFolderCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExcludeFolderCall defaultInstance;
public static ExcludeFolderCall getDefaultInstance() {
return defaultInstance;
}
public ExcludeFolderCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExcludeFolderCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<ExcludeFolderCall> PARSER =
new AbstractParser<ExcludeFolderCall>() {
public ExcludeFolderCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExcludeFolderCall(input, er);
}
};
@Override
public Parser<ExcludeFolderCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.ExcludeFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExcludeFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExcludeFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExcludeFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExcludeFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExcludeFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExcludeFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExcludeFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExcludeFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExcludeFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExcludeFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExcludeFolderCall, Builder>
implements
Ritual.ExcludeFolderCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExcludeFolderCall getDefaultInstanceForType() {
return Ritual.ExcludeFolderCall.getDefaultInstance();
}
public Ritual.ExcludeFolderCall build() {
Ritual.ExcludeFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExcludeFolderCall buildPartial() {
Ritual.ExcludeFolderCall result = new Ritual.ExcludeFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExcludeFolderCall other) {
if (other == Ritual.ExcludeFolderCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExcludeFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExcludeFolderCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new ExcludeFolderCall(true);
defaultInstance.initFields();
}
}
public interface IncludeFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class IncludeFolderCall extends
GeneratedMessageLite implements
IncludeFolderCallOrBuilder {
private IncludeFolderCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private IncludeFolderCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final IncludeFolderCall defaultInstance;
public static IncludeFolderCall getDefaultInstance() {
return defaultInstance;
}
public IncludeFolderCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private IncludeFolderCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<IncludeFolderCall> PARSER =
new AbstractParser<IncludeFolderCall>() {
public IncludeFolderCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new IncludeFolderCall(input, er);
}
};
@Override
public Parser<IncludeFolderCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.IncludeFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.IncludeFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.IncludeFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.IncludeFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.IncludeFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.IncludeFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.IncludeFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.IncludeFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.IncludeFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.IncludeFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.IncludeFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.IncludeFolderCall, Builder>
implements
Ritual.IncludeFolderCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.IncludeFolderCall getDefaultInstanceForType() {
return Ritual.IncludeFolderCall.getDefaultInstance();
}
public Ritual.IncludeFolderCall build() {
Ritual.IncludeFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.IncludeFolderCall buildPartial() {
Ritual.IncludeFolderCall result = new Ritual.IncludeFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.IncludeFolderCall other) {
if (other == Ritual.IncludeFolderCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.IncludeFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.IncludeFolderCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new IncludeFolderCall(true);
defaultInstance.initFields();
}
}
public interface ListExcludedFoldersReplyOrBuilder extends
MessageLiteOrBuilder {
List<Common.PBPath> 
getPathList();
Common.PBPath getPath(int index);
int getPathCount();
}
public static final class ListExcludedFoldersReply extends
GeneratedMessageLite implements
ListExcludedFoldersReplyOrBuilder {
private ListExcludedFoldersReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListExcludedFoldersReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListExcludedFoldersReply defaultInstance;
public static ListExcludedFoldersReply getDefaultInstance() {
return defaultInstance;
}
public ListExcludedFoldersReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListExcludedFoldersReply(
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
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<ListExcludedFoldersReply> PARSER =
new AbstractParser<ListExcludedFoldersReply>() {
public ListExcludedFoldersReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListExcludedFoldersReply(input, er);
}
};
@Override
public Parser<ListExcludedFoldersReply> getParserForType() {
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
private void initFields() {
path_ = Collections.emptyList();
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
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
for (int i = 0; i < path_.size(); i++) {
output.writeMessage(1, path_.get(i));
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
public static Ritual.ListExcludedFoldersReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListExcludedFoldersReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListExcludedFoldersReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListExcludedFoldersReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListExcludedFoldersReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListExcludedFoldersReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListExcludedFoldersReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListExcludedFoldersReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListExcludedFoldersReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListExcludedFoldersReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListExcludedFoldersReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListExcludedFoldersReply, Builder>
implements
Ritual.ListExcludedFoldersReplyOrBuilder {
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
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListExcludedFoldersReply getDefaultInstanceForType() {
return Ritual.ListExcludedFoldersReply.getDefaultInstance();
}
public Ritual.ListExcludedFoldersReply build() {
Ritual.ListExcludedFoldersReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListExcludedFoldersReply buildPartial() {
Ritual.ListExcludedFoldersReply result = new Ritual.ListExcludedFoldersReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
path_ = Collections.unmodifiableList(path_);
b0_ = (b0_ & ~0x00000001);
}
result.path_ = path_;
return result;
}
public Builder mergeFrom(Ritual.ListExcludedFoldersReply other) {
if (other == Ritual.ListExcludedFoldersReply.getDefaultInstance()) return this;
if (!other.path_.isEmpty()) {
if (path_.isEmpty()) {
path_ = other.path_;
b0_ = (b0_ & ~0x00000001);
} else {
ensurePathIsMutable();
path_.addAll(other.path_);
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
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListExcludedFoldersReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListExcludedFoldersReply) e.getUnfinishedMessage();
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
}
static {
defaultInstance = new ListExcludedFoldersReply(true);
defaultInstance.initFields();
}
}
public interface ImportFileCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasDestination();
Common.PBPath getDestination();
boolean hasSource();
String getSource();
ByteString
getSourceBytes();
}
public static final class ImportFileCall extends
GeneratedMessageLite implements
ImportFileCallOrBuilder {
private ImportFileCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ImportFileCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ImportFileCall defaultInstance;
public static ImportFileCall getDefaultInstance() {
return defaultInstance;
}
public ImportFileCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ImportFileCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = destination_.toBuilder();
}
destination_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(destination_);
destination_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
source_ = bs;
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
public static Parser<ImportFileCall> PARSER =
new AbstractParser<ImportFileCall>() {
public ImportFileCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ImportFileCall(input, er);
}
};
@Override
public Parser<ImportFileCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DESTINATION_FIELD_NUMBER = 1;
private Common.PBPath destination_;
public boolean hasDestination() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getDestination() {
return destination_;
}
public static final int SOURCE_FIELD_NUMBER = 2;
private Object source_;
public boolean hasSource() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getSource() {
Object ref = source_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
source_ = s;
}
return s;
}
}
public ByteString
getSourceBytes() {
Object ref = source_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
source_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
destination_ = Common.PBPath.getDefaultInstance();
source_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasDestination()) {
mii = 0;
return false;
}
if (!hasSource()) {
mii = 0;
return false;
}
if (!getDestination().isInitialized()) {
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
output.writeMessage(1, destination_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getSourceBytes());
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
.computeMessageSize(1, destination_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getSourceBytes());
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
public static Ritual.ImportFileCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ImportFileCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ImportFileCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ImportFileCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ImportFileCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ImportFileCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ImportFileCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ImportFileCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ImportFileCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ImportFileCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ImportFileCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ImportFileCall, Builder>
implements
Ritual.ImportFileCallOrBuilder {
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
destination_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
source_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ImportFileCall getDefaultInstanceForType() {
return Ritual.ImportFileCall.getDefaultInstance();
}
public Ritual.ImportFileCall build() {
Ritual.ImportFileCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ImportFileCall buildPartial() {
Ritual.ImportFileCall result = new Ritual.ImportFileCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.destination_ = destination_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.source_ = source_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ImportFileCall other) {
if (other == Ritual.ImportFileCall.getDefaultInstance()) return this;
if (other.hasDestination()) {
mergeDestination(other.getDestination());
}
if (other.hasSource()) {
b0_ |= 0x00000002;
source_ = other.source_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasDestination()) {
return false;
}
if (!hasSource()) {
return false;
}
if (!getDestination().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ImportFileCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ImportFileCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath destination_ = Common.PBPath.getDefaultInstance();
public boolean hasDestination() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getDestination() {
return destination_;
}
public Builder setDestination(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
destination_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setDestination(
Common.PBPath.Builder bdForValue) {
destination_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergeDestination(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
destination_ != Common.PBPath.getDefaultInstance()) {
destination_ =
Common.PBPath.newBuilder(destination_).mergeFrom(value).buildPartial();
} else {
destination_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearDestination() {
destination_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Object source_ = "";
public boolean hasSource() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getSource() {
Object ref = source_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
source_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getSourceBytes() {
Object ref = source_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
source_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setSource(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
source_ = value;
return this;
}
public Builder clearSource() {
b0_ = (b0_ & ~0x00000002);
source_ = getDefaultInstance().getSource();
return this;
}
public Builder setSourceBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
source_ = value;
return this;
}
}
static {
defaultInstance = new ImportFileCall(true);
defaultInstance.initFields();
}
}
public interface ExportFileCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasSource();
Common.PBPath getSource();
}
public static final class ExportFileCall extends
GeneratedMessageLite implements
ExportFileCallOrBuilder {
private ExportFileCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExportFileCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExportFileCall defaultInstance;
public static ExportFileCall getDefaultInstance() {
return defaultInstance;
}
public ExportFileCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExportFileCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = source_.toBuilder();
}
source_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(source_);
source_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<ExportFileCall> PARSER =
new AbstractParser<ExportFileCall>() {
public ExportFileCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExportFileCall(input, er);
}
};
@Override
public Parser<ExportFileCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SOURCE_FIELD_NUMBER = 1;
private Common.PBPath source_;
public boolean hasSource() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getSource() {
return source_;
}
private void initFields() {
source_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasSource()) {
mii = 0;
return false;
}
if (!getSource().isInitialized()) {
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
output.writeMessage(1, source_);
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
.computeMessageSize(1, source_);
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
public static Ritual.ExportFileCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportFileCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportFileCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportFileCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportFileCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportFileCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExportFileCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExportFileCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExportFileCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportFileCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExportFileCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExportFileCall, Builder>
implements
Ritual.ExportFileCallOrBuilder {
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
source_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExportFileCall getDefaultInstanceForType() {
return Ritual.ExportFileCall.getDefaultInstance();
}
public Ritual.ExportFileCall build() {
Ritual.ExportFileCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExportFileCall buildPartial() {
Ritual.ExportFileCall result = new Ritual.ExportFileCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.source_ = source_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExportFileCall other) {
if (other == Ritual.ExportFileCall.getDefaultInstance()) return this;
if (other.hasSource()) {
mergeSource(other.getSource());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasSource()) {
return false;
}
if (!getSource().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExportFileCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExportFileCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath source_ = Common.PBPath.getDefaultInstance();
public boolean hasSource() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getSource() {
return source_;
}
public Builder setSource(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
source_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setSource(
Common.PBPath.Builder bdForValue) {
source_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergeSource(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
source_ != Common.PBPath.getDefaultInstance()) {
source_ =
Common.PBPath.newBuilder(source_).mergeFrom(value).buildPartial();
} else {
source_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearSource() {
source_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new ExportFileCall(true);
defaultInstance.initFields();
}
}
public interface ExportFileReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasDest();
String getDest();
ByteString
getDestBytes();
}
public static final class ExportFileReply extends
GeneratedMessageLite implements
ExportFileReplyOrBuilder {
private ExportFileReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExportFileReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExportFileReply defaultInstance;
public static ExportFileReply getDefaultInstance() {
return defaultInstance;
}
public ExportFileReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExportFileReply(
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
dest_ = bs;
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
public static Parser<ExportFileReply> PARSER =
new AbstractParser<ExportFileReply>() {
public ExportFileReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExportFileReply(input, er);
}
};
@Override
public Parser<ExportFileReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DEST_FIELD_NUMBER = 1;
private Object dest_;
public boolean hasDest() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDest() {
Object ref = dest_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
dest_ = s;
}
return s;
}
}
public ByteString
getDestBytes() {
Object ref = dest_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
dest_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
dest_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasDest()) {
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
output.writeBytes(1, getDestBytes());
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
.computeBytesSize(1, getDestBytes());
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
public static Ritual.ExportFileReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportFileReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportFileReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportFileReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportFileReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportFileReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExportFileReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExportFileReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExportFileReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportFileReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExportFileReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExportFileReply, Builder>
implements
Ritual.ExportFileReplyOrBuilder {
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
dest_ = "";
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExportFileReply getDefaultInstanceForType() {
return Ritual.ExportFileReply.getDefaultInstance();
}
public Ritual.ExportFileReply build() {
Ritual.ExportFileReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExportFileReply buildPartial() {
Ritual.ExportFileReply result = new Ritual.ExportFileReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.dest_ = dest_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExportFileReply other) {
if (other == Ritual.ExportFileReply.getDefaultInstance()) return this;
if (other.hasDest()) {
b0_ |= 0x00000001;
dest_ = other.dest_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasDest()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExportFileReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExportFileReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object dest_ = "";
public boolean hasDest() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDest() {
Object ref = dest_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
dest_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDestBytes() {
Object ref = dest_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
dest_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDest(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
dest_ = value;
return this;
}
public Builder clearDest() {
b0_ = (b0_ & ~0x00000001);
dest_ = getDefaultInstance().getDest();
return this;
}
public Builder setDestBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
dest_ = value;
return this;
}
}
static {
defaultInstance = new ExportFileReply(true);
defaultInstance.initFields();
}
}
public interface ListConflictsReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.ListConflictsReply.ConflictedPath> 
getConflictList();
Ritual.ListConflictsReply.ConflictedPath getConflict(int index);
int getConflictCount();
}
public static final class ListConflictsReply extends
GeneratedMessageLite implements
ListConflictsReplyOrBuilder {
private ListConflictsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListConflictsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListConflictsReply defaultInstance;
public static ListConflictsReply getDefaultInstance() {
return defaultInstance;
}
public ListConflictsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListConflictsReply(
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
conflict_ = new ArrayList<Ritual.ListConflictsReply.ConflictedPath>();
mutable_b0_ |= 0x00000001;
}
conflict_.add(input.readMessage(Ritual.ListConflictsReply.ConflictedPath.PARSER, er));
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
conflict_ = Collections.unmodifiableList(conflict_);
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
public static Parser<ListConflictsReply> PARSER =
new AbstractParser<ListConflictsReply>() {
public ListConflictsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListConflictsReply(input, er);
}
};
@Override
public Parser<ListConflictsReply> getParserForType() {
return PARSER;
}
public interface ConflictedPathOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasBranchCount();
int getBranchCount();
}
public static final class ConflictedPath extends
GeneratedMessageLite implements
ConflictedPathOrBuilder {
private ConflictedPath(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ConflictedPath(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ConflictedPath defaultInstance;
public static ConflictedPath getDefaultInstance() {
return defaultInstance;
}
public ConflictedPath getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ConflictedPath(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 16: {
b0_ |= 0x00000002;
branchCount_ = input.readInt32();
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
public static Parser<ConflictedPath> PARSER =
new AbstractParser<ConflictedPath>() {
public ConflictedPath parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ConflictedPath(input, er);
}
};
@Override
public Parser<ConflictedPath> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int BRANCH_COUNT_FIELD_NUMBER = 2;
private int branchCount_;
public boolean hasBranchCount() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getBranchCount() {
return branchCount_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
branchCount_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasBranchCount()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeInt32(2, branchCount_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeInt32Size(2, branchCount_);
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
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListConflictsReply.ConflictedPath parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListConflictsReply.ConflictedPath parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListConflictsReply.ConflictedPath parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListConflictsReply.ConflictedPath prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListConflictsReply.ConflictedPath, Builder>
implements
Ritual.ListConflictsReply.ConflictedPathOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
branchCount_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListConflictsReply.ConflictedPath getDefaultInstanceForType() {
return Ritual.ListConflictsReply.ConflictedPath.getDefaultInstance();
}
public Ritual.ListConflictsReply.ConflictedPath build() {
Ritual.ListConflictsReply.ConflictedPath result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListConflictsReply.ConflictedPath buildPartial() {
Ritual.ListConflictsReply.ConflictedPath result = new Ritual.ListConflictsReply.ConflictedPath(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.branchCount_ = branchCount_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ListConflictsReply.ConflictedPath other) {
if (other == Ritual.ListConflictsReply.ConflictedPath.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasBranchCount()) {
setBranchCount(other.getBranchCount());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasBranchCount()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListConflictsReply.ConflictedPath pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListConflictsReply.ConflictedPath) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private int branchCount_ ;
public boolean hasBranchCount() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getBranchCount() {
return branchCount_;
}
public Builder setBranchCount(int value) {
b0_ |= 0x00000002;
branchCount_ = value;
return this;
}
public Builder clearBranchCount() {
b0_ = (b0_ & ~0x00000002);
branchCount_ = 0;
return this;
}
}
static {
defaultInstance = new ConflictedPath(true);
defaultInstance.initFields();
}
}
public static final int CONFLICT_FIELD_NUMBER = 1;
private List<Ritual.ListConflictsReply.ConflictedPath> conflict_;
public List<Ritual.ListConflictsReply.ConflictedPath> getConflictList() {
return conflict_;
}
public List<? extends Ritual.ListConflictsReply.ConflictedPathOrBuilder> 
getConflictOrBuilderList() {
return conflict_;
}
public int getConflictCount() {
return conflict_.size();
}
public Ritual.ListConflictsReply.ConflictedPath getConflict(int index) {
return conflict_.get(index);
}
public Ritual.ListConflictsReply.ConflictedPathOrBuilder getConflictOrBuilder(
int index) {
return conflict_.get(index);
}
private void initFields() {
conflict_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getConflictCount(); i++) {
if (!getConflict(i).isInitialized()) {
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
for (int i = 0; i < conflict_.size(); i++) {
output.writeMessage(1, conflict_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < conflict_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, conflict_.get(i));
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
public static Ritual.ListConflictsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListConflictsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListConflictsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListConflictsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListConflictsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListConflictsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListConflictsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListConflictsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListConflictsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListConflictsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListConflictsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListConflictsReply, Builder>
implements
Ritual.ListConflictsReplyOrBuilder {
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
conflict_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListConflictsReply getDefaultInstanceForType() {
return Ritual.ListConflictsReply.getDefaultInstance();
}
public Ritual.ListConflictsReply build() {
Ritual.ListConflictsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListConflictsReply buildPartial() {
Ritual.ListConflictsReply result = new Ritual.ListConflictsReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
conflict_ = Collections.unmodifiableList(conflict_);
b0_ = (b0_ & ~0x00000001);
}
result.conflict_ = conflict_;
return result;
}
public Builder mergeFrom(Ritual.ListConflictsReply other) {
if (other == Ritual.ListConflictsReply.getDefaultInstance()) return this;
if (!other.conflict_.isEmpty()) {
if (conflict_.isEmpty()) {
conflict_ = other.conflict_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureConflictIsMutable();
conflict_.addAll(other.conflict_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getConflictCount(); i++) {
if (!getConflict(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListConflictsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListConflictsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.ListConflictsReply.ConflictedPath> conflict_ =
Collections.emptyList();
private void ensureConflictIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
conflict_ = new ArrayList<Ritual.ListConflictsReply.ConflictedPath>(conflict_);
b0_ |= 0x00000001;
}
}
public List<Ritual.ListConflictsReply.ConflictedPath> getConflictList() {
return Collections.unmodifiableList(conflict_);
}
public int getConflictCount() {
return conflict_.size();
}
public Ritual.ListConflictsReply.ConflictedPath getConflict(int index) {
return conflict_.get(index);
}
public Builder setConflict(
int index, Ritual.ListConflictsReply.ConflictedPath value) {
if (value == null) {
throw new NullPointerException();
}
ensureConflictIsMutable();
conflict_.set(index, value);
return this;
}
public Builder setConflict(
int index, Ritual.ListConflictsReply.ConflictedPath.Builder bdForValue) {
ensureConflictIsMutable();
conflict_.set(index, bdForValue.build());
return this;
}
public Builder addConflict(Ritual.ListConflictsReply.ConflictedPath value) {
if (value == null) {
throw new NullPointerException();
}
ensureConflictIsMutable();
conflict_.add(value);
return this;
}
public Builder addConflict(
int index, Ritual.ListConflictsReply.ConflictedPath value) {
if (value == null) {
throw new NullPointerException();
}
ensureConflictIsMutable();
conflict_.add(index, value);
return this;
}
public Builder addConflict(
Ritual.ListConflictsReply.ConflictedPath.Builder bdForValue) {
ensureConflictIsMutable();
conflict_.add(bdForValue.build());
return this;
}
public Builder addConflict(
int index, Ritual.ListConflictsReply.ConflictedPath.Builder bdForValue) {
ensureConflictIsMutable();
conflict_.add(index, bdForValue.build());
return this;
}
public Builder addAllConflict(
Iterable<? extends Ritual.ListConflictsReply.ConflictedPath> values) {
ensureConflictIsMutable();
AbstractMessageLite.Builder.addAll(
values, conflict_);
return this;
}
public Builder clearConflict() {
conflict_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeConflict(int index) {
ensureConflictIsMutable();
conflict_.remove(index);
return this;
}
}
static {
defaultInstance = new ListConflictsReply(true);
defaultInstance.initFields();
}
}
public interface ExportConflictCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasKidx();
int getKidx();
}
public static final class ExportConflictCall extends
GeneratedMessageLite implements
ExportConflictCallOrBuilder {
private ExportConflictCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExportConflictCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExportConflictCall defaultInstance;
public static ExportConflictCall getDefaultInstance() {
return defaultInstance;
}
public ExportConflictCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExportConflictCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 16: {
b0_ |= 0x00000002;
kidx_ = input.readInt32();
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
public static Parser<ExportConflictCall> PARSER =
new AbstractParser<ExportConflictCall>() {
public ExportConflictCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExportConflictCall(input, er);
}
};
@Override
public Parser<ExportConflictCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int KIDX_FIELD_NUMBER = 2;
private int kidx_;
public boolean hasKidx() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getKidx() {
return kidx_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
kidx_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasKidx()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeInt32(2, kidx_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeInt32Size(2, kidx_);
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
public static Ritual.ExportConflictCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportConflictCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportConflictCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportConflictCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportConflictCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportConflictCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExportConflictCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExportConflictCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExportConflictCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportConflictCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExportConflictCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExportConflictCall, Builder>
implements
Ritual.ExportConflictCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
kidx_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExportConflictCall getDefaultInstanceForType() {
return Ritual.ExportConflictCall.getDefaultInstance();
}
public Ritual.ExportConflictCall build() {
Ritual.ExportConflictCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExportConflictCall buildPartial() {
Ritual.ExportConflictCall result = new Ritual.ExportConflictCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.kidx_ = kidx_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExportConflictCall other) {
if (other == Ritual.ExportConflictCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasKidx()) {
setKidx(other.getKidx());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasKidx()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExportConflictCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExportConflictCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private int kidx_ ;
public boolean hasKidx() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getKidx() {
return kidx_;
}
public Builder setKidx(int value) {
b0_ |= 0x00000002;
kidx_ = value;
return this;
}
public Builder clearKidx() {
b0_ = (b0_ & ~0x00000002);
kidx_ = 0;
return this;
}
}
static {
defaultInstance = new ExportConflictCall(true);
defaultInstance.initFields();
}
}
public interface ExportConflictReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasDest();
String getDest();
ByteString
getDestBytes();
}
public static final class ExportConflictReply extends
GeneratedMessageLite implements
ExportConflictReplyOrBuilder {
private ExportConflictReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExportConflictReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExportConflictReply defaultInstance;
public static ExportConflictReply getDefaultInstance() {
return defaultInstance;
}
public ExportConflictReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExportConflictReply(
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
dest_ = bs;
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
public static Parser<ExportConflictReply> PARSER =
new AbstractParser<ExportConflictReply>() {
public ExportConflictReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExportConflictReply(input, er);
}
};
@Override
public Parser<ExportConflictReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DEST_FIELD_NUMBER = 1;
private Object dest_;
public boolean hasDest() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDest() {
Object ref = dest_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
dest_ = s;
}
return s;
}
}
public ByteString
getDestBytes() {
Object ref = dest_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
dest_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
dest_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasDest()) {
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
output.writeBytes(1, getDestBytes());
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
.computeBytesSize(1, getDestBytes());
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
public static Ritual.ExportConflictReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportConflictReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportConflictReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportConflictReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportConflictReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportConflictReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExportConflictReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExportConflictReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExportConflictReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportConflictReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExportConflictReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExportConflictReply, Builder>
implements
Ritual.ExportConflictReplyOrBuilder {
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
dest_ = "";
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExportConflictReply getDefaultInstanceForType() {
return Ritual.ExportConflictReply.getDefaultInstance();
}
public Ritual.ExportConflictReply build() {
Ritual.ExportConflictReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExportConflictReply buildPartial() {
Ritual.ExportConflictReply result = new Ritual.ExportConflictReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.dest_ = dest_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExportConflictReply other) {
if (other == Ritual.ExportConflictReply.getDefaultInstance()) return this;
if (other.hasDest()) {
b0_ |= 0x00000001;
dest_ = other.dest_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasDest()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExportConflictReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExportConflictReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object dest_ = "";
public boolean hasDest() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDest() {
Object ref = dest_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
dest_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDestBytes() {
Object ref = dest_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
dest_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDest(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
dest_ = value;
return this;
}
public Builder clearDest() {
b0_ = (b0_ & ~0x00000001);
dest_ = getDefaultInstance().getDest();
return this;
}
public Builder setDestBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
dest_ = value;
return this;
}
}
static {
defaultInstance = new ExportConflictReply(true);
defaultInstance.initFields();
}
}
public interface DeleteConflictCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasKidx();
int getKidx();
}
public static final class DeleteConflictCall extends
GeneratedMessageLite implements
DeleteConflictCallOrBuilder {
private DeleteConflictCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DeleteConflictCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final DeleteConflictCall defaultInstance;
public static DeleteConflictCall getDefaultInstance() {
return defaultInstance;
}
public DeleteConflictCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private DeleteConflictCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 16: {
b0_ |= 0x00000002;
kidx_ = input.readInt32();
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
public static Parser<DeleteConflictCall> PARSER =
new AbstractParser<DeleteConflictCall>() {
public DeleteConflictCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DeleteConflictCall(input, er);
}
};
@Override
public Parser<DeleteConflictCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int KIDX_FIELD_NUMBER = 2;
private int kidx_;
public boolean hasKidx() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getKidx() {
return kidx_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
kidx_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasKidx()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeInt32(2, kidx_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeInt32Size(2, kidx_);
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
public static Ritual.DeleteConflictCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteConflictCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteConflictCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteConflictCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteConflictCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteConflictCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.DeleteConflictCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.DeleteConflictCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.DeleteConflictCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteConflictCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.DeleteConflictCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.DeleteConflictCall, Builder>
implements
Ritual.DeleteConflictCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
kidx_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.DeleteConflictCall getDefaultInstanceForType() {
return Ritual.DeleteConflictCall.getDefaultInstance();
}
public Ritual.DeleteConflictCall build() {
Ritual.DeleteConflictCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.DeleteConflictCall buildPartial() {
Ritual.DeleteConflictCall result = new Ritual.DeleteConflictCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.kidx_ = kidx_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.DeleteConflictCall other) {
if (other == Ritual.DeleteConflictCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasKidx()) {
setKidx(other.getKidx());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasKidx()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.DeleteConflictCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.DeleteConflictCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private int kidx_ ;
public boolean hasKidx() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getKidx() {
return kidx_;
}
public Builder setKidx(int value) {
b0_ |= 0x00000002;
kidx_ = value;
return this;
}
public Builder clearKidx() {
b0_ = (b0_ & ~0x00000002);
kidx_ = 0;
return this;
}
}
static {
defaultInstance = new DeleteConflictCall(true);
defaultInstance.initFields();
}
}
public interface UpdateACLCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasSubject();
String getSubject();
ByteString
getSubjectBytes();
boolean hasPermissions();
Common.PBPermissions getPermissions();
boolean hasSuppressSharingRulesWarnings();
boolean getSuppressSharingRulesWarnings();
}
public static final class UpdateACLCall extends
GeneratedMessageLite implements
UpdateACLCallOrBuilder {
private UpdateACLCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private UpdateACLCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final UpdateACLCall defaultInstance;
public static UpdateACLCall getDefaultInstance() {
return defaultInstance;
}
public UpdateACLCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private UpdateACLCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
subject_ = bs;
break;
}
case 26: {
Common.PBPermissions.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = permissions_.toBuilder();
}
permissions_ = input.readMessage(Common.PBPermissions.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(permissions_);
permissions_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
break;
}
case 32: {
b0_ |= 0x00000008;
suppressSharingRulesWarnings_ = input.readBool();
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
public static Parser<UpdateACLCall> PARSER =
new AbstractParser<UpdateACLCall>() {
public UpdateACLCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new UpdateACLCall(input, er);
}
};
@Override
public Parser<UpdateACLCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int SUBJECT_FIELD_NUMBER = 2;
private Object subject_;
public boolean hasSubject() {
return ((b0_ & 0x00000002) == 0x00000002);
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
public static final int PERMISSIONS_FIELD_NUMBER = 3;
private Common.PBPermissions permissions_;
public boolean hasPermissions() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Common.PBPermissions getPermissions() {
return permissions_;
}
public static final int SUPPRESS_SHARING_RULES_WARNINGS_FIELD_NUMBER = 4;
private boolean suppressSharingRulesWarnings_;
public boolean hasSuppressSharingRulesWarnings() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public boolean getSuppressSharingRulesWarnings() {
return suppressSharingRulesWarnings_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
subject_ = "";
permissions_ = Common.PBPermissions.getDefaultInstance();
suppressSharingRulesWarnings_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasSubject()) {
mii = 0;
return false;
}
if (!hasPermissions()) {
mii = 0;
return false;
}
if (!hasSuppressSharingRulesWarnings()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getSubjectBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, permissions_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeBool(4, suppressSharingRulesWarnings_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getSubjectBytes());
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, permissions_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeBoolSize(4, suppressSharingRulesWarnings_);
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
public static Ritual.UpdateACLCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.UpdateACLCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.UpdateACLCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.UpdateACLCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.UpdateACLCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.UpdateACLCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.UpdateACLCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.UpdateACLCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.UpdateACLCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.UpdateACLCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.UpdateACLCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.UpdateACLCall, Builder>
implements
Ritual.UpdateACLCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
subject_ = "";
b0_ = (b0_ & ~0x00000002);
permissions_ = Common.PBPermissions.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
suppressSharingRulesWarnings_ = false;
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.UpdateACLCall getDefaultInstanceForType() {
return Ritual.UpdateACLCall.getDefaultInstance();
}
public Ritual.UpdateACLCall build() {
Ritual.UpdateACLCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.UpdateACLCall buildPartial() {
Ritual.UpdateACLCall result = new Ritual.UpdateACLCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.subject_ = subject_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.permissions_ = permissions_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.suppressSharingRulesWarnings_ = suppressSharingRulesWarnings_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.UpdateACLCall other) {
if (other == Ritual.UpdateACLCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasSubject()) {
b0_ |= 0x00000002;
subject_ = other.subject_;
}
if (other.hasPermissions()) {
mergePermissions(other.getPermissions());
}
if (other.hasSuppressSharingRulesWarnings()) {
setSuppressSharingRulesWarnings(other.getSuppressSharingRulesWarnings());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasSubject()) {
return false;
}
if (!hasPermissions()) {
return false;
}
if (!hasSuppressSharingRulesWarnings()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.UpdateACLCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.UpdateACLCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Object subject_ = "";
public boolean hasSubject() {
return ((b0_ & 0x00000002) == 0x00000002);
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
b0_ |= 0x00000002;
subject_ = value;
return this;
}
public Builder clearSubject() {
b0_ = (b0_ & ~0x00000002);
subject_ = getDefaultInstance().getSubject();
return this;
}
public Builder setSubjectBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
subject_ = value;
return this;
}
private Common.PBPermissions permissions_ = Common.PBPermissions.getDefaultInstance();
public boolean hasPermissions() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Common.PBPermissions getPermissions() {
return permissions_;
}
public Builder setPermissions(Common.PBPermissions value) {
if (value == null) {
throw new NullPointerException();
}
permissions_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setPermissions(
Common.PBPermissions.Builder bdForValue) {
permissions_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergePermissions(Common.PBPermissions value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
permissions_ != Common.PBPermissions.getDefaultInstance()) {
permissions_ =
Common.PBPermissions.newBuilder(permissions_).mergeFrom(value).buildPartial();
} else {
permissions_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearPermissions() {
permissions_ = Common.PBPermissions.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
private boolean suppressSharingRulesWarnings_ ;
public boolean hasSuppressSharingRulesWarnings() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public boolean getSuppressSharingRulesWarnings() {
return suppressSharingRulesWarnings_;
}
public Builder setSuppressSharingRulesWarnings(boolean value) {
b0_ |= 0x00000008;
suppressSharingRulesWarnings_ = value;
return this;
}
public Builder clearSuppressSharingRulesWarnings() {
b0_ = (b0_ & ~0x00000008);
suppressSharingRulesWarnings_ = false;
return this;
}
}
static {
defaultInstance = new UpdateACLCall(true);
defaultInstance.initFields();
}
}
public interface DeleteACLCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasSubject();
String getSubject();
ByteString
getSubjectBytes();
}
public static final class DeleteACLCall extends
GeneratedMessageLite implements
DeleteACLCallOrBuilder {
private DeleteACLCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DeleteACLCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final DeleteACLCall defaultInstance;
public static DeleteACLCall getDefaultInstance() {
return defaultInstance;
}
public DeleteACLCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private DeleteACLCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
subject_ = bs;
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
public static Parser<DeleteACLCall> PARSER =
new AbstractParser<DeleteACLCall>() {
public DeleteACLCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DeleteACLCall(input, er);
}
};
@Override
public Parser<DeleteACLCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int SUBJECT_FIELD_NUMBER = 2;
private Object subject_;
public boolean hasSubject() {
return ((b0_ & 0x00000002) == 0x00000002);
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
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
subject_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasSubject()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getSubjectBytes());
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, getSubjectBytes());
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
public static Ritual.DeleteACLCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteACLCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteACLCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteACLCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteACLCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteACLCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.DeleteACLCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.DeleteACLCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.DeleteACLCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteACLCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.DeleteACLCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.DeleteACLCall, Builder>
implements
Ritual.DeleteACLCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
subject_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.DeleteACLCall getDefaultInstanceForType() {
return Ritual.DeleteACLCall.getDefaultInstance();
}
public Ritual.DeleteACLCall build() {
Ritual.DeleteACLCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.DeleteACLCall buildPartial() {
Ritual.DeleteACLCall result = new Ritual.DeleteACLCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.subject_ = subject_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.DeleteACLCall other) {
if (other == Ritual.DeleteACLCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasSubject()) {
b0_ |= 0x00000002;
subject_ = other.subject_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasSubject()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.DeleteACLCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.DeleteACLCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Object subject_ = "";
public boolean hasSubject() {
return ((b0_ & 0x00000002) == 0x00000002);
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
b0_ |= 0x00000002;
subject_ = value;
return this;
}
public Builder clearSubject() {
b0_ = (b0_ & ~0x00000002);
subject_ = getDefaultInstance().getSubject();
return this;
}
public Builder setSubjectBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
subject_ = value;
return this;
}
}
static {
defaultInstance = new DeleteACLCall(true);
defaultInstance.initFields();
}
}
public interface RelocateCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasAbsolutePath();
String getAbsolutePath();
ByteString
getAbsolutePathBytes();
boolean hasStoreId();
ByteString getStoreId();
}
public static final class RelocateCall extends
GeneratedMessageLite implements
RelocateCallOrBuilder {
private RelocateCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private RelocateCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final RelocateCall defaultInstance;
public static RelocateCall getDefaultInstance() {
return defaultInstance;
}
public RelocateCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private RelocateCall(
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
absolutePath_ = bs;
break;
}
case 18: {
b0_ |= 0x00000002;
storeId_ = input.readBytes();
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
public static Parser<RelocateCall> PARSER =
new AbstractParser<RelocateCall>() {
public RelocateCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new RelocateCall(input, er);
}
};
@Override
public Parser<RelocateCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int ABSOLUTE_PATH_FIELD_NUMBER = 1;
private Object absolutePath_;
public boolean hasAbsolutePath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getAbsolutePath() {
Object ref = absolutePath_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
absolutePath_ = s;
}
return s;
}
}
public ByteString
getAbsolutePathBytes() {
Object ref = absolutePath_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
absolutePath_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int STORE_ID_FIELD_NUMBER = 2;
private ByteString storeId_;
public boolean hasStoreId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getStoreId() {
return storeId_;
}
private void initFields() {
absolutePath_ = "";
storeId_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasAbsolutePath()) {
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
output.writeBytes(1, getAbsolutePathBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, storeId_);
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
.computeBytesSize(1, getAbsolutePathBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, storeId_);
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
public static Ritual.RelocateCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.RelocateCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.RelocateCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.RelocateCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.RelocateCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.RelocateCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.RelocateCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.RelocateCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.RelocateCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.RelocateCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.RelocateCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.RelocateCall, Builder>
implements
Ritual.RelocateCallOrBuilder {
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
absolutePath_ = "";
b0_ = (b0_ & ~0x00000001);
storeId_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.RelocateCall getDefaultInstanceForType() {
return Ritual.RelocateCall.getDefaultInstance();
}
public Ritual.RelocateCall build() {
Ritual.RelocateCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.RelocateCall buildPartial() {
Ritual.RelocateCall result = new Ritual.RelocateCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.absolutePath_ = absolutePath_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.storeId_ = storeId_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.RelocateCall other) {
if (other == Ritual.RelocateCall.getDefaultInstance()) return this;
if (other.hasAbsolutePath()) {
b0_ |= 0x00000001;
absolutePath_ = other.absolutePath_;
}
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasAbsolutePath()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.RelocateCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.RelocateCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object absolutePath_ = "";
public boolean hasAbsolutePath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getAbsolutePath() {
Object ref = absolutePath_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
absolutePath_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getAbsolutePathBytes() {
Object ref = absolutePath_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
absolutePath_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setAbsolutePath(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
absolutePath_ = value;
return this;
}
public Builder clearAbsolutePath() {
b0_ = (b0_ & ~0x00000001);
absolutePath_ = getDefaultInstance().getAbsolutePath();
return this;
}
public Builder setAbsolutePathBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
absolutePath_ = value;
return this;
}
private ByteString storeId_ = ByteString.EMPTY;
public boolean hasStoreId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getStoreId() {
return storeId_;
}
public Builder setStoreId(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
storeId_ = value;
return this;
}
public Builder clearStoreId() {
b0_ = (b0_ & ~0x00000002);
storeId_ = getDefaultInstance().getStoreId();
return this;
}
}
static {
defaultInstance = new RelocateCall(true);
defaultInstance.initFields();
}
}
public interface GetActivitiesCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasBrief();
boolean getBrief();
boolean hasMaxResults();
int getMaxResults();
boolean hasPageToken();
long getPageToken();
}
public static final class GetActivitiesCall extends
GeneratedMessageLite implements
GetActivitiesCallOrBuilder {
private GetActivitiesCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetActivitiesCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetActivitiesCall defaultInstance;
public static GetActivitiesCall getDefaultInstance() {
return defaultInstance;
}
public GetActivitiesCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetActivitiesCall(
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
brief_ = input.readBool();
break;
}
case 16: {
b0_ |= 0x00000002;
maxResults_ = input.readUInt32();
break;
}
case 24: {
b0_ |= 0x00000004;
pageToken_ = input.readUInt64();
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
public static Parser<GetActivitiesCall> PARSER =
new AbstractParser<GetActivitiesCall>() {
public GetActivitiesCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetActivitiesCall(input, er);
}
};
@Override
public Parser<GetActivitiesCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int BRIEF_FIELD_NUMBER = 1;
private boolean brief_;
public boolean hasBrief() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getBrief() {
return brief_;
}
public static final int MAX_RESULTS_FIELD_NUMBER = 2;
private int maxResults_;
public boolean hasMaxResults() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getMaxResults() {
return maxResults_;
}
public static final int PAGE_TOKEN_FIELD_NUMBER = 3;
private long pageToken_;
public boolean hasPageToken() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getPageToken() {
return pageToken_;
}
private void initFields() {
brief_ = false;
maxResults_ = 0;
pageToken_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasBrief()) {
mii = 0;
return false;
}
if (!hasMaxResults()) {
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
output.writeBool(1, brief_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt32(2, maxResults_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, pageToken_);
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
.computeBoolSize(1, brief_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt32Size(2, maxResults_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, pageToken_);
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
public static Ritual.GetActivitiesCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetActivitiesCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetActivitiesCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetActivitiesCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetActivitiesCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetActivitiesCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetActivitiesCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetActivitiesCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetActivitiesCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetActivitiesCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetActivitiesCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetActivitiesCall, Builder>
implements
Ritual.GetActivitiesCallOrBuilder {
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
brief_ = false;
b0_ = (b0_ & ~0x00000001);
maxResults_ = 0;
b0_ = (b0_ & ~0x00000002);
pageToken_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetActivitiesCall getDefaultInstanceForType() {
return Ritual.GetActivitiesCall.getDefaultInstance();
}
public Ritual.GetActivitiesCall build() {
Ritual.GetActivitiesCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetActivitiesCall buildPartial() {
Ritual.GetActivitiesCall result = new Ritual.GetActivitiesCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.brief_ = brief_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.maxResults_ = maxResults_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.pageToken_ = pageToken_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetActivitiesCall other) {
if (other == Ritual.GetActivitiesCall.getDefaultInstance()) return this;
if (other.hasBrief()) {
setBrief(other.getBrief());
}
if (other.hasMaxResults()) {
setMaxResults(other.getMaxResults());
}
if (other.hasPageToken()) {
setPageToken(other.getPageToken());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasBrief()) {
return false;
}
if (!hasMaxResults()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetActivitiesCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetActivitiesCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private boolean brief_ ;
public boolean hasBrief() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getBrief() {
return brief_;
}
public Builder setBrief(boolean value) {
b0_ |= 0x00000001;
brief_ = value;
return this;
}
public Builder clearBrief() {
b0_ = (b0_ & ~0x00000001);
brief_ = false;
return this;
}
private int maxResults_ ;
public boolean hasMaxResults() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getMaxResults() {
return maxResults_;
}
public Builder setMaxResults(int value) {
b0_ |= 0x00000002;
maxResults_ = value;
return this;
}
public Builder clearMaxResults() {
b0_ = (b0_ & ~0x00000002);
maxResults_ = 0;
return this;
}
private long pageToken_ ;
public boolean hasPageToken() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getPageToken() {
return pageToken_;
}
public Builder setPageToken(long value) {
b0_ |= 0x00000004;
pageToken_ = value;
return this;
}
public Builder clearPageToken() {
b0_ = (b0_ & ~0x00000004);
pageToken_ = 0L;
return this;
}
}
static {
defaultInstance = new GetActivitiesCall(true);
defaultInstance.initFields();
}
}
public interface GetActivitiesReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.GetActivitiesReply.PBActivity> 
getActivityList();
Ritual.GetActivitiesReply.PBActivity getActivity(int index);
int getActivityCount();
boolean hasHasUnresolvedDevices();
boolean getHasUnresolvedDevices();
boolean hasPageToken();
long getPageToken();
}
public static final class GetActivitiesReply extends
GeneratedMessageLite implements
GetActivitiesReplyOrBuilder {
private GetActivitiesReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetActivitiesReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetActivitiesReply defaultInstance;
public static GetActivitiesReply getDefaultInstance() {
return defaultInstance;
}
public GetActivitiesReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetActivitiesReply(
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
activity_ = new ArrayList<Ritual.GetActivitiesReply.PBActivity>();
mutable_b0_ |= 0x00000001;
}
activity_.add(input.readMessage(Ritual.GetActivitiesReply.PBActivity.PARSER, er));
break;
}
case 16: {
b0_ |= 0x00000001;
hasUnresolvedDevices_ = input.readBool();
break;
}
case 24: {
b0_ |= 0x00000002;
pageToken_ = input.readUInt64();
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
activity_ = Collections.unmodifiableList(activity_);
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
public static Parser<GetActivitiesReply> PARSER =
new AbstractParser<GetActivitiesReply>() {
public GetActivitiesReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetActivitiesReply(input, er);
}
};
@Override
public Parser<GetActivitiesReply> getParserForType() {
return PARSER;
}
public enum ActivityType
implements Internal.EnumLite {
CREATION(0, 1),
MODIFICATION(1, 2),
MOVEMENT(2, 4),
DELETION(3, 8),
OUTBOUND(4, 16),
;
public static final ActivityType COMPLETED = CREATION;
public static final ActivityType CONTENT = MODIFICATION;
public static final int CREATION_VALUE = 1;
public static final int MODIFICATION_VALUE = 2;
public static final int MOVEMENT_VALUE = 4;
public static final int DELETION_VALUE = 8;
public static final int OUTBOUND_VALUE = 16;
public static final int COMPLETED_VALUE = 1;
public static final int CONTENT_VALUE = 2;
public final int getNumber() { return value; }
public static ActivityType valueOf(int value) {
switch (value) {
case 1: return CREATION;
case 2: return MODIFICATION;
case 4: return MOVEMENT;
case 8: return DELETION;
case 16: return OUTBOUND;
default: return null;
}
}
public static Internal.EnumLiteMap<ActivityType>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<ActivityType>
internalValueMap =
new Internal.EnumLiteMap<ActivityType>() {
public ActivityType findValueByNumber(int number) {
return ActivityType.valueOf(number);
}
};
private final int value;
private ActivityType(int index, int value) {
this.value = value;
}
}
public interface PBActivityOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
int getType();
boolean hasTime();
long getTime();
boolean hasMessage();
String getMessage();
ByteString
getMessageBytes();
boolean hasPath();
Common.PBPath getPath();
}
public static final class PBActivity extends
GeneratedMessageLite implements
PBActivityOrBuilder {
private PBActivity(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBActivity(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBActivity defaultInstance;
public static PBActivity getDefaultInstance() {
return defaultInstance;
}
public PBActivity getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBActivity(
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
type_ = input.readUInt32();
break;
}
case 16: {
b0_ |= 0x00000002;
time_ = input.readUInt64();
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000004;
message_ = bs;
break;
}
case 34: {
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
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
public static Parser<PBActivity> PARSER =
new AbstractParser<PBActivity>() {
public PBActivity parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBActivity(input, er);
}
};
@Override
public Parser<PBActivity> getParserForType() {
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
public static final int TIME_FIELD_NUMBER = 2;
private long time_;
public boolean hasTime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getTime() {
return time_;
}
public static final int MESSAGE_FIELD_NUMBER = 3;
private Object message_;
public boolean hasMessage() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getMessage() {
Object ref = message_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
message_ = s;
}
return s;
}
}
public ByteString
getMessageBytes() {
Object ref = message_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
message_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int PATH_FIELD_NUMBER = 4;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
type_ = 0;
time_ = 0L;
message_ = "";
path_ = Common.PBPath.getDefaultInstance();
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
if (!hasTime()) {
mii = 0;
return false;
}
if (!hasMessage()) {
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
output.writeUInt32(1, type_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, time_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, getMessageBytes());
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, path_);
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
.computeUInt32Size(1, type_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, time_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, getMessageBytes());
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeMessageSize(4, path_);
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
public static Ritual.GetActivitiesReply.PBActivity parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetActivitiesReply.PBActivity parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetActivitiesReply.PBActivity parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetActivitiesReply.PBActivity parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetActivitiesReply.PBActivity prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetActivitiesReply.PBActivity, Builder>
implements
Ritual.GetActivitiesReply.PBActivityOrBuilder {
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
time_ = 0L;
b0_ = (b0_ & ~0x00000002);
message_ = "";
b0_ = (b0_ & ~0x00000004);
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetActivitiesReply.PBActivity getDefaultInstanceForType() {
return Ritual.GetActivitiesReply.PBActivity.getDefaultInstance();
}
public Ritual.GetActivitiesReply.PBActivity build() {
Ritual.GetActivitiesReply.PBActivity result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetActivitiesReply.PBActivity buildPartial() {
Ritual.GetActivitiesReply.PBActivity result = new Ritual.GetActivitiesReply.PBActivity(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.time_ = time_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.message_ = message_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetActivitiesReply.PBActivity other) {
if (other == Ritual.GetActivitiesReply.PBActivity.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasTime()) {
setTime(other.getTime());
}
if (other.hasMessage()) {
b0_ |= 0x00000004;
message_ = other.message_;
}
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (!hasTime()) {
return false;
}
if (!hasMessage()) {
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
Ritual.GetActivitiesReply.PBActivity pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetActivitiesReply.PBActivity) e.getUnfinishedMessage();
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
private long time_ ;
public boolean hasTime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getTime() {
return time_;
}
public Builder setTime(long value) {
b0_ |= 0x00000002;
time_ = value;
return this;
}
public Builder clearTime() {
b0_ = (b0_ & ~0x00000002);
time_ = 0L;
return this;
}
private Object message_ = "";
public boolean hasMessage() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getMessage() {
Object ref = message_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
message_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getMessageBytes() {
Object ref = message_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
message_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setMessage(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
message_ = value;
return this;
}
public Builder clearMessage() {
b0_ = (b0_ & ~0x00000004);
message_ = getDefaultInstance().getMessage();
return this;
}
public Builder setMessageBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
message_ = value;
return this;
}
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
}
static {
defaultInstance = new PBActivity(true);
defaultInstance.initFields();
}
}
private int b0_;
public static final int ACTIVITY_FIELD_NUMBER = 1;
private List<Ritual.GetActivitiesReply.PBActivity> activity_;
public List<Ritual.GetActivitiesReply.PBActivity> getActivityList() {
return activity_;
}
public List<? extends Ritual.GetActivitiesReply.PBActivityOrBuilder> 
getActivityOrBuilderList() {
return activity_;
}
public int getActivityCount() {
return activity_.size();
}
public Ritual.GetActivitiesReply.PBActivity getActivity(int index) {
return activity_.get(index);
}
public Ritual.GetActivitiesReply.PBActivityOrBuilder getActivityOrBuilder(
int index) {
return activity_.get(index);
}
public static final int HAS_UNRESOLVED_DEVICES_FIELD_NUMBER = 2;
private boolean hasUnresolvedDevices_;
public boolean hasHasUnresolvedDevices() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getHasUnresolvedDevices() {
return hasUnresolvedDevices_;
}
public static final int PAGE_TOKEN_FIELD_NUMBER = 3;
private long pageToken_;
public boolean hasPageToken() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getPageToken() {
return pageToken_;
}
private void initFields() {
activity_ = Collections.emptyList();
hasUnresolvedDevices_ = false;
pageToken_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasHasUnresolvedDevices()) {
mii = 0;
return false;
}
for (int i = 0; i < getActivityCount(); i++) {
if (!getActivity(i).isInitialized()) {
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
for (int i = 0; i < activity_.size(); i++) {
output.writeMessage(1, activity_.get(i));
}
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBool(2, hasUnresolvedDevices_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(3, pageToken_);
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < activity_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, activity_.get(i));
}
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeBoolSize(2, hasUnresolvedDevices_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(3, pageToken_);
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
public static Ritual.GetActivitiesReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetActivitiesReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetActivitiesReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetActivitiesReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetActivitiesReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetActivitiesReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetActivitiesReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetActivitiesReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetActivitiesReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetActivitiesReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetActivitiesReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetActivitiesReply, Builder>
implements
Ritual.GetActivitiesReplyOrBuilder {
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
activity_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
hasUnresolvedDevices_ = false;
b0_ = (b0_ & ~0x00000002);
pageToken_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetActivitiesReply getDefaultInstanceForType() {
return Ritual.GetActivitiesReply.getDefaultInstance();
}
public Ritual.GetActivitiesReply build() {
Ritual.GetActivitiesReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetActivitiesReply buildPartial() {
Ritual.GetActivitiesReply result = new Ritual.GetActivitiesReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
activity_ = Collections.unmodifiableList(activity_);
b0_ = (b0_ & ~0x00000001);
}
result.activity_ = activity_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000001;
}
result.hasUnresolvedDevices_ = hasUnresolvedDevices_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000002;
}
result.pageToken_ = pageToken_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetActivitiesReply other) {
if (other == Ritual.GetActivitiesReply.getDefaultInstance()) return this;
if (!other.activity_.isEmpty()) {
if (activity_.isEmpty()) {
activity_ = other.activity_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureActivityIsMutable();
activity_.addAll(other.activity_);
}
}
if (other.hasHasUnresolvedDevices()) {
setHasUnresolvedDevices(other.getHasUnresolvedDevices());
}
if (other.hasPageToken()) {
setPageToken(other.getPageToken());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasHasUnresolvedDevices()) {
return false;
}
for (int i = 0; i < getActivityCount(); i++) {
if (!getActivity(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetActivitiesReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetActivitiesReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.GetActivitiesReply.PBActivity> activity_ =
Collections.emptyList();
private void ensureActivityIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
activity_ = new ArrayList<Ritual.GetActivitiesReply.PBActivity>(activity_);
b0_ |= 0x00000001;
}
}
public List<Ritual.GetActivitiesReply.PBActivity> getActivityList() {
return Collections.unmodifiableList(activity_);
}
public int getActivityCount() {
return activity_.size();
}
public Ritual.GetActivitiesReply.PBActivity getActivity(int index) {
return activity_.get(index);
}
public Builder setActivity(
int index, Ritual.GetActivitiesReply.PBActivity value) {
if (value == null) {
throw new NullPointerException();
}
ensureActivityIsMutable();
activity_.set(index, value);
return this;
}
public Builder setActivity(
int index, Ritual.GetActivitiesReply.PBActivity.Builder bdForValue) {
ensureActivityIsMutable();
activity_.set(index, bdForValue.build());
return this;
}
public Builder addActivity(Ritual.GetActivitiesReply.PBActivity value) {
if (value == null) {
throw new NullPointerException();
}
ensureActivityIsMutable();
activity_.add(value);
return this;
}
public Builder addActivity(
int index, Ritual.GetActivitiesReply.PBActivity value) {
if (value == null) {
throw new NullPointerException();
}
ensureActivityIsMutable();
activity_.add(index, value);
return this;
}
public Builder addActivity(
Ritual.GetActivitiesReply.PBActivity.Builder bdForValue) {
ensureActivityIsMutable();
activity_.add(bdForValue.build());
return this;
}
public Builder addActivity(
int index, Ritual.GetActivitiesReply.PBActivity.Builder bdForValue) {
ensureActivityIsMutable();
activity_.add(index, bdForValue.build());
return this;
}
public Builder addAllActivity(
Iterable<? extends Ritual.GetActivitiesReply.PBActivity> values) {
ensureActivityIsMutable();
AbstractMessageLite.Builder.addAll(
values, activity_);
return this;
}
public Builder clearActivity() {
activity_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeActivity(int index) {
ensureActivityIsMutable();
activity_.remove(index);
return this;
}
private boolean hasUnresolvedDevices_ ;
public boolean hasHasUnresolvedDevices() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getHasUnresolvedDevices() {
return hasUnresolvedDevices_;
}
public Builder setHasUnresolvedDevices(boolean value) {
b0_ |= 0x00000002;
hasUnresolvedDevices_ = value;
return this;
}
public Builder clearHasUnresolvedDevices() {
b0_ = (b0_ & ~0x00000002);
hasUnresolvedDevices_ = false;
return this;
}
private long pageToken_ ;
public boolean hasPageToken() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getPageToken() {
return pageToken_;
}
public Builder setPageToken(long value) {
b0_ |= 0x00000004;
pageToken_ = value;
return this;
}
public Builder clearPageToken() {
b0_ = (b0_ & ~0x00000004);
pageToken_ = 0L;
return this;
}
}
static {
defaultInstance = new GetActivitiesReply(true);
defaultInstance.initFields();
}
}
public interface ListRevChildrenCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class ListRevChildrenCall extends
GeneratedMessageLite implements
ListRevChildrenCallOrBuilder {
private ListRevChildrenCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListRevChildrenCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListRevChildrenCall defaultInstance;
public static ListRevChildrenCall getDefaultInstance() {
return defaultInstance;
}
public ListRevChildrenCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListRevChildrenCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<ListRevChildrenCall> PARSER =
new AbstractParser<ListRevChildrenCall>() {
public ListRevChildrenCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListRevChildrenCall(input, er);
}
};
@Override
public Parser<ListRevChildrenCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.ListRevChildrenCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevChildrenCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevChildrenCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevChildrenCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevChildrenCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevChildrenCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListRevChildrenCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListRevChildrenCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListRevChildrenCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevChildrenCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListRevChildrenCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListRevChildrenCall, Builder>
implements
Ritual.ListRevChildrenCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListRevChildrenCall getDefaultInstanceForType() {
return Ritual.ListRevChildrenCall.getDefaultInstance();
}
public Ritual.ListRevChildrenCall build() {
Ritual.ListRevChildrenCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListRevChildrenCall buildPartial() {
Ritual.ListRevChildrenCall result = new Ritual.ListRevChildrenCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ListRevChildrenCall other) {
if (other == Ritual.ListRevChildrenCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListRevChildrenCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListRevChildrenCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new ListRevChildrenCall(true);
defaultInstance.initFields();
}
}
public interface ListRevChildrenReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.PBRevChild> 
getChildList();
Ritual.PBRevChild getChild(int index);
int getChildCount();
}
public static final class ListRevChildrenReply extends
GeneratedMessageLite implements
ListRevChildrenReplyOrBuilder {
private ListRevChildrenReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListRevChildrenReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListRevChildrenReply defaultInstance;
public static ListRevChildrenReply getDefaultInstance() {
return defaultInstance;
}
public ListRevChildrenReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListRevChildrenReply(
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
child_ = new ArrayList<Ritual.PBRevChild>();
mutable_b0_ |= 0x00000001;
}
child_.add(input.readMessage(Ritual.PBRevChild.PARSER, er));
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
child_ = Collections.unmodifiableList(child_);
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
public static Parser<ListRevChildrenReply> PARSER =
new AbstractParser<ListRevChildrenReply>() {
public ListRevChildrenReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListRevChildrenReply(input, er);
}
};
@Override
public Parser<ListRevChildrenReply> getParserForType() {
return PARSER;
}
public static final int CHILD_FIELD_NUMBER = 1;
private List<Ritual.PBRevChild> child_;
public List<Ritual.PBRevChild> getChildList() {
return child_;
}
public List<? extends Ritual.PBRevChildOrBuilder> 
getChildOrBuilderList() {
return child_;
}
public int getChildCount() {
return child_.size();
}
public Ritual.PBRevChild getChild(int index) {
return child_.get(index);
}
public Ritual.PBRevChildOrBuilder getChildOrBuilder(
int index) {
return child_.get(index);
}
private void initFields() {
child_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getChildCount(); i++) {
if (!getChild(i).isInitialized()) {
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
for (int i = 0; i < child_.size(); i++) {
output.writeMessage(1, child_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < child_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, child_.get(i));
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
public static Ritual.ListRevChildrenReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevChildrenReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevChildrenReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevChildrenReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevChildrenReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevChildrenReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListRevChildrenReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListRevChildrenReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListRevChildrenReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevChildrenReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListRevChildrenReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListRevChildrenReply, Builder>
implements
Ritual.ListRevChildrenReplyOrBuilder {
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
child_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListRevChildrenReply getDefaultInstanceForType() {
return Ritual.ListRevChildrenReply.getDefaultInstance();
}
public Ritual.ListRevChildrenReply build() {
Ritual.ListRevChildrenReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListRevChildrenReply buildPartial() {
Ritual.ListRevChildrenReply result = new Ritual.ListRevChildrenReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
child_ = Collections.unmodifiableList(child_);
b0_ = (b0_ & ~0x00000001);
}
result.child_ = child_;
return result;
}
public Builder mergeFrom(Ritual.ListRevChildrenReply other) {
if (other == Ritual.ListRevChildrenReply.getDefaultInstance()) return this;
if (!other.child_.isEmpty()) {
if (child_.isEmpty()) {
child_ = other.child_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureChildIsMutable();
child_.addAll(other.child_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getChildCount(); i++) {
if (!getChild(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListRevChildrenReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListRevChildrenReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.PBRevChild> child_ =
Collections.emptyList();
private void ensureChildIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
child_ = new ArrayList<Ritual.PBRevChild>(child_);
b0_ |= 0x00000001;
}
}
public List<Ritual.PBRevChild> getChildList() {
return Collections.unmodifiableList(child_);
}
public int getChildCount() {
return child_.size();
}
public Ritual.PBRevChild getChild(int index) {
return child_.get(index);
}
public Builder setChild(
int index, Ritual.PBRevChild value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildIsMutable();
child_.set(index, value);
return this;
}
public Builder setChild(
int index, Ritual.PBRevChild.Builder bdForValue) {
ensureChildIsMutable();
child_.set(index, bdForValue.build());
return this;
}
public Builder addChild(Ritual.PBRevChild value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildIsMutable();
child_.add(value);
return this;
}
public Builder addChild(
int index, Ritual.PBRevChild value) {
if (value == null) {
throw new NullPointerException();
}
ensureChildIsMutable();
child_.add(index, value);
return this;
}
public Builder addChild(
Ritual.PBRevChild.Builder bdForValue) {
ensureChildIsMutable();
child_.add(bdForValue.build());
return this;
}
public Builder addChild(
int index, Ritual.PBRevChild.Builder bdForValue) {
ensureChildIsMutable();
child_.add(index, bdForValue.build());
return this;
}
public Builder addAllChild(
Iterable<? extends Ritual.PBRevChild> values) {
ensureChildIsMutable();
AbstractMessageLite.Builder.addAll(
values, child_);
return this;
}
public Builder clearChild() {
child_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeChild(int index) {
ensureChildIsMutable();
child_.remove(index);
return this;
}
}
static {
defaultInstance = new ListRevChildrenReply(true);
defaultInstance.initFields();
}
}
public interface ListRevHistoryCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class ListRevHistoryCall extends
GeneratedMessageLite implements
ListRevHistoryCallOrBuilder {
private ListRevHistoryCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListRevHistoryCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListRevHistoryCall defaultInstance;
public static ListRevHistoryCall getDefaultInstance() {
return defaultInstance;
}
public ListRevHistoryCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListRevHistoryCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<ListRevHistoryCall> PARSER =
new AbstractParser<ListRevHistoryCall>() {
public ListRevHistoryCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListRevHistoryCall(input, er);
}
};
@Override
public Parser<ListRevHistoryCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.ListRevHistoryCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevHistoryCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevHistoryCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevHistoryCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevHistoryCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevHistoryCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListRevHistoryCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListRevHistoryCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListRevHistoryCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevHistoryCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListRevHistoryCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListRevHistoryCall, Builder>
implements
Ritual.ListRevHistoryCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListRevHistoryCall getDefaultInstanceForType() {
return Ritual.ListRevHistoryCall.getDefaultInstance();
}
public Ritual.ListRevHistoryCall build() {
Ritual.ListRevHistoryCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListRevHistoryCall buildPartial() {
Ritual.ListRevHistoryCall result = new Ritual.ListRevHistoryCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ListRevHistoryCall other) {
if (other == Ritual.ListRevHistoryCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListRevHistoryCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListRevHistoryCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new ListRevHistoryCall(true);
defaultInstance.initFields();
}
}
public interface ListRevHistoryReplyOrBuilder extends
MessageLiteOrBuilder {
List<Ritual.PBRevision> 
getRevisionList();
Ritual.PBRevision getRevision(int index);
int getRevisionCount();
}
public static final class ListRevHistoryReply extends
GeneratedMessageLite implements
ListRevHistoryReplyOrBuilder {
private ListRevHistoryReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ListRevHistoryReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ListRevHistoryReply defaultInstance;
public static ListRevHistoryReply getDefaultInstance() {
return defaultInstance;
}
public ListRevHistoryReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ListRevHistoryReply(
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
revision_ = new ArrayList<Ritual.PBRevision>();
mutable_b0_ |= 0x00000001;
}
revision_.add(input.readMessage(Ritual.PBRevision.PARSER, er));
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
revision_ = Collections.unmodifiableList(revision_);
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
public static Parser<ListRevHistoryReply> PARSER =
new AbstractParser<ListRevHistoryReply>() {
public ListRevHistoryReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ListRevHistoryReply(input, er);
}
};
@Override
public Parser<ListRevHistoryReply> getParserForType() {
return PARSER;
}
public static final int REVISION_FIELD_NUMBER = 1;
private List<Ritual.PBRevision> revision_;
public List<Ritual.PBRevision> getRevisionList() {
return revision_;
}
public List<? extends Ritual.PBRevisionOrBuilder> 
getRevisionOrBuilderList() {
return revision_;
}
public int getRevisionCount() {
return revision_.size();
}
public Ritual.PBRevision getRevision(int index) {
return revision_.get(index);
}
public Ritual.PBRevisionOrBuilder getRevisionOrBuilder(
int index) {
return revision_.get(index);
}
private void initFields() {
revision_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getRevisionCount(); i++) {
if (!getRevision(i).isInitialized()) {
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
for (int i = 0; i < revision_.size(); i++) {
output.writeMessage(1, revision_.get(i));
}
output.writeRawBytes(unknownFields);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < revision_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, revision_.get(i));
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
public static Ritual.ListRevHistoryReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevHistoryReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevHistoryReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ListRevHistoryReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ListRevHistoryReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevHistoryReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ListRevHistoryReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ListRevHistoryReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ListRevHistoryReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ListRevHistoryReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ListRevHistoryReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ListRevHistoryReply, Builder>
implements
Ritual.ListRevHistoryReplyOrBuilder {
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
revision_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ListRevHistoryReply getDefaultInstanceForType() {
return Ritual.ListRevHistoryReply.getDefaultInstance();
}
public Ritual.ListRevHistoryReply build() {
Ritual.ListRevHistoryReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ListRevHistoryReply buildPartial() {
Ritual.ListRevHistoryReply result = new Ritual.ListRevHistoryReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
revision_ = Collections.unmodifiableList(revision_);
b0_ = (b0_ & ~0x00000001);
}
result.revision_ = revision_;
return result;
}
public Builder mergeFrom(Ritual.ListRevHistoryReply other) {
if (other == Ritual.ListRevHistoryReply.getDefaultInstance()) return this;
if (!other.revision_.isEmpty()) {
if (revision_.isEmpty()) {
revision_ = other.revision_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureRevisionIsMutable();
revision_.addAll(other.revision_);
}
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getRevisionCount(); i++) {
if (!getRevision(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ListRevHistoryReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ListRevHistoryReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Ritual.PBRevision> revision_ =
Collections.emptyList();
private void ensureRevisionIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
revision_ = new ArrayList<Ritual.PBRevision>(revision_);
b0_ |= 0x00000001;
}
}
public List<Ritual.PBRevision> getRevisionList() {
return Collections.unmodifiableList(revision_);
}
public int getRevisionCount() {
return revision_.size();
}
public Ritual.PBRevision getRevision(int index) {
return revision_.get(index);
}
public Builder setRevision(
int index, Ritual.PBRevision value) {
if (value == null) {
throw new NullPointerException();
}
ensureRevisionIsMutable();
revision_.set(index, value);
return this;
}
public Builder setRevision(
int index, Ritual.PBRevision.Builder bdForValue) {
ensureRevisionIsMutable();
revision_.set(index, bdForValue.build());
return this;
}
public Builder addRevision(Ritual.PBRevision value) {
if (value == null) {
throw new NullPointerException();
}
ensureRevisionIsMutable();
revision_.add(value);
return this;
}
public Builder addRevision(
int index, Ritual.PBRevision value) {
if (value == null) {
throw new NullPointerException();
}
ensureRevisionIsMutable();
revision_.add(index, value);
return this;
}
public Builder addRevision(
Ritual.PBRevision.Builder bdForValue) {
ensureRevisionIsMutable();
revision_.add(bdForValue.build());
return this;
}
public Builder addRevision(
int index, Ritual.PBRevision.Builder bdForValue) {
ensureRevisionIsMutable();
revision_.add(index, bdForValue.build());
return this;
}
public Builder addAllRevision(
Iterable<? extends Ritual.PBRevision> values) {
ensureRevisionIsMutable();
AbstractMessageLite.Builder.addAll(
values, revision_);
return this;
}
public Builder clearRevision() {
revision_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeRevision(int index) {
ensureRevisionIsMutable();
revision_.remove(index);
return this;
}
}
static {
defaultInstance = new ListRevHistoryReply(true);
defaultInstance.initFields();
}
}
public interface ExportRevisionCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasIndex();
ByteString getIndex();
}
public static final class ExportRevisionCall extends
GeneratedMessageLite implements
ExportRevisionCallOrBuilder {
private ExportRevisionCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExportRevisionCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExportRevisionCall defaultInstance;
public static ExportRevisionCall getDefaultInstance() {
return defaultInstance;
}
public ExportRevisionCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExportRevisionCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
b0_ |= 0x00000002;
index_ = input.readBytes();
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
public static Parser<ExportRevisionCall> PARSER =
new AbstractParser<ExportRevisionCall>() {
public ExportRevisionCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExportRevisionCall(input, er);
}
};
@Override
public Parser<ExportRevisionCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int INDEX_FIELD_NUMBER = 2;
private ByteString index_;
public boolean hasIndex() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getIndex() {
return index_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
index_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!hasIndex()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, index_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, index_);
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
public static Ritual.ExportRevisionCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportRevisionCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportRevisionCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportRevisionCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportRevisionCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportRevisionCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExportRevisionCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExportRevisionCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExportRevisionCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportRevisionCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExportRevisionCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExportRevisionCall, Builder>
implements
Ritual.ExportRevisionCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
index_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExportRevisionCall getDefaultInstanceForType() {
return Ritual.ExportRevisionCall.getDefaultInstance();
}
public Ritual.ExportRevisionCall build() {
Ritual.ExportRevisionCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExportRevisionCall buildPartial() {
Ritual.ExportRevisionCall result = new Ritual.ExportRevisionCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.index_ = index_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExportRevisionCall other) {
if (other == Ritual.ExportRevisionCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasIndex()) {
setIndex(other.getIndex());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasIndex()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExportRevisionCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExportRevisionCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private ByteString index_ = ByteString.EMPTY;
public boolean hasIndex() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getIndex() {
return index_;
}
public Builder setIndex(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
index_ = value;
return this;
}
public Builder clearIndex() {
b0_ = (b0_ & ~0x00000002);
index_ = getDefaultInstance().getIndex();
return this;
}
}
static {
defaultInstance = new ExportRevisionCall(true);
defaultInstance.initFields();
}
}
public interface ExportRevisionReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasDest();
String getDest();
ByteString
getDestBytes();
}
public static final class ExportRevisionReply extends
GeneratedMessageLite implements
ExportRevisionReplyOrBuilder {
private ExportRevisionReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ExportRevisionReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ExportRevisionReply defaultInstance;
public static ExportRevisionReply getDefaultInstance() {
return defaultInstance;
}
public ExportRevisionReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ExportRevisionReply(
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
dest_ = bs;
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
public static Parser<ExportRevisionReply> PARSER =
new AbstractParser<ExportRevisionReply>() {
public ExportRevisionReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ExportRevisionReply(input, er);
}
};
@Override
public Parser<ExportRevisionReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DEST_FIELD_NUMBER = 1;
private Object dest_;
public boolean hasDest() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDest() {
Object ref = dest_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
dest_ = s;
}
return s;
}
}
public ByteString
getDestBytes() {
Object ref = dest_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
dest_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
dest_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasDest()) {
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
output.writeBytes(1, getDestBytes());
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
.computeBytesSize(1, getDestBytes());
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
public static Ritual.ExportRevisionReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportRevisionReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportRevisionReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.ExportRevisionReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.ExportRevisionReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportRevisionReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.ExportRevisionReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.ExportRevisionReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.ExportRevisionReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.ExportRevisionReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.ExportRevisionReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.ExportRevisionReply, Builder>
implements
Ritual.ExportRevisionReplyOrBuilder {
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
dest_ = "";
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.ExportRevisionReply getDefaultInstanceForType() {
return Ritual.ExportRevisionReply.getDefaultInstance();
}
public Ritual.ExportRevisionReply build() {
Ritual.ExportRevisionReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.ExportRevisionReply buildPartial() {
Ritual.ExportRevisionReply result = new Ritual.ExportRevisionReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.dest_ = dest_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.ExportRevisionReply other) {
if (other == Ritual.ExportRevisionReply.getDefaultInstance()) return this;
if (other.hasDest()) {
b0_ |= 0x00000001;
dest_ = other.dest_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasDest()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.ExportRevisionReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.ExportRevisionReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object dest_ = "";
public boolean hasDest() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getDest() {
Object ref = dest_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
dest_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDestBytes() {
Object ref = dest_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
dest_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDest(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
dest_ = value;
return this;
}
public Builder clearDest() {
b0_ = (b0_ & ~0x00000001);
dest_ = getDefaultInstance().getDest();
return this;
}
public Builder setDestBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
dest_ = value;
return this;
}
}
static {
defaultInstance = new ExportRevisionReply(true);
defaultInstance.initFields();
}
}
public interface DeleteRevisionCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
boolean hasIndex();
ByteString getIndex();
}
public static final class DeleteRevisionCall extends
GeneratedMessageLite implements
DeleteRevisionCallOrBuilder {
private DeleteRevisionCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DeleteRevisionCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final DeleteRevisionCall defaultInstance;
public static DeleteRevisionCall getDefaultInstance() {
return defaultInstance;
}
public DeleteRevisionCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private DeleteRevisionCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
b0_ |= 0x00000002;
index_ = input.readBytes();
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
public static Parser<DeleteRevisionCall> PARSER =
new AbstractParser<DeleteRevisionCall>() {
public DeleteRevisionCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DeleteRevisionCall(input, er);
}
};
@Override
public Parser<DeleteRevisionCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public static final int INDEX_FIELD_NUMBER = 2;
private ByteString index_;
public boolean hasIndex() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getIndex() {
return index_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
index_ = ByteString.EMPTY;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, index_);
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
.computeMessageSize(1, path_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, index_);
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
public static Ritual.DeleteRevisionCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteRevisionCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteRevisionCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.DeleteRevisionCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.DeleteRevisionCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteRevisionCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.DeleteRevisionCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.DeleteRevisionCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.DeleteRevisionCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.DeleteRevisionCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.DeleteRevisionCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.DeleteRevisionCall, Builder>
implements
Ritual.DeleteRevisionCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
index_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.DeleteRevisionCall getDefaultInstanceForType() {
return Ritual.DeleteRevisionCall.getDefaultInstance();
}
public Ritual.DeleteRevisionCall build() {
Ritual.DeleteRevisionCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.DeleteRevisionCall buildPartial() {
Ritual.DeleteRevisionCall result = new Ritual.DeleteRevisionCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.index_ = index_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.DeleteRevisionCall other) {
if (other == Ritual.DeleteRevisionCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
if (other.hasIndex()) {
setIndex(other.getIndex());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.DeleteRevisionCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.DeleteRevisionCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private ByteString index_ = ByteString.EMPTY;
public boolean hasIndex() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getIndex() {
return index_;
}
public Builder setIndex(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
index_ = value;
return this;
}
public Builder clearIndex() {
b0_ = (b0_ & ~0x00000002);
index_ = getDefaultInstance().getIndex();
return this;
}
}
static {
defaultInstance = new DeleteRevisionCall(true);
defaultInstance.initFields();
}
}
public interface GetPathStatusCallOrBuilder extends
MessageLiteOrBuilder {
List<Common.PBPath> 
getPathList();
Common.PBPath getPath(int index);
int getPathCount();
}
public static final class GetPathStatusCall extends
GeneratedMessageLite implements
GetPathStatusCallOrBuilder {
private GetPathStatusCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetPathStatusCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetPathStatusCall defaultInstance;
public static GetPathStatusCall getDefaultInstance() {
return defaultInstance;
}
public GetPathStatusCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetPathStatusCall(
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
try {
unknownFieldsCodedOutput.flush();
} catch (IOException e) {
} finally {
unknownFields = unknownFieldsOutput.toByteString();
}
makeExtensionsImmutable();
}
}
public static Parser<GetPathStatusCall> PARSER =
new AbstractParser<GetPathStatusCall>() {
public GetPathStatusCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetPathStatusCall(input, er);
}
};
@Override
public Parser<GetPathStatusCall> getParserForType() {
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
private void initFields() {
path_ = Collections.emptyList();
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
mii = 1;
return true;
}
public void writeTo(CodedOutputStream output)
throws IOException {
getSerializedSize();
for (int i = 0; i < path_.size(); i++) {
output.writeMessage(1, path_.get(i));
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
public static Ritual.GetPathStatusCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetPathStatusCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetPathStatusCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetPathStatusCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetPathStatusCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetPathStatusCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetPathStatusCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetPathStatusCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetPathStatusCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetPathStatusCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetPathStatusCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetPathStatusCall, Builder>
implements
Ritual.GetPathStatusCallOrBuilder {
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
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetPathStatusCall getDefaultInstanceForType() {
return Ritual.GetPathStatusCall.getDefaultInstance();
}
public Ritual.GetPathStatusCall build() {
Ritual.GetPathStatusCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetPathStatusCall buildPartial() {
Ritual.GetPathStatusCall result = new Ritual.GetPathStatusCall(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
path_ = Collections.unmodifiableList(path_);
b0_ = (b0_ & ~0x00000001);
}
result.path_ = path_;
return result;
}
public Builder mergeFrom(Ritual.GetPathStatusCall other) {
if (other == Ritual.GetPathStatusCall.getDefaultInstance()) return this;
if (!other.path_.isEmpty()) {
if (path_.isEmpty()) {
path_ = other.path_;
b0_ = (b0_ & ~0x00000001);
} else {
ensurePathIsMutable();
path_.addAll(other.path_);
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
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetPathStatusCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetPathStatusCall) e.getUnfinishedMessage();
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
}
static {
defaultInstance = new GetPathStatusCall(true);
defaultInstance.initFields();
}
}
public interface GetPathStatusReplyOrBuilder extends
MessageLiteOrBuilder {
List<PathStatus.PBPathStatus> 
getStatusList();
PathStatus.PBPathStatus getStatus(int index);
int getStatusCount();
}
public static final class GetPathStatusReply extends
GeneratedMessageLite implements
GetPathStatusReplyOrBuilder {
private GetPathStatusReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetPathStatusReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetPathStatusReply defaultInstance;
public static GetPathStatusReply getDefaultInstance() {
return defaultInstance;
}
public GetPathStatusReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetPathStatusReply(
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
case 18: {
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
status_ = new ArrayList<PathStatus.PBPathStatus>();
mutable_b0_ |= 0x00000001;
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
public static Parser<GetPathStatusReply> PARSER =
new AbstractParser<GetPathStatusReply>() {
public GetPathStatusReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetPathStatusReply(input, er);
}
};
@Override
public Parser<GetPathStatusReply> getParserForType() {
return PARSER;
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
status_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
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
public static Ritual.GetPathStatusReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetPathStatusReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetPathStatusReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetPathStatusReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetPathStatusReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetPathStatusReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetPathStatusReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetPathStatusReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetPathStatusReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetPathStatusReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetPathStatusReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetPathStatusReply, Builder>
implements
Ritual.GetPathStatusReplyOrBuilder {
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
status_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetPathStatusReply getDefaultInstanceForType() {
return Ritual.GetPathStatusReply.getDefaultInstance();
}
public Ritual.GetPathStatusReply build() {
Ritual.GetPathStatusReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetPathStatusReply buildPartial() {
Ritual.GetPathStatusReply result = new Ritual.GetPathStatusReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
status_ = Collections.unmodifiableList(status_);
b0_ = (b0_ & ~0x00000001);
}
result.status_ = status_;
return result;
}
public Builder mergeFrom(Ritual.GetPathStatusReply other) {
if (other == Ritual.GetPathStatusReply.getDefaultInstance()) return this;
if (!other.status_.isEmpty()) {
if (status_.isEmpty()) {
status_ = other.status_;
b0_ = (b0_ & ~0x00000001);
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
Ritual.GetPathStatusReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetPathStatusReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<PathStatus.PBPathStatus> status_ =
Collections.emptyList();
private void ensureStatusIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
status_ = new ArrayList<PathStatus.PBPathStatus>(status_);
b0_ |= 0x00000001;
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
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder removeStatus(int index) {
ensureStatusIsMutable();
status_.remove(index);
return this;
}
}
static {
defaultInstance = new GetPathStatusReply(true);
defaultInstance.initFields();
}
}
public interface CreateSeedFileCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasStoreId();
ByteString getStoreId();
}
public static final class CreateSeedFileCall extends
GeneratedMessageLite implements
CreateSeedFileCallOrBuilder {
private CreateSeedFileCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateSeedFileCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateSeedFileCall defaultInstance;
public static CreateSeedFileCall getDefaultInstance() {
return defaultInstance;
}
public CreateSeedFileCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateSeedFileCall(
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
public static Parser<CreateSeedFileCall> PARSER =
new AbstractParser<CreateSeedFileCall>() {
public CreateSeedFileCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateSeedFileCall(input, er);
}
};
@Override
public Parser<CreateSeedFileCall> getParserForType() {
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
private void initFields() {
storeId_ = ByteString.EMPTY;
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
public static Ritual.CreateSeedFileCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateSeedFileCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateSeedFileCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateSeedFileCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateSeedFileCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateSeedFileCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateSeedFileCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateSeedFileCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateSeedFileCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateSeedFileCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateSeedFileCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateSeedFileCall, Builder>
implements
Ritual.CreateSeedFileCallOrBuilder {
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
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateSeedFileCall getDefaultInstanceForType() {
return Ritual.CreateSeedFileCall.getDefaultInstance();
}
public Ritual.CreateSeedFileCall build() {
Ritual.CreateSeedFileCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateSeedFileCall buildPartial() {
Ritual.CreateSeedFileCall result = new Ritual.CreateSeedFileCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeId_ = storeId_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateSeedFileCall other) {
if (other == Ritual.CreateSeedFileCall.getDefaultInstance()) return this;
if (other.hasStoreId()) {
setStoreId(other.getStoreId());
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
Ritual.CreateSeedFileCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateSeedFileCall) e.getUnfinishedMessage();
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
}
static {
defaultInstance = new CreateSeedFileCall(true);
defaultInstance.initFields();
}
}
public interface CreateSeedFileReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
}
public static final class CreateSeedFileReply extends
GeneratedMessageLite implements
CreateSeedFileReplyOrBuilder {
private CreateSeedFileReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateSeedFileReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateSeedFileReply defaultInstance;
public static CreateSeedFileReply getDefaultInstance() {
return defaultInstance;
}
public CreateSeedFileReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateSeedFileReply(
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
path_ = bs;
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
public static Parser<CreateSeedFileReply> PARSER =
new AbstractParser<CreateSeedFileReply>() {
public CreateSeedFileReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateSeedFileReply(input, er);
}
};
@Override
public Parser<CreateSeedFileReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Object path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getPath() {
Object ref = path_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
path_ = s;
}
return s;
}
}
public ByteString
getPathBytes() {
Object ref = path_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
path_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
path_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
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
output.writeBytes(1, getPathBytes());
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
.computeBytesSize(1, getPathBytes());
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
public static Ritual.CreateSeedFileReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateSeedFileReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateSeedFileReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.CreateSeedFileReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.CreateSeedFileReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateSeedFileReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.CreateSeedFileReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.CreateSeedFileReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.CreateSeedFileReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.CreateSeedFileReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.CreateSeedFileReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.CreateSeedFileReply, Builder>
implements
Ritual.CreateSeedFileReplyOrBuilder {
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
path_ = "";
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.CreateSeedFileReply getDefaultInstanceForType() {
return Ritual.CreateSeedFileReply.getDefaultInstance();
}
public Ritual.CreateSeedFileReply build() {
Ritual.CreateSeedFileReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.CreateSeedFileReply buildPartial() {
Ritual.CreateSeedFileReply result = new Ritual.CreateSeedFileReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.CreateSeedFileReply other) {
if (other == Ritual.CreateSeedFileReply.getDefaultInstance()) return this;
if (other.hasPath()) {
b0_ |= 0x00000001;
path_ = other.path_;
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.CreateSeedFileReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.CreateSeedFileReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object path_ = "";
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getPath() {
Object ref = path_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
path_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getPathBytes() {
Object ref = path_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
path_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setPath(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
path_ = value;
return this;
}
public Builder clearPath() {
b0_ = (b0_ & ~0x00000001);
path_ = getDefaultInstance().getPath();
return this;
}
public Builder setPathBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
path_ = value;
return this;
}
}
static {
defaultInstance = new CreateSeedFileReply(true);
defaultInstance.initFields();
}
}
public interface GetTransferStatsReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasUpTime();
long getUpTime();
boolean hasBytesIn();
long getBytesIn();
boolean hasBytesOut();
long getBytesOut();
}
public static final class GetTransferStatsReply extends
GeneratedMessageLite implements
GetTransferStatsReplyOrBuilder {
private GetTransferStatsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetTransferStatsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetTransferStatsReply defaultInstance;
public static GetTransferStatsReply getDefaultInstance() {
return defaultInstance;
}
public GetTransferStatsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetTransferStatsReply(
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
upTime_ = input.readUInt64();
break;
}
case 16: {
b0_ |= 0x00000002;
bytesIn_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
bytesOut_ = input.readUInt64();
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
public static Parser<GetTransferStatsReply> PARSER =
new AbstractParser<GetTransferStatsReply>() {
public GetTransferStatsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetTransferStatsReply(input, er);
}
};
@Override
public Parser<GetTransferStatsReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int UP_TIME_FIELD_NUMBER = 1;
private long upTime_;
public boolean hasUpTime() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getUpTime() {
return upTime_;
}
public static final int BYTES_IN_FIELD_NUMBER = 2;
private long bytesIn_;
public boolean hasBytesIn() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getBytesIn() {
return bytesIn_;
}
public static final int BYTES_OUT_FIELD_NUMBER = 3;
private long bytesOut_;
public boolean hasBytesOut() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getBytesOut() {
return bytesOut_;
}
private void initFields() {
upTime_ = 0L;
bytesIn_ = 0L;
bytesOut_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasUpTime()) {
mii = 0;
return false;
}
if (!hasBytesIn()) {
mii = 0;
return false;
}
if (!hasBytesOut()) {
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
output.writeUInt64(1, upTime_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, bytesIn_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, bytesOut_);
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
.computeUInt64Size(1, upTime_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, bytesIn_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, bytesOut_);
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
public static Ritual.GetTransferStatsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetTransferStatsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetTransferStatsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetTransferStatsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetTransferStatsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetTransferStatsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetTransferStatsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetTransferStatsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetTransferStatsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetTransferStatsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetTransferStatsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetTransferStatsReply, Builder>
implements
Ritual.GetTransferStatsReplyOrBuilder {
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
upTime_ = 0L;
b0_ = (b0_ & ~0x00000001);
bytesIn_ = 0L;
b0_ = (b0_ & ~0x00000002);
bytesOut_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetTransferStatsReply getDefaultInstanceForType() {
return Ritual.GetTransferStatsReply.getDefaultInstance();
}
public Ritual.GetTransferStatsReply build() {
Ritual.GetTransferStatsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetTransferStatsReply buildPartial() {
Ritual.GetTransferStatsReply result = new Ritual.GetTransferStatsReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.upTime_ = upTime_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.bytesIn_ = bytesIn_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.bytesOut_ = bytesOut_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetTransferStatsReply other) {
if (other == Ritual.GetTransferStatsReply.getDefaultInstance()) return this;
if (other.hasUpTime()) {
setUpTime(other.getUpTime());
}
if (other.hasBytesIn()) {
setBytesIn(other.getBytesIn());
}
if (other.hasBytesOut()) {
setBytesOut(other.getBytesOut());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasUpTime()) {
return false;
}
if (!hasBytesIn()) {
return false;
}
if (!hasBytesOut()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetTransferStatsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetTransferStatsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private long upTime_ ;
public boolean hasUpTime() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getUpTime() {
return upTime_;
}
public Builder setUpTime(long value) {
b0_ |= 0x00000001;
upTime_ = value;
return this;
}
public Builder clearUpTime() {
b0_ = (b0_ & ~0x00000001);
upTime_ = 0L;
return this;
}
private long bytesIn_ ;
public boolean hasBytesIn() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getBytesIn() {
return bytesIn_;
}
public Builder setBytesIn(long value) {
b0_ |= 0x00000002;
bytesIn_ = value;
return this;
}
public Builder clearBytesIn() {
b0_ = (b0_ & ~0x00000002);
bytesIn_ = 0L;
return this;
}
private long bytesOut_ ;
public boolean hasBytesOut() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getBytesOut() {
return bytesOut_;
}
public Builder setBytesOut(long value) {
b0_ |= 0x00000004;
bytesOut_ = value;
return this;
}
public Builder clearBytesOut() {
b0_ = (b0_ & ~0x00000004);
bytesOut_ = 0L;
return this;
}
}
static {
defaultInstance = new GetTransferStatsReply(true);
defaultInstance.initFields();
}
}
public interface PBRevChildOrBuilder extends
MessageLiteOrBuilder {
boolean hasName();
String getName();
ByteString
getNameBytes();
boolean hasIsDir();
boolean getIsDir();
}
public static final class PBRevChild extends
GeneratedMessageLite implements
PBRevChildOrBuilder {
private PBRevChild(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBRevChild(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBRevChild defaultInstance;
public static PBRevChild getDefaultInstance() {
return defaultInstance;
}
public PBRevChild getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBRevChild(
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
name_ = bs;
break;
}
case 16: {
b0_ |= 0x00000002;
isDir_ = input.readBool();
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
public static Parser<PBRevChild> PARSER =
new AbstractParser<PBRevChild>() {
public PBRevChild parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBRevChild(input, er);
}
};
@Override
public Parser<PBRevChild> getParserForType() {
return PARSER;
}
private int b0_;
public static final int NAME_FIELD_NUMBER = 1;
private Object name_;
public boolean hasName() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getName() {
Object ref = name_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int IS_DIR_FIELD_NUMBER = 2;
private boolean isDir_;
public boolean hasIsDir() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getIsDir() {
return isDir_;
}
private void initFields() {
name_ = "";
isDir_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasName()) {
mii = 0;
return false;
}
if (!hasIsDir()) {
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
output.writeBytes(1, getNameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBool(2, isDir_);
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
.computeBytesSize(1, getNameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBoolSize(2, isDir_);
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
public static Ritual.PBRevChild parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBRevChild parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBRevChild parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBRevChild parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBRevChild parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBRevChild parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.PBRevChild parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.PBRevChild parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.PBRevChild parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBRevChild parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.PBRevChild prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.PBRevChild, Builder>
implements
Ritual.PBRevChildOrBuilder {
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
name_ = "";
b0_ = (b0_ & ~0x00000001);
isDir_ = false;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.PBRevChild getDefaultInstanceForType() {
return Ritual.PBRevChild.getDefaultInstance();
}
public Ritual.PBRevChild build() {
Ritual.PBRevChild result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.PBRevChild buildPartial() {
Ritual.PBRevChild result = new Ritual.PBRevChild(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.name_ = name_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.isDir_ = isDir_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.PBRevChild other) {
if (other == Ritual.PBRevChild.getDefaultInstance()) return this;
if (other.hasName()) {
b0_ |= 0x00000001;
name_ = other.name_;
}
if (other.hasIsDir()) {
setIsDir(other.getIsDir());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasName()) {
return false;
}
if (!hasIsDir()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.PBRevChild pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.PBRevChild) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object name_ = "";
public boolean hasName() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getName() {
Object ref = name_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
name_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getNameBytes() {
Object ref = name_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
name_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setName(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
name_ = value;
return this;
}
public Builder clearName() {
b0_ = (b0_ & ~0x00000001);
name_ = getDefaultInstance().getName();
return this;
}
public Builder setNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
name_ = value;
return this;
}
private boolean isDir_ ;
public boolean hasIsDir() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getIsDir() {
return isDir_;
}
public Builder setIsDir(boolean value) {
b0_ |= 0x00000002;
isDir_ = value;
return this;
}
public Builder clearIsDir() {
b0_ = (b0_ & ~0x00000002);
isDir_ = false;
return this;
}
}
static {
defaultInstance = new PBRevChild(true);
defaultInstance.initFields();
}
}
public interface PBRevisionOrBuilder extends
MessageLiteOrBuilder {
boolean hasIndex();
ByteString getIndex();
boolean hasMtime();
long getMtime();
boolean hasLength();
long getLength();
}
public static final class PBRevision extends
GeneratedMessageLite implements
PBRevisionOrBuilder {
private PBRevision(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBRevision(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PBRevision defaultInstance;
public static PBRevision getDefaultInstance() {
return defaultInstance;
}
public PBRevision getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PBRevision(
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
index_ = input.readBytes();
break;
}
case 16: {
b0_ |= 0x00000002;
mtime_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
length_ = input.readUInt64();
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
public static Parser<PBRevision> PARSER =
new AbstractParser<PBRevision>() {
public PBRevision parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBRevision(input, er);
}
};
@Override
public Parser<PBRevision> getParserForType() {
return PARSER;
}
private int b0_;
public static final int INDEX_FIELD_NUMBER = 1;
private ByteString index_;
public boolean hasIndex() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getIndex() {
return index_;
}
public static final int MTIME_FIELD_NUMBER = 2;
private long mtime_;
public boolean hasMtime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getMtime() {
return mtime_;
}
public static final int LENGTH_FIELD_NUMBER = 3;
private long length_;
public boolean hasLength() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getLength() {
return length_;
}
private void initFields() {
index_ = ByteString.EMPTY;
mtime_ = 0L;
length_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasIndex()) {
mii = 0;
return false;
}
if (!hasMtime()) {
mii = 0;
return false;
}
if (!hasLength()) {
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
output.writeBytes(1, index_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, mtime_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, length_);
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
.computeBytesSize(1, index_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, mtime_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, length_);
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
public static Ritual.PBRevision parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBRevision parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBRevision parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.PBRevision parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.PBRevision parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBRevision parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.PBRevision parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.PBRevision parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.PBRevision parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.PBRevision parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.PBRevision prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.PBRevision, Builder>
implements
Ritual.PBRevisionOrBuilder {
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
index_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
mtime_ = 0L;
b0_ = (b0_ & ~0x00000002);
length_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.PBRevision getDefaultInstanceForType() {
return Ritual.PBRevision.getDefaultInstance();
}
public Ritual.PBRevision build() {
Ritual.PBRevision result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.PBRevision buildPartial() {
Ritual.PBRevision result = new Ritual.PBRevision(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.index_ = index_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.mtime_ = mtime_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.length_ = length_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.PBRevision other) {
if (other == Ritual.PBRevision.getDefaultInstance()) return this;
if (other.hasIndex()) {
setIndex(other.getIndex());
}
if (other.hasMtime()) {
setMtime(other.getMtime());
}
if (other.hasLength()) {
setLength(other.getLength());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasIndex()) {
return false;
}
if (!hasMtime()) {
return false;
}
if (!hasLength()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.PBRevision pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.PBRevision) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString index_ = ByteString.EMPTY;
public boolean hasIndex() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getIndex() {
return index_;
}
public Builder setIndex(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
index_ = value;
return this;
}
public Builder clearIndex() {
b0_ = (b0_ & ~0x00000001);
index_ = getDefaultInstance().getIndex();
return this;
}
private long mtime_ ;
public boolean hasMtime() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getMtime() {
return mtime_;
}
public Builder setMtime(long value) {
b0_ |= 0x00000002;
mtime_ = value;
return this;
}
public Builder clearMtime() {
b0_ = (b0_ & ~0x00000002);
mtime_ = 0L;
return this;
}
private long length_ ;
public boolean hasLength() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getLength() {
return length_;
}
public Builder setLength(long value) {
b0_ |= 0x00000004;
length_ = value;
return this;
}
public Builder clearLength() {
b0_ = (b0_ & ~0x00000004);
length_ = 0L;
return this;
}
}
static {
defaultInstance = new PBRevision(true);
defaultInstance.initFields();
}
}
public interface TestGetObjectIdentifierCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class TestGetObjectIdentifierCall extends
GeneratedMessageLite implements
TestGetObjectIdentifierCallOrBuilder {
private TestGetObjectIdentifierCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TestGetObjectIdentifierCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final TestGetObjectIdentifierCall defaultInstance;
public static TestGetObjectIdentifierCall getDefaultInstance() {
return defaultInstance;
}
public TestGetObjectIdentifierCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private TestGetObjectIdentifierCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<TestGetObjectIdentifierCall> PARSER =
new AbstractParser<TestGetObjectIdentifierCall>() {
public TestGetObjectIdentifierCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TestGetObjectIdentifierCall(input, er);
}
};
@Override
public Parser<TestGetObjectIdentifierCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.TestGetObjectIdentifierCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.TestGetObjectIdentifierCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.TestGetObjectIdentifierCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetObjectIdentifierCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.TestGetObjectIdentifierCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.TestGetObjectIdentifierCall, Builder>
implements
Ritual.TestGetObjectIdentifierCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.TestGetObjectIdentifierCall getDefaultInstanceForType() {
return Ritual.TestGetObjectIdentifierCall.getDefaultInstance();
}
public Ritual.TestGetObjectIdentifierCall build() {
Ritual.TestGetObjectIdentifierCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.TestGetObjectIdentifierCall buildPartial() {
Ritual.TestGetObjectIdentifierCall result = new Ritual.TestGetObjectIdentifierCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.TestGetObjectIdentifierCall other) {
if (other == Ritual.TestGetObjectIdentifierCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.TestGetObjectIdentifierCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.TestGetObjectIdentifierCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new TestGetObjectIdentifierCall(true);
defaultInstance.initFields();
}
}
public interface TestGetObjectIdentifierReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasSidx();
int getSidx();
boolean hasOid();
ByteString getOid();
}
public static final class TestGetObjectIdentifierReply extends
GeneratedMessageLite implements
TestGetObjectIdentifierReplyOrBuilder {
private TestGetObjectIdentifierReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TestGetObjectIdentifierReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final TestGetObjectIdentifierReply defaultInstance;
public static TestGetObjectIdentifierReply getDefaultInstance() {
return defaultInstance;
}
public TestGetObjectIdentifierReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private TestGetObjectIdentifierReply(
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
public static Parser<TestGetObjectIdentifierReply> PARSER =
new AbstractParser<TestGetObjectIdentifierReply>() {
public TestGetObjectIdentifierReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TestGetObjectIdentifierReply(input, er);
}
};
@Override
public Parser<TestGetObjectIdentifierReply> getParserForType() {
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
private void initFields() {
sidx_ = 0;
oid_ = ByteString.EMPTY;
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
public static Ritual.TestGetObjectIdentifierReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.TestGetObjectIdentifierReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.TestGetObjectIdentifierReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetObjectIdentifierReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.TestGetObjectIdentifierReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.TestGetObjectIdentifierReply, Builder>
implements
Ritual.TestGetObjectIdentifierReplyOrBuilder {
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
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.TestGetObjectIdentifierReply getDefaultInstanceForType() {
return Ritual.TestGetObjectIdentifierReply.getDefaultInstance();
}
public Ritual.TestGetObjectIdentifierReply build() {
Ritual.TestGetObjectIdentifierReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.TestGetObjectIdentifierReply buildPartial() {
Ritual.TestGetObjectIdentifierReply result = new Ritual.TestGetObjectIdentifierReply(this);
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
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.TestGetObjectIdentifierReply other) {
if (other == Ritual.TestGetObjectIdentifierReply.getDefaultInstance()) return this;
if (other.hasSidx()) {
setSidx(other.getSidx());
}
if (other.hasOid()) {
setOid(other.getOid());
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
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.TestGetObjectIdentifierReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.TestGetObjectIdentifierReply) e.getUnfinishedMessage();
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
}
static {
defaultInstance = new TestGetObjectIdentifierReply(true);
defaultInstance.initFields();
}
}
public interface TestGetAliasObjectCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
Common.PBPath getPath();
}
public static final class TestGetAliasObjectCall extends
GeneratedMessageLite implements
TestGetAliasObjectCallOrBuilder {
private TestGetAliasObjectCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TestGetAliasObjectCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final TestGetAliasObjectCall defaultInstance;
public static TestGetAliasObjectCall getDefaultInstance() {
return defaultInstance;
}
public TestGetAliasObjectCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private TestGetAliasObjectCall(
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
Common.PBPath.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = path_.toBuilder();
}
path_ = input.readMessage(Common.PBPath.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(path_);
path_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
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
public static Parser<TestGetAliasObjectCall> PARSER =
new AbstractParser<TestGetAliasObjectCall>() {
public TestGetAliasObjectCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TestGetAliasObjectCall(input, er);
}
};
@Override
public Parser<TestGetAliasObjectCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PATH_FIELD_NUMBER = 1;
private Common.PBPath path_;
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
private void initFields() {
path_ = Common.PBPath.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasPath()) {
mii = 0;
return false;
}
if (!getPath().isInitialized()) {
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
output.writeMessage(1, path_);
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
.computeMessageSize(1, path_);
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
public static Ritual.TestGetAliasObjectCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetAliasObjectCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetAliasObjectCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetAliasObjectCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetAliasObjectCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetAliasObjectCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.TestGetAliasObjectCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.TestGetAliasObjectCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.TestGetAliasObjectCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetAliasObjectCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.TestGetAliasObjectCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.TestGetAliasObjectCall, Builder>
implements
Ritual.TestGetAliasObjectCallOrBuilder {
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
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.TestGetAliasObjectCall getDefaultInstanceForType() {
return Ritual.TestGetAliasObjectCall.getDefaultInstance();
}
public Ritual.TestGetAliasObjectCall build() {
Ritual.TestGetAliasObjectCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.TestGetAliasObjectCall buildPartial() {
Ritual.TestGetAliasObjectCall result = new Ritual.TestGetAliasObjectCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.TestGetAliasObjectCall other) {
if (other == Ritual.TestGetAliasObjectCall.getDefaultInstance()) return this;
if (other.hasPath()) {
mergePath(other.getPath());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!getPath().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.TestGetAliasObjectCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.TestGetAliasObjectCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Common.PBPath path_ = Common.PBPath.getDefaultInstance();
public boolean hasPath() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Common.PBPath getPath() {
return path_;
}
public Builder setPath(Common.PBPath value) {
if (value == null) {
throw new NullPointerException();
}
path_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setPath(
Common.PBPath.Builder bdForValue) {
path_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergePath(Common.PBPath value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
path_ != Common.PBPath.getDefaultInstance()) {
path_ =
Common.PBPath.newBuilder(path_).mergeFrom(value).buildPartial();
} else {
path_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearPath() {
path_ = Common.PBPath.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new TestGetAliasObjectCall(true);
defaultInstance.initFields();
}
}
public interface TestGetAliasObjectReplyOrBuilder extends
MessageLiteOrBuilder {
List<ByteString> getOidList();
int getOidCount();
ByteString getOid(int index);
}
public static final class TestGetAliasObjectReply extends
GeneratedMessageLite implements
TestGetAliasObjectReplyOrBuilder {
private TestGetAliasObjectReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TestGetAliasObjectReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final TestGetAliasObjectReply defaultInstance;
public static TestGetAliasObjectReply getDefaultInstance() {
return defaultInstance;
}
public TestGetAliasObjectReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private TestGetAliasObjectReply(
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
oid_ = new ArrayList<ByteString>();
mutable_b0_ |= 0x00000001;
}
oid_.add(input.readBytes());
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
oid_ = Collections.unmodifiableList(oid_);
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
public static Parser<TestGetAliasObjectReply> PARSER =
new AbstractParser<TestGetAliasObjectReply>() {
public TestGetAliasObjectReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TestGetAliasObjectReply(input, er);
}
};
@Override
public Parser<TestGetAliasObjectReply> getParserForType() {
return PARSER;
}
public static final int OID_FIELD_NUMBER = 1;
private List<ByteString> oid_;
public List<ByteString>
getOidList() {
return oid_;
}
public int getOidCount() {
return oid_.size();
}
public ByteString getOid(int index) {
return oid_.get(index);
}
private void initFields() {
oid_ = Collections.emptyList();
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
for (int i = 0; i < oid_.size(); i++) {
output.writeBytes(1, oid_.get(i));
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
for (int i = 0; i < oid_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(oid_.get(i));
}
size += dataSize;
size += 1 * getOidList().size();
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
public static Ritual.TestGetAliasObjectReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetAliasObjectReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetAliasObjectReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.TestGetAliasObjectReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.TestGetAliasObjectReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetAliasObjectReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.TestGetAliasObjectReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.TestGetAliasObjectReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.TestGetAliasObjectReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.TestGetAliasObjectReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.TestGetAliasObjectReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.TestGetAliasObjectReply, Builder>
implements
Ritual.TestGetAliasObjectReplyOrBuilder {
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
oid_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.TestGetAliasObjectReply getDefaultInstanceForType() {
return Ritual.TestGetAliasObjectReply.getDefaultInstance();
}
public Ritual.TestGetAliasObjectReply build() {
Ritual.TestGetAliasObjectReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.TestGetAliasObjectReply buildPartial() {
Ritual.TestGetAliasObjectReply result = new Ritual.TestGetAliasObjectReply(this);
int from_b0_ = b0_;
if (((b0_ & 0x00000001) == 0x00000001)) {
oid_ = Collections.unmodifiableList(oid_);
b0_ = (b0_ & ~0x00000001);
}
result.oid_ = oid_;
return result;
}
public Builder mergeFrom(Ritual.TestGetAliasObjectReply other) {
if (other == Ritual.TestGetAliasObjectReply.getDefaultInstance()) return this;
if (!other.oid_.isEmpty()) {
if (oid_.isEmpty()) {
oid_ = other.oid_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureOidIsMutable();
oid_.addAll(other.oid_);
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
Ritual.TestGetAliasObjectReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.TestGetAliasObjectReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<ByteString> oid_ = Collections.emptyList();
private void ensureOidIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
oid_ = new ArrayList<ByteString>(oid_);
b0_ |= 0x00000001;
}
}
public List<ByteString>
getOidList() {
return Collections.unmodifiableList(oid_);
}
public int getOidCount() {
return oid_.size();
}
public ByteString getOid(int index) {
return oid_.get(index);
}
public Builder setOid(
int index, ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureOidIsMutable();
oid_.set(index, value);
return this;
}
public Builder addOid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureOidIsMutable();
oid_.add(value);
return this;
}
public Builder addAllOid(
Iterable<? extends ByteString> values) {
ensureOidIsMutable();
AbstractMessageLite.Builder.addAll(
values, oid_);
return this;
}
public Builder clearOid() {
oid_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
return this;
}
}
static {
defaultInstance = new TestGetAliasObjectReply(true);
defaultInstance.initFields();
}
}
public interface GetDiagnosticsReplyOrBuilder extends
MessageLiteOrBuilder {
boolean hasDeviceDiagnostics();
Diagnostics.DeviceDiagnostics getDeviceDiagnostics();
boolean hasTransportDiagnostics();
Diagnostics.TransportDiagnostics getTransportDiagnostics();
}
public static final class GetDiagnosticsReply extends
GeneratedMessageLite implements
GetDiagnosticsReplyOrBuilder {
private GetDiagnosticsReply(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GetDiagnosticsReply(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GetDiagnosticsReply defaultInstance;
public static GetDiagnosticsReply getDefaultInstance() {
return defaultInstance;
}
public GetDiagnosticsReply getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GetDiagnosticsReply(
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
Diagnostics.DeviceDiagnostics.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = deviceDiagnostics_.toBuilder();
}
deviceDiagnostics_ = input.readMessage(Diagnostics.DeviceDiagnostics.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(deviceDiagnostics_);
deviceDiagnostics_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
Diagnostics.TransportDiagnostics.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = transportDiagnostics_.toBuilder();
}
transportDiagnostics_ = input.readMessage(Diagnostics.TransportDiagnostics.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(transportDiagnostics_);
transportDiagnostics_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
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
public static Parser<GetDiagnosticsReply> PARSER =
new AbstractParser<GetDiagnosticsReply>() {
public GetDiagnosticsReply parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GetDiagnosticsReply(input, er);
}
};
@Override
public Parser<GetDiagnosticsReply> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DEVICE_DIAGNOSTICS_FIELD_NUMBER = 1;
private Diagnostics.DeviceDiagnostics deviceDiagnostics_;
public boolean hasDeviceDiagnostics() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.DeviceDiagnostics getDeviceDiagnostics() {
return deviceDiagnostics_;
}
public static final int TRANSPORT_DIAGNOSTICS_FIELD_NUMBER = 2;
private Diagnostics.TransportDiagnostics transportDiagnostics_;
public boolean hasTransportDiagnostics() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Diagnostics.TransportDiagnostics getTransportDiagnostics() {
return transportDiagnostics_;
}
private void initFields() {
deviceDiagnostics_ = Diagnostics.DeviceDiagnostics.getDefaultInstance();
transportDiagnostics_ = Diagnostics.TransportDiagnostics.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (hasDeviceDiagnostics()) {
if (!getDeviceDiagnostics().isInitialized()) {
mii = 0;
return false;
}
}
if (hasTransportDiagnostics()) {
if (!getTransportDiagnostics().isInitialized()) {
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
output.writeMessage(1, deviceDiagnostics_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeMessage(2, transportDiagnostics_);
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
.computeMessageSize(1, deviceDiagnostics_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeMessageSize(2, transportDiagnostics_);
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
public static Ritual.GetDiagnosticsReply parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetDiagnosticsReply parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetDiagnosticsReply parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Ritual.GetDiagnosticsReply parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Ritual.GetDiagnosticsReply parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetDiagnosticsReply parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Ritual.GetDiagnosticsReply parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Ritual.GetDiagnosticsReply parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Ritual.GetDiagnosticsReply parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Ritual.GetDiagnosticsReply parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Ritual.GetDiagnosticsReply prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Ritual.GetDiagnosticsReply, Builder>
implements
Ritual.GetDiagnosticsReplyOrBuilder {
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
deviceDiagnostics_ = Diagnostics.DeviceDiagnostics.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
transportDiagnostics_ = Diagnostics.TransportDiagnostics.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Ritual.GetDiagnosticsReply getDefaultInstanceForType() {
return Ritual.GetDiagnosticsReply.getDefaultInstance();
}
public Ritual.GetDiagnosticsReply build() {
Ritual.GetDiagnosticsReply result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Ritual.GetDiagnosticsReply buildPartial() {
Ritual.GetDiagnosticsReply result = new Ritual.GetDiagnosticsReply(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.deviceDiagnostics_ = deviceDiagnostics_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.transportDiagnostics_ = transportDiagnostics_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Ritual.GetDiagnosticsReply other) {
if (other == Ritual.GetDiagnosticsReply.getDefaultInstance()) return this;
if (other.hasDeviceDiagnostics()) {
mergeDeviceDiagnostics(other.getDeviceDiagnostics());
}
if (other.hasTransportDiagnostics()) {
mergeTransportDiagnostics(other.getTransportDiagnostics());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (hasDeviceDiagnostics()) {
if (!getDeviceDiagnostics().isInitialized()) {
return false;
}
}
if (hasTransportDiagnostics()) {
if (!getTransportDiagnostics().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Ritual.GetDiagnosticsReply pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Ritual.GetDiagnosticsReply) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.DeviceDiagnostics deviceDiagnostics_ = Diagnostics.DeviceDiagnostics.getDefaultInstance();
public boolean hasDeviceDiagnostics() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.DeviceDiagnostics getDeviceDiagnostics() {
return deviceDiagnostics_;
}
public Builder setDeviceDiagnostics(Diagnostics.DeviceDiagnostics value) {
if (value == null) {
throw new NullPointerException();
}
deviceDiagnostics_ = value;
b0_ |= 0x00000001;
return this;
}
public Builder setDeviceDiagnostics(
Diagnostics.DeviceDiagnostics.Builder bdForValue) {
deviceDiagnostics_ = bdForValue.build();
b0_ |= 0x00000001;
return this;
}
public Builder mergeDeviceDiagnostics(Diagnostics.DeviceDiagnostics value) {
if (((b0_ & 0x00000001) == 0x00000001) &&
deviceDiagnostics_ != Diagnostics.DeviceDiagnostics.getDefaultInstance()) {
deviceDiagnostics_ =
Diagnostics.DeviceDiagnostics.newBuilder(deviceDiagnostics_).mergeFrom(value).buildPartial();
} else {
deviceDiagnostics_ = value;
}
b0_ |= 0x00000001;
return this;
}
public Builder clearDeviceDiagnostics() {
deviceDiagnostics_ = Diagnostics.DeviceDiagnostics.getDefaultInstance();
b0_ = (b0_ & ~0x00000001);
return this;
}
private Diagnostics.TransportDiagnostics transportDiagnostics_ = Diagnostics.TransportDiagnostics.getDefaultInstance();
public boolean hasTransportDiagnostics() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Diagnostics.TransportDiagnostics getTransportDiagnostics() {
return transportDiagnostics_;
}
public Builder setTransportDiagnostics(Diagnostics.TransportDiagnostics value) {
if (value == null) {
throw new NullPointerException();
}
transportDiagnostics_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setTransportDiagnostics(
Diagnostics.TransportDiagnostics.Builder bdForValue) {
transportDiagnostics_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeTransportDiagnostics(Diagnostics.TransportDiagnostics value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
transportDiagnostics_ != Diagnostics.TransportDiagnostics.getDefaultInstance()) {
transportDiagnostics_ =
Diagnostics.TransportDiagnostics.newBuilder(transportDiagnostics_).mergeFrom(value).buildPartial();
} else {
transportDiagnostics_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearTransportDiagnostics() {
transportDiagnostics_ = Diagnostics.TransportDiagnostics.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
}
static {
defaultInstance = new GetDiagnosticsReply(true);
defaultInstance.initFields();
}
}
static {
}
public interface IRitualService
{
Common.PBException encodeError(Throwable error);
public ListenableFuture<Ritual.GetObjectAttributesReply> getObjectAttributes(Common.PBPath path) throws Exception;
public ListenableFuture<Ritual.GetChildrenAttributesReply> getChildrenAttributes(Common.PBPath path) throws Exception;
public ListenableFuture<Ritual.ListNonRepresentableObjectsReply> listNonRepresentableObjects() throws Exception;
public ListenableFuture<Common.Void> createObject(Common.PBPath path, Boolean dir) throws Exception;
public ListenableFuture<Common.Void> deleteObject(Common.PBPath path) throws Exception;
public ListenableFuture<Common.Void> moveObject(Common.PBPath pathFrom, Common.PBPath pathTo) throws Exception;
public ListenableFuture<Common.Void> importFile(Common.PBPath destination, String source) throws Exception;
public ListenableFuture<Ritual.ExportFileReply> exportFile(Common.PBPath source) throws Exception;
public ListenableFuture<Ritual.CreateRootReply> createRoot(String path) throws Exception;
public ListenableFuture<Common.Void> linkRoot(String path, ByteString sid) throws Exception;
public ListenableFuture<Ritual.ListUnlinkedRootsReply> listUnlinkedRoots() throws Exception;
public ListenableFuture<Common.Void> unlinkRoot(ByteString sid) throws Exception;
public ListenableFuture<Common.Void> shareFolder(Common.PBPath path, List<Common.PBSubjectPermissions> subjectPermissions, String note, Boolean suppressSharingRulesWarnings) throws Exception;
public ListenableFuture<Ritual.CreateUrlReply> createUrl(Common.PBPath path) throws Exception;
public ListenableFuture<Common.Void> unshareFolder(Common.PBPath path) throws Exception;
public ListenableFuture<Ritual.ListUserRootsReply> listUserRoots() throws Exception;
public ListenableFuture<Ritual.ListSharedFoldersReply> listSharedFolders() throws Exception;
public ListenableFuture<Ritual.ListSharedFolderInvitationsReply> listSharedFolderInvitations() throws Exception;
public ListenableFuture<Common.Void> joinSharedFolder(ByteString id) throws Exception;
public ListenableFuture<Common.Void> leaveSharedFolder(Common.PBPath path) throws Exception;
public ListenableFuture<Common.Void> excludeFolder(Common.PBPath path) throws Exception;
public ListenableFuture<Common.Void> includeFolder(Common.PBPath path) throws Exception;
public ListenableFuture<Ritual.ListExcludedFoldersReply> listExcludedFolders() throws Exception;
public ListenableFuture<Common.Void> updateACL(Common.PBPath path, String subject, Common.PBPermissions permissions, Boolean suppressSharingRulesWarnings) throws Exception;
public ListenableFuture<Common.Void> deleteACL(Common.PBPath path, String subject) throws Exception;
public ListenableFuture<Common.Void> pauseSyncing() throws Exception;
public ListenableFuture<Common.Void> resumeSyncing() throws Exception;
public ListenableFuture<Ritual.GetActivitiesReply> getActivities(Boolean brief, Integer maxResults, Long pageToken) throws Exception;
public ListenableFuture<Ritual.GetPathStatusReply> getPathStatus(List<Common.PBPath> path) throws Exception;
public ListenableFuture<Ritual.ListRevChildrenReply> listRevChildren(Common.PBPath path) throws Exception;
public ListenableFuture<Ritual.ListRevHistoryReply> listRevHistory(Common.PBPath path) throws Exception;
public ListenableFuture<Ritual.ExportRevisionReply> exportRevision(Common.PBPath path, ByteString index) throws Exception;
public ListenableFuture<Common.Void> deleteRevision(Common.PBPath path, ByteString index) throws Exception;
public ListenableFuture<Ritual.ListConflictsReply> listConflicts() throws Exception;
public ListenableFuture<Ritual.ExportConflictReply> exportConflict(Common.PBPath path, Integer kidx) throws Exception;
public ListenableFuture<Common.Void> deleteConflict(Common.PBPath path, Integer kidx) throws Exception;
public ListenableFuture<Common.Void> invalidateDeviceNameCache() throws Exception;
public ListenableFuture<Common.Void> invalidateUserNameCache() throws Exception;
public ListenableFuture<Common.Void> heartbeat() throws Exception;
public ListenableFuture<Common.Void> relocate(String absolutePath, ByteString storeId) throws Exception;
public ListenableFuture<Common.Void> reloadConfig() throws Exception;
public ListenableFuture<Common.Void> shutdown() throws Exception;
public ListenableFuture<Ritual.CreateSeedFileReply> createSeedFile(ByteString storeId) throws Exception;
public ListenableFuture<Ritual.GetTransferStatsReply> getTransferStats() throws Exception;
public ListenableFuture<Ritual.DumpStatsReply> dumpStats(Diagnostics.PBDumpStat template) throws Exception;
public ListenableFuture<Ritual.GetDiagnosticsReply> getDiagnostics() throws Exception;
public ListenableFuture<Common.Void> logThreads() throws Exception;
public ListenableFuture<Ritual.TestGetObjectIdentifierReply> testGetObjectIdentifier(Common.PBPath path) throws Exception;
public ListenableFuture<Common.Void> testPauseLinker() throws Exception;
public ListenableFuture<Common.Void> testResumeLinker() throws Exception;
public ListenableFuture<Common.Void> testLogSendDefect() throws Exception;
public ListenableFuture<Ritual.TestGetAliasObjectReply> testGetAliasObject(Common.PBPath path) throws Exception;
public ListenableFuture<Common.Void> testCheckQuota() throws Exception;
}
public static class RitualServiceReactor
{
Ritual.IRitualService _service;
public enum ServiceRpcTypes {
__ERROR__,
GET_OBJECT_ATTRIBUTES,
GET_CHILDREN_ATTRIBUTES,
LIST_NON_REPRESENTABLE_OBJECTS,
CREATE_OBJECT,
DELETE_OBJECT,
MOVE_OBJECT,
IMPORT_FILE,
EXPORT_FILE,
CREATE_ROOT,
LINK_ROOT,
LIST_UNLINKED_ROOTS,
UNLINK_ROOT,
SHARE_FOLDER,
CREATE_URL,
UNSHARE_FOLDER,
LIST_USER_ROOTS,
LIST_SHARED_FOLDERS,
LIST_SHARED_FOLDER_INVITATIONS,
JOIN_SHARED_FOLDER,
LEAVE_SHARED_FOLDER,
EXCLUDE_FOLDER,
INCLUDE_FOLDER,
LIST_EXCLUDED_FOLDERS,
UPDATE_ACL,
DELETE_ACL,
PAUSE_SYNCING,
RESUME_SYNCING,
GET_ACTIVITIES,
GET_PATH_STATUS,
LIST_REV_CHILDREN,
LIST_REV_HISTORY,
EXPORT_REVISION,
DELETE_REVISION,
LIST_CONFLICTS,
EXPORT_CONFLICT,
DELETE_CONFLICT,
INVALIDATE_DEVICE_NAME_CACHE,
INVALIDATE_USER_NAME_CACHE,
HEARTBEAT,
RELOCATE,
RELOAD_CONFIG,
SHUTDOWN,
CREATE_SEED_FILE,
GET_TRANSFER_STATS,
DUMP_STATS,
GET_DIAGNOSTICS,
LOG_THREADS,
TEST_GET_OBJECT_IDENTIFIER,
TEST_PAUSE_LINKER,
TEST_RESUME_LINKER,
TEST_LOG_SEND_DEFECT,
TEST_GET_ALIAS_OBJECT,
TEST_CHECK_QUOTA
}
public RitualServiceReactor(Ritual.IRitualService service)
{
_service = service;
}
public ListenableFuture<byte[]> react(byte[] data)
{
ListenableFuture<? extends GeneratedMessageLite> reply;
int callType;
try {
RpcService.Payload p = RpcService.Payload.parseFrom(data);
callType = p.getType();
ServiceRpcTypes t;
try {
t = ServiceRpcTypes.values()[callType];
} catch (ArrayIndexOutOfBoundsException ex) {
throw new InvalidProtocolBufferException("Unknown message type: " + callType + ". Wrong protocol version.");
}
switch (t) {
case GET_OBJECT_ATTRIBUTES: {
Ritual.GetObjectAttributesCall call = Ritual.GetObjectAttributesCall.parseFrom(p.getPayloadData());
reply = _service.getObjectAttributes(call.getPath());
break;
}
case GET_CHILDREN_ATTRIBUTES: {
Ritual.GetChildrenAttributesCall call = Ritual.GetChildrenAttributesCall.parseFrom(p.getPayloadData());
reply = _service.getChildrenAttributes(call.getPath());
break;
}
case LIST_NON_REPRESENTABLE_OBJECTS: {
reply = _service.listNonRepresentableObjects();
break;
}
case CREATE_OBJECT: {
Ritual.CreateObjectCall call = Ritual.CreateObjectCall.parseFrom(p.getPayloadData());
reply = _service.createObject(call.getPath(),
call.getDir());
break;
}
case DELETE_OBJECT: {
Ritual.DeleteObjectCall call = Ritual.DeleteObjectCall.parseFrom(p.getPayloadData());
reply = _service.deleteObject(call.getPath());
break;
}
case MOVE_OBJECT: {
Ritual.MoveObjectCall call = Ritual.MoveObjectCall.parseFrom(p.getPayloadData());
reply = _service.moveObject(call.getPathFrom(),
call.getPathTo());
break;
}
case IMPORT_FILE: {
Ritual.ImportFileCall call = Ritual.ImportFileCall.parseFrom(p.getPayloadData());
reply = _service.importFile(call.getDestination(),
call.getSource());
break;
}
case EXPORT_FILE: {
Ritual.ExportFileCall call = Ritual.ExportFileCall.parseFrom(p.getPayloadData());
reply = _service.exportFile(call.getSource());
break;
}
case CREATE_ROOT: {
Ritual.CreateRootCall call = Ritual.CreateRootCall.parseFrom(p.getPayloadData());
reply = _service.createRoot(call.getPath());
break;
}
case LINK_ROOT: {
Ritual.LinkRootCall call = Ritual.LinkRootCall.parseFrom(p.getPayloadData());
reply = _service.linkRoot(call.getPath(),
call.getSid());
break;
}
case LIST_UNLINKED_ROOTS: {
reply = _service.listUnlinkedRoots();
break;
}
case UNLINK_ROOT: {
Ritual.UnlinkRootCall call = Ritual.UnlinkRootCall.parseFrom(p.getPayloadData());
reply = _service.unlinkRoot(call.getSid());
break;
}
case SHARE_FOLDER: {
Ritual.ShareFolderCall call = Ritual.ShareFolderCall.parseFrom(p.getPayloadData());
reply = _service.shareFolder(call.getPath(),
call.getSubjectPermissionsList(),
call.getNote(),
call.getSuppressSharingRulesWarnings());
break;
}
case CREATE_URL: {
Ritual.CreateUrlCall call = Ritual.CreateUrlCall.parseFrom(p.getPayloadData());
reply = _service.createUrl(call.getPath());
break;
}
case UNSHARE_FOLDER: {
Ritual.UnshareFolderCall call = Ritual.UnshareFolderCall.parseFrom(p.getPayloadData());
reply = _service.unshareFolder(call.getPath());
break;
}
case LIST_USER_ROOTS: {
reply = _service.listUserRoots();
break;
}
case LIST_SHARED_FOLDERS: {
reply = _service.listSharedFolders();
break;
}
case LIST_SHARED_FOLDER_INVITATIONS: {
reply = _service.listSharedFolderInvitations();
break;
}
case JOIN_SHARED_FOLDER: {
Ritual.JoinSharedFolderCall call = Ritual.JoinSharedFolderCall.parseFrom(p.getPayloadData());
reply = _service.joinSharedFolder(call.getId());
break;
}
case LEAVE_SHARED_FOLDER: {
Ritual.LeaveSharedFolderCall call = Ritual.LeaveSharedFolderCall.parseFrom(p.getPayloadData());
reply = _service.leaveSharedFolder(call.getPath());
break;
}
case EXCLUDE_FOLDER: {
Ritual.ExcludeFolderCall call = Ritual.ExcludeFolderCall.parseFrom(p.getPayloadData());
reply = _service.excludeFolder(call.getPath());
break;
}
case INCLUDE_FOLDER: {
Ritual.IncludeFolderCall call = Ritual.IncludeFolderCall.parseFrom(p.getPayloadData());
reply = _service.includeFolder(call.getPath());
break;
}
case LIST_EXCLUDED_FOLDERS: {
reply = _service.listExcludedFolders();
break;
}
case UPDATE_ACL: {
Ritual.UpdateACLCall call = Ritual.UpdateACLCall.parseFrom(p.getPayloadData());
reply = _service.updateACL(call.getPath(),
call.getSubject(),
call.getPermissions(),
call.getSuppressSharingRulesWarnings());
break;
}
case DELETE_ACL: {
Ritual.DeleteACLCall call = Ritual.DeleteACLCall.parseFrom(p.getPayloadData());
reply = _service.deleteACL(call.getPath(),
call.getSubject());
break;
}
case PAUSE_SYNCING: {
reply = _service.pauseSyncing();
break;
}
case RESUME_SYNCING: {
reply = _service.resumeSyncing();
break;
}
case GET_ACTIVITIES: {
Ritual.GetActivitiesCall call = Ritual.GetActivitiesCall.parseFrom(p.getPayloadData());
reply = _service.getActivities(call.getBrief(),
call.getMaxResults(),
call.hasPageToken() ? call.getPageToken() : null);
break;
}
case GET_PATH_STATUS: {
Ritual.GetPathStatusCall call = Ritual.GetPathStatusCall.parseFrom(p.getPayloadData());
reply = _service.getPathStatus(call.getPathList());
break;
}
case LIST_REV_CHILDREN: {
Ritual.ListRevChildrenCall call = Ritual.ListRevChildrenCall.parseFrom(p.getPayloadData());
reply = _service.listRevChildren(call.getPath());
break;
}
case LIST_REV_HISTORY: {
Ritual.ListRevHistoryCall call = Ritual.ListRevHistoryCall.parseFrom(p.getPayloadData());
reply = _service.listRevHistory(call.getPath());
break;
}
case EXPORT_REVISION: {
Ritual.ExportRevisionCall call = Ritual.ExportRevisionCall.parseFrom(p.getPayloadData());
reply = _service.exportRevision(call.getPath(),
call.getIndex());
break;
}
case DELETE_REVISION: {
Ritual.DeleteRevisionCall call = Ritual.DeleteRevisionCall.parseFrom(p.getPayloadData());
reply = _service.deleteRevision(call.getPath(),
call.hasIndex() ? call.getIndex() : null);
break;
}
case LIST_CONFLICTS: {
reply = _service.listConflicts();
break;
}
case EXPORT_CONFLICT: {
Ritual.ExportConflictCall call = Ritual.ExportConflictCall.parseFrom(p.getPayloadData());
reply = _service.exportConflict(call.getPath(),
call.getKidx());
break;
}
case DELETE_CONFLICT: {
Ritual.DeleteConflictCall call = Ritual.DeleteConflictCall.parseFrom(p.getPayloadData());
reply = _service.deleteConflict(call.getPath(),
call.getKidx());
break;
}
case INVALIDATE_DEVICE_NAME_CACHE: {
reply = _service.invalidateDeviceNameCache();
break;
}
case INVALIDATE_USER_NAME_CACHE: {
reply = _service.invalidateUserNameCache();
break;
}
case HEARTBEAT: {
reply = _service.heartbeat();
break;
}
case RELOCATE: {
Ritual.RelocateCall call = Ritual.RelocateCall.parseFrom(p.getPayloadData());
reply = _service.relocate(call.getAbsolutePath(),
call.hasStoreId() ? call.getStoreId() : null);
break;
}
case RELOAD_CONFIG: {
reply = _service.reloadConfig();
break;
}
case SHUTDOWN: {
reply = _service.shutdown();
break;
}
case CREATE_SEED_FILE: {
Ritual.CreateSeedFileCall call = Ritual.CreateSeedFileCall.parseFrom(p.getPayloadData());
reply = _service.createSeedFile(call.getStoreId());
break;
}
case GET_TRANSFER_STATS: {
reply = _service.getTransferStats();
break;
}
case DUMP_STATS: {
Ritual.DumpStatsCall call = Ritual.DumpStatsCall.parseFrom(p.getPayloadData());
reply = _service.dumpStats(call.getTemplate());
break;
}
case GET_DIAGNOSTICS: {
reply = _service.getDiagnostics();
break;
}
case LOG_THREADS: {
reply = _service.logThreads();
break;
}
case TEST_GET_OBJECT_IDENTIFIER: {
Ritual.TestGetObjectIdentifierCall call = Ritual.TestGetObjectIdentifierCall.parseFrom(p.getPayloadData());
reply = _service.testGetObjectIdentifier(call.getPath());
break;
}
case TEST_PAUSE_LINKER: {
reply = _service.testPauseLinker();
break;
}
case TEST_RESUME_LINKER: {
reply = _service.testResumeLinker();
break;
}
case TEST_LOG_SEND_DEFECT: {
reply = _service.testLogSendDefect();
break;
}
case TEST_GET_ALIAS_OBJECT: {
Ritual.TestGetAliasObjectCall call = Ritual.TestGetAliasObjectCall.parseFrom(p.getPayloadData());
reply = _service.testGetAliasObject(call.getPath());
break;
}
case TEST_CHECK_QUOTA: {
reply = _service.testCheckQuota();
break;
}
default:
throw new InvalidProtocolBufferException("Invalid RPC call: " + t);
}
if (reply == null) {
throw new NullPointerException("RitualService: implementation returned a null pointer for call " + t);
}
} catch (Exception e) {
SettableFuture<GeneratedMessageLite> r = SettableFuture.create();
r.setException(e);
reply = r;
callType = ServiceRpcTypes.__ERROR__.ordinal();
}
final SettableFuture<byte[]> future = SettableFuture.create();
final int finalCallType = callType;
addCallback(reply, new FutureCallback<GeneratedMessageLite>()
{
@Override
public void onSuccess(GeneratedMessageLite r)
{
RpcService.Payload p = RpcService.Payload.newBuilder()
.setType(finalCallType)
.setPayloadData(r.toByteString())
.build();
future.set(p.toByteArray());
}
@Override
public void onFailure(Throwable error)
{
RpcService.Payload p = RpcService.Payload.newBuilder()
.setType(ServiceRpcTypes.__ERROR__.ordinal())
.setPayloadData(_service.encodeError(error).toByteString())
.build();
future.set(p.toByteArray());
}
});
return future;
}
}
public static class RitualServiceStub
{
public interface RitualServiceStubCallbacks
{
public ListenableFuture<byte[]> doRPC(byte[] data);
Throwable decodeError(Common.PBException error);
}
RitualServiceStubCallbacks _callbacks;
public RitualServiceStub(RitualServiceStubCallbacks callbacks)
{
_callbacks = callbacks;
}
private <T extends MessageLite> ListenableFuture<T>
sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes type, ByteString bytes, MessageLite.Builder b, Class<T> tClass)
{
RpcService.Payload p = RpcService.Payload.newBuilder()
.setType(type.ordinal())
.setPayloadData(bytes)
.build();
SettableFuture<T> receiveFuture = SettableFuture.create();
ListenableFuture<byte[]> sendFuture = _callbacks.doRPC(p.toByteArray());
addCallback(sendFuture, new ReplyCallback<T>(receiveFuture, type, b, tClass));
return receiveFuture;
}
private class ReplyCallback<T extends MessageLite>
implements FutureCallback<byte[]>
{
private final SettableFuture<T> _replyFuture;
private final Ritual.RitualServiceReactor.ServiceRpcTypes _replyType;
private final MessageLite.Builder _bd;
private final Class<T> _tClass;
public ReplyCallback(SettableFuture<T> future,
Ritual.RitualServiceReactor.ServiceRpcTypes type, MessageLite.Builder bd, Class<T> tClass)
{
_replyFuture = future;
_replyType = type;
_bd = bd;
_tClass = tClass;
}
@Override
public void onSuccess(byte[] bytes)
{
try {
RpcService.Payload p = RpcService.Payload.parseFrom(bytes);
if (p.getType() == Ritual.RitualServiceReactor.ServiceRpcTypes.__ERROR__.ordinal()) {
Common.PBException error = Common.PBException.parseFrom(p.getPayloadData());
_replyFuture.setException(_callbacks.decodeError(error));
return;
}
if (p.getType() != _replyType.ordinal()) {
throw new RuntimeException("Unexpected response received from the server. Code: " + p.getType() + ". Expecting: " + _replyType.ordinal());
}
MessageLite r = _bd.mergeFrom(p.getPayloadData()).build();
T reply = _tClass.cast(r);
_replyFuture.set(reply);
} catch (Throwable e) {
_replyFuture.setException(e);
}
}
@Override
public void onFailure(Throwable throwable)
{
_replyFuture.setException(throwable);
}
}
public ListenableFuture<Ritual.GetObjectAttributesReply> getObjectAttributes(Common.PBPath path)
{
Ritual.GetObjectAttributesCall.Builder bd = Ritual.GetObjectAttributesCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.GET_OBJECT_ATTRIBUTES, bd.build().toByteString(), Ritual.GetObjectAttributesReply.newBuilder(), Ritual.GetObjectAttributesReply.class);
}
public ListenableFuture<Ritual.GetChildrenAttributesReply> getChildrenAttributes(Common.PBPath path)
{
Ritual.GetChildrenAttributesCall.Builder bd = Ritual.GetChildrenAttributesCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.GET_CHILDREN_ATTRIBUTES, bd.build().toByteString(), Ritual.GetChildrenAttributesReply.newBuilder(), Ritual.GetChildrenAttributesReply.class);
}
public ListenableFuture<Ritual.ListNonRepresentableObjectsReply> listNonRepresentableObjects()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_NON_REPRESENTABLE_OBJECTS, bd.build().toByteString(), Ritual.ListNonRepresentableObjectsReply.newBuilder(), Ritual.ListNonRepresentableObjectsReply.class);
}
public ListenableFuture<Common.Void> createObject(Common.PBPath path, Boolean dir)
{
Ritual.CreateObjectCall.Builder bd = Ritual.CreateObjectCall.newBuilder();
bd.setPath(path);
bd.setDir(dir);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.CREATE_OBJECT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> deleteObject(Common.PBPath path)
{
Ritual.DeleteObjectCall.Builder bd = Ritual.DeleteObjectCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.DELETE_OBJECT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> moveObject(Common.PBPath pathFrom, Common.PBPath pathTo)
{
Ritual.MoveObjectCall.Builder bd = Ritual.MoveObjectCall.newBuilder();
bd.setPathFrom(pathFrom);
bd.setPathTo(pathTo);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.MOVE_OBJECT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> importFile(Common.PBPath destination, String source)
{
Ritual.ImportFileCall.Builder bd = Ritual.ImportFileCall.newBuilder();
bd.setDestination(destination);
bd.setSource(source);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.IMPORT_FILE, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.ExportFileReply> exportFile(Common.PBPath source)
{
Ritual.ExportFileCall.Builder bd = Ritual.ExportFileCall.newBuilder();
bd.setSource(source);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.EXPORT_FILE, bd.build().toByteString(), Ritual.ExportFileReply.newBuilder(), Ritual.ExportFileReply.class);
}
public ListenableFuture<Ritual.CreateRootReply> createRoot(String path)
{
Ritual.CreateRootCall.Builder bd = Ritual.CreateRootCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.CREATE_ROOT, bd.build().toByteString(), Ritual.CreateRootReply.newBuilder(), Ritual.CreateRootReply.class);
}
public ListenableFuture<Common.Void> linkRoot(String path, ByteString sid)
{
Ritual.LinkRootCall.Builder bd = Ritual.LinkRootCall.newBuilder();
bd.setPath(path);
bd.setSid(sid);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LINK_ROOT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.ListUnlinkedRootsReply> listUnlinkedRoots()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_UNLINKED_ROOTS, bd.build().toByteString(), Ritual.ListUnlinkedRootsReply.newBuilder(), Ritual.ListUnlinkedRootsReply.class);
}
public ListenableFuture<Common.Void> unlinkRoot(ByteString sid)
{
Ritual.UnlinkRootCall.Builder bd = Ritual.UnlinkRootCall.newBuilder();
bd.setSid(sid);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.UNLINK_ROOT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> shareFolder(Common.PBPath path, Iterable<Common.PBSubjectPermissions> subjectPermissions, String note, Boolean suppressSharingRulesWarnings)
{
Ritual.ShareFolderCall.Builder bd = Ritual.ShareFolderCall.newBuilder();
bd.setPath(path);
if (subjectPermissions != null) { bd.addAllSubjectPermissions(subjectPermissions); }
bd.setNote(note);
bd.setSuppressSharingRulesWarnings(suppressSharingRulesWarnings);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.SHARE_FOLDER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.CreateUrlReply> createUrl(Common.PBPath path)
{
Ritual.CreateUrlCall.Builder bd = Ritual.CreateUrlCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.CREATE_URL, bd.build().toByteString(), Ritual.CreateUrlReply.newBuilder(), Ritual.CreateUrlReply.class);
}
public ListenableFuture<Common.Void> unshareFolder(Common.PBPath path)
{
Ritual.UnshareFolderCall.Builder bd = Ritual.UnshareFolderCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.UNSHARE_FOLDER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.ListUserRootsReply> listUserRoots()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_USER_ROOTS, bd.build().toByteString(), Ritual.ListUserRootsReply.newBuilder(), Ritual.ListUserRootsReply.class);
}
public ListenableFuture<Ritual.ListSharedFoldersReply> listSharedFolders()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_SHARED_FOLDERS, bd.build().toByteString(), Ritual.ListSharedFoldersReply.newBuilder(), Ritual.ListSharedFoldersReply.class);
}
public ListenableFuture<Ritual.ListSharedFolderInvitationsReply> listSharedFolderInvitations()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_SHARED_FOLDER_INVITATIONS, bd.build().toByteString(), Ritual.ListSharedFolderInvitationsReply.newBuilder(), Ritual.ListSharedFolderInvitationsReply.class);
}
public ListenableFuture<Common.Void> joinSharedFolder(ByteString id)
{
Ritual.JoinSharedFolderCall.Builder bd = Ritual.JoinSharedFolderCall.newBuilder();
bd.setId(id);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.JOIN_SHARED_FOLDER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> leaveSharedFolder(Common.PBPath path)
{
Ritual.LeaveSharedFolderCall.Builder bd = Ritual.LeaveSharedFolderCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LEAVE_SHARED_FOLDER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> excludeFolder(Common.PBPath path)
{
Ritual.ExcludeFolderCall.Builder bd = Ritual.ExcludeFolderCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.EXCLUDE_FOLDER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> includeFolder(Common.PBPath path)
{
Ritual.IncludeFolderCall.Builder bd = Ritual.IncludeFolderCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.INCLUDE_FOLDER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.ListExcludedFoldersReply> listExcludedFolders()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_EXCLUDED_FOLDERS, bd.build().toByteString(), Ritual.ListExcludedFoldersReply.newBuilder(), Ritual.ListExcludedFoldersReply.class);
}
public ListenableFuture<Common.Void> updateACL(Common.PBPath path, String subject, Common.PBPermissions permissions, Boolean suppressSharingRulesWarnings)
{
Ritual.UpdateACLCall.Builder bd = Ritual.UpdateACLCall.newBuilder();
bd.setPath(path);
bd.setSubject(subject);
bd.setPermissions(permissions);
bd.setSuppressSharingRulesWarnings(suppressSharingRulesWarnings);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.UPDATE_ACL, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> deleteACL(Common.PBPath path, String subject)
{
Ritual.DeleteACLCall.Builder bd = Ritual.DeleteACLCall.newBuilder();
bd.setPath(path);
bd.setSubject(subject);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.DELETE_ACL, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> pauseSyncing()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.PAUSE_SYNCING, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> resumeSyncing()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.RESUME_SYNCING, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.GetActivitiesReply> getActivities(Boolean brief, Integer maxResults, Long pageToken)
{
Ritual.GetActivitiesCall.Builder bd = Ritual.GetActivitiesCall.newBuilder();
bd.setBrief(brief);
bd.setMaxResults(maxResults);
if (pageToken != null) { bd.setPageToken(pageToken); }
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.GET_ACTIVITIES, bd.build().toByteString(), Ritual.GetActivitiesReply.newBuilder(), Ritual.GetActivitiesReply.class);
}
public ListenableFuture<Ritual.GetPathStatusReply> getPathStatus(Iterable<Common.PBPath> path)
{
Ritual.GetPathStatusCall.Builder bd = Ritual.GetPathStatusCall.newBuilder();
if (path != null) { bd.addAllPath(path); }
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.GET_PATH_STATUS, bd.build().toByteString(), Ritual.GetPathStatusReply.newBuilder(), Ritual.GetPathStatusReply.class);
}
public ListenableFuture<Ritual.ListRevChildrenReply> listRevChildren(Common.PBPath path)
{
Ritual.ListRevChildrenCall.Builder bd = Ritual.ListRevChildrenCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_REV_CHILDREN, bd.build().toByteString(), Ritual.ListRevChildrenReply.newBuilder(), Ritual.ListRevChildrenReply.class);
}
public ListenableFuture<Ritual.ListRevHistoryReply> listRevHistory(Common.PBPath path)
{
Ritual.ListRevHistoryCall.Builder bd = Ritual.ListRevHistoryCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_REV_HISTORY, bd.build().toByteString(), Ritual.ListRevHistoryReply.newBuilder(), Ritual.ListRevHistoryReply.class);
}
public ListenableFuture<Ritual.ExportRevisionReply> exportRevision(Common.PBPath path, ByteString index)
{
Ritual.ExportRevisionCall.Builder bd = Ritual.ExportRevisionCall.newBuilder();
bd.setPath(path);
bd.setIndex(index);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.EXPORT_REVISION, bd.build().toByteString(), Ritual.ExportRevisionReply.newBuilder(), Ritual.ExportRevisionReply.class);
}
public ListenableFuture<Common.Void> deleteRevision(Common.PBPath path, ByteString index)
{
Ritual.DeleteRevisionCall.Builder bd = Ritual.DeleteRevisionCall.newBuilder();
bd.setPath(path);
if (index != null) { bd.setIndex(index); }
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.DELETE_REVISION, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.ListConflictsReply> listConflicts()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LIST_CONFLICTS, bd.build().toByteString(), Ritual.ListConflictsReply.newBuilder(), Ritual.ListConflictsReply.class);
}
public ListenableFuture<Ritual.ExportConflictReply> exportConflict(Common.PBPath path, Integer kidx)
{
Ritual.ExportConflictCall.Builder bd = Ritual.ExportConflictCall.newBuilder();
bd.setPath(path);
bd.setKidx(kidx);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.EXPORT_CONFLICT, bd.build().toByteString(), Ritual.ExportConflictReply.newBuilder(), Ritual.ExportConflictReply.class);
}
public ListenableFuture<Common.Void> deleteConflict(Common.PBPath path, Integer kidx)
{
Ritual.DeleteConflictCall.Builder bd = Ritual.DeleteConflictCall.newBuilder();
bd.setPath(path);
bd.setKidx(kidx);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.DELETE_CONFLICT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> invalidateDeviceNameCache()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.INVALIDATE_DEVICE_NAME_CACHE, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> invalidateUserNameCache()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.INVALIDATE_USER_NAME_CACHE, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> heartbeat()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.HEARTBEAT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> relocate(String absolutePath, ByteString storeId)
{
Ritual.RelocateCall.Builder bd = Ritual.RelocateCall.newBuilder();
bd.setAbsolutePath(absolutePath);
if (storeId != null) { bd.setStoreId(storeId); }
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.RELOCATE, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> reloadConfig()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.RELOAD_CONFIG, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> shutdown()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.SHUTDOWN, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.CreateSeedFileReply> createSeedFile(ByteString storeId)
{
Ritual.CreateSeedFileCall.Builder bd = Ritual.CreateSeedFileCall.newBuilder();
bd.setStoreId(storeId);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.CREATE_SEED_FILE, bd.build().toByteString(), Ritual.CreateSeedFileReply.newBuilder(), Ritual.CreateSeedFileReply.class);
}
public ListenableFuture<Ritual.GetTransferStatsReply> getTransferStats()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.GET_TRANSFER_STATS, bd.build().toByteString(), Ritual.GetTransferStatsReply.newBuilder(), Ritual.GetTransferStatsReply.class);
}
public ListenableFuture<Ritual.DumpStatsReply> dumpStats(Diagnostics.PBDumpStat template)
{
Ritual.DumpStatsCall.Builder bd = Ritual.DumpStatsCall.newBuilder();
bd.setTemplate(template);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.DUMP_STATS, bd.build().toByteString(), Ritual.DumpStatsReply.newBuilder(), Ritual.DumpStatsReply.class);
}
public ListenableFuture<Ritual.GetDiagnosticsReply> getDiagnostics()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.GET_DIAGNOSTICS, bd.build().toByteString(), Ritual.GetDiagnosticsReply.newBuilder(), Ritual.GetDiagnosticsReply.class);
}
public ListenableFuture<Common.Void> logThreads()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.LOG_THREADS, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.TestGetObjectIdentifierReply> testGetObjectIdentifier(Common.PBPath path)
{
Ritual.TestGetObjectIdentifierCall.Builder bd = Ritual.TestGetObjectIdentifierCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.TEST_GET_OBJECT_IDENTIFIER, bd.build().toByteString(), Ritual.TestGetObjectIdentifierReply.newBuilder(), Ritual.TestGetObjectIdentifierReply.class);
}
public ListenableFuture<Common.Void> testPauseLinker()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.TEST_PAUSE_LINKER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> testResumeLinker()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.TEST_RESUME_LINKER, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Common.Void> testLogSendDefect()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.TEST_LOG_SEND_DEFECT, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
public ListenableFuture<Ritual.TestGetAliasObjectReply> testGetAliasObject(Common.PBPath path)
{
Ritual.TestGetAliasObjectCall.Builder bd = Ritual.TestGetAliasObjectCall.newBuilder();
bd.setPath(path);
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.TEST_GET_ALIAS_OBJECT, bd.build().toByteString(), Ritual.TestGetAliasObjectReply.newBuilder(), Ritual.TestGetAliasObjectReply.class);
}
public ListenableFuture<Common.Void> testCheckQuota()
{
Common.Void.Builder bd = Common.Void.newBuilder();
return sendQuery(Ritual.RitualServiceReactor.ServiceRpcTypes.TEST_CHECK_QUOTA, bd.build().toByteString(), Common.Void.newBuilder(), Common.Void.class);
}
}
public static class RitualServiceBlockingStub
{
private final RitualServiceStub _stub;
public RitualServiceBlockingStub(RitualServiceStub.RitualServiceStubCallbacks callbacks)
{
_stub = new RitualServiceStub(callbacks);
}
public Ritual.GetObjectAttributesReply getObjectAttributes(Common.PBPath path) throws Exception
{
try {
return get(_stub.getObjectAttributes(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetObjectAttributesReply getObjectAttributes(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.getObjectAttributes(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetChildrenAttributesReply getChildrenAttributes(Common.PBPath path) throws Exception
{
try {
return get(_stub.getChildrenAttributes(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetChildrenAttributesReply getChildrenAttributes(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.getChildrenAttributes(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListNonRepresentableObjectsReply listNonRepresentableObjects() throws Exception
{
try {
return get(_stub.listNonRepresentableObjects(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListNonRepresentableObjectsReply listNonRepresentableObjects(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listNonRepresentableObjects(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void createObject(Common.PBPath path, Boolean dir) throws Exception
{
try {
return get(_stub.createObject(path, dir), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void createObject(Common.PBPath path, Boolean dir, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.createObject(path, dir), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteObject(Common.PBPath path) throws Exception
{
try {
return get(_stub.deleteObject(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteObject(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.deleteObject(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void moveObject(Common.PBPath pathFrom, Common.PBPath pathTo) throws Exception
{
try {
return get(_stub.moveObject(pathFrom, pathTo), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void moveObject(Common.PBPath pathFrom, Common.PBPath pathTo, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.moveObject(pathFrom, pathTo), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void importFile(Common.PBPath destination, String source) throws Exception
{
try {
return get(_stub.importFile(destination, source), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void importFile(Common.PBPath destination, String source, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.importFile(destination, source), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ExportFileReply exportFile(Common.PBPath source) throws Exception
{
try {
return get(_stub.exportFile(source), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ExportFileReply exportFile(Common.PBPath source, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.exportFile(source), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.CreateRootReply createRoot(String path) throws Exception
{
try {
return get(_stub.createRoot(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.CreateRootReply createRoot(String path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.createRoot(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void linkRoot(String path, ByteString sid) throws Exception
{
try {
return get(_stub.linkRoot(path, sid), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void linkRoot(String path, ByteString sid, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.linkRoot(path, sid), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListUnlinkedRootsReply listUnlinkedRoots() throws Exception
{
try {
return get(_stub.listUnlinkedRoots(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListUnlinkedRootsReply listUnlinkedRoots(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listUnlinkedRoots(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void unlinkRoot(ByteString sid) throws Exception
{
try {
return get(_stub.unlinkRoot(sid), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void unlinkRoot(ByteString sid, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.unlinkRoot(sid), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void shareFolder(Common.PBPath path, Iterable<Common.PBSubjectPermissions> subjectPermissions, String note, Boolean suppressSharingRulesWarnings) throws Exception
{
try {
return get(_stub.shareFolder(path, subjectPermissions, note, suppressSharingRulesWarnings), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void shareFolder(Common.PBPath path, Iterable<Common.PBSubjectPermissions> subjectPermissions, String note, Boolean suppressSharingRulesWarnings, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.shareFolder(path, subjectPermissions, note, suppressSharingRulesWarnings), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.CreateUrlReply createUrl(Common.PBPath path) throws Exception
{
try {
return get(_stub.createUrl(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.CreateUrlReply createUrl(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.createUrl(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void unshareFolder(Common.PBPath path) throws Exception
{
try {
return get(_stub.unshareFolder(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void unshareFolder(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.unshareFolder(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListUserRootsReply listUserRoots() throws Exception
{
try {
return get(_stub.listUserRoots(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListUserRootsReply listUserRoots(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listUserRoots(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListSharedFoldersReply listSharedFolders() throws Exception
{
try {
return get(_stub.listSharedFolders(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListSharedFoldersReply listSharedFolders(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listSharedFolders(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListSharedFolderInvitationsReply listSharedFolderInvitations() throws Exception
{
try {
return get(_stub.listSharedFolderInvitations(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListSharedFolderInvitationsReply listSharedFolderInvitations(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listSharedFolderInvitations(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void joinSharedFolder(ByteString id) throws Exception
{
try {
return get(_stub.joinSharedFolder(id), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void joinSharedFolder(ByteString id, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.joinSharedFolder(id), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void leaveSharedFolder(Common.PBPath path) throws Exception
{
try {
return get(_stub.leaveSharedFolder(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void leaveSharedFolder(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.leaveSharedFolder(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void excludeFolder(Common.PBPath path) throws Exception
{
try {
return get(_stub.excludeFolder(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void excludeFolder(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.excludeFolder(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void includeFolder(Common.PBPath path) throws Exception
{
try {
return get(_stub.includeFolder(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void includeFolder(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.includeFolder(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListExcludedFoldersReply listExcludedFolders() throws Exception
{
try {
return get(_stub.listExcludedFolders(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListExcludedFoldersReply listExcludedFolders(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listExcludedFolders(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void updateACL(Common.PBPath path, String subject, Common.PBPermissions permissions, Boolean suppressSharingRulesWarnings) throws Exception
{
try {
return get(_stub.updateACL(path, subject, permissions, suppressSharingRulesWarnings), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void updateACL(Common.PBPath path, String subject, Common.PBPermissions permissions, Boolean suppressSharingRulesWarnings, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.updateACL(path, subject, permissions, suppressSharingRulesWarnings), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteACL(Common.PBPath path, String subject) throws Exception
{
try {
return get(_stub.deleteACL(path, subject), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteACL(Common.PBPath path, String subject, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.deleteACL(path, subject), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void pauseSyncing() throws Exception
{
try {
return get(_stub.pauseSyncing(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void pauseSyncing(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.pauseSyncing(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void resumeSyncing() throws Exception
{
try {
return get(_stub.resumeSyncing(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void resumeSyncing(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.resumeSyncing(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetActivitiesReply getActivities(Boolean brief, Integer maxResults, Long pageToken) throws Exception
{
try {
return get(_stub.getActivities(brief, maxResults, pageToken), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetActivitiesReply getActivities(Boolean brief, Integer maxResults, Long pageToken, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.getActivities(brief, maxResults, pageToken), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetPathStatusReply getPathStatus(Iterable<Common.PBPath> path) throws Exception
{
try {
return get(_stub.getPathStatus(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetPathStatusReply getPathStatus(Iterable<Common.PBPath> path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.getPathStatus(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListRevChildrenReply listRevChildren(Common.PBPath path) throws Exception
{
try {
return get(_stub.listRevChildren(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListRevChildrenReply listRevChildren(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listRevChildren(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListRevHistoryReply listRevHistory(Common.PBPath path) throws Exception
{
try {
return get(_stub.listRevHistory(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListRevHistoryReply listRevHistory(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listRevHistory(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ExportRevisionReply exportRevision(Common.PBPath path, ByteString index) throws Exception
{
try {
return get(_stub.exportRevision(path, index), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ExportRevisionReply exportRevision(Common.PBPath path, ByteString index, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.exportRevision(path, index), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteRevision(Common.PBPath path, ByteString index) throws Exception
{
try {
return get(_stub.deleteRevision(path, index), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteRevision(Common.PBPath path, ByteString index, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.deleteRevision(path, index), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListConflictsReply listConflicts() throws Exception
{
try {
return get(_stub.listConflicts(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ListConflictsReply listConflicts(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.listConflicts(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ExportConflictReply exportConflict(Common.PBPath path, Integer kidx) throws Exception
{
try {
return get(_stub.exportConflict(path, kidx), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.ExportConflictReply exportConflict(Common.PBPath path, Integer kidx, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.exportConflict(path, kidx), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteConflict(Common.PBPath path, Integer kidx) throws Exception
{
try {
return get(_stub.deleteConflict(path, kidx), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void deleteConflict(Common.PBPath path, Integer kidx, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.deleteConflict(path, kidx), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void invalidateDeviceNameCache() throws Exception
{
try {
return get(_stub.invalidateDeviceNameCache(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void invalidateDeviceNameCache(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.invalidateDeviceNameCache(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void invalidateUserNameCache() throws Exception
{
try {
return get(_stub.invalidateUserNameCache(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void invalidateUserNameCache(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.invalidateUserNameCache(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void heartbeat() throws Exception
{
try {
return get(_stub.heartbeat(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void heartbeat(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.heartbeat(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void relocate(String absolutePath, ByteString storeId) throws Exception
{
try {
return get(_stub.relocate(absolutePath, storeId), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void relocate(String absolutePath, ByteString storeId, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.relocate(absolutePath, storeId), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void reloadConfig() throws Exception
{
try {
return get(_stub.reloadConfig(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void reloadConfig(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.reloadConfig(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void shutdown() throws Exception
{
try {
return get(_stub.shutdown(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void shutdown(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.shutdown(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.CreateSeedFileReply createSeedFile(ByteString storeId) throws Exception
{
try {
return get(_stub.createSeedFile(storeId), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.CreateSeedFileReply createSeedFile(ByteString storeId, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.createSeedFile(storeId), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetTransferStatsReply getTransferStats() throws Exception
{
try {
return get(_stub.getTransferStats(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetTransferStatsReply getTransferStats(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.getTransferStats(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.DumpStatsReply dumpStats(Diagnostics.PBDumpStat template) throws Exception
{
try {
return get(_stub.dumpStats(template), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.DumpStatsReply dumpStats(Diagnostics.PBDumpStat template, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.dumpStats(template), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetDiagnosticsReply getDiagnostics() throws Exception
{
try {
return get(_stub.getDiagnostics(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.GetDiagnosticsReply getDiagnostics(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.getDiagnostics(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void logThreads() throws Exception
{
try {
return get(_stub.logThreads(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void logThreads(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.logThreads(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.TestGetObjectIdentifierReply testGetObjectIdentifier(Common.PBPath path) throws Exception
{
try {
return get(_stub.testGetObjectIdentifier(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.TestGetObjectIdentifierReply testGetObjectIdentifier(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.testGetObjectIdentifier(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testPauseLinker() throws Exception
{
try {
return get(_stub.testPauseLinker(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testPauseLinker(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.testPauseLinker(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testResumeLinker() throws Exception
{
try {
return get(_stub.testResumeLinker(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testResumeLinker(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.testResumeLinker(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testLogSendDefect() throws Exception
{
try {
return get(_stub.testLogSendDefect(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testLogSendDefect(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.testLogSendDefect(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.TestGetAliasObjectReply testGetAliasObject(Common.PBPath path) throws Exception
{
try {
return get(_stub.testGetAliasObject(path), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Ritual.TestGetAliasObjectReply testGetAliasObject(Common.PBPath path, long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.testGetAliasObject(path), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testCheckQuota() throws Exception
{
try {
return get(_stub.testCheckQuota(), Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
public Common.Void testCheckQuota(long timeout, TimeUnit unit) throws Exception
{
try {
return get(_stub.testCheckQuota(), timeout, unit, Exception.class);
} catch (Exception e) {
if (e.getCause() instanceof Exception) {throw (Exception)e.getCause();}
else {throw e;}
}
}
}
}
