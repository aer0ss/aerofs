package com.aerofs.zephyr.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Zephyr {
private Zephyr() {}
public static void registerAllExtensions(
ExtensionRegistry registry) {
}
public interface ZephyrControlMessageOrBuilder extends
MessageOrBuilder {
boolean hasType();
Zephyr.ZephyrControlMessage.Type getType();
boolean hasHandshake();
Zephyr.ZephyrHandshake getHandshake();
Zephyr.ZephyrHandshakeOrBuilder getHandshakeOrBuilder();
}
public static final class ZephyrControlMessage extends
GeneratedMessage implements
ZephyrControlMessageOrBuilder {
private ZephyrControlMessage(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ZephyrControlMessage(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final ZephyrControlMessage defaultInstance;
public static ZephyrControlMessage getDefaultInstance() {
return defaultInstance;
}
public ZephyrControlMessage getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private ZephyrControlMessage(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
UnknownFieldSet.Builder unknownFields =
UnknownFieldSet.newBuilder();
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFields,
er, tag)) {
done = true;
}
break;
}
case 8: {
int rawValue = input.readEnum();
Zephyr.ZephyrControlMessage.Type value = Zephyr.ZephyrControlMessage.Type.valueOf(rawValue);
if (value == null) {
unknownFields.mergeVarintField(1, rawValue);
} else {
b0_ |= 0x00000001;
type_ = value;
}
break;
}
case 18: {
Zephyr.ZephyrHandshake.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = handshake_.toBuilder();
}
handshake_ = input.readMessage(Zephyr.ZephyrHandshake.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(handshake_);
handshake_ = subBuilder.buildPartial();
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
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Zephyr.internal_static_ZephyrControlMessage_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Zephyr.internal_static_ZephyrControlMessage_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Zephyr.ZephyrControlMessage.class, Zephyr.ZephyrControlMessage.Builder.class);
}
public static Parser<ZephyrControlMessage> PARSER =
new AbstractParser<ZephyrControlMessage>() {
public ZephyrControlMessage parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ZephyrControlMessage(input, er);
}
};
@Override
public Parser<ZephyrControlMessage> getParserForType() {
return PARSER;
}
public enum Type
implements ProtocolMessageEnum {
HANDSHAKE(0, 0),
;
public static final int HANDSHAKE_VALUE = 0;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return HANDSHAKE;
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
public final Descriptors.EnumValueDescriptor
getValueDescriptor() {
return getDescriptor().getValues().get(index);
}
public final Descriptors.EnumDescriptor
getDescriptorForType() {
return getDescriptor();
}
public static final Descriptors.EnumDescriptor
getDescriptor() {
return Zephyr.ZephyrControlMessage.getDescriptor().getEnumTypes().get(0);
}
private static final Type[] VALUES = values();
public static Type valueOf(
Descriptors.EnumValueDescriptor desc) {
if (desc.getType() != getDescriptor()) {
throw new IllegalArgumentException(
"EnumValueDescriptor is not for this type.");
}
return VALUES[desc.getIndex()];
}
private final int index;
private final int value;
private Type(int index, int value) {
this.index = index;
this.value = value;
}
}
private int b0_;
public static final int TYPE_FIELD_NUMBER = 1;
private Zephyr.ZephyrControlMessage.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Zephyr.ZephyrControlMessage.Type getType() {
return type_;
}
public static final int HANDSHAKE_FIELD_NUMBER = 2;
private Zephyr.ZephyrHandshake handshake_;
public boolean hasHandshake() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Zephyr.ZephyrHandshake getHandshake() {
return handshake_;
}
public Zephyr.ZephyrHandshakeOrBuilder getHandshakeOrBuilder() {
return handshake_;
}
private void initFields() {
type_ = Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
handshake_ = Zephyr.ZephyrHandshake.getDefaultInstance();
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
if (hasHandshake()) {
if (!getHandshake().isInitialized()) {
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
output.writeMessage(2, handshake_);
}
getUnknownFields().writeTo(output);
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
.computeMessageSize(2, handshake_);
}
size += getUnknownFields().getSerializedSize();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Zephyr.ZephyrControlMessage parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Zephyr.ZephyrControlMessage parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Zephyr.ZephyrControlMessage parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Zephyr.ZephyrControlMessage parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Zephyr.ZephyrControlMessage parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Zephyr.ZephyrControlMessage parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Zephyr.ZephyrControlMessage parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Zephyr.ZephyrControlMessage parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Zephyr.ZephyrControlMessage parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Zephyr.ZephyrControlMessage parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Zephyr.ZephyrControlMessage prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
@Override
protected Builder newBuilderForType(
GeneratedMessage.BuilderParent parent) {
Builder bd = new Builder(parent);
return bd;
}
public static final class Builder extends
GeneratedMessage.Builder<Builder> implements
Zephyr.ZephyrControlMessageOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Zephyr.internal_static_ZephyrControlMessage_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Zephyr.internal_static_ZephyrControlMessage_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Zephyr.ZephyrControlMessage.class, Zephyr.ZephyrControlMessage.Builder.class);
}
private Builder() {
maybeForceBuilderInitialization();
}
private Builder(
GeneratedMessage.BuilderParent parent) {
super(parent);
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
if (GeneratedMessage.alwaysUseFieldBuilders) {
getHandshakeFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
type_ = Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
b0_ = (b0_ & ~0x00000001);
if (handshakeBuilder_ == null) {
handshake_ = Zephyr.ZephyrHandshake.getDefaultInstance();
} else {
handshakeBuilder_.clear();
}
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Zephyr.internal_static_ZephyrControlMessage_descriptor;
}
public Zephyr.ZephyrControlMessage getDefaultInstanceForType() {
return Zephyr.ZephyrControlMessage.getDefaultInstance();
}
public Zephyr.ZephyrControlMessage build() {
Zephyr.ZephyrControlMessage result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Zephyr.ZephyrControlMessage buildPartial() {
Zephyr.ZephyrControlMessage result = new Zephyr.ZephyrControlMessage(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
if (handshakeBuilder_ == null) {
result.handshake_ = handshake_;
} else {
result.handshake_ = handshakeBuilder_.build();
}
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Zephyr.ZephyrControlMessage) {
return mergeFrom((Zephyr.ZephyrControlMessage)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Zephyr.ZephyrControlMessage other) {
if (other == Zephyr.ZephyrControlMessage.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasHandshake()) {
mergeHandshake(other.getHandshake());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (hasHandshake()) {
if (!getHandshake().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Zephyr.ZephyrControlMessage pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Zephyr.ZephyrControlMessage) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Zephyr.ZephyrControlMessage.Type type_ = Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Zephyr.ZephyrControlMessage.Type getType() {
return type_;
}
public Builder setType(Zephyr.ZephyrControlMessage.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
onChanged();
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
onChanged();
return this;
}
private Zephyr.ZephyrHandshake handshake_ = Zephyr.ZephyrHandshake.getDefaultInstance();
private SingleFieldBuilder<
Zephyr.ZephyrHandshake, Zephyr.ZephyrHandshake.Builder, Zephyr.ZephyrHandshakeOrBuilder> handshakeBuilder_;
public boolean hasHandshake() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Zephyr.ZephyrHandshake getHandshake() {
if (handshakeBuilder_ == null) {
return handshake_;
} else {
return handshakeBuilder_.getMessage();
}
}
public Builder setHandshake(Zephyr.ZephyrHandshake value) {
if (handshakeBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
handshake_ = value;
onChanged();
} else {
handshakeBuilder_.setMessage(value);
}
b0_ |= 0x00000002;
return this;
}
public Builder setHandshake(
Zephyr.ZephyrHandshake.Builder bdForValue) {
if (handshakeBuilder_ == null) {
handshake_ = bdForValue.build();
onChanged();
} else {
handshakeBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000002;
return this;
}
public Builder mergeHandshake(Zephyr.ZephyrHandshake value) {
if (handshakeBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002) &&
handshake_ != Zephyr.ZephyrHandshake.getDefaultInstance()) {
handshake_ =
Zephyr.ZephyrHandshake.newBuilder(handshake_).mergeFrom(value).buildPartial();
} else {
handshake_ = value;
}
onChanged();
} else {
handshakeBuilder_.mergeFrom(value);
}
b0_ |= 0x00000002;
return this;
}
public Builder clearHandshake() {
if (handshakeBuilder_ == null) {
handshake_ = Zephyr.ZephyrHandshake.getDefaultInstance();
onChanged();
} else {
handshakeBuilder_.clear();
}
b0_ = (b0_ & ~0x00000002);
return this;
}
public Zephyr.ZephyrHandshake.Builder getHandshakeBuilder() {
b0_ |= 0x00000002;
onChanged();
return getHandshakeFieldBuilder().getBuilder();
}
public Zephyr.ZephyrHandshakeOrBuilder getHandshakeOrBuilder() {
if (handshakeBuilder_ != null) {
return handshakeBuilder_.getMessageOrBuilder();
} else {
return handshake_;
}
}
private SingleFieldBuilder<
Zephyr.ZephyrHandshake, Zephyr.ZephyrHandshake.Builder, Zephyr.ZephyrHandshakeOrBuilder> 
getHandshakeFieldBuilder() {
if (handshakeBuilder_ == null) {
handshakeBuilder_ = new SingleFieldBuilder<
Zephyr.ZephyrHandshake, Zephyr.ZephyrHandshake.Builder, Zephyr.ZephyrHandshakeOrBuilder>(
getHandshake(),
getParentForChildren(),
isClean());
handshake_ = null;
}
return handshakeBuilder_;
}
}
static {
defaultInstance = new ZephyrControlMessage(true);
defaultInstance.initFields();
}
}
public interface ZephyrHandshakeOrBuilder extends
MessageOrBuilder {
boolean hasSourceZephyrId();
int getSourceZephyrId();
boolean hasDestinationZephyrId();
int getDestinationZephyrId();
}
public static final class ZephyrHandshake extends
GeneratedMessage implements
ZephyrHandshakeOrBuilder {
private ZephyrHandshake(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ZephyrHandshake(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final ZephyrHandshake defaultInstance;
public static ZephyrHandshake getDefaultInstance() {
return defaultInstance;
}
public ZephyrHandshake getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private ZephyrHandshake(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
initFields();
int mutable_b0_ = 0;
UnknownFieldSet.Builder unknownFields =
UnknownFieldSet.newBuilder();
try {
boolean done = false;
while (!done) {
int tag = input.readTag();
switch (tag) {
case 0:
done = true;
break;
default: {
if (!parseUnknownField(input, unknownFields,
er, tag)) {
done = true;
}
break;
}
case 8: {
b0_ |= 0x00000001;
sourceZephyrId_ = input.readUInt32();
break;
}
case 16: {
b0_ |= 0x00000002;
destinationZephyrId_ = input.readUInt32();
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
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Zephyr.internal_static_ZephyrHandshake_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Zephyr.internal_static_ZephyrHandshake_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Zephyr.ZephyrHandshake.class, Zephyr.ZephyrHandshake.Builder.class);
}
public static Parser<ZephyrHandshake> PARSER =
new AbstractParser<ZephyrHandshake>() {
public ZephyrHandshake parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ZephyrHandshake(input, er);
}
};
@Override
public Parser<ZephyrHandshake> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SOURCE_ZEPHYR_ID_FIELD_NUMBER = 1;
private int sourceZephyrId_;
public boolean hasSourceZephyrId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getSourceZephyrId() {
return sourceZephyrId_;
}
public static final int DESTINATION_ZEPHYR_ID_FIELD_NUMBER = 2;
private int destinationZephyrId_;
public boolean hasDestinationZephyrId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getDestinationZephyrId() {
return destinationZephyrId_;
}
private void initFields() {
sourceZephyrId_ = 0;
destinationZephyrId_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasSourceZephyrId()) {
mii = 0;
return false;
}
if (!hasDestinationZephyrId()) {
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
output.writeUInt32(1, sourceZephyrId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt32(2, destinationZephyrId_);
}
getUnknownFields().writeTo(output);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeUInt32Size(1, sourceZephyrId_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt32Size(2, destinationZephyrId_);
}
size += getUnknownFields().getSerializedSize();
mss = size;
return size;
}
private static final long serialVersionUID = 0L;
@Override
protected Object writeReplace()
throws java.io.ObjectStreamException {
return super.writeReplace();
}
public static Zephyr.ZephyrHandshake parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Zephyr.ZephyrHandshake parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Zephyr.ZephyrHandshake parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Zephyr.ZephyrHandshake parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Zephyr.ZephyrHandshake parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Zephyr.ZephyrHandshake parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Zephyr.ZephyrHandshake parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Zephyr.ZephyrHandshake parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Zephyr.ZephyrHandshake parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Zephyr.ZephyrHandshake parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Zephyr.ZephyrHandshake prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
@Override
protected Builder newBuilderForType(
GeneratedMessage.BuilderParent parent) {
Builder bd = new Builder(parent);
return bd;
}
public static final class Builder extends
GeneratedMessage.Builder<Builder> implements
Zephyr.ZephyrHandshakeOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Zephyr.internal_static_ZephyrHandshake_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Zephyr.internal_static_ZephyrHandshake_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Zephyr.ZephyrHandshake.class, Zephyr.ZephyrHandshake.Builder.class);
}
private Builder() {
maybeForceBuilderInitialization();
}
private Builder(
GeneratedMessage.BuilderParent parent) {
super(parent);
maybeForceBuilderInitialization();
}
private void maybeForceBuilderInitialization() {
if (GeneratedMessage.alwaysUseFieldBuilders) {
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
sourceZephyrId_ = 0;
b0_ = (b0_ & ~0x00000001);
destinationZephyrId_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Zephyr.internal_static_ZephyrHandshake_descriptor;
}
public Zephyr.ZephyrHandshake getDefaultInstanceForType() {
return Zephyr.ZephyrHandshake.getDefaultInstance();
}
public Zephyr.ZephyrHandshake build() {
Zephyr.ZephyrHandshake result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Zephyr.ZephyrHandshake buildPartial() {
Zephyr.ZephyrHandshake result = new Zephyr.ZephyrHandshake(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.sourceZephyrId_ = sourceZephyrId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.destinationZephyrId_ = destinationZephyrId_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Zephyr.ZephyrHandshake) {
return mergeFrom((Zephyr.ZephyrHandshake)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Zephyr.ZephyrHandshake other) {
if (other == Zephyr.ZephyrHandshake.getDefaultInstance()) return this;
if (other.hasSourceZephyrId()) {
setSourceZephyrId(other.getSourceZephyrId());
}
if (other.hasDestinationZephyrId()) {
setDestinationZephyrId(other.getDestinationZephyrId());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasSourceZephyrId()) {
return false;
}
if (!hasDestinationZephyrId()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Zephyr.ZephyrHandshake pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Zephyr.ZephyrHandshake) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int sourceZephyrId_ ;
public boolean hasSourceZephyrId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getSourceZephyrId() {
return sourceZephyrId_;
}
public Builder setSourceZephyrId(int value) {
b0_ |= 0x00000001;
sourceZephyrId_ = value;
onChanged();
return this;
}
public Builder clearSourceZephyrId() {
b0_ = (b0_ & ~0x00000001);
sourceZephyrId_ = 0;
onChanged();
return this;
}
private int destinationZephyrId_ ;
public boolean hasDestinationZephyrId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public int getDestinationZephyrId() {
return destinationZephyrId_;
}
public Builder setDestinationZephyrId(int value) {
b0_ |= 0x00000002;
destinationZephyrId_ = value;
onChanged();
return this;
}
public Builder clearDestinationZephyrId() {
b0_ = (b0_ & ~0x00000002);
destinationZephyrId_ = 0;
onChanged();
return this;
}
}
static {
defaultInstance = new ZephyrHandshake(true);
defaultInstance.initFields();
}
}
private static final Descriptors.Descriptor
internal_static_ZephyrControlMessage_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_ZephyrControlMessage_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_ZephyrHandshake_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_ZephyrHandshake_fieldAccessorTable;
public static Descriptors.FileDescriptor
getDescriptor() {
return descriptor;
}
private static Descriptors.FileDescriptor
descriptor;
static {
String[] descriptorData = {
"\n\014zephyr.proto\"|\n\024ZephyrControlMessage\022(" +
"\n\004type\030\001 \002(\0162\032.ZephyrControlMessage.Type" +
"\022#\n\thandshake\030\002 \001(\0132\020.ZephyrHandshake\"\025\n" +
"\004Type\022\r\n\tHANDSHAKE\020\000\"J\n\017ZephyrHandshake\022" +
"\030\n\020source_zephyr_id\030\001 \002(\r\022\035\n\025destination" +
"_zephyr_id\030\002 \002(\rB\031\n\027com.aerofs.zephyr.pr" +
"oto"
};
Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
new Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
public ExtensionRegistry assignDescriptors(
Descriptors.FileDescriptor root) {
descriptor = root;
return null;
}
};
Descriptors.FileDescriptor
.internalBuildGeneratedFileFrom(descriptorData,
new Descriptors.FileDescriptor[] {
}, assigner);
internal_static_ZephyrControlMessage_descriptor =
getDescriptor().getMessageTypes().get(0);
internal_static_ZephyrControlMessage_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_ZephyrControlMessage_descriptor,
new String[] { "Type", "Handshake", });
internal_static_ZephyrHandshake_descriptor =
getDescriptor().getMessageTypes().get(1);
internal_static_ZephyrHandshake_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_ZephyrHandshake_descriptor,
new String[] { "SourceZephyrId", "DestinationZephyrId", });
}
}
