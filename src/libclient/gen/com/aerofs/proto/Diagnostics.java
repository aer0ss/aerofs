package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Diagnostics {
private Diagnostics() {}
public static void registerAllExtensions(
ExtensionRegistry registry) {
}
public enum ChannelState
implements ProtocolMessageEnum {
CONNECTING(0, 1),
VERIFIED(1, 2),
CLOSED(2, 3),
;
public static final int CONNECTING_VALUE = 1;
public static final int VERIFIED_VALUE = 2;
public static final int CLOSED_VALUE = 3;
public final int getNumber() { return value; }
public static ChannelState valueOf(int value) {
switch (value) {
case 1: return CONNECTING;
case 2: return VERIFIED;
case 3: return CLOSED;
default: return null;
}
}
public static Internal.EnumLiteMap<ChannelState>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<ChannelState>
internalValueMap =
new Internal.EnumLiteMap<ChannelState>() {
public ChannelState findValueByNumber(int number) {
return ChannelState.valueOf(number);
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
return Diagnostics.getDescriptor().getEnumTypes().get(0);
}
private static final ChannelState[] VALUES = values();
public static ChannelState valueOf(
Descriptors.EnumValueDescriptor desc) {
if (desc.getType() != getDescriptor()) {
throw new IllegalArgumentException(
"EnumValueDescriptor is not for this type.");
}
return VALUES[desc.getIndex()];
}
private final int index;
private final int value;
private ChannelState(int index, int value) {
this.index = index;
this.value = value;
}
}
public interface PBDumpStatOrBuilder extends
MessageOrBuilder {
boolean hasUpTime();
long getUpTime();
ProtocolStringList
getEnabledTransportsList();
int getEnabledTransportsCount();
String getEnabledTransports(int index);
ByteString
getEnabledTransportsBytes(int index);
List<Diagnostics.PBDumpStat.PBTransport> 
getTransportList();
Diagnostics.PBDumpStat.PBTransport getTransport(int index);
int getTransportCount();
List<? extends Diagnostics.PBDumpStat.PBTransportOrBuilder> 
getTransportOrBuilderList();
Diagnostics.PBDumpStat.PBTransportOrBuilder getTransportOrBuilder(
int index);
boolean hasMisc();
String getMisc();
ByteString
getMiscBytes();
}
public static final class PBDumpStat extends
GeneratedMessage implements
PBDumpStatOrBuilder {
private PBDumpStat(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBDumpStat(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final PBDumpStat defaultInstance;
public static PBDumpStat getDefaultInstance() {
return defaultInstance;
}
public PBDumpStat getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private PBDumpStat(
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
upTime_ = input.readUInt64();
break;
}
case 18: {
ByteString bs = input.readBytes();
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
enabledTransports_ = new LazyStringArrayList();
mutable_b0_ |= 0x00000002;
}
enabledTransports_.add(bs);
break;
}
case 26: {
if (!((mutable_b0_ & 0x00000004) == 0x00000004)) {
transport_ = new ArrayList<Diagnostics.PBDumpStat.PBTransport>();
mutable_b0_ |= 0x00000004;
}
transport_.add(input.readMessage(Diagnostics.PBDumpStat.PBTransport.PARSER, er));
break;
}
case 122: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
misc_ = bs;
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
enabledTransports_ = enabledTransports_.getUnmodifiableView();
}
if (((mutable_b0_ & 0x00000004) == 0x00000004)) {
transport_ = Collections.unmodifiableList(transport_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_PBDumpStat_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_PBDumpStat_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.PBDumpStat.class, Diagnostics.PBDumpStat.Builder.class);
}
public static Parser<PBDumpStat> PARSER =
new AbstractParser<PBDumpStat>() {
public PBDumpStat parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBDumpStat(input, er);
}
};
@Override
public Parser<PBDumpStat> getParserForType() {
return PARSER;
}
public interface PBTransportOrBuilder extends
MessageOrBuilder {
boolean hasName();
String getName();
ByteString
getNameBytes();
boolean hasBytesIn();
long getBytesIn();
boolean hasBytesOut();
long getBytesOut();
ProtocolStringList
getConnectionList();
int getConnectionCount();
String getConnection(int index);
ByteString
getConnectionBytes(int index);
boolean hasDiagnosis();
String getDiagnosis();
ByteString
getDiagnosisBytes();
}
public static final class PBTransport extends
GeneratedMessage implements
PBTransportOrBuilder {
private PBTransport(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBTransport(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final PBTransport defaultInstance;
public static PBTransport getDefaultInstance() {
return defaultInstance;
}
public PBTransport getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private PBTransport(
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
case 10: {
ByteString bs = input.readBytes();
b0_ |= 0x00000001;
name_ = bs;
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
case 34: {
ByteString bs = input.readBytes();
if (!((mutable_b0_ & 0x00000008) == 0x00000008)) {
connection_ = new LazyStringArrayList();
mutable_b0_ |= 0x00000008;
}
connection_.add(bs);
break;
}
case 42: {
ByteString bs = input.readBytes();
b0_ |= 0x00000008;
diagnosis_ = bs;
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
if (((mutable_b0_ & 0x00000008) == 0x00000008)) {
connection_ = connection_.getUnmodifiableView();
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_PBDumpStat_PBTransport_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_PBDumpStat_PBTransport_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.PBDumpStat.PBTransport.class, Diagnostics.PBDumpStat.PBTransport.Builder.class);
}
public static Parser<PBTransport> PARSER =
new AbstractParser<PBTransport>() {
public PBTransport parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBTransport(input, er);
}
};
@Override
public Parser<PBTransport> getParserForType() {
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
public static final int CONNECTION_FIELD_NUMBER = 4;
private LazyStringList connection_;
public ProtocolStringList
getConnectionList() {
return connection_;
}
public int getConnectionCount() {
return connection_.size();
}
public String getConnection(int index) {
return connection_.get(index);
}
public ByteString
getConnectionBytes(int index) {
return connection_.getByteString(index);
}
public static final int DIAGNOSIS_FIELD_NUMBER = 5;
private Object diagnosis_;
public boolean hasDiagnosis() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public String getDiagnosis() {
Object ref = diagnosis_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
diagnosis_ = s;
}
return s;
}
}
public ByteString
getDiagnosisBytes() {
Object ref = diagnosis_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
diagnosis_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
name_ = "";
bytesIn_ = 0L;
bytesOut_ = 0L;
connection_ = LazyStringArrayList.EMPTY;
diagnosis_ = "";
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
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, getNameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, bytesIn_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, bytesOut_);
}
for (int i = 0; i < connection_.size(); i++) {
output.writeBytes(4, connection_.getByteString(i));
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeBytes(5, getDiagnosisBytes());
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
.computeBytesSize(1, getNameBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, bytesIn_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, bytesOut_);
}
{
int dataSize = 0;
for (int i = 0; i < connection_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(connection_.getByteString(i));
}
size += dataSize;
size += 1 * getConnectionList().size();
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeBytesSize(5, getDiagnosisBytes());
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
public static Diagnostics.PBDumpStat.PBTransport parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.PBDumpStat.PBTransport parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.PBDumpStat.PBTransport parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.PBDumpStat.PBTransport parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.PBDumpStat.PBTransport prototype) {
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
Diagnostics.PBDumpStat.PBTransportOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_PBDumpStat_PBTransport_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_PBDumpStat_PBTransport_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.PBDumpStat.PBTransport.class, Diagnostics.PBDumpStat.PBTransport.Builder.class);
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
name_ = "";
b0_ = (b0_ & ~0x00000001);
bytesIn_ = 0L;
b0_ = (b0_ & ~0x00000002);
bytesOut_ = 0L;
b0_ = (b0_ & ~0x00000004);
connection_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000008);
diagnosis_ = "";
b0_ = (b0_ & ~0x00000010);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_PBDumpStat_PBTransport_descriptor;
}
public Diagnostics.PBDumpStat.PBTransport getDefaultInstanceForType() {
return Diagnostics.PBDumpStat.PBTransport.getDefaultInstance();
}
public Diagnostics.PBDumpStat.PBTransport build() {
Diagnostics.PBDumpStat.PBTransport result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.PBDumpStat.PBTransport buildPartial() {
Diagnostics.PBDumpStat.PBTransport result = new Diagnostics.PBDumpStat.PBTransport(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.name_ = name_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.bytesIn_ = bytesIn_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.bytesOut_ = bytesOut_;
if (((b0_ & 0x00000008) == 0x00000008)) {
connection_ = connection_.getUnmodifiableView();
b0_ = (b0_ & ~0x00000008);
}
result.connection_ = connection_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000008;
}
result.diagnosis_ = diagnosis_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.PBDumpStat.PBTransport) {
return mergeFrom((Diagnostics.PBDumpStat.PBTransport)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.PBDumpStat.PBTransport other) {
if (other == Diagnostics.PBDumpStat.PBTransport.getDefaultInstance()) return this;
if (other.hasName()) {
b0_ |= 0x00000001;
name_ = other.name_;
onChanged();
}
if (other.hasBytesIn()) {
setBytesIn(other.getBytesIn());
}
if (other.hasBytesOut()) {
setBytesOut(other.getBytesOut());
}
if (!other.connection_.isEmpty()) {
if (connection_.isEmpty()) {
connection_ = other.connection_;
b0_ = (b0_ & ~0x00000008);
} else {
ensureConnectionIsMutable();
connection_.addAll(other.connection_);
}
onChanged();
}
if (other.hasDiagnosis()) {
b0_ |= 0x00000010;
diagnosis_ = other.diagnosis_;
onChanged();
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.PBDumpStat.PBTransport pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.PBDumpStat.PBTransport) e.getUnfinishedMessage();
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
onChanged();
return this;
}
public Builder clearName() {
b0_ = (b0_ & ~0x00000001);
name_ = getDefaultInstance().getName();
onChanged();
return this;
}
public Builder setNameBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
name_ = value;
onChanged();
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
onChanged();
return this;
}
public Builder clearBytesIn() {
b0_ = (b0_ & ~0x00000002);
bytesIn_ = 0L;
onChanged();
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
onChanged();
return this;
}
public Builder clearBytesOut() {
b0_ = (b0_ & ~0x00000004);
bytesOut_ = 0L;
onChanged();
return this;
}
private LazyStringList connection_ = LazyStringArrayList.EMPTY;
private void ensureConnectionIsMutable() {
if (!((b0_ & 0x00000008) == 0x00000008)) {
connection_ = new LazyStringArrayList(connection_);
b0_ |= 0x00000008;
}
}
public ProtocolStringList
getConnectionList() {
return connection_.getUnmodifiableView();
}
public int getConnectionCount() {
return connection_.size();
}
public String getConnection(int index) {
return connection_.get(index);
}
public ByteString
getConnectionBytes(int index) {
return connection_.getByteString(index);
}
public Builder setConnection(
int index, String value) {
if (value == null) {
throw new NullPointerException();
}
ensureConnectionIsMutable();
connection_.set(index, value);
onChanged();
return this;
}
public Builder addConnection(
String value) {
if (value == null) {
throw new NullPointerException();
}
ensureConnectionIsMutable();
connection_.add(value);
onChanged();
return this;
}
public Builder addAllConnection(
Iterable<String> values) {
ensureConnectionIsMutable();
AbstractMessageLite.Builder.addAll(
values, connection_);
onChanged();
return this;
}
public Builder clearConnection() {
connection_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000008);
onChanged();
return this;
}
public Builder addConnectionBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureConnectionIsMutable();
connection_.add(value);
onChanged();
return this;
}
private Object diagnosis_ = "";
public boolean hasDiagnosis() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public String getDiagnosis() {
Object ref = diagnosis_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
diagnosis_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getDiagnosisBytes() {
Object ref = diagnosis_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
diagnosis_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setDiagnosis(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000010;
diagnosis_ = value;
onChanged();
return this;
}
public Builder clearDiagnosis() {
b0_ = (b0_ & ~0x00000010);
diagnosis_ = getDefaultInstance().getDiagnosis();
onChanged();
return this;
}
public Builder setDiagnosisBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000010;
diagnosis_ = value;
onChanged();
return this;
}
}
static {
defaultInstance = new PBTransport(true);
defaultInstance.initFields();
}
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
public static final int ENABLED_TRANSPORTS_FIELD_NUMBER = 2;
private LazyStringList enabledTransports_;
public ProtocolStringList
getEnabledTransportsList() {
return enabledTransports_;
}
public int getEnabledTransportsCount() {
return enabledTransports_.size();
}
public String getEnabledTransports(int index) {
return enabledTransports_.get(index);
}
public ByteString
getEnabledTransportsBytes(int index) {
return enabledTransports_.getByteString(index);
}
public static final int TRANSPORT_FIELD_NUMBER = 3;
private List<Diagnostics.PBDumpStat.PBTransport> transport_;
public List<Diagnostics.PBDumpStat.PBTransport> getTransportList() {
return transport_;
}
public List<? extends Diagnostics.PBDumpStat.PBTransportOrBuilder> 
getTransportOrBuilderList() {
return transport_;
}
public int getTransportCount() {
return transport_.size();
}
public Diagnostics.PBDumpStat.PBTransport getTransport(int index) {
return transport_.get(index);
}
public Diagnostics.PBDumpStat.PBTransportOrBuilder getTransportOrBuilder(
int index) {
return transport_.get(index);
}
public static final int MISC_FIELD_NUMBER = 15;
private Object misc_;
public boolean hasMisc() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getMisc() {
Object ref = misc_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
misc_ = s;
}
return s;
}
}
public ByteString
getMiscBytes() {
Object ref = misc_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
misc_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
upTime_ = 0L;
enabledTransports_ = LazyStringArrayList.EMPTY;
transport_ = Collections.emptyList();
misc_ = "";
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
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeUInt64(1, upTime_);
}
for (int i = 0; i < enabledTransports_.size(); i++) {
output.writeBytes(2, enabledTransports_.getByteString(i));
}
for (int i = 0; i < transport_.size(); i++) {
output.writeMessage(3, transport_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(15, getMiscBytes());
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
.computeUInt64Size(1, upTime_);
}
{
int dataSize = 0;
for (int i = 0; i < enabledTransports_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(enabledTransports_.getByteString(i));
}
size += dataSize;
size += 1 * getEnabledTransportsList().size();
}
for (int i = 0; i < transport_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(3, transport_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(15, getMiscBytes());
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
public static Diagnostics.PBDumpStat parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.PBDumpStat parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.PBDumpStat parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.PBDumpStat parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.PBDumpStat parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.PBDumpStat parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.PBDumpStat parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.PBDumpStat parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.PBDumpStat parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.PBDumpStat parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.PBDumpStat prototype) {
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
Diagnostics.PBDumpStatOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_PBDumpStat_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_PBDumpStat_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.PBDumpStat.class, Diagnostics.PBDumpStat.Builder.class);
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
getTransportFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
upTime_ = 0L;
b0_ = (b0_ & ~0x00000001);
enabledTransports_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000002);
if (transportBuilder_ == null) {
transport_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
} else {
transportBuilder_.clear();
}
misc_ = "";
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_PBDumpStat_descriptor;
}
public Diagnostics.PBDumpStat getDefaultInstanceForType() {
return Diagnostics.PBDumpStat.getDefaultInstance();
}
public Diagnostics.PBDumpStat build() {
Diagnostics.PBDumpStat result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.PBDumpStat buildPartial() {
Diagnostics.PBDumpStat result = new Diagnostics.PBDumpStat(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.upTime_ = upTime_;
if (((b0_ & 0x00000002) == 0x00000002)) {
enabledTransports_ = enabledTransports_.getUnmodifiableView();
b0_ = (b0_ & ~0x00000002);
}
result.enabledTransports_ = enabledTransports_;
if (transportBuilder_ == null) {
if (((b0_ & 0x00000004) == 0x00000004)) {
transport_ = Collections.unmodifiableList(transport_);
b0_ = (b0_ & ~0x00000004);
}
result.transport_ = transport_;
} else {
result.transport_ = transportBuilder_.build();
}
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000002;
}
result.misc_ = misc_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.PBDumpStat) {
return mergeFrom((Diagnostics.PBDumpStat)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.PBDumpStat other) {
if (other == Diagnostics.PBDumpStat.getDefaultInstance()) return this;
if (other.hasUpTime()) {
setUpTime(other.getUpTime());
}
if (!other.enabledTransports_.isEmpty()) {
if (enabledTransports_.isEmpty()) {
enabledTransports_ = other.enabledTransports_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureEnabledTransportsIsMutable();
enabledTransports_.addAll(other.enabledTransports_);
}
onChanged();
}
if (transportBuilder_ == null) {
if (!other.transport_.isEmpty()) {
if (transport_.isEmpty()) {
transport_ = other.transport_;
b0_ = (b0_ & ~0x00000004);
} else {
ensureTransportIsMutable();
transport_.addAll(other.transport_);
}
onChanged();
}
} else {
if (!other.transport_.isEmpty()) {
if (transportBuilder_.isEmpty()) {
transportBuilder_.dispose();
transportBuilder_ = null;
transport_ = other.transport_;
b0_ = (b0_ & ~0x00000004);
transportBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getTransportFieldBuilder() : null;
} else {
transportBuilder_.addAllMessages(other.transport_);
}
}
}
if (other.hasMisc()) {
b0_ |= 0x00000008;
misc_ = other.misc_;
onChanged();
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.PBDumpStat pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.PBDumpStat) e.getUnfinishedMessage();
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
onChanged();
return this;
}
public Builder clearUpTime() {
b0_ = (b0_ & ~0x00000001);
upTime_ = 0L;
onChanged();
return this;
}
private LazyStringList enabledTransports_ = LazyStringArrayList.EMPTY;
private void ensureEnabledTransportsIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
enabledTransports_ = new LazyStringArrayList(enabledTransports_);
b0_ |= 0x00000002;
}
}
public ProtocolStringList
getEnabledTransportsList() {
return enabledTransports_.getUnmodifiableView();
}
public int getEnabledTransportsCount() {
return enabledTransports_.size();
}
public String getEnabledTransports(int index) {
return enabledTransports_.get(index);
}
public ByteString
getEnabledTransportsBytes(int index) {
return enabledTransports_.getByteString(index);
}
public Builder setEnabledTransports(
int index, String value) {
if (value == null) {
throw new NullPointerException();
}
ensureEnabledTransportsIsMutable();
enabledTransports_.set(index, value);
onChanged();
return this;
}
public Builder addEnabledTransports(
String value) {
if (value == null) {
throw new NullPointerException();
}
ensureEnabledTransportsIsMutable();
enabledTransports_.add(value);
onChanged();
return this;
}
public Builder addAllEnabledTransports(
Iterable<String> values) {
ensureEnabledTransportsIsMutable();
AbstractMessageLite.Builder.addAll(
values, enabledTransports_);
onChanged();
return this;
}
public Builder clearEnabledTransports() {
enabledTransports_ = LazyStringArrayList.EMPTY;
b0_ = (b0_ & ~0x00000002);
onChanged();
return this;
}
public Builder addEnabledTransportsBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureEnabledTransportsIsMutable();
enabledTransports_.add(value);
onChanged();
return this;
}
private List<Diagnostics.PBDumpStat.PBTransport> transport_ =
Collections.emptyList();
private void ensureTransportIsMutable() {
if (!((b0_ & 0x00000004) == 0x00000004)) {
transport_ = new ArrayList<Diagnostics.PBDumpStat.PBTransport>(transport_);
b0_ |= 0x00000004;
}
}
private RepeatedFieldBuilder<
Diagnostics.PBDumpStat.PBTransport, Diagnostics.PBDumpStat.PBTransport.Builder, Diagnostics.PBDumpStat.PBTransportOrBuilder> transportBuilder_;
public List<Diagnostics.PBDumpStat.PBTransport> getTransportList() {
if (transportBuilder_ == null) {
return Collections.unmodifiableList(transport_);
} else {
return transportBuilder_.getMessageList();
}
}
public int getTransportCount() {
if (transportBuilder_ == null) {
return transport_.size();
} else {
return transportBuilder_.getCount();
}
}
public Diagnostics.PBDumpStat.PBTransport getTransport(int index) {
if (transportBuilder_ == null) {
return transport_.get(index);
} else {
return transportBuilder_.getMessage(index);
}
}
public Builder setTransport(
int index, Diagnostics.PBDumpStat.PBTransport value) {
if (transportBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransportIsMutable();
transport_.set(index, value);
onChanged();
} else {
transportBuilder_.setMessage(index, value);
}
return this;
}
public Builder setTransport(
int index, Diagnostics.PBDumpStat.PBTransport.Builder bdForValue) {
if (transportBuilder_ == null) {
ensureTransportIsMutable();
transport_.set(index, bdForValue.build());
onChanged();
} else {
transportBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addTransport(Diagnostics.PBDumpStat.PBTransport value) {
if (transportBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransportIsMutable();
transport_.add(value);
onChanged();
} else {
transportBuilder_.addMessage(value);
}
return this;
}
public Builder addTransport(
int index, Diagnostics.PBDumpStat.PBTransport value) {
if (transportBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransportIsMutable();
transport_.add(index, value);
onChanged();
} else {
transportBuilder_.addMessage(index, value);
}
return this;
}
public Builder addTransport(
Diagnostics.PBDumpStat.PBTransport.Builder bdForValue) {
if (transportBuilder_ == null) {
ensureTransportIsMutable();
transport_.add(bdForValue.build());
onChanged();
} else {
transportBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addTransport(
int index, Diagnostics.PBDumpStat.PBTransport.Builder bdForValue) {
if (transportBuilder_ == null) {
ensureTransportIsMutable();
transport_.add(index, bdForValue.build());
onChanged();
} else {
transportBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllTransport(
Iterable<? extends Diagnostics.PBDumpStat.PBTransport> values) {
if (transportBuilder_ == null) {
ensureTransportIsMutable();
AbstractMessageLite.Builder.addAll(
values, transport_);
onChanged();
} else {
transportBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearTransport() {
if (transportBuilder_ == null) {
transport_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
onChanged();
} else {
transportBuilder_.clear();
}
return this;
}
public Builder removeTransport(int index) {
if (transportBuilder_ == null) {
ensureTransportIsMutable();
transport_.remove(index);
onChanged();
} else {
transportBuilder_.remove(index);
}
return this;
}
public Diagnostics.PBDumpStat.PBTransport.Builder getTransportBuilder(
int index) {
return getTransportFieldBuilder().getBuilder(index);
}
public Diagnostics.PBDumpStat.PBTransportOrBuilder getTransportOrBuilder(
int index) {
if (transportBuilder_ == null) {
return transport_.get(index);  } else {
return transportBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.PBDumpStat.PBTransportOrBuilder> 
getTransportOrBuilderList() {
if (transportBuilder_ != null) {
return transportBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(transport_);
}
}
public Diagnostics.PBDumpStat.PBTransport.Builder addTransportBuilder() {
return getTransportFieldBuilder().addBuilder(
Diagnostics.PBDumpStat.PBTransport.getDefaultInstance());
}
public Diagnostics.PBDumpStat.PBTransport.Builder addTransportBuilder(
int index) {
return getTransportFieldBuilder().addBuilder(
index, Diagnostics.PBDumpStat.PBTransport.getDefaultInstance());
}
public List<Diagnostics.PBDumpStat.PBTransport.Builder> 
getTransportBuilderList() {
return getTransportFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.PBDumpStat.PBTransport, Diagnostics.PBDumpStat.PBTransport.Builder, Diagnostics.PBDumpStat.PBTransportOrBuilder> 
getTransportFieldBuilder() {
if (transportBuilder_ == null) {
transportBuilder_ = new RepeatedFieldBuilder<
Diagnostics.PBDumpStat.PBTransport, Diagnostics.PBDumpStat.PBTransport.Builder, Diagnostics.PBDumpStat.PBTransportOrBuilder>(
transport_,
((b0_ & 0x00000004) == 0x00000004),
getParentForChildren(),
isClean());
transport_ = null;
}
return transportBuilder_;
}
private Object misc_ = "";
public boolean hasMisc() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public String getMisc() {
Object ref = misc_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
misc_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getMiscBytes() {
Object ref = misc_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
misc_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setMisc(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
misc_ = value;
onChanged();
return this;
}
public Builder clearMisc() {
b0_ = (b0_ & ~0x00000008);
misc_ = getDefaultInstance().getMisc();
onChanged();
return this;
}
public Builder setMiscBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000008;
misc_ = value;
onChanged();
return this;
}
}
static {
defaultInstance = new PBDumpStat(true);
defaultInstance.initFields();
}
}
public interface DeviceDiagnosticsOrBuilder extends
MessageOrBuilder {
List<Diagnostics.Store> 
getAvailableStoresList();
Diagnostics.Store getAvailableStores(int index);
int getAvailableStoresCount();
List<? extends Diagnostics.StoreOrBuilder> 
getAvailableStoresOrBuilderList();
Diagnostics.StoreOrBuilder getAvailableStoresOrBuilder(
int index);
List<Diagnostics.Store> 
getUnavailableStoresList();
Diagnostics.Store getUnavailableStores(int index);
int getUnavailableStoresCount();
List<? extends Diagnostics.StoreOrBuilder> 
getUnavailableStoresOrBuilderList();
Diagnostics.StoreOrBuilder getUnavailableStoresOrBuilder(
int index);
List<Diagnostics.Device> 
getDevicesList();
Diagnostics.Device getDevices(int index);
int getDevicesCount();
List<? extends Diagnostics.DeviceOrBuilder> 
getDevicesOrBuilderList();
Diagnostics.DeviceOrBuilder getDevicesOrBuilder(
int index);
}
public static final class DeviceDiagnostics extends
GeneratedMessage implements
DeviceDiagnosticsOrBuilder {
private DeviceDiagnostics(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private DeviceDiagnostics(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final DeviceDiagnostics defaultInstance;
public static DeviceDiagnostics getDefaultInstance() {
return defaultInstance;
}
public DeviceDiagnostics getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private DeviceDiagnostics(
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
case 10: {
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
availableStores_ = new ArrayList<Diagnostics.Store>();
mutable_b0_ |= 0x00000001;
}
availableStores_.add(input.readMessage(Diagnostics.Store.PARSER, er));
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
unavailableStores_ = new ArrayList<Diagnostics.Store>();
mutable_b0_ |= 0x00000002;
}
unavailableStores_.add(input.readMessage(Diagnostics.Store.PARSER, er));
break;
}
case 26: {
if (!((mutable_b0_ & 0x00000004) == 0x00000004)) {
devices_ = new ArrayList<Diagnostics.Device>();
mutable_b0_ |= 0x00000004;
}
devices_.add(input.readMessage(Diagnostics.Device.PARSER, er));
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
availableStores_ = Collections.unmodifiableList(availableStores_);
}
if (((mutable_b0_ & 0x00000002) == 0x00000002)) {
unavailableStores_ = Collections.unmodifiableList(unavailableStores_);
}
if (((mutable_b0_ & 0x00000004) == 0x00000004)) {
devices_ = Collections.unmodifiableList(devices_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_DeviceDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_DeviceDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.DeviceDiagnostics.class, Diagnostics.DeviceDiagnostics.Builder.class);
}
public static Parser<DeviceDiagnostics> PARSER =
new AbstractParser<DeviceDiagnostics>() {
public DeviceDiagnostics parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new DeviceDiagnostics(input, er);
}
};
@Override
public Parser<DeviceDiagnostics> getParserForType() {
return PARSER;
}
public static final int AVAILABLE_STORES_FIELD_NUMBER = 1;
private List<Diagnostics.Store> availableStores_;
public List<Diagnostics.Store> getAvailableStoresList() {
return availableStores_;
}
public List<? extends Diagnostics.StoreOrBuilder> 
getAvailableStoresOrBuilderList() {
return availableStores_;
}
public int getAvailableStoresCount() {
return availableStores_.size();
}
public Diagnostics.Store getAvailableStores(int index) {
return availableStores_.get(index);
}
public Diagnostics.StoreOrBuilder getAvailableStoresOrBuilder(
int index) {
return availableStores_.get(index);
}
public static final int UNAVAILABLE_STORES_FIELD_NUMBER = 2;
private List<Diagnostics.Store> unavailableStores_;
public List<Diagnostics.Store> getUnavailableStoresList() {
return unavailableStores_;
}
public List<? extends Diagnostics.StoreOrBuilder> 
getUnavailableStoresOrBuilderList() {
return unavailableStores_;
}
public int getUnavailableStoresCount() {
return unavailableStores_.size();
}
public Diagnostics.Store getUnavailableStores(int index) {
return unavailableStores_.get(index);
}
public Diagnostics.StoreOrBuilder getUnavailableStoresOrBuilder(
int index) {
return unavailableStores_.get(index);
}
public static final int DEVICES_FIELD_NUMBER = 3;
private List<Diagnostics.Device> devices_;
public List<Diagnostics.Device> getDevicesList() {
return devices_;
}
public List<? extends Diagnostics.DeviceOrBuilder> 
getDevicesOrBuilderList() {
return devices_;
}
public int getDevicesCount() {
return devices_.size();
}
public Diagnostics.Device getDevices(int index) {
return devices_.get(index);
}
public Diagnostics.DeviceOrBuilder getDevicesOrBuilder(
int index) {
return devices_.get(index);
}
private void initFields() {
availableStores_ = Collections.emptyList();
unavailableStores_ = Collections.emptyList();
devices_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getAvailableStoresCount(); i++) {
if (!getAvailableStores(i).isInitialized()) {
mii = 0;
return false;
}
}
for (int i = 0; i < getUnavailableStoresCount(); i++) {
if (!getUnavailableStores(i).isInitialized()) {
mii = 0;
return false;
}
}
for (int i = 0; i < getDevicesCount(); i++) {
if (!getDevices(i).isInitialized()) {
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
for (int i = 0; i < availableStores_.size(); i++) {
output.writeMessage(1, availableStores_.get(i));
}
for (int i = 0; i < unavailableStores_.size(); i++) {
output.writeMessage(2, unavailableStores_.get(i));
}
for (int i = 0; i < devices_.size(); i++) {
output.writeMessage(3, devices_.get(i));
}
getUnknownFields().writeTo(output);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < availableStores_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, availableStores_.get(i));
}
for (int i = 0; i < unavailableStores_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, unavailableStores_.get(i));
}
for (int i = 0; i < devices_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(3, devices_.get(i));
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
public static Diagnostics.DeviceDiagnostics parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.DeviceDiagnostics parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.DeviceDiagnostics parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.DeviceDiagnostics parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.DeviceDiagnostics parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.DeviceDiagnostics parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.DeviceDiagnostics parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.DeviceDiagnostics parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.DeviceDiagnostics parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.DeviceDiagnostics parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.DeviceDiagnostics prototype) {
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
Diagnostics.DeviceDiagnosticsOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_DeviceDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_DeviceDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.DeviceDiagnostics.class, Diagnostics.DeviceDiagnostics.Builder.class);
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
getAvailableStoresFieldBuilder();
getUnavailableStoresFieldBuilder();
getDevicesFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (availableStoresBuilder_ == null) {
availableStores_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
} else {
availableStoresBuilder_.clear();
}
if (unavailableStoresBuilder_ == null) {
unavailableStores_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
} else {
unavailableStoresBuilder_.clear();
}
if (devicesBuilder_ == null) {
devices_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
} else {
devicesBuilder_.clear();
}
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_DeviceDiagnostics_descriptor;
}
public Diagnostics.DeviceDiagnostics getDefaultInstanceForType() {
return Diagnostics.DeviceDiagnostics.getDefaultInstance();
}
public Diagnostics.DeviceDiagnostics build() {
Diagnostics.DeviceDiagnostics result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.DeviceDiagnostics buildPartial() {
Diagnostics.DeviceDiagnostics result = new Diagnostics.DeviceDiagnostics(this);
int from_b0_ = b0_;
if (availableStoresBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001)) {
availableStores_ = Collections.unmodifiableList(availableStores_);
b0_ = (b0_ & ~0x00000001);
}
result.availableStores_ = availableStores_;
} else {
result.availableStores_ = availableStoresBuilder_.build();
}
if (unavailableStoresBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002)) {
unavailableStores_ = Collections.unmodifiableList(unavailableStores_);
b0_ = (b0_ & ~0x00000002);
}
result.unavailableStores_ = unavailableStores_;
} else {
result.unavailableStores_ = unavailableStoresBuilder_.build();
}
if (devicesBuilder_ == null) {
if (((b0_ & 0x00000004) == 0x00000004)) {
devices_ = Collections.unmodifiableList(devices_);
b0_ = (b0_ & ~0x00000004);
}
result.devices_ = devices_;
} else {
result.devices_ = devicesBuilder_.build();
}
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.DeviceDiagnostics) {
return mergeFrom((Diagnostics.DeviceDiagnostics)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.DeviceDiagnostics other) {
if (other == Diagnostics.DeviceDiagnostics.getDefaultInstance()) return this;
if (availableStoresBuilder_ == null) {
if (!other.availableStores_.isEmpty()) {
if (availableStores_.isEmpty()) {
availableStores_ = other.availableStores_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureAvailableStoresIsMutable();
availableStores_.addAll(other.availableStores_);
}
onChanged();
}
} else {
if (!other.availableStores_.isEmpty()) {
if (availableStoresBuilder_.isEmpty()) {
availableStoresBuilder_.dispose();
availableStoresBuilder_ = null;
availableStores_ = other.availableStores_;
b0_ = (b0_ & ~0x00000001);
availableStoresBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getAvailableStoresFieldBuilder() : null;
} else {
availableStoresBuilder_.addAllMessages(other.availableStores_);
}
}
}
if (unavailableStoresBuilder_ == null) {
if (!other.unavailableStores_.isEmpty()) {
if (unavailableStores_.isEmpty()) {
unavailableStores_ = other.unavailableStores_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureUnavailableStoresIsMutable();
unavailableStores_.addAll(other.unavailableStores_);
}
onChanged();
}
} else {
if (!other.unavailableStores_.isEmpty()) {
if (unavailableStoresBuilder_.isEmpty()) {
unavailableStoresBuilder_.dispose();
unavailableStoresBuilder_ = null;
unavailableStores_ = other.unavailableStores_;
b0_ = (b0_ & ~0x00000002);
unavailableStoresBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getUnavailableStoresFieldBuilder() : null;
} else {
unavailableStoresBuilder_.addAllMessages(other.unavailableStores_);
}
}
}
if (devicesBuilder_ == null) {
if (!other.devices_.isEmpty()) {
if (devices_.isEmpty()) {
devices_ = other.devices_;
b0_ = (b0_ & ~0x00000004);
} else {
ensureDevicesIsMutable();
devices_.addAll(other.devices_);
}
onChanged();
}
} else {
if (!other.devices_.isEmpty()) {
if (devicesBuilder_.isEmpty()) {
devicesBuilder_.dispose();
devicesBuilder_ = null;
devices_ = other.devices_;
b0_ = (b0_ & ~0x00000004);
devicesBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getDevicesFieldBuilder() : null;
} else {
devicesBuilder_.addAllMessages(other.devices_);
}
}
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getAvailableStoresCount(); i++) {
if (!getAvailableStores(i).isInitialized()) {
return false;
}
}
for (int i = 0; i < getUnavailableStoresCount(); i++) {
if (!getUnavailableStores(i).isInitialized()) {
return false;
}
}
for (int i = 0; i < getDevicesCount(); i++) {
if (!getDevices(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.DeviceDiagnostics pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.DeviceDiagnostics) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Diagnostics.Store> availableStores_ =
Collections.emptyList();
private void ensureAvailableStoresIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
availableStores_ = new ArrayList<Diagnostics.Store>(availableStores_);
b0_ |= 0x00000001;
}
}
private RepeatedFieldBuilder<
Diagnostics.Store, Diagnostics.Store.Builder, Diagnostics.StoreOrBuilder> availableStoresBuilder_;
public List<Diagnostics.Store> getAvailableStoresList() {
if (availableStoresBuilder_ == null) {
return Collections.unmodifiableList(availableStores_);
} else {
return availableStoresBuilder_.getMessageList();
}
}
public int getAvailableStoresCount() {
if (availableStoresBuilder_ == null) {
return availableStores_.size();
} else {
return availableStoresBuilder_.getCount();
}
}
public Diagnostics.Store getAvailableStores(int index) {
if (availableStoresBuilder_ == null) {
return availableStores_.get(index);
} else {
return availableStoresBuilder_.getMessage(index);
}
}
public Builder setAvailableStores(
int index, Diagnostics.Store value) {
if (availableStoresBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureAvailableStoresIsMutable();
availableStores_.set(index, value);
onChanged();
} else {
availableStoresBuilder_.setMessage(index, value);
}
return this;
}
public Builder setAvailableStores(
int index, Diagnostics.Store.Builder bdForValue) {
if (availableStoresBuilder_ == null) {
ensureAvailableStoresIsMutable();
availableStores_.set(index, bdForValue.build());
onChanged();
} else {
availableStoresBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addAvailableStores(Diagnostics.Store value) {
if (availableStoresBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureAvailableStoresIsMutable();
availableStores_.add(value);
onChanged();
} else {
availableStoresBuilder_.addMessage(value);
}
return this;
}
public Builder addAvailableStores(
int index, Diagnostics.Store value) {
if (availableStoresBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureAvailableStoresIsMutable();
availableStores_.add(index, value);
onChanged();
} else {
availableStoresBuilder_.addMessage(index, value);
}
return this;
}
public Builder addAvailableStores(
Diagnostics.Store.Builder bdForValue) {
if (availableStoresBuilder_ == null) {
ensureAvailableStoresIsMutable();
availableStores_.add(bdForValue.build());
onChanged();
} else {
availableStoresBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addAvailableStores(
int index, Diagnostics.Store.Builder bdForValue) {
if (availableStoresBuilder_ == null) {
ensureAvailableStoresIsMutable();
availableStores_.add(index, bdForValue.build());
onChanged();
} else {
availableStoresBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllAvailableStores(
Iterable<? extends Diagnostics.Store> values) {
if (availableStoresBuilder_ == null) {
ensureAvailableStoresIsMutable();
AbstractMessageLite.Builder.addAll(
values, availableStores_);
onChanged();
} else {
availableStoresBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearAvailableStores() {
if (availableStoresBuilder_ == null) {
availableStores_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
onChanged();
} else {
availableStoresBuilder_.clear();
}
return this;
}
public Builder removeAvailableStores(int index) {
if (availableStoresBuilder_ == null) {
ensureAvailableStoresIsMutable();
availableStores_.remove(index);
onChanged();
} else {
availableStoresBuilder_.remove(index);
}
return this;
}
public Diagnostics.Store.Builder getAvailableStoresBuilder(
int index) {
return getAvailableStoresFieldBuilder().getBuilder(index);
}
public Diagnostics.StoreOrBuilder getAvailableStoresOrBuilder(
int index) {
if (availableStoresBuilder_ == null) {
return availableStores_.get(index);  } else {
return availableStoresBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.StoreOrBuilder> 
getAvailableStoresOrBuilderList() {
if (availableStoresBuilder_ != null) {
return availableStoresBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(availableStores_);
}
}
public Diagnostics.Store.Builder addAvailableStoresBuilder() {
return getAvailableStoresFieldBuilder().addBuilder(
Diagnostics.Store.getDefaultInstance());
}
public Diagnostics.Store.Builder addAvailableStoresBuilder(
int index) {
return getAvailableStoresFieldBuilder().addBuilder(
index, Diagnostics.Store.getDefaultInstance());
}
public List<Diagnostics.Store.Builder> 
getAvailableStoresBuilderList() {
return getAvailableStoresFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.Store, Diagnostics.Store.Builder, Diagnostics.StoreOrBuilder> 
getAvailableStoresFieldBuilder() {
if (availableStoresBuilder_ == null) {
availableStoresBuilder_ = new RepeatedFieldBuilder<
Diagnostics.Store, Diagnostics.Store.Builder, Diagnostics.StoreOrBuilder>(
availableStores_,
((b0_ & 0x00000001) == 0x00000001),
getParentForChildren(),
isClean());
availableStores_ = null;
}
return availableStoresBuilder_;
}
private List<Diagnostics.Store> unavailableStores_ =
Collections.emptyList();
private void ensureUnavailableStoresIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
unavailableStores_ = new ArrayList<Diagnostics.Store>(unavailableStores_);
b0_ |= 0x00000002;
}
}
private RepeatedFieldBuilder<
Diagnostics.Store, Diagnostics.Store.Builder, Diagnostics.StoreOrBuilder> unavailableStoresBuilder_;
public List<Diagnostics.Store> getUnavailableStoresList() {
if (unavailableStoresBuilder_ == null) {
return Collections.unmodifiableList(unavailableStores_);
} else {
return unavailableStoresBuilder_.getMessageList();
}
}
public int getUnavailableStoresCount() {
if (unavailableStoresBuilder_ == null) {
return unavailableStores_.size();
} else {
return unavailableStoresBuilder_.getCount();
}
}
public Diagnostics.Store getUnavailableStores(int index) {
if (unavailableStoresBuilder_ == null) {
return unavailableStores_.get(index);
} else {
return unavailableStoresBuilder_.getMessage(index);
}
}
public Builder setUnavailableStores(
int index, Diagnostics.Store value) {
if (unavailableStoresBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureUnavailableStoresIsMutable();
unavailableStores_.set(index, value);
onChanged();
} else {
unavailableStoresBuilder_.setMessage(index, value);
}
return this;
}
public Builder setUnavailableStores(
int index, Diagnostics.Store.Builder bdForValue) {
if (unavailableStoresBuilder_ == null) {
ensureUnavailableStoresIsMutable();
unavailableStores_.set(index, bdForValue.build());
onChanged();
} else {
unavailableStoresBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addUnavailableStores(Diagnostics.Store value) {
if (unavailableStoresBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureUnavailableStoresIsMutable();
unavailableStores_.add(value);
onChanged();
} else {
unavailableStoresBuilder_.addMessage(value);
}
return this;
}
public Builder addUnavailableStores(
int index, Diagnostics.Store value) {
if (unavailableStoresBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureUnavailableStoresIsMutable();
unavailableStores_.add(index, value);
onChanged();
} else {
unavailableStoresBuilder_.addMessage(index, value);
}
return this;
}
public Builder addUnavailableStores(
Diagnostics.Store.Builder bdForValue) {
if (unavailableStoresBuilder_ == null) {
ensureUnavailableStoresIsMutable();
unavailableStores_.add(bdForValue.build());
onChanged();
} else {
unavailableStoresBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addUnavailableStores(
int index, Diagnostics.Store.Builder bdForValue) {
if (unavailableStoresBuilder_ == null) {
ensureUnavailableStoresIsMutable();
unavailableStores_.add(index, bdForValue.build());
onChanged();
} else {
unavailableStoresBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllUnavailableStores(
Iterable<? extends Diagnostics.Store> values) {
if (unavailableStoresBuilder_ == null) {
ensureUnavailableStoresIsMutable();
AbstractMessageLite.Builder.addAll(
values, unavailableStores_);
onChanged();
} else {
unavailableStoresBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearUnavailableStores() {
if (unavailableStoresBuilder_ == null) {
unavailableStores_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
onChanged();
} else {
unavailableStoresBuilder_.clear();
}
return this;
}
public Builder removeUnavailableStores(int index) {
if (unavailableStoresBuilder_ == null) {
ensureUnavailableStoresIsMutable();
unavailableStores_.remove(index);
onChanged();
} else {
unavailableStoresBuilder_.remove(index);
}
return this;
}
public Diagnostics.Store.Builder getUnavailableStoresBuilder(
int index) {
return getUnavailableStoresFieldBuilder().getBuilder(index);
}
public Diagnostics.StoreOrBuilder getUnavailableStoresOrBuilder(
int index) {
if (unavailableStoresBuilder_ == null) {
return unavailableStores_.get(index);  } else {
return unavailableStoresBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.StoreOrBuilder> 
getUnavailableStoresOrBuilderList() {
if (unavailableStoresBuilder_ != null) {
return unavailableStoresBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(unavailableStores_);
}
}
public Diagnostics.Store.Builder addUnavailableStoresBuilder() {
return getUnavailableStoresFieldBuilder().addBuilder(
Diagnostics.Store.getDefaultInstance());
}
public Diagnostics.Store.Builder addUnavailableStoresBuilder(
int index) {
return getUnavailableStoresFieldBuilder().addBuilder(
index, Diagnostics.Store.getDefaultInstance());
}
public List<Diagnostics.Store.Builder> 
getUnavailableStoresBuilderList() {
return getUnavailableStoresFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.Store, Diagnostics.Store.Builder, Diagnostics.StoreOrBuilder> 
getUnavailableStoresFieldBuilder() {
if (unavailableStoresBuilder_ == null) {
unavailableStoresBuilder_ = new RepeatedFieldBuilder<
Diagnostics.Store, Diagnostics.Store.Builder, Diagnostics.StoreOrBuilder>(
unavailableStores_,
((b0_ & 0x00000002) == 0x00000002),
getParentForChildren(),
isClean());
unavailableStores_ = null;
}
return unavailableStoresBuilder_;
}
private List<Diagnostics.Device> devices_ =
Collections.emptyList();
private void ensureDevicesIsMutable() {
if (!((b0_ & 0x00000004) == 0x00000004)) {
devices_ = new ArrayList<Diagnostics.Device>(devices_);
b0_ |= 0x00000004;
}
}
private RepeatedFieldBuilder<
Diagnostics.Device, Diagnostics.Device.Builder, Diagnostics.DeviceOrBuilder> devicesBuilder_;
public List<Diagnostics.Device> getDevicesList() {
if (devicesBuilder_ == null) {
return Collections.unmodifiableList(devices_);
} else {
return devicesBuilder_.getMessageList();
}
}
public int getDevicesCount() {
if (devicesBuilder_ == null) {
return devices_.size();
} else {
return devicesBuilder_.getCount();
}
}
public Diagnostics.Device getDevices(int index) {
if (devicesBuilder_ == null) {
return devices_.get(index);
} else {
return devicesBuilder_.getMessage(index);
}
}
public Builder setDevices(
int index, Diagnostics.Device value) {
if (devicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureDevicesIsMutable();
devices_.set(index, value);
onChanged();
} else {
devicesBuilder_.setMessage(index, value);
}
return this;
}
public Builder setDevices(
int index, Diagnostics.Device.Builder bdForValue) {
if (devicesBuilder_ == null) {
ensureDevicesIsMutable();
devices_.set(index, bdForValue.build());
onChanged();
} else {
devicesBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addDevices(Diagnostics.Device value) {
if (devicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureDevicesIsMutable();
devices_.add(value);
onChanged();
} else {
devicesBuilder_.addMessage(value);
}
return this;
}
public Builder addDevices(
int index, Diagnostics.Device value) {
if (devicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureDevicesIsMutable();
devices_.add(index, value);
onChanged();
} else {
devicesBuilder_.addMessage(index, value);
}
return this;
}
public Builder addDevices(
Diagnostics.Device.Builder bdForValue) {
if (devicesBuilder_ == null) {
ensureDevicesIsMutable();
devices_.add(bdForValue.build());
onChanged();
} else {
devicesBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addDevices(
int index, Diagnostics.Device.Builder bdForValue) {
if (devicesBuilder_ == null) {
ensureDevicesIsMutable();
devices_.add(index, bdForValue.build());
onChanged();
} else {
devicesBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllDevices(
Iterable<? extends Diagnostics.Device> values) {
if (devicesBuilder_ == null) {
ensureDevicesIsMutable();
AbstractMessageLite.Builder.addAll(
values, devices_);
onChanged();
} else {
devicesBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearDevices() {
if (devicesBuilder_ == null) {
devices_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
onChanged();
} else {
devicesBuilder_.clear();
}
return this;
}
public Builder removeDevices(int index) {
if (devicesBuilder_ == null) {
ensureDevicesIsMutable();
devices_.remove(index);
onChanged();
} else {
devicesBuilder_.remove(index);
}
return this;
}
public Diagnostics.Device.Builder getDevicesBuilder(
int index) {
return getDevicesFieldBuilder().getBuilder(index);
}
public Diagnostics.DeviceOrBuilder getDevicesOrBuilder(
int index) {
if (devicesBuilder_ == null) {
return devices_.get(index);  } else {
return devicesBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.DeviceOrBuilder> 
getDevicesOrBuilderList() {
if (devicesBuilder_ != null) {
return devicesBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(devices_);
}
}
public Diagnostics.Device.Builder addDevicesBuilder() {
return getDevicesFieldBuilder().addBuilder(
Diagnostics.Device.getDefaultInstance());
}
public Diagnostics.Device.Builder addDevicesBuilder(
int index) {
return getDevicesFieldBuilder().addBuilder(
index, Diagnostics.Device.getDefaultInstance());
}
public List<Diagnostics.Device.Builder> 
getDevicesBuilderList() {
return getDevicesFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.Device, Diagnostics.Device.Builder, Diagnostics.DeviceOrBuilder> 
getDevicesFieldBuilder() {
if (devicesBuilder_ == null) {
devicesBuilder_ = new RepeatedFieldBuilder<
Diagnostics.Device, Diagnostics.Device.Builder, Diagnostics.DeviceOrBuilder>(
devices_,
((b0_ & 0x00000004) == 0x00000004),
getParentForChildren(),
isClean());
devices_ = null;
}
return devicesBuilder_;
}
}
static {
defaultInstance = new DeviceDiagnostics(true);
defaultInstance.initFields();
}
}
public interface StoreOrBuilder extends
MessageOrBuilder {
boolean hasStoreIndex();
long getStoreIndex();
boolean hasSid();
ByteString getSid();
List<ByteString> getKnownOnDidsList();
int getKnownOnDidsCount();
ByteString getKnownOnDids(int index);
}
public static final class Store extends
GeneratedMessage implements
StoreOrBuilder {
private Store(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Store(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final Store defaultInstance;
public static Store getDefaultInstance() {
return defaultInstance;
}
public Store getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private Store(
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
storeIndex_ = input.readUInt64();
break;
}
case 18: {
b0_ |= 0x00000002;
sid_ = input.readBytes();
break;
}
case 26: {
if (!((mutable_b0_ & 0x00000004) == 0x00000004)) {
knownOnDids_ = new ArrayList<ByteString>();
mutable_b0_ |= 0x00000004;
}
knownOnDids_.add(input.readBytes());
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
if (((mutable_b0_ & 0x00000004) == 0x00000004)) {
knownOnDids_ = Collections.unmodifiableList(knownOnDids_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_Store_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_Store_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.Store.class, Diagnostics.Store.Builder.class);
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
public static final int STORE_INDEX_FIELD_NUMBER = 1;
private long storeIndex_;
public boolean hasStoreIndex() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getStoreIndex() {
return storeIndex_;
}
public static final int SID_FIELD_NUMBER = 2;
private ByteString sid_;
public boolean hasSid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getSid() {
return sid_;
}
public static final int KNOWN_ON_DIDS_FIELD_NUMBER = 3;
private List<ByteString> knownOnDids_;
public List<ByteString>
getKnownOnDidsList() {
return knownOnDids_;
}
public int getKnownOnDidsCount() {
return knownOnDids_.size();
}
public ByteString getKnownOnDids(int index) {
return knownOnDids_.get(index);
}
private void initFields() {
storeIndex_ = 0L;
sid_ = ByteString.EMPTY;
knownOnDids_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreIndex()) {
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
output.writeUInt64(1, storeIndex_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, sid_);
}
for (int i = 0; i < knownOnDids_.size(); i++) {
output.writeBytes(3, knownOnDids_.get(i));
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
.computeUInt64Size(1, storeIndex_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, sid_);
}
{
int dataSize = 0;
for (int i = 0; i < knownOnDids_.size(); i++) {
dataSize += CodedOutputStream
.computeBytesSizeNoTag(knownOnDids_.get(i));
}
size += dataSize;
size += 1 * getKnownOnDidsList().size();
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
public static Diagnostics.Store parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.Store parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.Store parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.Store parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.Store parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.Store parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.Store parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.Store parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.Store parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.Store parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.Store prototype) {
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
Diagnostics.StoreOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_Store_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_Store_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.Store.class, Diagnostics.Store.Builder.class);
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
storeIndex_ = 0L;
b0_ = (b0_ & ~0x00000001);
sid_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
knownOnDids_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_Store_descriptor;
}
public Diagnostics.Store getDefaultInstanceForType() {
return Diagnostics.Store.getDefaultInstance();
}
public Diagnostics.Store build() {
Diagnostics.Store result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.Store buildPartial() {
Diagnostics.Store result = new Diagnostics.Store(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeIndex_ = storeIndex_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.sid_ = sid_;
if (((b0_ & 0x00000004) == 0x00000004)) {
knownOnDids_ = Collections.unmodifiableList(knownOnDids_);
b0_ = (b0_ & ~0x00000004);
}
result.knownOnDids_ = knownOnDids_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.Store) {
return mergeFrom((Diagnostics.Store)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.Store other) {
if (other == Diagnostics.Store.getDefaultInstance()) return this;
if (other.hasStoreIndex()) {
setStoreIndex(other.getStoreIndex());
}
if (other.hasSid()) {
setSid(other.getSid());
}
if (!other.knownOnDids_.isEmpty()) {
if (knownOnDids_.isEmpty()) {
knownOnDids_ = other.knownOnDids_;
b0_ = (b0_ & ~0x00000004);
} else {
ensureKnownOnDidsIsMutable();
knownOnDids_.addAll(other.knownOnDids_);
}
onChanged();
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasStoreIndex()) {
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
Diagnostics.Store pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.Store) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private long storeIndex_ ;
public boolean hasStoreIndex() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getStoreIndex() {
return storeIndex_;
}
public Builder setStoreIndex(long value) {
b0_ |= 0x00000001;
storeIndex_ = value;
onChanged();
return this;
}
public Builder clearStoreIndex() {
b0_ = (b0_ & ~0x00000001);
storeIndex_ = 0L;
onChanged();
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
onChanged();
return this;
}
public Builder clearSid() {
b0_ = (b0_ & ~0x00000002);
sid_ = getDefaultInstance().getSid();
onChanged();
return this;
}
private List<ByteString> knownOnDids_ = Collections.emptyList();
private void ensureKnownOnDidsIsMutable() {
if (!((b0_ & 0x00000004) == 0x00000004)) {
knownOnDids_ = new ArrayList<ByteString>(knownOnDids_);
b0_ |= 0x00000004;
}
}
public List<ByteString>
getKnownOnDidsList() {
return Collections.unmodifiableList(knownOnDids_);
}
public int getKnownOnDidsCount() {
return knownOnDids_.size();
}
public ByteString getKnownOnDids(int index) {
return knownOnDids_.get(index);
}
public Builder setKnownOnDids(
int index, ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureKnownOnDidsIsMutable();
knownOnDids_.set(index, value);
onChanged();
return this;
}
public Builder addKnownOnDids(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
ensureKnownOnDidsIsMutable();
knownOnDids_.add(value);
onChanged();
return this;
}
public Builder addAllKnownOnDids(
Iterable<? extends ByteString> values) {
ensureKnownOnDidsIsMutable();
AbstractMessageLite.Builder.addAll(
values, knownOnDids_);
onChanged();
return this;
}
public Builder clearKnownOnDids() {
knownOnDids_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
onChanged();
return this;
}
}
static {
defaultInstance = new Store(true);
defaultInstance.initFields();
}
}
public interface DeviceOrBuilder extends
MessageOrBuilder {
boolean hasDid();
ByteString getDid();
List<Diagnostics.Transport> 
getAvailableTransportsList();
Diagnostics.Transport getAvailableTransports(int index);
int getAvailableTransportsCount();
List<? extends Diagnostics.TransportOrBuilder> 
getAvailableTransportsOrBuilderList();
Diagnostics.TransportOrBuilder getAvailableTransportsOrBuilder(
int index);
boolean hasPreferredTransportId();
String getPreferredTransportId();
ByteString
getPreferredTransportIdBytes();
}
public static final class Device extends
GeneratedMessage implements
DeviceOrBuilder {
private Device(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Device(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final Device defaultInstance;
public static Device getDefaultInstance() {
return defaultInstance;
}
public Device getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private Device(
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
case 10: {
b0_ |= 0x00000001;
did_ = input.readBytes();
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
availableTransports_ = new ArrayList<Diagnostics.Transport>();
mutable_b0_ |= 0x00000002;
}
availableTransports_.add(input.readMessage(Diagnostics.Transport.PARSER, er));
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
preferredTransportId_ = bs;
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
availableTransports_ = Collections.unmodifiableList(availableTransports_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_Device_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_Device_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.Device.class, Diagnostics.Device.Builder.class);
}
public static Parser<Device> PARSER =
new AbstractParser<Device>() {
public Device parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Device(input, er);
}
};
@Override
public Parser<Device> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DID_FIELD_NUMBER = 1;
private ByteString did_;
public boolean hasDid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getDid() {
return did_;
}
public static final int AVAILABLE_TRANSPORTS_FIELD_NUMBER = 2;
private List<Diagnostics.Transport> availableTransports_;
public List<Diagnostics.Transport> getAvailableTransportsList() {
return availableTransports_;
}
public List<? extends Diagnostics.TransportOrBuilder> 
getAvailableTransportsOrBuilderList() {
return availableTransports_;
}
public int getAvailableTransportsCount() {
return availableTransports_.size();
}
public Diagnostics.Transport getAvailableTransports(int index) {
return availableTransports_.get(index);
}
public Diagnostics.TransportOrBuilder getAvailableTransportsOrBuilder(
int index) {
return availableTransports_.get(index);
}
public static final int PREFERRED_TRANSPORT_ID_FIELD_NUMBER = 3;
private Object preferredTransportId_;
public boolean hasPreferredTransportId() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public String getPreferredTransportId() {
Object ref = preferredTransportId_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
preferredTransportId_ = s;
}
return s;
}
}
public ByteString
getPreferredTransportIdBytes() {
Object ref = preferredTransportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
preferredTransportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
did_ = ByteString.EMPTY;
availableTransports_ = Collections.emptyList();
preferredTransportId_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasDid()) {
mii = 0;
return false;
}
for (int i = 0; i < getAvailableTransportsCount(); i++) {
if (!getAvailableTransports(i).isInitialized()) {
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
output.writeBytes(1, did_);
}
for (int i = 0; i < availableTransports_.size(); i++) {
output.writeMessage(2, availableTransports_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(3, getPreferredTransportIdBytes());
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
.computeBytesSize(1, did_);
}
for (int i = 0; i < availableTransports_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, availableTransports_.get(i));
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(3, getPreferredTransportIdBytes());
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
public static Diagnostics.Device parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.Device parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.Device parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.Device parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.Device parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.Device parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.Device parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.Device parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.Device parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.Device parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.Device prototype) {
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
Diagnostics.DeviceOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_Device_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_Device_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.Device.class, Diagnostics.Device.Builder.class);
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
getAvailableTransportsFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
did_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
if (availableTransportsBuilder_ == null) {
availableTransports_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
} else {
availableTransportsBuilder_.clear();
}
preferredTransportId_ = "";
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_Device_descriptor;
}
public Diagnostics.Device getDefaultInstanceForType() {
return Diagnostics.Device.getDefaultInstance();
}
public Diagnostics.Device build() {
Diagnostics.Device result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.Device buildPartial() {
Diagnostics.Device result = new Diagnostics.Device(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.did_ = did_;
if (availableTransportsBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002)) {
availableTransports_ = Collections.unmodifiableList(availableTransports_);
b0_ = (b0_ & ~0x00000002);
}
result.availableTransports_ = availableTransports_;
} else {
result.availableTransports_ = availableTransportsBuilder_.build();
}
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000002;
}
result.preferredTransportId_ = preferredTransportId_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.Device) {
return mergeFrom((Diagnostics.Device)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.Device other) {
if (other == Diagnostics.Device.getDefaultInstance()) return this;
if (other.hasDid()) {
setDid(other.getDid());
}
if (availableTransportsBuilder_ == null) {
if (!other.availableTransports_.isEmpty()) {
if (availableTransports_.isEmpty()) {
availableTransports_ = other.availableTransports_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureAvailableTransportsIsMutable();
availableTransports_.addAll(other.availableTransports_);
}
onChanged();
}
} else {
if (!other.availableTransports_.isEmpty()) {
if (availableTransportsBuilder_.isEmpty()) {
availableTransportsBuilder_.dispose();
availableTransportsBuilder_ = null;
availableTransports_ = other.availableTransports_;
b0_ = (b0_ & ~0x00000002);
availableTransportsBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getAvailableTransportsFieldBuilder() : null;
} else {
availableTransportsBuilder_.addAllMessages(other.availableTransports_);
}
}
}
if (other.hasPreferredTransportId()) {
b0_ |= 0x00000004;
preferredTransportId_ = other.preferredTransportId_;
onChanged();
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasDid()) {
return false;
}
for (int i = 0; i < getAvailableTransportsCount(); i++) {
if (!getAvailableTransports(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.Device pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.Device) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString did_ = ByteString.EMPTY;
public boolean hasDid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getDid() {
return did_;
}
public Builder setDid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
did_ = value;
onChanged();
return this;
}
public Builder clearDid() {
b0_ = (b0_ & ~0x00000001);
did_ = getDefaultInstance().getDid();
onChanged();
return this;
}
private List<Diagnostics.Transport> availableTransports_ =
Collections.emptyList();
private void ensureAvailableTransportsIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
availableTransports_ = new ArrayList<Diagnostics.Transport>(availableTransports_);
b0_ |= 0x00000002;
}
}
private RepeatedFieldBuilder<
Diagnostics.Transport, Diagnostics.Transport.Builder, Diagnostics.TransportOrBuilder> availableTransportsBuilder_;
public List<Diagnostics.Transport> getAvailableTransportsList() {
if (availableTransportsBuilder_ == null) {
return Collections.unmodifiableList(availableTransports_);
} else {
return availableTransportsBuilder_.getMessageList();
}
}
public int getAvailableTransportsCount() {
if (availableTransportsBuilder_ == null) {
return availableTransports_.size();
} else {
return availableTransportsBuilder_.getCount();
}
}
public Diagnostics.Transport getAvailableTransports(int index) {
if (availableTransportsBuilder_ == null) {
return availableTransports_.get(index);
} else {
return availableTransportsBuilder_.getMessage(index);
}
}
public Builder setAvailableTransports(
int index, Diagnostics.Transport value) {
if (availableTransportsBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureAvailableTransportsIsMutable();
availableTransports_.set(index, value);
onChanged();
} else {
availableTransportsBuilder_.setMessage(index, value);
}
return this;
}
public Builder setAvailableTransports(
int index, Diagnostics.Transport.Builder bdForValue) {
if (availableTransportsBuilder_ == null) {
ensureAvailableTransportsIsMutable();
availableTransports_.set(index, bdForValue.build());
onChanged();
} else {
availableTransportsBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addAvailableTransports(Diagnostics.Transport value) {
if (availableTransportsBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureAvailableTransportsIsMutable();
availableTransports_.add(value);
onChanged();
} else {
availableTransportsBuilder_.addMessage(value);
}
return this;
}
public Builder addAvailableTransports(
int index, Diagnostics.Transport value) {
if (availableTransportsBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureAvailableTransportsIsMutable();
availableTransports_.add(index, value);
onChanged();
} else {
availableTransportsBuilder_.addMessage(index, value);
}
return this;
}
public Builder addAvailableTransports(
Diagnostics.Transport.Builder bdForValue) {
if (availableTransportsBuilder_ == null) {
ensureAvailableTransportsIsMutable();
availableTransports_.add(bdForValue.build());
onChanged();
} else {
availableTransportsBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addAvailableTransports(
int index, Diagnostics.Transport.Builder bdForValue) {
if (availableTransportsBuilder_ == null) {
ensureAvailableTransportsIsMutable();
availableTransports_.add(index, bdForValue.build());
onChanged();
} else {
availableTransportsBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllAvailableTransports(
Iterable<? extends Diagnostics.Transport> values) {
if (availableTransportsBuilder_ == null) {
ensureAvailableTransportsIsMutable();
AbstractMessageLite.Builder.addAll(
values, availableTransports_);
onChanged();
} else {
availableTransportsBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearAvailableTransports() {
if (availableTransportsBuilder_ == null) {
availableTransports_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
onChanged();
} else {
availableTransportsBuilder_.clear();
}
return this;
}
public Builder removeAvailableTransports(int index) {
if (availableTransportsBuilder_ == null) {
ensureAvailableTransportsIsMutable();
availableTransports_.remove(index);
onChanged();
} else {
availableTransportsBuilder_.remove(index);
}
return this;
}
public Diagnostics.Transport.Builder getAvailableTransportsBuilder(
int index) {
return getAvailableTransportsFieldBuilder().getBuilder(index);
}
public Diagnostics.TransportOrBuilder getAvailableTransportsOrBuilder(
int index) {
if (availableTransportsBuilder_ == null) {
return availableTransports_.get(index);  } else {
return availableTransportsBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.TransportOrBuilder> 
getAvailableTransportsOrBuilderList() {
if (availableTransportsBuilder_ != null) {
return availableTransportsBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(availableTransports_);
}
}
public Diagnostics.Transport.Builder addAvailableTransportsBuilder() {
return getAvailableTransportsFieldBuilder().addBuilder(
Diagnostics.Transport.getDefaultInstance());
}
public Diagnostics.Transport.Builder addAvailableTransportsBuilder(
int index) {
return getAvailableTransportsFieldBuilder().addBuilder(
index, Diagnostics.Transport.getDefaultInstance());
}
public List<Diagnostics.Transport.Builder> 
getAvailableTransportsBuilderList() {
return getAvailableTransportsFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.Transport, Diagnostics.Transport.Builder, Diagnostics.TransportOrBuilder> 
getAvailableTransportsFieldBuilder() {
if (availableTransportsBuilder_ == null) {
availableTransportsBuilder_ = new RepeatedFieldBuilder<
Diagnostics.Transport, Diagnostics.Transport.Builder, Diagnostics.TransportOrBuilder>(
availableTransports_,
((b0_ & 0x00000002) == 0x00000002),
getParentForChildren(),
isClean());
availableTransports_ = null;
}
return availableTransportsBuilder_;
}
private Object preferredTransportId_ = "";
public boolean hasPreferredTransportId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getPreferredTransportId() {
Object ref = preferredTransportId_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
preferredTransportId_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getPreferredTransportIdBytes() {
Object ref = preferredTransportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
preferredTransportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setPreferredTransportId(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
preferredTransportId_ = value;
onChanged();
return this;
}
public Builder clearPreferredTransportId() {
b0_ = (b0_ & ~0x00000004);
preferredTransportId_ = getDefaultInstance().getPreferredTransportId();
onChanged();
return this;
}
public Builder setPreferredTransportIdBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
preferredTransportId_ = value;
onChanged();
return this;
}
}
static {
defaultInstance = new Device(true);
defaultInstance.initFields();
}
}
public interface TransportOrBuilder extends
MessageOrBuilder {
boolean hasTransportId();
String getTransportId();
ByteString
getTransportIdBytes();
boolean hasState();
Diagnostics.Transport.TransportState getState();
List<Long> getKnownStoreIndexesList();
int getKnownStoreIndexesCount();
long getKnownStoreIndexes(int index);
}
public static final class Transport extends
GeneratedMessage implements
TransportOrBuilder {
private Transport(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private Transport(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final Transport defaultInstance;
public static Transport getDefaultInstance() {
return defaultInstance;
}
public Transport getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private Transport(
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
case 10: {
ByteString bs = input.readBytes();
b0_ |= 0x00000001;
transportId_ = bs;
break;
}
case 16: {
int rawValue = input.readEnum();
Diagnostics.Transport.TransportState value = Diagnostics.Transport.TransportState.valueOf(rawValue);
if (value == null) {
unknownFields.mergeVarintField(2, rawValue);
} else {
b0_ |= 0x00000002;
state_ = value;
}
break;
}
case 24: {
if (!((mutable_b0_ & 0x00000004) == 0x00000004)) {
knownStoreIndexes_ = new ArrayList<Long>();
mutable_b0_ |= 0x00000004;
}
knownStoreIndexes_.add(input.readUInt64());
break;
}
case 26: {
int length = input.readRawVarint32();
int limit = input.pushLimit(length);
if (!((mutable_b0_ & 0x00000004) == 0x00000004) && input.getBytesUntilLimit() > 0) {
knownStoreIndexes_ = new ArrayList<Long>();
mutable_b0_ |= 0x00000004;
}
while (input.getBytesUntilLimit() > 0) {
knownStoreIndexes_.add(input.readUInt64());
}
input.popLimit(limit);
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
if (((mutable_b0_ & 0x00000004) == 0x00000004)) {
knownStoreIndexes_ = Collections.unmodifiableList(knownStoreIndexes_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_Transport_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_Transport_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.Transport.class, Diagnostics.Transport.Builder.class);
}
public static Parser<Transport> PARSER =
new AbstractParser<Transport>() {
public Transport parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new Transport(input, er);
}
};
@Override
public Parser<Transport> getParserForType() {
return PARSER;
}
public enum TransportState
implements ProtocolMessageEnum {
POTENTIALLY_AVAILABLE(0, 1),
PULSING(1, 2),
;
public static final int POTENTIALLY_AVAILABLE_VALUE = 1;
public static final int PULSING_VALUE = 2;
public final int getNumber() { return value; }
public static TransportState valueOf(int value) {
switch (value) {
case 1: return POTENTIALLY_AVAILABLE;
case 2: return PULSING;
default: return null;
}
}
public static Internal.EnumLiteMap<TransportState>
internalGetValueMap() {
return internalValueMap;
}
private static Internal.EnumLiteMap<TransportState>
internalValueMap =
new Internal.EnumLiteMap<TransportState>() {
public TransportState findValueByNumber(int number) {
return TransportState.valueOf(number);
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
return Diagnostics.Transport.getDescriptor().getEnumTypes().get(0);
}
private static final TransportState[] VALUES = values();
public static TransportState valueOf(
Descriptors.EnumValueDescriptor desc) {
if (desc.getType() != getDescriptor()) {
throw new IllegalArgumentException(
"EnumValueDescriptor is not for this type.");
}
return VALUES[desc.getIndex()];
}
private final int index;
private final int value;
private TransportState(int index, int value) {
this.index = index;
this.value = value;
}
}
private int b0_;
public static final int TRANSPORT_ID_FIELD_NUMBER = 1;
private Object transportId_;
public boolean hasTransportId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getTransportId() {
Object ref = transportId_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
transportId_ = s;
}
return s;
}
}
public ByteString
getTransportIdBytes() {
Object ref = transportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
transportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int STATE_FIELD_NUMBER = 2;
private Diagnostics.Transport.TransportState state_;
public boolean hasState() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Diagnostics.Transport.TransportState getState() {
return state_;
}
public static final int KNOWN_STORE_INDEXES_FIELD_NUMBER = 3;
private List<Long> knownStoreIndexes_;
public List<Long>
getKnownStoreIndexesList() {
return knownStoreIndexes_;
}
public int getKnownStoreIndexesCount() {
return knownStoreIndexes_.size();
}
public long getKnownStoreIndexes(int index) {
return knownStoreIndexes_.get(index);
}
private void initFields() {
transportId_ = "";
state_ = Diagnostics.Transport.TransportState.POTENTIALLY_AVAILABLE;
knownStoreIndexes_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasTransportId()) {
mii = 0;
return false;
}
if (!hasState()) {
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
output.writeBytes(1, getTransportIdBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeEnum(2, state_.getNumber());
}
for (int i = 0; i < knownStoreIndexes_.size(); i++) {
output.writeUInt64(3, knownStoreIndexes_.get(i));
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
.computeBytesSize(1, getTransportIdBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeEnumSize(2, state_.getNumber());
}
{
int dataSize = 0;
for (int i = 0; i < knownStoreIndexes_.size(); i++) {
dataSize += CodedOutputStream
.computeUInt64SizeNoTag(knownStoreIndexes_.get(i));
}
size += dataSize;
size += 1 * getKnownStoreIndexesList().size();
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
public static Diagnostics.Transport parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.Transport parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.Transport parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.Transport parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.Transport parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.Transport parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.Transport parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.Transport parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.Transport parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.Transport parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.Transport prototype) {
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
Diagnostics.TransportOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_Transport_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_Transport_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.Transport.class, Diagnostics.Transport.Builder.class);
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
transportId_ = "";
b0_ = (b0_ & ~0x00000001);
state_ = Diagnostics.Transport.TransportState.POTENTIALLY_AVAILABLE;
b0_ = (b0_ & ~0x00000002);
knownStoreIndexes_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_Transport_descriptor;
}
public Diagnostics.Transport getDefaultInstanceForType() {
return Diagnostics.Transport.getDefaultInstance();
}
public Diagnostics.Transport build() {
Diagnostics.Transport result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.Transport buildPartial() {
Diagnostics.Transport result = new Diagnostics.Transport(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.transportId_ = transportId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.state_ = state_;
if (((b0_ & 0x00000004) == 0x00000004)) {
knownStoreIndexes_ = Collections.unmodifiableList(knownStoreIndexes_);
b0_ = (b0_ & ~0x00000004);
}
result.knownStoreIndexes_ = knownStoreIndexes_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.Transport) {
return mergeFrom((Diagnostics.Transport)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.Transport other) {
if (other == Diagnostics.Transport.getDefaultInstance()) return this;
if (other.hasTransportId()) {
b0_ |= 0x00000001;
transportId_ = other.transportId_;
onChanged();
}
if (other.hasState()) {
setState(other.getState());
}
if (!other.knownStoreIndexes_.isEmpty()) {
if (knownStoreIndexes_.isEmpty()) {
knownStoreIndexes_ = other.knownStoreIndexes_;
b0_ = (b0_ & ~0x00000004);
} else {
ensureKnownStoreIndexesIsMutable();
knownStoreIndexes_.addAll(other.knownStoreIndexes_);
}
onChanged();
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasTransportId()) {
return false;
}
if (!hasState()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.Transport pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.Transport) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object transportId_ = "";
public boolean hasTransportId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getTransportId() {
Object ref = transportId_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
transportId_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getTransportIdBytes() {
Object ref = transportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
transportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setTransportId(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
transportId_ = value;
onChanged();
return this;
}
public Builder clearTransportId() {
b0_ = (b0_ & ~0x00000001);
transportId_ = getDefaultInstance().getTransportId();
onChanged();
return this;
}
public Builder setTransportIdBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
transportId_ = value;
onChanged();
return this;
}
private Diagnostics.Transport.TransportState state_ = Diagnostics.Transport.TransportState.POTENTIALLY_AVAILABLE;
public boolean hasState() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Diagnostics.Transport.TransportState getState() {
return state_;
}
public Builder setState(Diagnostics.Transport.TransportState value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
state_ = value;
onChanged();
return this;
}
public Builder clearState() {
b0_ = (b0_ & ~0x00000002);
state_ = Diagnostics.Transport.TransportState.POTENTIALLY_AVAILABLE;
onChanged();
return this;
}
private List<Long> knownStoreIndexes_ = Collections.emptyList();
private void ensureKnownStoreIndexesIsMutable() {
if (!((b0_ & 0x00000004) == 0x00000004)) {
knownStoreIndexes_ = new ArrayList<Long>(knownStoreIndexes_);
b0_ |= 0x00000004;
}
}
public List<Long>
getKnownStoreIndexesList() {
return Collections.unmodifiableList(knownStoreIndexes_);
}
public int getKnownStoreIndexesCount() {
return knownStoreIndexes_.size();
}
public long getKnownStoreIndexes(int index) {
return knownStoreIndexes_.get(index);
}
public Builder setKnownStoreIndexes(
int index, long value) {
ensureKnownStoreIndexesIsMutable();
knownStoreIndexes_.set(index, value);
onChanged();
return this;
}
public Builder addKnownStoreIndexes(long value) {
ensureKnownStoreIndexesIsMutable();
knownStoreIndexes_.add(value);
onChanged();
return this;
}
public Builder addAllKnownStoreIndexes(
Iterable<? extends Long> values) {
ensureKnownStoreIndexesIsMutable();
AbstractMessageLite.Builder.addAll(
values, knownStoreIndexes_);
onChanged();
return this;
}
public Builder clearKnownStoreIndexes() {
knownStoreIndexes_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000004);
onChanged();
return this;
}
}
static {
defaultInstance = new Transport(true);
defaultInstance.initFields();
}
}
public interface TransportDiagnosticsOrBuilder extends
MessageOrBuilder {
boolean hasTcpDiagnostics();
Diagnostics.TCPDiagnostics getTcpDiagnostics();
Diagnostics.TCPDiagnosticsOrBuilder getTcpDiagnosticsOrBuilder();
boolean hasZephyrDiagnostics();
Diagnostics.ZephyrDiagnostics getZephyrDiagnostics();
Diagnostics.ZephyrDiagnosticsOrBuilder getZephyrDiagnosticsOrBuilder();
}
public static final class TransportDiagnostics extends
GeneratedMessage implements
TransportDiagnosticsOrBuilder {
private TransportDiagnostics(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TransportDiagnostics(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TransportDiagnostics defaultInstance;
public static TransportDiagnostics getDefaultInstance() {
return defaultInstance;
}
public TransportDiagnostics getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TransportDiagnostics(
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
case 10: {
Diagnostics.TCPDiagnostics.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = tcpDiagnostics_.toBuilder();
}
tcpDiagnostics_ = input.readMessage(Diagnostics.TCPDiagnostics.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(tcpDiagnostics_);
tcpDiagnostics_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 26: {
Diagnostics.ZephyrDiagnostics.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = zephyrDiagnostics_.toBuilder();
}
zephyrDiagnostics_ = input.readMessage(Diagnostics.ZephyrDiagnostics.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(zephyrDiagnostics_);
zephyrDiagnostics_ = subBuilder.buildPartial();
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
return Diagnostics.internal_static_TransportDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransportDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransportDiagnostics.class, Diagnostics.TransportDiagnostics.Builder.class);
}
public static Parser<TransportDiagnostics> PARSER =
new AbstractParser<TransportDiagnostics>() {
public TransportDiagnostics parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TransportDiagnostics(input, er);
}
};
@Override
public Parser<TransportDiagnostics> getParserForType() {
return PARSER;
}
private int b0_;
public static final int TCP_DIAGNOSTICS_FIELD_NUMBER = 1;
private Diagnostics.TCPDiagnostics tcpDiagnostics_;
public boolean hasTcpDiagnostics() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.TCPDiagnostics getTcpDiagnostics() {
return tcpDiagnostics_;
}
public Diagnostics.TCPDiagnosticsOrBuilder getTcpDiagnosticsOrBuilder() {
return tcpDiagnostics_;
}
public static final int ZEPHYR_DIAGNOSTICS_FIELD_NUMBER = 3;
private Diagnostics.ZephyrDiagnostics zephyrDiagnostics_;
public boolean hasZephyrDiagnostics() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Diagnostics.ZephyrDiagnostics getZephyrDiagnostics() {
return zephyrDiagnostics_;
}
public Diagnostics.ZephyrDiagnosticsOrBuilder getZephyrDiagnosticsOrBuilder() {
return zephyrDiagnostics_;
}
private void initFields() {
tcpDiagnostics_ = Diagnostics.TCPDiagnostics.getDefaultInstance();
zephyrDiagnostics_ = Diagnostics.ZephyrDiagnostics.getDefaultInstance();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (hasTcpDiagnostics()) {
if (!getTcpDiagnostics().isInitialized()) {
mii = 0;
return false;
}
}
if (hasZephyrDiagnostics()) {
if (!getZephyrDiagnostics().isInitialized()) {
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
output.writeMessage(1, tcpDiagnostics_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeMessage(3, zephyrDiagnostics_);
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
.computeMessageSize(1, tcpDiagnostics_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeMessageSize(3, zephyrDiagnostics_);
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
public static Diagnostics.TransportDiagnostics parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransportDiagnostics parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransportDiagnostics parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransportDiagnostics parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransportDiagnostics parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransportDiagnostics parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TransportDiagnostics parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TransportDiagnostics parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TransportDiagnostics parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransportDiagnostics parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TransportDiagnostics prototype) {
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
Diagnostics.TransportDiagnosticsOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TransportDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransportDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransportDiagnostics.class, Diagnostics.TransportDiagnostics.Builder.class);
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
getTcpDiagnosticsFieldBuilder();
getZephyrDiagnosticsFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (tcpDiagnosticsBuilder_ == null) {
tcpDiagnostics_ = Diagnostics.TCPDiagnostics.getDefaultInstance();
} else {
tcpDiagnosticsBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
if (zephyrDiagnosticsBuilder_ == null) {
zephyrDiagnostics_ = Diagnostics.ZephyrDiagnostics.getDefaultInstance();
} else {
zephyrDiagnosticsBuilder_.clear();
}
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TransportDiagnostics_descriptor;
}
public Diagnostics.TransportDiagnostics getDefaultInstanceForType() {
return Diagnostics.TransportDiagnostics.getDefaultInstance();
}
public Diagnostics.TransportDiagnostics build() {
Diagnostics.TransportDiagnostics result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TransportDiagnostics buildPartial() {
Diagnostics.TransportDiagnostics result = new Diagnostics.TransportDiagnostics(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
if (tcpDiagnosticsBuilder_ == null) {
result.tcpDiagnostics_ = tcpDiagnostics_;
} else {
result.tcpDiagnostics_ = tcpDiagnosticsBuilder_.build();
}
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
if (zephyrDiagnosticsBuilder_ == null) {
result.zephyrDiagnostics_ = zephyrDiagnostics_;
} else {
result.zephyrDiagnostics_ = zephyrDiagnosticsBuilder_.build();
}
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TransportDiagnostics) {
return mergeFrom((Diagnostics.TransportDiagnostics)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TransportDiagnostics other) {
if (other == Diagnostics.TransportDiagnostics.getDefaultInstance()) return this;
if (other.hasTcpDiagnostics()) {
mergeTcpDiagnostics(other.getTcpDiagnostics());
}
if (other.hasZephyrDiagnostics()) {
mergeZephyrDiagnostics(other.getZephyrDiagnostics());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (hasTcpDiagnostics()) {
if (!getTcpDiagnostics().isInitialized()) {
return false;
}
}
if (hasZephyrDiagnostics()) {
if (!getZephyrDiagnostics().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TransportDiagnostics pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TransportDiagnostics) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.TCPDiagnostics tcpDiagnostics_ = Diagnostics.TCPDiagnostics.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.TCPDiagnostics, Diagnostics.TCPDiagnostics.Builder, Diagnostics.TCPDiagnosticsOrBuilder> tcpDiagnosticsBuilder_;
public boolean hasTcpDiagnostics() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.TCPDiagnostics getTcpDiagnostics() {
if (tcpDiagnosticsBuilder_ == null) {
return tcpDiagnostics_;
} else {
return tcpDiagnosticsBuilder_.getMessage();
}
}
public Builder setTcpDiagnostics(Diagnostics.TCPDiagnostics value) {
if (tcpDiagnosticsBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
tcpDiagnostics_ = value;
onChanged();
} else {
tcpDiagnosticsBuilder_.setMessage(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder setTcpDiagnostics(
Diagnostics.TCPDiagnostics.Builder bdForValue) {
if (tcpDiagnosticsBuilder_ == null) {
tcpDiagnostics_ = bdForValue.build();
onChanged();
} else {
tcpDiagnosticsBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000001;
return this;
}
public Builder mergeTcpDiagnostics(Diagnostics.TCPDiagnostics value) {
if (tcpDiagnosticsBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001) &&
tcpDiagnostics_ != Diagnostics.TCPDiagnostics.getDefaultInstance()) {
tcpDiagnostics_ =
Diagnostics.TCPDiagnostics.newBuilder(tcpDiagnostics_).mergeFrom(value).buildPartial();
} else {
tcpDiagnostics_ = value;
}
onChanged();
} else {
tcpDiagnosticsBuilder_.mergeFrom(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder clearTcpDiagnostics() {
if (tcpDiagnosticsBuilder_ == null) {
tcpDiagnostics_ = Diagnostics.TCPDiagnostics.getDefaultInstance();
onChanged();
} else {
tcpDiagnosticsBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
return this;
}
public Diagnostics.TCPDiagnostics.Builder getTcpDiagnosticsBuilder() {
b0_ |= 0x00000001;
onChanged();
return getTcpDiagnosticsFieldBuilder().getBuilder();
}
public Diagnostics.TCPDiagnosticsOrBuilder getTcpDiagnosticsOrBuilder() {
if (tcpDiagnosticsBuilder_ != null) {
return tcpDiagnosticsBuilder_.getMessageOrBuilder();
} else {
return tcpDiagnostics_;
}
}
private SingleFieldBuilder<
Diagnostics.TCPDiagnostics, Diagnostics.TCPDiagnostics.Builder, Diagnostics.TCPDiagnosticsOrBuilder> 
getTcpDiagnosticsFieldBuilder() {
if (tcpDiagnosticsBuilder_ == null) {
tcpDiagnosticsBuilder_ = new SingleFieldBuilder<
Diagnostics.TCPDiagnostics, Diagnostics.TCPDiagnostics.Builder, Diagnostics.TCPDiagnosticsOrBuilder>(
getTcpDiagnostics(),
getParentForChildren(),
isClean());
tcpDiagnostics_ = null;
}
return tcpDiagnosticsBuilder_;
}
private Diagnostics.ZephyrDiagnostics zephyrDiagnostics_ = Diagnostics.ZephyrDiagnostics.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.ZephyrDiagnostics, Diagnostics.ZephyrDiagnostics.Builder, Diagnostics.ZephyrDiagnosticsOrBuilder> zephyrDiagnosticsBuilder_;
public boolean hasZephyrDiagnostics() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Diagnostics.ZephyrDiagnostics getZephyrDiagnostics() {
if (zephyrDiagnosticsBuilder_ == null) {
return zephyrDiagnostics_;
} else {
return zephyrDiagnosticsBuilder_.getMessage();
}
}
public Builder setZephyrDiagnostics(Diagnostics.ZephyrDiagnostics value) {
if (zephyrDiagnosticsBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
zephyrDiagnostics_ = value;
onChanged();
} else {
zephyrDiagnosticsBuilder_.setMessage(value);
}
b0_ |= 0x00000002;
return this;
}
public Builder setZephyrDiagnostics(
Diagnostics.ZephyrDiagnostics.Builder bdForValue) {
if (zephyrDiagnosticsBuilder_ == null) {
zephyrDiagnostics_ = bdForValue.build();
onChanged();
} else {
zephyrDiagnosticsBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000002;
return this;
}
public Builder mergeZephyrDiagnostics(Diagnostics.ZephyrDiagnostics value) {
if (zephyrDiagnosticsBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002) &&
zephyrDiagnostics_ != Diagnostics.ZephyrDiagnostics.getDefaultInstance()) {
zephyrDiagnostics_ =
Diagnostics.ZephyrDiagnostics.newBuilder(zephyrDiagnostics_).mergeFrom(value).buildPartial();
} else {
zephyrDiagnostics_ = value;
}
onChanged();
} else {
zephyrDiagnosticsBuilder_.mergeFrom(value);
}
b0_ |= 0x00000002;
return this;
}
public Builder clearZephyrDiagnostics() {
if (zephyrDiagnosticsBuilder_ == null) {
zephyrDiagnostics_ = Diagnostics.ZephyrDiagnostics.getDefaultInstance();
onChanged();
} else {
zephyrDiagnosticsBuilder_.clear();
}
b0_ = (b0_ & ~0x00000002);
return this;
}
public Diagnostics.ZephyrDiagnostics.Builder getZephyrDiagnosticsBuilder() {
b0_ |= 0x00000002;
onChanged();
return getZephyrDiagnosticsFieldBuilder().getBuilder();
}
public Diagnostics.ZephyrDiagnosticsOrBuilder getZephyrDiagnosticsOrBuilder() {
if (zephyrDiagnosticsBuilder_ != null) {
return zephyrDiagnosticsBuilder_.getMessageOrBuilder();
} else {
return zephyrDiagnostics_;
}
}
private SingleFieldBuilder<
Diagnostics.ZephyrDiagnostics, Diagnostics.ZephyrDiagnostics.Builder, Diagnostics.ZephyrDiagnosticsOrBuilder> 
getZephyrDiagnosticsFieldBuilder() {
if (zephyrDiagnosticsBuilder_ == null) {
zephyrDiagnosticsBuilder_ = new SingleFieldBuilder<
Diagnostics.ZephyrDiagnostics, Diagnostics.ZephyrDiagnostics.Builder, Diagnostics.ZephyrDiagnosticsOrBuilder>(
getZephyrDiagnostics(),
getParentForChildren(),
isClean());
zephyrDiagnostics_ = null;
}
return zephyrDiagnosticsBuilder_;
}
}
static {
defaultInstance = new TransportDiagnostics(true);
defaultInstance.initFields();
}
}
public interface TCPDiagnosticsOrBuilder extends
MessageOrBuilder {
boolean hasListeningAddress();
Diagnostics.PBInetSocketAddress getListeningAddress();
Diagnostics.PBInetSocketAddressOrBuilder getListeningAddressOrBuilder();
List<Diagnostics.TCPDevice> 
getReachableDevicesList();
Diagnostics.TCPDevice getReachableDevices(int index);
int getReachableDevicesCount();
List<? extends Diagnostics.TCPDeviceOrBuilder> 
getReachableDevicesOrBuilderList();
Diagnostics.TCPDeviceOrBuilder getReachableDevicesOrBuilder(
int index);
}
public static final class TCPDiagnostics extends
GeneratedMessage implements
TCPDiagnosticsOrBuilder {
private TCPDiagnostics(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TCPDiagnostics(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TCPDiagnostics defaultInstance;
public static TCPDiagnostics getDefaultInstance() {
return defaultInstance;
}
public TCPDiagnostics getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TCPDiagnostics(
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
case 10: {
Diagnostics.PBInetSocketAddress.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = listeningAddress_.toBuilder();
}
listeningAddress_ = input.readMessage(Diagnostics.PBInetSocketAddress.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(listeningAddress_);
listeningAddress_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
reachableDevices_ = new ArrayList<Diagnostics.TCPDevice>();
mutable_b0_ |= 0x00000002;
}
reachableDevices_.add(input.readMessage(Diagnostics.TCPDevice.PARSER, er));
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
reachableDevices_ = Collections.unmodifiableList(reachableDevices_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TCPDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TCPDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TCPDiagnostics.class, Diagnostics.TCPDiagnostics.Builder.class);
}
public static Parser<TCPDiagnostics> PARSER =
new AbstractParser<TCPDiagnostics>() {
public TCPDiagnostics parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TCPDiagnostics(input, er);
}
};
@Override
public Parser<TCPDiagnostics> getParserForType() {
return PARSER;
}
private int b0_;
public static final int LISTENING_ADDRESS_FIELD_NUMBER = 1;
private Diagnostics.PBInetSocketAddress listeningAddress_;
public boolean hasListeningAddress() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBInetSocketAddress getListeningAddress() {
return listeningAddress_;
}
public Diagnostics.PBInetSocketAddressOrBuilder getListeningAddressOrBuilder() {
return listeningAddress_;
}
public static final int REACHABLE_DEVICES_FIELD_NUMBER = 2;
private List<Diagnostics.TCPDevice> reachableDevices_;
public List<Diagnostics.TCPDevice> getReachableDevicesList() {
return reachableDevices_;
}
public List<? extends Diagnostics.TCPDeviceOrBuilder> 
getReachableDevicesOrBuilderList() {
return reachableDevices_;
}
public int getReachableDevicesCount() {
return reachableDevices_.size();
}
public Diagnostics.TCPDevice getReachableDevices(int index) {
return reachableDevices_.get(index);
}
public Diagnostics.TCPDeviceOrBuilder getReachableDevicesOrBuilder(
int index) {
return reachableDevices_.get(index);
}
private void initFields() {
listeningAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
reachableDevices_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasListeningAddress()) {
mii = 0;
return false;
}
for (int i = 0; i < getReachableDevicesCount(); i++) {
if (!getReachableDevices(i).isInitialized()) {
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
output.writeMessage(1, listeningAddress_);
}
for (int i = 0; i < reachableDevices_.size(); i++) {
output.writeMessage(2, reachableDevices_.get(i));
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
.computeMessageSize(1, listeningAddress_);
}
for (int i = 0; i < reachableDevices_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, reachableDevices_.get(i));
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
public static Diagnostics.TCPDiagnostics parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TCPDiagnostics parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TCPDiagnostics parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TCPDiagnostics parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TCPDiagnostics parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TCPDiagnostics parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TCPDiagnostics parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TCPDiagnostics parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TCPDiagnostics parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TCPDiagnostics parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TCPDiagnostics prototype) {
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
Diagnostics.TCPDiagnosticsOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TCPDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TCPDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TCPDiagnostics.class, Diagnostics.TCPDiagnostics.Builder.class);
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
getListeningAddressFieldBuilder();
getReachableDevicesFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (listeningAddressBuilder_ == null) {
listeningAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
} else {
listeningAddressBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
if (reachableDevicesBuilder_ == null) {
reachableDevices_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
} else {
reachableDevicesBuilder_.clear();
}
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TCPDiagnostics_descriptor;
}
public Diagnostics.TCPDiagnostics getDefaultInstanceForType() {
return Diagnostics.TCPDiagnostics.getDefaultInstance();
}
public Diagnostics.TCPDiagnostics build() {
Diagnostics.TCPDiagnostics result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TCPDiagnostics buildPartial() {
Diagnostics.TCPDiagnostics result = new Diagnostics.TCPDiagnostics(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
if (listeningAddressBuilder_ == null) {
result.listeningAddress_ = listeningAddress_;
} else {
result.listeningAddress_ = listeningAddressBuilder_.build();
}
if (reachableDevicesBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002)) {
reachableDevices_ = Collections.unmodifiableList(reachableDevices_);
b0_ = (b0_ & ~0x00000002);
}
result.reachableDevices_ = reachableDevices_;
} else {
result.reachableDevices_ = reachableDevicesBuilder_.build();
}
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TCPDiagnostics) {
return mergeFrom((Diagnostics.TCPDiagnostics)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TCPDiagnostics other) {
if (other == Diagnostics.TCPDiagnostics.getDefaultInstance()) return this;
if (other.hasListeningAddress()) {
mergeListeningAddress(other.getListeningAddress());
}
if (reachableDevicesBuilder_ == null) {
if (!other.reachableDevices_.isEmpty()) {
if (reachableDevices_.isEmpty()) {
reachableDevices_ = other.reachableDevices_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureReachableDevicesIsMutable();
reachableDevices_.addAll(other.reachableDevices_);
}
onChanged();
}
} else {
if (!other.reachableDevices_.isEmpty()) {
if (reachableDevicesBuilder_.isEmpty()) {
reachableDevicesBuilder_.dispose();
reachableDevicesBuilder_ = null;
reachableDevices_ = other.reachableDevices_;
b0_ = (b0_ & ~0x00000002);
reachableDevicesBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getReachableDevicesFieldBuilder() : null;
} else {
reachableDevicesBuilder_.addAllMessages(other.reachableDevices_);
}
}
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasListeningAddress()) {
return false;
}
for (int i = 0; i < getReachableDevicesCount(); i++) {
if (!getReachableDevices(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TCPDiagnostics pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TCPDiagnostics) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.PBInetSocketAddress listeningAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder> listeningAddressBuilder_;
public boolean hasListeningAddress() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBInetSocketAddress getListeningAddress() {
if (listeningAddressBuilder_ == null) {
return listeningAddress_;
} else {
return listeningAddressBuilder_.getMessage();
}
}
public Builder setListeningAddress(Diagnostics.PBInetSocketAddress value) {
if (listeningAddressBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
listeningAddress_ = value;
onChanged();
} else {
listeningAddressBuilder_.setMessage(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder setListeningAddress(
Diagnostics.PBInetSocketAddress.Builder bdForValue) {
if (listeningAddressBuilder_ == null) {
listeningAddress_ = bdForValue.build();
onChanged();
} else {
listeningAddressBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000001;
return this;
}
public Builder mergeListeningAddress(Diagnostics.PBInetSocketAddress value) {
if (listeningAddressBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001) &&
listeningAddress_ != Diagnostics.PBInetSocketAddress.getDefaultInstance()) {
listeningAddress_ =
Diagnostics.PBInetSocketAddress.newBuilder(listeningAddress_).mergeFrom(value).buildPartial();
} else {
listeningAddress_ = value;
}
onChanged();
} else {
listeningAddressBuilder_.mergeFrom(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder clearListeningAddress() {
if (listeningAddressBuilder_ == null) {
listeningAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
onChanged();
} else {
listeningAddressBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
return this;
}
public Diagnostics.PBInetSocketAddress.Builder getListeningAddressBuilder() {
b0_ |= 0x00000001;
onChanged();
return getListeningAddressFieldBuilder().getBuilder();
}
public Diagnostics.PBInetSocketAddressOrBuilder getListeningAddressOrBuilder() {
if (listeningAddressBuilder_ != null) {
return listeningAddressBuilder_.getMessageOrBuilder();
} else {
return listeningAddress_;
}
}
private SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder> 
getListeningAddressFieldBuilder() {
if (listeningAddressBuilder_ == null) {
listeningAddressBuilder_ = new SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder>(
getListeningAddress(),
getParentForChildren(),
isClean());
listeningAddress_ = null;
}
return listeningAddressBuilder_;
}
private List<Diagnostics.TCPDevice> reachableDevices_ =
Collections.emptyList();
private void ensureReachableDevicesIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
reachableDevices_ = new ArrayList<Diagnostics.TCPDevice>(reachableDevices_);
b0_ |= 0x00000002;
}
}
private RepeatedFieldBuilder<
Diagnostics.TCPDevice, Diagnostics.TCPDevice.Builder, Diagnostics.TCPDeviceOrBuilder> reachableDevicesBuilder_;
public List<Diagnostics.TCPDevice> getReachableDevicesList() {
if (reachableDevicesBuilder_ == null) {
return Collections.unmodifiableList(reachableDevices_);
} else {
return reachableDevicesBuilder_.getMessageList();
}
}
public int getReachableDevicesCount() {
if (reachableDevicesBuilder_ == null) {
return reachableDevices_.size();
} else {
return reachableDevicesBuilder_.getCount();
}
}
public Diagnostics.TCPDevice getReachableDevices(int index) {
if (reachableDevicesBuilder_ == null) {
return reachableDevices_.get(index);
} else {
return reachableDevicesBuilder_.getMessage(index);
}
}
public Builder setReachableDevices(
int index, Diagnostics.TCPDevice value) {
if (reachableDevicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureReachableDevicesIsMutable();
reachableDevices_.set(index, value);
onChanged();
} else {
reachableDevicesBuilder_.setMessage(index, value);
}
return this;
}
public Builder setReachableDevices(
int index, Diagnostics.TCPDevice.Builder bdForValue) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.set(index, bdForValue.build());
onChanged();
} else {
reachableDevicesBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addReachableDevices(Diagnostics.TCPDevice value) {
if (reachableDevicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureReachableDevicesIsMutable();
reachableDevices_.add(value);
onChanged();
} else {
reachableDevicesBuilder_.addMessage(value);
}
return this;
}
public Builder addReachableDevices(
int index, Diagnostics.TCPDevice value) {
if (reachableDevicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureReachableDevicesIsMutable();
reachableDevices_.add(index, value);
onChanged();
} else {
reachableDevicesBuilder_.addMessage(index, value);
}
return this;
}
public Builder addReachableDevices(
Diagnostics.TCPDevice.Builder bdForValue) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.add(bdForValue.build());
onChanged();
} else {
reachableDevicesBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addReachableDevices(
int index, Diagnostics.TCPDevice.Builder bdForValue) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.add(index, bdForValue.build());
onChanged();
} else {
reachableDevicesBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllReachableDevices(
Iterable<? extends Diagnostics.TCPDevice> values) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
AbstractMessageLite.Builder.addAll(
values, reachableDevices_);
onChanged();
} else {
reachableDevicesBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearReachableDevices() {
if (reachableDevicesBuilder_ == null) {
reachableDevices_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
onChanged();
} else {
reachableDevicesBuilder_.clear();
}
return this;
}
public Builder removeReachableDevices(int index) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.remove(index);
onChanged();
} else {
reachableDevicesBuilder_.remove(index);
}
return this;
}
public Diagnostics.TCPDevice.Builder getReachableDevicesBuilder(
int index) {
return getReachableDevicesFieldBuilder().getBuilder(index);
}
public Diagnostics.TCPDeviceOrBuilder getReachableDevicesOrBuilder(
int index) {
if (reachableDevicesBuilder_ == null) {
return reachableDevices_.get(index);  } else {
return reachableDevicesBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.TCPDeviceOrBuilder> 
getReachableDevicesOrBuilderList() {
if (reachableDevicesBuilder_ != null) {
return reachableDevicesBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(reachableDevices_);
}
}
public Diagnostics.TCPDevice.Builder addReachableDevicesBuilder() {
return getReachableDevicesFieldBuilder().addBuilder(
Diagnostics.TCPDevice.getDefaultInstance());
}
public Diagnostics.TCPDevice.Builder addReachableDevicesBuilder(
int index) {
return getReachableDevicesFieldBuilder().addBuilder(
index, Diagnostics.TCPDevice.getDefaultInstance());
}
public List<Diagnostics.TCPDevice.Builder> 
getReachableDevicesBuilderList() {
return getReachableDevicesFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.TCPDevice, Diagnostics.TCPDevice.Builder, Diagnostics.TCPDeviceOrBuilder> 
getReachableDevicesFieldBuilder() {
if (reachableDevicesBuilder_ == null) {
reachableDevicesBuilder_ = new RepeatedFieldBuilder<
Diagnostics.TCPDevice, Diagnostics.TCPDevice.Builder, Diagnostics.TCPDeviceOrBuilder>(
reachableDevices_,
((b0_ & 0x00000002) == 0x00000002),
getParentForChildren(),
isClean());
reachableDevices_ = null;
}
return reachableDevicesBuilder_;
}
}
static {
defaultInstance = new TCPDiagnostics(true);
defaultInstance.initFields();
}
}
public interface TCPDeviceOrBuilder extends
MessageOrBuilder {
boolean hasDid();
ByteString getDid();
List<Diagnostics.TCPChannel> 
getChannelList();
Diagnostics.TCPChannel getChannel(int index);
int getChannelCount();
List<? extends Diagnostics.TCPChannelOrBuilder> 
getChannelOrBuilderList();
Diagnostics.TCPChannelOrBuilder getChannelOrBuilder(
int index);
}
public static final class TCPDevice extends
GeneratedMessage implements
TCPDeviceOrBuilder {
private TCPDevice(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TCPDevice(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TCPDevice defaultInstance;
public static TCPDevice getDefaultInstance() {
return defaultInstance;
}
public TCPDevice getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TCPDevice(
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
case 10: {
b0_ |= 0x00000001;
did_ = input.readBytes();
break;
}
case 26: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
channel_ = new ArrayList<Diagnostics.TCPChannel>();
mutable_b0_ |= 0x00000002;
}
channel_.add(input.readMessage(Diagnostics.TCPChannel.PARSER, er));
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
channel_ = Collections.unmodifiableList(channel_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TCPDevice_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TCPDevice_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TCPDevice.class, Diagnostics.TCPDevice.Builder.class);
}
public static Parser<TCPDevice> PARSER =
new AbstractParser<TCPDevice>() {
public TCPDevice parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TCPDevice(input, er);
}
};
@Override
public Parser<TCPDevice> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DID_FIELD_NUMBER = 1;
private ByteString did_;
public boolean hasDid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getDid() {
return did_;
}
public static final int CHANNEL_FIELD_NUMBER = 3;
private List<Diagnostics.TCPChannel> channel_;
public List<Diagnostics.TCPChannel> getChannelList() {
return channel_;
}
public List<? extends Diagnostics.TCPChannelOrBuilder> 
getChannelOrBuilderList() {
return channel_;
}
public int getChannelCount() {
return channel_.size();
}
public Diagnostics.TCPChannel getChannel(int index) {
return channel_.get(index);
}
public Diagnostics.TCPChannelOrBuilder getChannelOrBuilder(
int index) {
return channel_.get(index);
}
private void initFields() {
did_ = ByteString.EMPTY;
channel_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
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
output.writeBytes(1, did_);
}
for (int i = 0; i < channel_.size(); i++) {
output.writeMessage(3, channel_.get(i));
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
.computeBytesSize(1, did_);
}
for (int i = 0; i < channel_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(3, channel_.get(i));
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
public static Diagnostics.TCPDevice parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TCPDevice parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TCPDevice parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TCPDevice parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TCPDevice parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TCPDevice parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TCPDevice parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TCPDevice parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TCPDevice parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TCPDevice parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TCPDevice prototype) {
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
Diagnostics.TCPDeviceOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TCPDevice_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TCPDevice_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TCPDevice.class, Diagnostics.TCPDevice.Builder.class);
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
getChannelFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
did_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
if (channelBuilder_ == null) {
channel_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
} else {
channelBuilder_.clear();
}
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TCPDevice_descriptor;
}
public Diagnostics.TCPDevice getDefaultInstanceForType() {
return Diagnostics.TCPDevice.getDefaultInstance();
}
public Diagnostics.TCPDevice build() {
Diagnostics.TCPDevice result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TCPDevice buildPartial() {
Diagnostics.TCPDevice result = new Diagnostics.TCPDevice(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.did_ = did_;
if (channelBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002)) {
channel_ = Collections.unmodifiableList(channel_);
b0_ = (b0_ & ~0x00000002);
}
result.channel_ = channel_;
} else {
result.channel_ = channelBuilder_.build();
}
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TCPDevice) {
return mergeFrom((Diagnostics.TCPDevice)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TCPDevice other) {
if (other == Diagnostics.TCPDevice.getDefaultInstance()) return this;
if (other.hasDid()) {
setDid(other.getDid());
}
if (channelBuilder_ == null) {
if (!other.channel_.isEmpty()) {
if (channel_.isEmpty()) {
channel_ = other.channel_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureChannelIsMutable();
channel_.addAll(other.channel_);
}
onChanged();
}
} else {
if (!other.channel_.isEmpty()) {
if (channelBuilder_.isEmpty()) {
channelBuilder_.dispose();
channelBuilder_ = null;
channel_ = other.channel_;
b0_ = (b0_ & ~0x00000002);
channelBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getChannelFieldBuilder() : null;
} else {
channelBuilder_.addAllMessages(other.channel_);
}
}
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasDid()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TCPDevice pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TCPDevice) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString did_ = ByteString.EMPTY;
public boolean hasDid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getDid() {
return did_;
}
public Builder setDid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
did_ = value;
onChanged();
return this;
}
public Builder clearDid() {
b0_ = (b0_ & ~0x00000001);
did_ = getDefaultInstance().getDid();
onChanged();
return this;
}
private List<Diagnostics.TCPChannel> channel_ =
Collections.emptyList();
private void ensureChannelIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
channel_ = new ArrayList<Diagnostics.TCPChannel>(channel_);
b0_ |= 0x00000002;
}
}
private RepeatedFieldBuilder<
Diagnostics.TCPChannel, Diagnostics.TCPChannel.Builder, Diagnostics.TCPChannelOrBuilder> channelBuilder_;
public List<Diagnostics.TCPChannel> getChannelList() {
if (channelBuilder_ == null) {
return Collections.unmodifiableList(channel_);
} else {
return channelBuilder_.getMessageList();
}
}
public int getChannelCount() {
if (channelBuilder_ == null) {
return channel_.size();
} else {
return channelBuilder_.getCount();
}
}
public Diagnostics.TCPChannel getChannel(int index) {
if (channelBuilder_ == null) {
return channel_.get(index);
} else {
return channelBuilder_.getMessage(index);
}
}
public Builder setChannel(
int index, Diagnostics.TCPChannel value) {
if (channelBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureChannelIsMutable();
channel_.set(index, value);
onChanged();
} else {
channelBuilder_.setMessage(index, value);
}
return this;
}
public Builder setChannel(
int index, Diagnostics.TCPChannel.Builder bdForValue) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.set(index, bdForValue.build());
onChanged();
} else {
channelBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addChannel(Diagnostics.TCPChannel value) {
if (channelBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureChannelIsMutable();
channel_.add(value);
onChanged();
} else {
channelBuilder_.addMessage(value);
}
return this;
}
public Builder addChannel(
int index, Diagnostics.TCPChannel value) {
if (channelBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureChannelIsMutable();
channel_.add(index, value);
onChanged();
} else {
channelBuilder_.addMessage(index, value);
}
return this;
}
public Builder addChannel(
Diagnostics.TCPChannel.Builder bdForValue) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.add(bdForValue.build());
onChanged();
} else {
channelBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addChannel(
int index, Diagnostics.TCPChannel.Builder bdForValue) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.add(index, bdForValue.build());
onChanged();
} else {
channelBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllChannel(
Iterable<? extends Diagnostics.TCPChannel> values) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
AbstractMessageLite.Builder.addAll(
values, channel_);
onChanged();
} else {
channelBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearChannel() {
if (channelBuilder_ == null) {
channel_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
onChanged();
} else {
channelBuilder_.clear();
}
return this;
}
public Builder removeChannel(int index) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.remove(index);
onChanged();
} else {
channelBuilder_.remove(index);
}
return this;
}
public Diagnostics.TCPChannel.Builder getChannelBuilder(
int index) {
return getChannelFieldBuilder().getBuilder(index);
}
public Diagnostics.TCPChannelOrBuilder getChannelOrBuilder(
int index) {
if (channelBuilder_ == null) {
return channel_.get(index);  } else {
return channelBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.TCPChannelOrBuilder> 
getChannelOrBuilderList() {
if (channelBuilder_ != null) {
return channelBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(channel_);
}
}
public Diagnostics.TCPChannel.Builder addChannelBuilder() {
return getChannelFieldBuilder().addBuilder(
Diagnostics.TCPChannel.getDefaultInstance());
}
public Diagnostics.TCPChannel.Builder addChannelBuilder(
int index) {
return getChannelFieldBuilder().addBuilder(
index, Diagnostics.TCPChannel.getDefaultInstance());
}
public List<Diagnostics.TCPChannel.Builder> 
getChannelBuilderList() {
return getChannelFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.TCPChannel, Diagnostics.TCPChannel.Builder, Diagnostics.TCPChannelOrBuilder> 
getChannelFieldBuilder() {
if (channelBuilder_ == null) {
channelBuilder_ = new RepeatedFieldBuilder<
Diagnostics.TCPChannel, Diagnostics.TCPChannel.Builder, Diagnostics.TCPChannelOrBuilder>(
channel_,
((b0_ & 0x00000002) == 0x00000002),
getParentForChildren(),
isClean());
channel_ = null;
}
return channelBuilder_;
}
}
static {
defaultInstance = new TCPDevice(true);
defaultInstance.initFields();
}
}
public interface TCPChannelOrBuilder extends
MessageOrBuilder {
boolean hasState();
Diagnostics.ChannelState getState();
boolean hasBytesSent();
long getBytesSent();
boolean hasBytesReceived();
long getBytesReceived();
boolean hasLifetime();
long getLifetime();
boolean hasOriginator();
boolean getOriginator();
boolean hasRemoteAddress();
Diagnostics.PBInetSocketAddress getRemoteAddress();
Diagnostics.PBInetSocketAddressOrBuilder getRemoteAddressOrBuilder();
boolean hasRoundTripTime();
long getRoundTripTime();
}
public static final class TCPChannel extends
GeneratedMessage implements
TCPChannelOrBuilder {
private TCPChannel(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TCPChannel(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TCPChannel defaultInstance;
public static TCPChannel getDefaultInstance() {
return defaultInstance;
}
public TCPChannel getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TCPChannel(
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
Diagnostics.ChannelState value = Diagnostics.ChannelState.valueOf(rawValue);
if (value == null) {
unknownFields.mergeVarintField(1, rawValue);
} else {
b0_ |= 0x00000001;
state_ = value;
}
break;
}
case 16: {
b0_ |= 0x00000002;
bytesSent_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
bytesReceived_ = input.readUInt64();
break;
}
case 32: {
b0_ |= 0x00000008;
lifetime_ = input.readUInt64();
break;
}
case 40: {
b0_ |= 0x00000010;
originator_ = input.readBool();
break;
}
case 50: {
Diagnostics.PBInetSocketAddress.Builder subBuilder = null;
if (((b0_ & 0x00000020) == 0x00000020)) {
subBuilder = remoteAddress_.toBuilder();
}
remoteAddress_ = input.readMessage(Diagnostics.PBInetSocketAddress.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(remoteAddress_);
remoteAddress_ = subBuilder.buildPartial();
}
b0_ |= 0x00000020;
break;
}
case 56: {
b0_ |= 0x00000040;
roundTripTime_ = input.readUInt64();
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
return Diagnostics.internal_static_TCPChannel_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TCPChannel_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TCPChannel.class, Diagnostics.TCPChannel.Builder.class);
}
public static Parser<TCPChannel> PARSER =
new AbstractParser<TCPChannel>() {
public TCPChannel parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TCPChannel(input, er);
}
};
@Override
public Parser<TCPChannel> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STATE_FIELD_NUMBER = 1;
private Diagnostics.ChannelState state_;
public boolean hasState() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.ChannelState getState() {
return state_;
}
public static final int BYTES_SENT_FIELD_NUMBER = 2;
private long bytesSent_;
public boolean hasBytesSent() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getBytesSent() {
return bytesSent_;
}
public static final int BYTES_RECEIVED_FIELD_NUMBER = 3;
private long bytesReceived_;
public boolean hasBytesReceived() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getBytesReceived() {
return bytesReceived_;
}
public static final int LIFETIME_FIELD_NUMBER = 4;
private long lifetime_;
public boolean hasLifetime() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getLifetime() {
return lifetime_;
}
public static final int ORIGINATOR_FIELD_NUMBER = 5;
private boolean originator_;
public boolean hasOriginator() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public boolean getOriginator() {
return originator_;
}
public static final int REMOTE_ADDRESS_FIELD_NUMBER = 6;
private Diagnostics.PBInetSocketAddress remoteAddress_;
public boolean hasRemoteAddress() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public Diagnostics.PBInetSocketAddress getRemoteAddress() {
return remoteAddress_;
}
public Diagnostics.PBInetSocketAddressOrBuilder getRemoteAddressOrBuilder() {
return remoteAddress_;
}
public static final int ROUND_TRIP_TIME_FIELD_NUMBER = 7;
private long roundTripTime_;
public boolean hasRoundTripTime() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public long getRoundTripTime() {
return roundTripTime_;
}
private void initFields() {
state_ = Diagnostics.ChannelState.CONNECTING;
bytesSent_ = 0L;
bytesReceived_ = 0L;
lifetime_ = 0L;
originator_ = false;
remoteAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
roundTripTime_ = 0L;
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
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeEnum(1, state_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, bytesSent_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, bytesReceived_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeUInt64(4, lifetime_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeBool(5, originator_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeMessage(6, remoteAddress_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeUInt64(7, roundTripTime_);
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
.computeEnumSize(1, state_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, bytesSent_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, bytesReceived_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeUInt64Size(4, lifetime_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeBoolSize(5, originator_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeMessageSize(6, remoteAddress_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeUInt64Size(7, roundTripTime_);
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
public static Diagnostics.TCPChannel parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TCPChannel parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TCPChannel parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TCPChannel parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TCPChannel parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TCPChannel parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TCPChannel parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TCPChannel parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TCPChannel parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TCPChannel parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TCPChannel prototype) {
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
Diagnostics.TCPChannelOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TCPChannel_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TCPChannel_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TCPChannel.class, Diagnostics.TCPChannel.Builder.class);
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
getRemoteAddressFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
state_ = Diagnostics.ChannelState.CONNECTING;
b0_ = (b0_ & ~0x00000001);
bytesSent_ = 0L;
b0_ = (b0_ & ~0x00000002);
bytesReceived_ = 0L;
b0_ = (b0_ & ~0x00000004);
lifetime_ = 0L;
b0_ = (b0_ & ~0x00000008);
originator_ = false;
b0_ = (b0_ & ~0x00000010);
if (remoteAddressBuilder_ == null) {
remoteAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
} else {
remoteAddressBuilder_.clear();
}
b0_ = (b0_ & ~0x00000020);
roundTripTime_ = 0L;
b0_ = (b0_ & ~0x00000040);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TCPChannel_descriptor;
}
public Diagnostics.TCPChannel getDefaultInstanceForType() {
return Diagnostics.TCPChannel.getDefaultInstance();
}
public Diagnostics.TCPChannel build() {
Diagnostics.TCPChannel result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TCPChannel buildPartial() {
Diagnostics.TCPChannel result = new Diagnostics.TCPChannel(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.state_ = state_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.bytesSent_ = bytesSent_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.bytesReceived_ = bytesReceived_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.lifetime_ = lifetime_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.originator_ = originator_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
if (remoteAddressBuilder_ == null) {
result.remoteAddress_ = remoteAddress_;
} else {
result.remoteAddress_ = remoteAddressBuilder_.build();
}
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.roundTripTime_ = roundTripTime_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TCPChannel) {
return mergeFrom((Diagnostics.TCPChannel)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TCPChannel other) {
if (other == Diagnostics.TCPChannel.getDefaultInstance()) return this;
if (other.hasState()) {
setState(other.getState());
}
if (other.hasBytesSent()) {
setBytesSent(other.getBytesSent());
}
if (other.hasBytesReceived()) {
setBytesReceived(other.getBytesReceived());
}
if (other.hasLifetime()) {
setLifetime(other.getLifetime());
}
if (other.hasOriginator()) {
setOriginator(other.getOriginator());
}
if (other.hasRemoteAddress()) {
mergeRemoteAddress(other.getRemoteAddress());
}
if (other.hasRoundTripTime()) {
setRoundTripTime(other.getRoundTripTime());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TCPChannel pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TCPChannel) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.ChannelState state_ = Diagnostics.ChannelState.CONNECTING;
public boolean hasState() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.ChannelState getState() {
return state_;
}
public Builder setState(Diagnostics.ChannelState value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
state_ = value;
onChanged();
return this;
}
public Builder clearState() {
b0_ = (b0_ & ~0x00000001);
state_ = Diagnostics.ChannelState.CONNECTING;
onChanged();
return this;
}
private long bytesSent_ ;
public boolean hasBytesSent() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getBytesSent() {
return bytesSent_;
}
public Builder setBytesSent(long value) {
b0_ |= 0x00000002;
bytesSent_ = value;
onChanged();
return this;
}
public Builder clearBytesSent() {
b0_ = (b0_ & ~0x00000002);
bytesSent_ = 0L;
onChanged();
return this;
}
private long bytesReceived_ ;
public boolean hasBytesReceived() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getBytesReceived() {
return bytesReceived_;
}
public Builder setBytesReceived(long value) {
b0_ |= 0x00000004;
bytesReceived_ = value;
onChanged();
return this;
}
public Builder clearBytesReceived() {
b0_ = (b0_ & ~0x00000004);
bytesReceived_ = 0L;
onChanged();
return this;
}
private long lifetime_ ;
public boolean hasLifetime() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getLifetime() {
return lifetime_;
}
public Builder setLifetime(long value) {
b0_ |= 0x00000008;
lifetime_ = value;
onChanged();
return this;
}
public Builder clearLifetime() {
b0_ = (b0_ & ~0x00000008);
lifetime_ = 0L;
onChanged();
return this;
}
private boolean originator_ ;
public boolean hasOriginator() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public boolean getOriginator() {
return originator_;
}
public Builder setOriginator(boolean value) {
b0_ |= 0x00000010;
originator_ = value;
onChanged();
return this;
}
public Builder clearOriginator() {
b0_ = (b0_ & ~0x00000010);
originator_ = false;
onChanged();
return this;
}
private Diagnostics.PBInetSocketAddress remoteAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder> remoteAddressBuilder_;
public boolean hasRemoteAddress() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public Diagnostics.PBInetSocketAddress getRemoteAddress() {
if (remoteAddressBuilder_ == null) {
return remoteAddress_;
} else {
return remoteAddressBuilder_.getMessage();
}
}
public Builder setRemoteAddress(Diagnostics.PBInetSocketAddress value) {
if (remoteAddressBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
remoteAddress_ = value;
onChanged();
} else {
remoteAddressBuilder_.setMessage(value);
}
b0_ |= 0x00000020;
return this;
}
public Builder setRemoteAddress(
Diagnostics.PBInetSocketAddress.Builder bdForValue) {
if (remoteAddressBuilder_ == null) {
remoteAddress_ = bdForValue.build();
onChanged();
} else {
remoteAddressBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000020;
return this;
}
public Builder mergeRemoteAddress(Diagnostics.PBInetSocketAddress value) {
if (remoteAddressBuilder_ == null) {
if (((b0_ & 0x00000020) == 0x00000020) &&
remoteAddress_ != Diagnostics.PBInetSocketAddress.getDefaultInstance()) {
remoteAddress_ =
Diagnostics.PBInetSocketAddress.newBuilder(remoteAddress_).mergeFrom(value).buildPartial();
} else {
remoteAddress_ = value;
}
onChanged();
} else {
remoteAddressBuilder_.mergeFrom(value);
}
b0_ |= 0x00000020;
return this;
}
public Builder clearRemoteAddress() {
if (remoteAddressBuilder_ == null) {
remoteAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
onChanged();
} else {
remoteAddressBuilder_.clear();
}
b0_ = (b0_ & ~0x00000020);
return this;
}
public Diagnostics.PBInetSocketAddress.Builder getRemoteAddressBuilder() {
b0_ |= 0x00000020;
onChanged();
return getRemoteAddressFieldBuilder().getBuilder();
}
public Diagnostics.PBInetSocketAddressOrBuilder getRemoteAddressOrBuilder() {
if (remoteAddressBuilder_ != null) {
return remoteAddressBuilder_.getMessageOrBuilder();
} else {
return remoteAddress_;
}
}
private SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder> 
getRemoteAddressFieldBuilder() {
if (remoteAddressBuilder_ == null) {
remoteAddressBuilder_ = new SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder>(
getRemoteAddress(),
getParentForChildren(),
isClean());
remoteAddress_ = null;
}
return remoteAddressBuilder_;
}
private long roundTripTime_ ;
public boolean hasRoundTripTime() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public long getRoundTripTime() {
return roundTripTime_;
}
public Builder setRoundTripTime(long value) {
b0_ |= 0x00000040;
roundTripTime_ = value;
onChanged();
return this;
}
public Builder clearRoundTripTime() {
b0_ = (b0_ & ~0x00000040);
roundTripTime_ = 0L;
onChanged();
return this;
}
}
static {
defaultInstance = new TCPChannel(true);
defaultInstance.initFields();
}
}
public interface ZephyrDiagnosticsOrBuilder extends
MessageOrBuilder {
boolean hasZephyrServer();
Diagnostics.ServerStatus getZephyrServer();
Diagnostics.ServerStatusOrBuilder getZephyrServerOrBuilder();
List<Diagnostics.ZephyrDevice> 
getReachableDevicesList();
Diagnostics.ZephyrDevice getReachableDevices(int index);
int getReachableDevicesCount();
List<? extends Diagnostics.ZephyrDeviceOrBuilder> 
getReachableDevicesOrBuilderList();
Diagnostics.ZephyrDeviceOrBuilder getReachableDevicesOrBuilder(
int index);
}
public static final class ZephyrDiagnostics extends
GeneratedMessage implements
ZephyrDiagnosticsOrBuilder {
private ZephyrDiagnostics(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ZephyrDiagnostics(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final ZephyrDiagnostics defaultInstance;
public static ZephyrDiagnostics getDefaultInstance() {
return defaultInstance;
}
public ZephyrDiagnostics getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private ZephyrDiagnostics(
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
case 18: {
Diagnostics.ServerStatus.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = zephyrServer_.toBuilder();
}
zephyrServer_ = input.readMessage(Diagnostics.ServerStatus.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(zephyrServer_);
zephyrServer_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 26: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
reachableDevices_ = new ArrayList<Diagnostics.ZephyrDevice>();
mutable_b0_ |= 0x00000002;
}
reachableDevices_.add(input.readMessage(Diagnostics.ZephyrDevice.PARSER, er));
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
reachableDevices_ = Collections.unmodifiableList(reachableDevices_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_ZephyrDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ZephyrDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ZephyrDiagnostics.class, Diagnostics.ZephyrDiagnostics.Builder.class);
}
public static Parser<ZephyrDiagnostics> PARSER =
new AbstractParser<ZephyrDiagnostics>() {
public ZephyrDiagnostics parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ZephyrDiagnostics(input, er);
}
};
@Override
public Parser<ZephyrDiagnostics> getParserForType() {
return PARSER;
}
private int b0_;
public static final int ZEPHYR_SERVER_FIELD_NUMBER = 2;
private Diagnostics.ServerStatus zephyrServer_;
public boolean hasZephyrServer() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.ServerStatus getZephyrServer() {
return zephyrServer_;
}
public Diagnostics.ServerStatusOrBuilder getZephyrServerOrBuilder() {
return zephyrServer_;
}
public static final int REACHABLE_DEVICES_FIELD_NUMBER = 3;
private List<Diagnostics.ZephyrDevice> reachableDevices_;
public List<Diagnostics.ZephyrDevice> getReachableDevicesList() {
return reachableDevices_;
}
public List<? extends Diagnostics.ZephyrDeviceOrBuilder> 
getReachableDevicesOrBuilderList() {
return reachableDevices_;
}
public int getReachableDevicesCount() {
return reachableDevices_.size();
}
public Diagnostics.ZephyrDevice getReachableDevices(int index) {
return reachableDevices_.get(index);
}
public Diagnostics.ZephyrDeviceOrBuilder getReachableDevicesOrBuilder(
int index) {
return reachableDevices_.get(index);
}
private void initFields() {
zephyrServer_ = Diagnostics.ServerStatus.getDefaultInstance();
reachableDevices_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasZephyrServer()) {
mii = 0;
return false;
}
if (!getZephyrServer().isInitialized()) {
mii = 0;
return false;
}
for (int i = 0; i < getReachableDevicesCount(); i++) {
if (!getReachableDevices(i).isInitialized()) {
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
output.writeMessage(2, zephyrServer_);
}
for (int i = 0; i < reachableDevices_.size(); i++) {
output.writeMessage(3, reachableDevices_.get(i));
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
.computeMessageSize(2, zephyrServer_);
}
for (int i = 0; i < reachableDevices_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(3, reachableDevices_.get(i));
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
public static Diagnostics.ZephyrDiagnostics parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.ZephyrDiagnostics parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.ZephyrDiagnostics parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ZephyrDiagnostics parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.ZephyrDiagnostics prototype) {
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
Diagnostics.ZephyrDiagnosticsOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_ZephyrDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ZephyrDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ZephyrDiagnostics.class, Diagnostics.ZephyrDiagnostics.Builder.class);
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
getZephyrServerFieldBuilder();
getReachableDevicesFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (zephyrServerBuilder_ == null) {
zephyrServer_ = Diagnostics.ServerStatus.getDefaultInstance();
} else {
zephyrServerBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
if (reachableDevicesBuilder_ == null) {
reachableDevices_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
} else {
reachableDevicesBuilder_.clear();
}
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_ZephyrDiagnostics_descriptor;
}
public Diagnostics.ZephyrDiagnostics getDefaultInstanceForType() {
return Diagnostics.ZephyrDiagnostics.getDefaultInstance();
}
public Diagnostics.ZephyrDiagnostics build() {
Diagnostics.ZephyrDiagnostics result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.ZephyrDiagnostics buildPartial() {
Diagnostics.ZephyrDiagnostics result = new Diagnostics.ZephyrDiagnostics(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
if (zephyrServerBuilder_ == null) {
result.zephyrServer_ = zephyrServer_;
} else {
result.zephyrServer_ = zephyrServerBuilder_.build();
}
if (reachableDevicesBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002)) {
reachableDevices_ = Collections.unmodifiableList(reachableDevices_);
b0_ = (b0_ & ~0x00000002);
}
result.reachableDevices_ = reachableDevices_;
} else {
result.reachableDevices_ = reachableDevicesBuilder_.build();
}
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.ZephyrDiagnostics) {
return mergeFrom((Diagnostics.ZephyrDiagnostics)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.ZephyrDiagnostics other) {
if (other == Diagnostics.ZephyrDiagnostics.getDefaultInstance()) return this;
if (other.hasZephyrServer()) {
mergeZephyrServer(other.getZephyrServer());
}
if (reachableDevicesBuilder_ == null) {
if (!other.reachableDevices_.isEmpty()) {
if (reachableDevices_.isEmpty()) {
reachableDevices_ = other.reachableDevices_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureReachableDevicesIsMutable();
reachableDevices_.addAll(other.reachableDevices_);
}
onChanged();
}
} else {
if (!other.reachableDevices_.isEmpty()) {
if (reachableDevicesBuilder_.isEmpty()) {
reachableDevicesBuilder_.dispose();
reachableDevicesBuilder_ = null;
reachableDevices_ = other.reachableDevices_;
b0_ = (b0_ & ~0x00000002);
reachableDevicesBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getReachableDevicesFieldBuilder() : null;
} else {
reachableDevicesBuilder_.addAllMessages(other.reachableDevices_);
}
}
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasZephyrServer()) {
return false;
}
if (!getZephyrServer().isInitialized()) {
return false;
}
for (int i = 0; i < getReachableDevicesCount(); i++) {
if (!getReachableDevices(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.ZephyrDiagnostics pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.ZephyrDiagnostics) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.ServerStatus zephyrServer_ = Diagnostics.ServerStatus.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.ServerStatus, Diagnostics.ServerStatus.Builder, Diagnostics.ServerStatusOrBuilder> zephyrServerBuilder_;
public boolean hasZephyrServer() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.ServerStatus getZephyrServer() {
if (zephyrServerBuilder_ == null) {
return zephyrServer_;
} else {
return zephyrServerBuilder_.getMessage();
}
}
public Builder setZephyrServer(Diagnostics.ServerStatus value) {
if (zephyrServerBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
zephyrServer_ = value;
onChanged();
} else {
zephyrServerBuilder_.setMessage(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder setZephyrServer(
Diagnostics.ServerStatus.Builder bdForValue) {
if (zephyrServerBuilder_ == null) {
zephyrServer_ = bdForValue.build();
onChanged();
} else {
zephyrServerBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000001;
return this;
}
public Builder mergeZephyrServer(Diagnostics.ServerStatus value) {
if (zephyrServerBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001) &&
zephyrServer_ != Diagnostics.ServerStatus.getDefaultInstance()) {
zephyrServer_ =
Diagnostics.ServerStatus.newBuilder(zephyrServer_).mergeFrom(value).buildPartial();
} else {
zephyrServer_ = value;
}
onChanged();
} else {
zephyrServerBuilder_.mergeFrom(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder clearZephyrServer() {
if (zephyrServerBuilder_ == null) {
zephyrServer_ = Diagnostics.ServerStatus.getDefaultInstance();
onChanged();
} else {
zephyrServerBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
return this;
}
public Diagnostics.ServerStatus.Builder getZephyrServerBuilder() {
b0_ |= 0x00000001;
onChanged();
return getZephyrServerFieldBuilder().getBuilder();
}
public Diagnostics.ServerStatusOrBuilder getZephyrServerOrBuilder() {
if (zephyrServerBuilder_ != null) {
return zephyrServerBuilder_.getMessageOrBuilder();
} else {
return zephyrServer_;
}
}
private SingleFieldBuilder<
Diagnostics.ServerStatus, Diagnostics.ServerStatus.Builder, Diagnostics.ServerStatusOrBuilder> 
getZephyrServerFieldBuilder() {
if (zephyrServerBuilder_ == null) {
zephyrServerBuilder_ = new SingleFieldBuilder<
Diagnostics.ServerStatus, Diagnostics.ServerStatus.Builder, Diagnostics.ServerStatusOrBuilder>(
getZephyrServer(),
getParentForChildren(),
isClean());
zephyrServer_ = null;
}
return zephyrServerBuilder_;
}
private List<Diagnostics.ZephyrDevice> reachableDevices_ =
Collections.emptyList();
private void ensureReachableDevicesIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
reachableDevices_ = new ArrayList<Diagnostics.ZephyrDevice>(reachableDevices_);
b0_ |= 0x00000002;
}
}
private RepeatedFieldBuilder<
Diagnostics.ZephyrDevice, Diagnostics.ZephyrDevice.Builder, Diagnostics.ZephyrDeviceOrBuilder> reachableDevicesBuilder_;
public List<Diagnostics.ZephyrDevice> getReachableDevicesList() {
if (reachableDevicesBuilder_ == null) {
return Collections.unmodifiableList(reachableDevices_);
} else {
return reachableDevicesBuilder_.getMessageList();
}
}
public int getReachableDevicesCount() {
if (reachableDevicesBuilder_ == null) {
return reachableDevices_.size();
} else {
return reachableDevicesBuilder_.getCount();
}
}
public Diagnostics.ZephyrDevice getReachableDevices(int index) {
if (reachableDevicesBuilder_ == null) {
return reachableDevices_.get(index);
} else {
return reachableDevicesBuilder_.getMessage(index);
}
}
public Builder setReachableDevices(
int index, Diagnostics.ZephyrDevice value) {
if (reachableDevicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureReachableDevicesIsMutable();
reachableDevices_.set(index, value);
onChanged();
} else {
reachableDevicesBuilder_.setMessage(index, value);
}
return this;
}
public Builder setReachableDevices(
int index, Diagnostics.ZephyrDevice.Builder bdForValue) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.set(index, bdForValue.build());
onChanged();
} else {
reachableDevicesBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addReachableDevices(Diagnostics.ZephyrDevice value) {
if (reachableDevicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureReachableDevicesIsMutable();
reachableDevices_.add(value);
onChanged();
} else {
reachableDevicesBuilder_.addMessage(value);
}
return this;
}
public Builder addReachableDevices(
int index, Diagnostics.ZephyrDevice value) {
if (reachableDevicesBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureReachableDevicesIsMutable();
reachableDevices_.add(index, value);
onChanged();
} else {
reachableDevicesBuilder_.addMessage(index, value);
}
return this;
}
public Builder addReachableDevices(
Diagnostics.ZephyrDevice.Builder bdForValue) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.add(bdForValue.build());
onChanged();
} else {
reachableDevicesBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addReachableDevices(
int index, Diagnostics.ZephyrDevice.Builder bdForValue) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.add(index, bdForValue.build());
onChanged();
} else {
reachableDevicesBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllReachableDevices(
Iterable<? extends Diagnostics.ZephyrDevice> values) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
AbstractMessageLite.Builder.addAll(
values, reachableDevices_);
onChanged();
} else {
reachableDevicesBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearReachableDevices() {
if (reachableDevicesBuilder_ == null) {
reachableDevices_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
onChanged();
} else {
reachableDevicesBuilder_.clear();
}
return this;
}
public Builder removeReachableDevices(int index) {
if (reachableDevicesBuilder_ == null) {
ensureReachableDevicesIsMutable();
reachableDevices_.remove(index);
onChanged();
} else {
reachableDevicesBuilder_.remove(index);
}
return this;
}
public Diagnostics.ZephyrDevice.Builder getReachableDevicesBuilder(
int index) {
return getReachableDevicesFieldBuilder().getBuilder(index);
}
public Diagnostics.ZephyrDeviceOrBuilder getReachableDevicesOrBuilder(
int index) {
if (reachableDevicesBuilder_ == null) {
return reachableDevices_.get(index);  } else {
return reachableDevicesBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.ZephyrDeviceOrBuilder> 
getReachableDevicesOrBuilderList() {
if (reachableDevicesBuilder_ != null) {
return reachableDevicesBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(reachableDevices_);
}
}
public Diagnostics.ZephyrDevice.Builder addReachableDevicesBuilder() {
return getReachableDevicesFieldBuilder().addBuilder(
Diagnostics.ZephyrDevice.getDefaultInstance());
}
public Diagnostics.ZephyrDevice.Builder addReachableDevicesBuilder(
int index) {
return getReachableDevicesFieldBuilder().addBuilder(
index, Diagnostics.ZephyrDevice.getDefaultInstance());
}
public List<Diagnostics.ZephyrDevice.Builder> 
getReachableDevicesBuilderList() {
return getReachableDevicesFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.ZephyrDevice, Diagnostics.ZephyrDevice.Builder, Diagnostics.ZephyrDeviceOrBuilder> 
getReachableDevicesFieldBuilder() {
if (reachableDevicesBuilder_ == null) {
reachableDevicesBuilder_ = new RepeatedFieldBuilder<
Diagnostics.ZephyrDevice, Diagnostics.ZephyrDevice.Builder, Diagnostics.ZephyrDeviceOrBuilder>(
reachableDevices_,
((b0_ & 0x00000002) == 0x00000002),
getParentForChildren(),
isClean());
reachableDevices_ = null;
}
return reachableDevicesBuilder_;
}
}
static {
defaultInstance = new ZephyrDiagnostics(true);
defaultInstance.initFields();
}
}
public interface ZephyrDeviceOrBuilder extends
MessageOrBuilder {
boolean hasDid();
ByteString getDid();
List<Diagnostics.ZephyrChannel> 
getChannelList();
Diagnostics.ZephyrChannel getChannel(int index);
int getChannelCount();
List<? extends Diagnostics.ZephyrChannelOrBuilder> 
getChannelOrBuilderList();
Diagnostics.ZephyrChannelOrBuilder getChannelOrBuilder(
int index);
}
public static final class ZephyrDevice extends
GeneratedMessage implements
ZephyrDeviceOrBuilder {
private ZephyrDevice(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ZephyrDevice(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final ZephyrDevice defaultInstance;
public static ZephyrDevice getDefaultInstance() {
return defaultInstance;
}
public ZephyrDevice getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private ZephyrDevice(
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
case 10: {
b0_ |= 0x00000001;
did_ = input.readBytes();
break;
}
case 18: {
if (!((mutable_b0_ & 0x00000002) == 0x00000002)) {
channel_ = new ArrayList<Diagnostics.ZephyrChannel>();
mutable_b0_ |= 0x00000002;
}
channel_.add(input.readMessage(Diagnostics.ZephyrChannel.PARSER, er));
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
channel_ = Collections.unmodifiableList(channel_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_ZephyrDevice_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ZephyrDevice_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ZephyrDevice.class, Diagnostics.ZephyrDevice.Builder.class);
}
public static Parser<ZephyrDevice> PARSER =
new AbstractParser<ZephyrDevice>() {
public ZephyrDevice parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ZephyrDevice(input, er);
}
};
@Override
public Parser<ZephyrDevice> getParserForType() {
return PARSER;
}
private int b0_;
public static final int DID_FIELD_NUMBER = 1;
private ByteString did_;
public boolean hasDid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getDid() {
return did_;
}
public static final int CHANNEL_FIELD_NUMBER = 2;
private List<Diagnostics.ZephyrChannel> channel_;
public List<Diagnostics.ZephyrChannel> getChannelList() {
return channel_;
}
public List<? extends Diagnostics.ZephyrChannelOrBuilder> 
getChannelOrBuilderList() {
return channel_;
}
public int getChannelCount() {
return channel_.size();
}
public Diagnostics.ZephyrChannel getChannel(int index) {
return channel_.get(index);
}
public Diagnostics.ZephyrChannelOrBuilder getChannelOrBuilder(
int index) {
return channel_.get(index);
}
private void initFields() {
did_ = ByteString.EMPTY;
channel_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
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
output.writeBytes(1, did_);
}
for (int i = 0; i < channel_.size(); i++) {
output.writeMessage(2, channel_.get(i));
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
.computeBytesSize(1, did_);
}
for (int i = 0; i < channel_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(2, channel_.get(i));
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
public static Diagnostics.ZephyrDevice parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ZephyrDevice parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ZephyrDevice parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ZephyrDevice parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ZephyrDevice parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ZephyrDevice parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.ZephyrDevice parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.ZephyrDevice parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.ZephyrDevice parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ZephyrDevice parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.ZephyrDevice prototype) {
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
Diagnostics.ZephyrDeviceOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_ZephyrDevice_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ZephyrDevice_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ZephyrDevice.class, Diagnostics.ZephyrDevice.Builder.class);
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
getChannelFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
did_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000001);
if (channelBuilder_ == null) {
channel_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
} else {
channelBuilder_.clear();
}
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_ZephyrDevice_descriptor;
}
public Diagnostics.ZephyrDevice getDefaultInstanceForType() {
return Diagnostics.ZephyrDevice.getDefaultInstance();
}
public Diagnostics.ZephyrDevice build() {
Diagnostics.ZephyrDevice result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.ZephyrDevice buildPartial() {
Diagnostics.ZephyrDevice result = new Diagnostics.ZephyrDevice(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.did_ = did_;
if (channelBuilder_ == null) {
if (((b0_ & 0x00000002) == 0x00000002)) {
channel_ = Collections.unmodifiableList(channel_);
b0_ = (b0_ & ~0x00000002);
}
result.channel_ = channel_;
} else {
result.channel_ = channelBuilder_.build();
}
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.ZephyrDevice) {
return mergeFrom((Diagnostics.ZephyrDevice)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.ZephyrDevice other) {
if (other == Diagnostics.ZephyrDevice.getDefaultInstance()) return this;
if (other.hasDid()) {
setDid(other.getDid());
}
if (channelBuilder_ == null) {
if (!other.channel_.isEmpty()) {
if (channel_.isEmpty()) {
channel_ = other.channel_;
b0_ = (b0_ & ~0x00000002);
} else {
ensureChannelIsMutable();
channel_.addAll(other.channel_);
}
onChanged();
}
} else {
if (!other.channel_.isEmpty()) {
if (channelBuilder_.isEmpty()) {
channelBuilder_.dispose();
channelBuilder_ = null;
channel_ = other.channel_;
b0_ = (b0_ & ~0x00000002);
channelBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getChannelFieldBuilder() : null;
} else {
channelBuilder_.addAllMessages(other.channel_);
}
}
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasDid()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.ZephyrDevice pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.ZephyrDevice) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private ByteString did_ = ByteString.EMPTY;
public boolean hasDid() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public ByteString getDid() {
return did_;
}
public Builder setDid(ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
did_ = value;
onChanged();
return this;
}
public Builder clearDid() {
b0_ = (b0_ & ~0x00000001);
did_ = getDefaultInstance().getDid();
onChanged();
return this;
}
private List<Diagnostics.ZephyrChannel> channel_ =
Collections.emptyList();
private void ensureChannelIsMutable() {
if (!((b0_ & 0x00000002) == 0x00000002)) {
channel_ = new ArrayList<Diagnostics.ZephyrChannel>(channel_);
b0_ |= 0x00000002;
}
}
private RepeatedFieldBuilder<
Diagnostics.ZephyrChannel, Diagnostics.ZephyrChannel.Builder, Diagnostics.ZephyrChannelOrBuilder> channelBuilder_;
public List<Diagnostics.ZephyrChannel> getChannelList() {
if (channelBuilder_ == null) {
return Collections.unmodifiableList(channel_);
} else {
return channelBuilder_.getMessageList();
}
}
public int getChannelCount() {
if (channelBuilder_ == null) {
return channel_.size();
} else {
return channelBuilder_.getCount();
}
}
public Diagnostics.ZephyrChannel getChannel(int index) {
if (channelBuilder_ == null) {
return channel_.get(index);
} else {
return channelBuilder_.getMessage(index);
}
}
public Builder setChannel(
int index, Diagnostics.ZephyrChannel value) {
if (channelBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureChannelIsMutable();
channel_.set(index, value);
onChanged();
} else {
channelBuilder_.setMessage(index, value);
}
return this;
}
public Builder setChannel(
int index, Diagnostics.ZephyrChannel.Builder bdForValue) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.set(index, bdForValue.build());
onChanged();
} else {
channelBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addChannel(Diagnostics.ZephyrChannel value) {
if (channelBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureChannelIsMutable();
channel_.add(value);
onChanged();
} else {
channelBuilder_.addMessage(value);
}
return this;
}
public Builder addChannel(
int index, Diagnostics.ZephyrChannel value) {
if (channelBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureChannelIsMutable();
channel_.add(index, value);
onChanged();
} else {
channelBuilder_.addMessage(index, value);
}
return this;
}
public Builder addChannel(
Diagnostics.ZephyrChannel.Builder bdForValue) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.add(bdForValue.build());
onChanged();
} else {
channelBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addChannel(
int index, Diagnostics.ZephyrChannel.Builder bdForValue) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.add(index, bdForValue.build());
onChanged();
} else {
channelBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllChannel(
Iterable<? extends Diagnostics.ZephyrChannel> values) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
AbstractMessageLite.Builder.addAll(
values, channel_);
onChanged();
} else {
channelBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearChannel() {
if (channelBuilder_ == null) {
channel_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000002);
onChanged();
} else {
channelBuilder_.clear();
}
return this;
}
public Builder removeChannel(int index) {
if (channelBuilder_ == null) {
ensureChannelIsMutable();
channel_.remove(index);
onChanged();
} else {
channelBuilder_.remove(index);
}
return this;
}
public Diagnostics.ZephyrChannel.Builder getChannelBuilder(
int index) {
return getChannelFieldBuilder().getBuilder(index);
}
public Diagnostics.ZephyrChannelOrBuilder getChannelOrBuilder(
int index) {
if (channelBuilder_ == null) {
return channel_.get(index);  } else {
return channelBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.ZephyrChannelOrBuilder> 
getChannelOrBuilderList() {
if (channelBuilder_ != null) {
return channelBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(channel_);
}
}
public Diagnostics.ZephyrChannel.Builder addChannelBuilder() {
return getChannelFieldBuilder().addBuilder(
Diagnostics.ZephyrChannel.getDefaultInstance());
}
public Diagnostics.ZephyrChannel.Builder addChannelBuilder(
int index) {
return getChannelFieldBuilder().addBuilder(
index, Diagnostics.ZephyrChannel.getDefaultInstance());
}
public List<Diagnostics.ZephyrChannel.Builder> 
getChannelBuilderList() {
return getChannelFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.ZephyrChannel, Diagnostics.ZephyrChannel.Builder, Diagnostics.ZephyrChannelOrBuilder> 
getChannelFieldBuilder() {
if (channelBuilder_ == null) {
channelBuilder_ = new RepeatedFieldBuilder<
Diagnostics.ZephyrChannel, Diagnostics.ZephyrChannel.Builder, Diagnostics.ZephyrChannelOrBuilder>(
channel_,
((b0_ & 0x00000002) == 0x00000002),
getParentForChildren(),
isClean());
channel_ = null;
}
return channelBuilder_;
}
}
static {
defaultInstance = new ZephyrDevice(true);
defaultInstance.initFields();
}
}
public interface ZephyrChannelOrBuilder extends
MessageOrBuilder {
boolean hasState();
Diagnostics.ChannelState getState();
boolean hasZidLocal();
long getZidLocal();
boolean hasZidRemote();
long getZidRemote();
boolean hasBytesSent();
long getBytesSent();
boolean hasBytesReceived();
long getBytesReceived();
boolean hasLifetime();
long getLifetime();
boolean hasRoundTripTime();
long getRoundTripTime();
}
public static final class ZephyrChannel extends
GeneratedMessage implements
ZephyrChannelOrBuilder {
private ZephyrChannel(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ZephyrChannel(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final ZephyrChannel defaultInstance;
public static ZephyrChannel getDefaultInstance() {
return defaultInstance;
}
public ZephyrChannel getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private ZephyrChannel(
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
Diagnostics.ChannelState value = Diagnostics.ChannelState.valueOf(rawValue);
if (value == null) {
unknownFields.mergeVarintField(1, rawValue);
} else {
b0_ |= 0x00000001;
state_ = value;
}
break;
}
case 16: {
b0_ |= 0x00000002;
zidLocal_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
zidRemote_ = input.readUInt64();
break;
}
case 32: {
b0_ |= 0x00000008;
bytesSent_ = input.readUInt64();
break;
}
case 40: {
b0_ |= 0x00000010;
bytesReceived_ = input.readUInt64();
break;
}
case 48: {
b0_ |= 0x00000020;
lifetime_ = input.readUInt64();
break;
}
case 56: {
b0_ |= 0x00000040;
roundTripTime_ = input.readUInt64();
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
return Diagnostics.internal_static_ZephyrChannel_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ZephyrChannel_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ZephyrChannel.class, Diagnostics.ZephyrChannel.Builder.class);
}
public static Parser<ZephyrChannel> PARSER =
new AbstractParser<ZephyrChannel>() {
public ZephyrChannel parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ZephyrChannel(input, er);
}
};
@Override
public Parser<ZephyrChannel> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STATE_FIELD_NUMBER = 1;
private Diagnostics.ChannelState state_;
public boolean hasState() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.ChannelState getState() {
return state_;
}
public static final int ZID_LOCAL_FIELD_NUMBER = 2;
private long zidLocal_;
public boolean hasZidLocal() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getZidLocal() {
return zidLocal_;
}
public static final int ZID_REMOTE_FIELD_NUMBER = 3;
private long zidRemote_;
public boolean hasZidRemote() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getZidRemote() {
return zidRemote_;
}
public static final int BYTES_SENT_FIELD_NUMBER = 4;
private long bytesSent_;
public boolean hasBytesSent() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getBytesSent() {
return bytesSent_;
}
public static final int BYTES_RECEIVED_FIELD_NUMBER = 5;
private long bytesReceived_;
public boolean hasBytesReceived() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getBytesReceived() {
return bytesReceived_;
}
public static final int LIFETIME_FIELD_NUMBER = 6;
private long lifetime_;
public boolean hasLifetime() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getLifetime() {
return lifetime_;
}
public static final int ROUND_TRIP_TIME_FIELD_NUMBER = 7;
private long roundTripTime_;
public boolean hasRoundTripTime() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public long getRoundTripTime() {
return roundTripTime_;
}
private void initFields() {
state_ = Diagnostics.ChannelState.CONNECTING;
zidLocal_ = 0L;
zidRemote_ = 0L;
bytesSent_ = 0L;
bytesReceived_ = 0L;
lifetime_ = 0L;
roundTripTime_ = 0L;
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
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeEnum(1, state_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, zidLocal_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, zidRemote_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeUInt64(4, bytesSent_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeUInt64(5, bytesReceived_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeUInt64(6, lifetime_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeUInt64(7, roundTripTime_);
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
.computeEnumSize(1, state_.getNumber());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, zidLocal_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, zidRemote_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeUInt64Size(4, bytesSent_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeUInt64Size(5, bytesReceived_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeUInt64Size(6, lifetime_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeUInt64Size(7, roundTripTime_);
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
public static Diagnostics.ZephyrChannel parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ZephyrChannel parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ZephyrChannel parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ZephyrChannel parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ZephyrChannel parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ZephyrChannel parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.ZephyrChannel parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.ZephyrChannel parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.ZephyrChannel parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ZephyrChannel parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.ZephyrChannel prototype) {
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
Diagnostics.ZephyrChannelOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_ZephyrChannel_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ZephyrChannel_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ZephyrChannel.class, Diagnostics.ZephyrChannel.Builder.class);
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
state_ = Diagnostics.ChannelState.CONNECTING;
b0_ = (b0_ & ~0x00000001);
zidLocal_ = 0L;
b0_ = (b0_ & ~0x00000002);
zidRemote_ = 0L;
b0_ = (b0_ & ~0x00000004);
bytesSent_ = 0L;
b0_ = (b0_ & ~0x00000008);
bytesReceived_ = 0L;
b0_ = (b0_ & ~0x00000010);
lifetime_ = 0L;
b0_ = (b0_ & ~0x00000020);
roundTripTime_ = 0L;
b0_ = (b0_ & ~0x00000040);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_ZephyrChannel_descriptor;
}
public Diagnostics.ZephyrChannel getDefaultInstanceForType() {
return Diagnostics.ZephyrChannel.getDefaultInstance();
}
public Diagnostics.ZephyrChannel build() {
Diagnostics.ZephyrChannel result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.ZephyrChannel buildPartial() {
Diagnostics.ZephyrChannel result = new Diagnostics.ZephyrChannel(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.state_ = state_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.zidLocal_ = zidLocal_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.zidRemote_ = zidRemote_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.bytesSent_ = bytesSent_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.bytesReceived_ = bytesReceived_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.lifetime_ = lifetime_;
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.roundTripTime_ = roundTripTime_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.ZephyrChannel) {
return mergeFrom((Diagnostics.ZephyrChannel)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.ZephyrChannel other) {
if (other == Diagnostics.ZephyrChannel.getDefaultInstance()) return this;
if (other.hasState()) {
setState(other.getState());
}
if (other.hasZidLocal()) {
setZidLocal(other.getZidLocal());
}
if (other.hasZidRemote()) {
setZidRemote(other.getZidRemote());
}
if (other.hasBytesSent()) {
setBytesSent(other.getBytesSent());
}
if (other.hasBytesReceived()) {
setBytesReceived(other.getBytesReceived());
}
if (other.hasLifetime()) {
setLifetime(other.getLifetime());
}
if (other.hasRoundTripTime()) {
setRoundTripTime(other.getRoundTripTime());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.ZephyrChannel pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.ZephyrChannel) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.ChannelState state_ = Diagnostics.ChannelState.CONNECTING;
public boolean hasState() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.ChannelState getState() {
return state_;
}
public Builder setState(Diagnostics.ChannelState value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
state_ = value;
onChanged();
return this;
}
public Builder clearState() {
b0_ = (b0_ & ~0x00000001);
state_ = Diagnostics.ChannelState.CONNECTING;
onChanged();
return this;
}
private long zidLocal_ ;
public boolean hasZidLocal() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getZidLocal() {
return zidLocal_;
}
public Builder setZidLocal(long value) {
b0_ |= 0x00000002;
zidLocal_ = value;
onChanged();
return this;
}
public Builder clearZidLocal() {
b0_ = (b0_ & ~0x00000002);
zidLocal_ = 0L;
onChanged();
return this;
}
private long zidRemote_ ;
public boolean hasZidRemote() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getZidRemote() {
return zidRemote_;
}
public Builder setZidRemote(long value) {
b0_ |= 0x00000004;
zidRemote_ = value;
onChanged();
return this;
}
public Builder clearZidRemote() {
b0_ = (b0_ & ~0x00000004);
zidRemote_ = 0L;
onChanged();
return this;
}
private long bytesSent_ ;
public boolean hasBytesSent() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getBytesSent() {
return bytesSent_;
}
public Builder setBytesSent(long value) {
b0_ |= 0x00000008;
bytesSent_ = value;
onChanged();
return this;
}
public Builder clearBytesSent() {
b0_ = (b0_ & ~0x00000008);
bytesSent_ = 0L;
onChanged();
return this;
}
private long bytesReceived_ ;
public boolean hasBytesReceived() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getBytesReceived() {
return bytesReceived_;
}
public Builder setBytesReceived(long value) {
b0_ |= 0x00000010;
bytesReceived_ = value;
onChanged();
return this;
}
public Builder clearBytesReceived() {
b0_ = (b0_ & ~0x00000010);
bytesReceived_ = 0L;
onChanged();
return this;
}
private long lifetime_ ;
public boolean hasLifetime() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getLifetime() {
return lifetime_;
}
public Builder setLifetime(long value) {
b0_ |= 0x00000020;
lifetime_ = value;
onChanged();
return this;
}
public Builder clearLifetime() {
b0_ = (b0_ & ~0x00000020);
lifetime_ = 0L;
onChanged();
return this;
}
private long roundTripTime_ ;
public boolean hasRoundTripTime() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public long getRoundTripTime() {
return roundTripTime_;
}
public Builder setRoundTripTime(long value) {
b0_ |= 0x00000040;
roundTripTime_ = value;
onChanged();
return this;
}
public Builder clearRoundTripTime() {
b0_ = (b0_ & ~0x00000040);
roundTripTime_ = 0L;
onChanged();
return this;
}
}
static {
defaultInstance = new ZephyrChannel(true);
defaultInstance.initFields();
}
}
public interface ServerStatusOrBuilder extends
MessageOrBuilder {
boolean hasServerAddress();
Diagnostics.PBInetSocketAddress getServerAddress();
Diagnostics.PBInetSocketAddressOrBuilder getServerAddressOrBuilder();
boolean hasReachable();
boolean getReachable();
boolean hasReachabilityError();
String getReachabilityError();
ByteString
getReachabilityErrorBytes();
}
public static final class ServerStatus extends
GeneratedMessage implements
ServerStatusOrBuilder {
private ServerStatus(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ServerStatus(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final ServerStatus defaultInstance;
public static ServerStatus getDefaultInstance() {
return defaultInstance;
}
public ServerStatus getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private ServerStatus(
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
case 10: {
Diagnostics.PBInetSocketAddress.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = serverAddress_.toBuilder();
}
serverAddress_ = input.readMessage(Diagnostics.PBInetSocketAddress.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(serverAddress_);
serverAddress_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 16: {
b0_ |= 0x00000002;
reachable_ = input.readBool();
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000004;
reachabilityError_ = bs;
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
return Diagnostics.internal_static_ServerStatus_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ServerStatus_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ServerStatus.class, Diagnostics.ServerStatus.Builder.class);
}
public static Parser<ServerStatus> PARSER =
new AbstractParser<ServerStatus>() {
public ServerStatus parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ServerStatus(input, er);
}
};
@Override
public Parser<ServerStatus> getParserForType() {
return PARSER;
}
private int b0_;
public static final int SERVER_ADDRESS_FIELD_NUMBER = 1;
private Diagnostics.PBInetSocketAddress serverAddress_;
public boolean hasServerAddress() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBInetSocketAddress getServerAddress() {
return serverAddress_;
}
public Diagnostics.PBInetSocketAddressOrBuilder getServerAddressOrBuilder() {
return serverAddress_;
}
public static final int REACHABLE_FIELD_NUMBER = 2;
private boolean reachable_;
public boolean hasReachable() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getReachable() {
return reachable_;
}
public static final int REACHABILITY_ERROR_FIELD_NUMBER = 3;
private Object reachabilityError_;
public boolean hasReachabilityError() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getReachabilityError() {
Object ref = reachabilityError_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
reachabilityError_ = s;
}
return s;
}
}
public ByteString
getReachabilityErrorBytes() {
Object ref = reachabilityError_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
reachabilityError_ = b;
return b;
} else {
return (ByteString) ref;
}
}
private void initFields() {
serverAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
reachable_ = false;
reachabilityError_ = "";
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasServerAddress()) {
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
output.writeMessage(1, serverAddress_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBool(2, reachable_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, getReachabilityErrorBytes());
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
.computeMessageSize(1, serverAddress_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBoolSize(2, reachable_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, getReachabilityErrorBytes());
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
public static Diagnostics.ServerStatus parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ServerStatus parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ServerStatus parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.ServerStatus parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.ServerStatus parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ServerStatus parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.ServerStatus parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.ServerStatus parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.ServerStatus parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.ServerStatus parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.ServerStatus prototype) {
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
Diagnostics.ServerStatusOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_ServerStatus_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_ServerStatus_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.ServerStatus.class, Diagnostics.ServerStatus.Builder.class);
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
getServerAddressFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (serverAddressBuilder_ == null) {
serverAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
} else {
serverAddressBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
reachable_ = false;
b0_ = (b0_ & ~0x00000002);
reachabilityError_ = "";
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_ServerStatus_descriptor;
}
public Diagnostics.ServerStatus getDefaultInstanceForType() {
return Diagnostics.ServerStatus.getDefaultInstance();
}
public Diagnostics.ServerStatus build() {
Diagnostics.ServerStatus result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.ServerStatus buildPartial() {
Diagnostics.ServerStatus result = new Diagnostics.ServerStatus(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
if (serverAddressBuilder_ == null) {
result.serverAddress_ = serverAddress_;
} else {
result.serverAddress_ = serverAddressBuilder_.build();
}
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.reachable_ = reachable_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.reachabilityError_ = reachabilityError_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.ServerStatus) {
return mergeFrom((Diagnostics.ServerStatus)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.ServerStatus other) {
if (other == Diagnostics.ServerStatus.getDefaultInstance()) return this;
if (other.hasServerAddress()) {
mergeServerAddress(other.getServerAddress());
}
if (other.hasReachable()) {
setReachable(other.getReachable());
}
if (other.hasReachabilityError()) {
b0_ |= 0x00000004;
reachabilityError_ = other.reachabilityError_;
onChanged();
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasServerAddress()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.ServerStatus pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.ServerStatus) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.PBInetSocketAddress serverAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder> serverAddressBuilder_;
public boolean hasServerAddress() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.PBInetSocketAddress getServerAddress() {
if (serverAddressBuilder_ == null) {
return serverAddress_;
} else {
return serverAddressBuilder_.getMessage();
}
}
public Builder setServerAddress(Diagnostics.PBInetSocketAddress value) {
if (serverAddressBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
serverAddress_ = value;
onChanged();
} else {
serverAddressBuilder_.setMessage(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder setServerAddress(
Diagnostics.PBInetSocketAddress.Builder bdForValue) {
if (serverAddressBuilder_ == null) {
serverAddress_ = bdForValue.build();
onChanged();
} else {
serverAddressBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000001;
return this;
}
public Builder mergeServerAddress(Diagnostics.PBInetSocketAddress value) {
if (serverAddressBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001) &&
serverAddress_ != Diagnostics.PBInetSocketAddress.getDefaultInstance()) {
serverAddress_ =
Diagnostics.PBInetSocketAddress.newBuilder(serverAddress_).mergeFrom(value).buildPartial();
} else {
serverAddress_ = value;
}
onChanged();
} else {
serverAddressBuilder_.mergeFrom(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder clearServerAddress() {
if (serverAddressBuilder_ == null) {
serverAddress_ = Diagnostics.PBInetSocketAddress.getDefaultInstance();
onChanged();
} else {
serverAddressBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
return this;
}
public Diagnostics.PBInetSocketAddress.Builder getServerAddressBuilder() {
b0_ |= 0x00000001;
onChanged();
return getServerAddressFieldBuilder().getBuilder();
}
public Diagnostics.PBInetSocketAddressOrBuilder getServerAddressOrBuilder() {
if (serverAddressBuilder_ != null) {
return serverAddressBuilder_.getMessageOrBuilder();
} else {
return serverAddress_;
}
}
private SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder> 
getServerAddressFieldBuilder() {
if (serverAddressBuilder_ == null) {
serverAddressBuilder_ = new SingleFieldBuilder<
Diagnostics.PBInetSocketAddress, Diagnostics.PBInetSocketAddress.Builder, Diagnostics.PBInetSocketAddressOrBuilder>(
getServerAddress(),
getParentForChildren(),
isClean());
serverAddress_ = null;
}
return serverAddressBuilder_;
}
private boolean reachable_ ;
public boolean hasReachable() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public boolean getReachable() {
return reachable_;
}
public Builder setReachable(boolean value) {
b0_ |= 0x00000002;
reachable_ = value;
onChanged();
return this;
}
public Builder clearReachable() {
b0_ = (b0_ & ~0x00000002);
reachable_ = false;
onChanged();
return this;
}
private Object reachabilityError_ = "";
public boolean hasReachabilityError() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getReachabilityError() {
Object ref = reachabilityError_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
reachabilityError_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getReachabilityErrorBytes() {
Object ref = reachabilityError_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
reachabilityError_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setReachabilityError(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
reachabilityError_ = value;
onChanged();
return this;
}
public Builder clearReachabilityError() {
b0_ = (b0_ & ~0x00000004);
reachabilityError_ = getDefaultInstance().getReachabilityError();
onChanged();
return this;
}
public Builder setReachabilityErrorBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
reachabilityError_ = value;
onChanged();
return this;
}
}
static {
defaultInstance = new ServerStatus(true);
defaultInstance.initFields();
}
}
public interface PBInetSocketAddressOrBuilder extends
MessageOrBuilder {
boolean hasHost();
String getHost();
ByteString
getHostBytes();
boolean hasPort();
int getPort();
}
public static final class PBInetSocketAddress extends
GeneratedMessage implements
PBInetSocketAddressOrBuilder {
private PBInetSocketAddress(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PBInetSocketAddress(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final PBInetSocketAddress defaultInstance;
public static PBInetSocketAddress getDefaultInstance() {
return defaultInstance;
}
public PBInetSocketAddress getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private PBInetSocketAddress(
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
case 10: {
ByteString bs = input.readBytes();
b0_ |= 0x00000001;
host_ = bs;
break;
}
case 16: {
b0_ |= 0x00000002;
port_ = input.readUInt32();
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
return Diagnostics.internal_static_PBInetSocketAddress_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_PBInetSocketAddress_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.PBInetSocketAddress.class, Diagnostics.PBInetSocketAddress.Builder.class);
}
public static Parser<PBInetSocketAddress> PARSER =
new AbstractParser<PBInetSocketAddress>() {
public PBInetSocketAddress parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PBInetSocketAddress(input, er);
}
};
@Override
public Parser<PBInetSocketAddress> getParserForType() {
return PARSER;
}
private int b0_;
public static final int HOST_FIELD_NUMBER = 1;
private Object host_;
public boolean hasHost() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getHost() {
Object ref = host_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
host_ = s;
}
return s;
}
}
public ByteString
getHostBytes() {
Object ref = host_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
host_ = b;
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
private void initFields() {
host_ = "";
port_ = 0;
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
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeBytes(1, getHostBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt32(2, port_);
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
.computeBytesSize(1, getHostBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt32Size(2, port_);
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
public static Diagnostics.PBInetSocketAddress parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.PBInetSocketAddress parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.PBInetSocketAddress parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.PBInetSocketAddress parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.PBInetSocketAddress parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.PBInetSocketAddress parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.PBInetSocketAddress parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.PBInetSocketAddress parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.PBInetSocketAddress parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.PBInetSocketAddress parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.PBInetSocketAddress prototype) {
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
Diagnostics.PBInetSocketAddressOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_PBInetSocketAddress_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_PBInetSocketAddress_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.PBInetSocketAddress.class, Diagnostics.PBInetSocketAddress.Builder.class);
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
host_ = "";
b0_ = (b0_ & ~0x00000001);
port_ = 0;
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_PBInetSocketAddress_descriptor;
}
public Diagnostics.PBInetSocketAddress getDefaultInstanceForType() {
return Diagnostics.PBInetSocketAddress.getDefaultInstance();
}
public Diagnostics.PBInetSocketAddress build() {
Diagnostics.PBInetSocketAddress result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.PBInetSocketAddress buildPartial() {
Diagnostics.PBInetSocketAddress result = new Diagnostics.PBInetSocketAddress(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.host_ = host_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.port_ = port_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.PBInetSocketAddress) {
return mergeFrom((Diagnostics.PBInetSocketAddress)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.PBInetSocketAddress other) {
if (other == Diagnostics.PBInetSocketAddress.getDefaultInstance()) return this;
if (other.hasHost()) {
b0_ |= 0x00000001;
host_ = other.host_;
onChanged();
}
if (other.hasPort()) {
setPort(other.getPort());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.PBInetSocketAddress pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.PBInetSocketAddress) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object host_ = "";
public boolean hasHost() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getHost() {
Object ref = host_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
host_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getHostBytes() {
Object ref = host_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
host_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setHost(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
host_ = value;
onChanged();
return this;
}
public Builder clearHost() {
b0_ = (b0_ & ~0x00000001);
host_ = getDefaultInstance().getHost();
onChanged();
return this;
}
public Builder setHostBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
host_ = value;
onChanged();
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
onChanged();
return this;
}
public Builder clearPort() {
b0_ = (b0_ & ~0x00000002);
port_ = 0;
onChanged();
return this;
}
}
static {
defaultInstance = new PBInetSocketAddress(true);
defaultInstance.initFields();
}
}
public interface TransportTransferDiagnosticsOrBuilder extends
MessageOrBuilder {
List<Diagnostics.TransportTransfer> 
getTransferList();
Diagnostics.TransportTransfer getTransfer(int index);
int getTransferCount();
List<? extends Diagnostics.TransportTransferOrBuilder> 
getTransferOrBuilderList();
Diagnostics.TransportTransferOrBuilder getTransferOrBuilder(
int index);
boolean hasTotalBytesTransferred();
long getTotalBytesTransferred();
boolean hasTotalBytesErrored();
long getTotalBytesErrored();
}
public static final class TransportTransferDiagnostics extends
GeneratedMessage implements
TransportTransferDiagnosticsOrBuilder {
private TransportTransferDiagnostics(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TransportTransferDiagnostics(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TransportTransferDiagnostics defaultInstance;
public static TransportTransferDiagnostics getDefaultInstance() {
return defaultInstance;
}
public TransportTransferDiagnostics getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TransportTransferDiagnostics(
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
case 10: {
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
transfer_ = new ArrayList<Diagnostics.TransportTransfer>();
mutable_b0_ |= 0x00000001;
}
transfer_.add(input.readMessage(Diagnostics.TransportTransfer.PARSER, er));
break;
}
case 16: {
b0_ |= 0x00000001;
totalBytesTransferred_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000002;
totalBytesErrored_ = input.readUInt64();
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
transfer_ = Collections.unmodifiableList(transfer_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TransportTransferDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransportTransferDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransportTransferDiagnostics.class, Diagnostics.TransportTransferDiagnostics.Builder.class);
}
public static Parser<TransportTransferDiagnostics> PARSER =
new AbstractParser<TransportTransferDiagnostics>() {
public TransportTransferDiagnostics parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TransportTransferDiagnostics(input, er);
}
};
@Override
public Parser<TransportTransferDiagnostics> getParserForType() {
return PARSER;
}
private int b0_;
public static final int TRANSFER_FIELD_NUMBER = 1;
private List<Diagnostics.TransportTransfer> transfer_;
public List<Diagnostics.TransportTransfer> getTransferList() {
return transfer_;
}
public List<? extends Diagnostics.TransportTransferOrBuilder> 
getTransferOrBuilderList() {
return transfer_;
}
public int getTransferCount() {
return transfer_.size();
}
public Diagnostics.TransportTransfer getTransfer(int index) {
return transfer_.get(index);
}
public Diagnostics.TransportTransferOrBuilder getTransferOrBuilder(
int index) {
return transfer_.get(index);
}
public static final int TOTAL_BYTES_TRANSFERRED_FIELD_NUMBER = 2;
private long totalBytesTransferred_;
public boolean hasTotalBytesTransferred() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getTotalBytesTransferred() {
return totalBytesTransferred_;
}
public static final int TOTAL_BYTES_ERRORED_FIELD_NUMBER = 3;
private long totalBytesErrored_;
public boolean hasTotalBytesErrored() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getTotalBytesErrored() {
return totalBytesErrored_;
}
private void initFields() {
transfer_ = Collections.emptyList();
totalBytesTransferred_ = 0L;
totalBytesErrored_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getTransferCount(); i++) {
if (!getTransfer(i).isInitialized()) {
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
for (int i = 0; i < transfer_.size(); i++) {
output.writeMessage(1, transfer_.get(i));
}
if (((b0_ & 0x00000001) == 0x00000001)) {
output.writeUInt64(2, totalBytesTransferred_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(3, totalBytesErrored_);
}
getUnknownFields().writeTo(output);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < transfer_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, transfer_.get(i));
}
if (((b0_ & 0x00000001) == 0x00000001)) {
size += CodedOutputStream
.computeUInt64Size(2, totalBytesTransferred_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(3, totalBytesErrored_);
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
public static Diagnostics.TransportTransferDiagnostics parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TransportTransferDiagnostics parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TransportTransferDiagnostics parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransportTransferDiagnostics parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TransportTransferDiagnostics prototype) {
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
Diagnostics.TransportTransferDiagnosticsOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TransportTransferDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransportTransferDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransportTransferDiagnostics.class, Diagnostics.TransportTransferDiagnostics.Builder.class);
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
getTransferFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (transferBuilder_ == null) {
transfer_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
} else {
transferBuilder_.clear();
}
totalBytesTransferred_ = 0L;
b0_ = (b0_ & ~0x00000002);
totalBytesErrored_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TransportTransferDiagnostics_descriptor;
}
public Diagnostics.TransportTransferDiagnostics getDefaultInstanceForType() {
return Diagnostics.TransportTransferDiagnostics.getDefaultInstance();
}
public Diagnostics.TransportTransferDiagnostics build() {
Diagnostics.TransportTransferDiagnostics result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TransportTransferDiagnostics buildPartial() {
Diagnostics.TransportTransferDiagnostics result = new Diagnostics.TransportTransferDiagnostics(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (transferBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001)) {
transfer_ = Collections.unmodifiableList(transfer_);
b0_ = (b0_ & ~0x00000001);
}
result.transfer_ = transfer_;
} else {
result.transfer_ = transferBuilder_.build();
}
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000001;
}
result.totalBytesTransferred_ = totalBytesTransferred_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000002;
}
result.totalBytesErrored_ = totalBytesErrored_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TransportTransferDiagnostics) {
return mergeFrom((Diagnostics.TransportTransferDiagnostics)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TransportTransferDiagnostics other) {
if (other == Diagnostics.TransportTransferDiagnostics.getDefaultInstance()) return this;
if (transferBuilder_ == null) {
if (!other.transfer_.isEmpty()) {
if (transfer_.isEmpty()) {
transfer_ = other.transfer_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureTransferIsMutable();
transfer_.addAll(other.transfer_);
}
onChanged();
}
} else {
if (!other.transfer_.isEmpty()) {
if (transferBuilder_.isEmpty()) {
transferBuilder_.dispose();
transferBuilder_ = null;
transfer_ = other.transfer_;
b0_ = (b0_ & ~0x00000001);
transferBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getTransferFieldBuilder() : null;
} else {
transferBuilder_.addAllMessages(other.transfer_);
}
}
}
if (other.hasTotalBytesTransferred()) {
setTotalBytesTransferred(other.getTotalBytesTransferred());
}
if (other.hasTotalBytesErrored()) {
setTotalBytesErrored(other.getTotalBytesErrored());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getTransferCount(); i++) {
if (!getTransfer(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TransportTransferDiagnostics pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TransportTransferDiagnostics) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Diagnostics.TransportTransfer> transfer_ =
Collections.emptyList();
private void ensureTransferIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
transfer_ = new ArrayList<Diagnostics.TransportTransfer>(transfer_);
b0_ |= 0x00000001;
}
}
private RepeatedFieldBuilder<
Diagnostics.TransportTransfer, Diagnostics.TransportTransfer.Builder, Diagnostics.TransportTransferOrBuilder> transferBuilder_;
public List<Diagnostics.TransportTransfer> getTransferList() {
if (transferBuilder_ == null) {
return Collections.unmodifiableList(transfer_);
} else {
return transferBuilder_.getMessageList();
}
}
public int getTransferCount() {
if (transferBuilder_ == null) {
return transfer_.size();
} else {
return transferBuilder_.getCount();
}
}
public Diagnostics.TransportTransfer getTransfer(int index) {
if (transferBuilder_ == null) {
return transfer_.get(index);
} else {
return transferBuilder_.getMessage(index);
}
}
public Builder setTransfer(
int index, Diagnostics.TransportTransfer value) {
if (transferBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransferIsMutable();
transfer_.set(index, value);
onChanged();
} else {
transferBuilder_.setMessage(index, value);
}
return this;
}
public Builder setTransfer(
int index, Diagnostics.TransportTransfer.Builder bdForValue) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.set(index, bdForValue.build());
onChanged();
} else {
transferBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addTransfer(Diagnostics.TransportTransfer value) {
if (transferBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransferIsMutable();
transfer_.add(value);
onChanged();
} else {
transferBuilder_.addMessage(value);
}
return this;
}
public Builder addTransfer(
int index, Diagnostics.TransportTransfer value) {
if (transferBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransferIsMutable();
transfer_.add(index, value);
onChanged();
} else {
transferBuilder_.addMessage(index, value);
}
return this;
}
public Builder addTransfer(
Diagnostics.TransportTransfer.Builder bdForValue) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.add(bdForValue.build());
onChanged();
} else {
transferBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addTransfer(
int index, Diagnostics.TransportTransfer.Builder bdForValue) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.add(index, bdForValue.build());
onChanged();
} else {
transferBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllTransfer(
Iterable<? extends Diagnostics.TransportTransfer> values) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
AbstractMessageLite.Builder.addAll(
values, transfer_);
onChanged();
} else {
transferBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearTransfer() {
if (transferBuilder_ == null) {
transfer_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
onChanged();
} else {
transferBuilder_.clear();
}
return this;
}
public Builder removeTransfer(int index) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.remove(index);
onChanged();
} else {
transferBuilder_.remove(index);
}
return this;
}
public Diagnostics.TransportTransfer.Builder getTransferBuilder(
int index) {
return getTransferFieldBuilder().getBuilder(index);
}
public Diagnostics.TransportTransferOrBuilder getTransferOrBuilder(
int index) {
if (transferBuilder_ == null) {
return transfer_.get(index);  } else {
return transferBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.TransportTransferOrBuilder> 
getTransferOrBuilderList() {
if (transferBuilder_ != null) {
return transferBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(transfer_);
}
}
public Diagnostics.TransportTransfer.Builder addTransferBuilder() {
return getTransferFieldBuilder().addBuilder(
Diagnostics.TransportTransfer.getDefaultInstance());
}
public Diagnostics.TransportTransfer.Builder addTransferBuilder(
int index) {
return getTransferFieldBuilder().addBuilder(
index, Diagnostics.TransportTransfer.getDefaultInstance());
}
public List<Diagnostics.TransportTransfer.Builder> 
getTransferBuilderList() {
return getTransferFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.TransportTransfer, Diagnostics.TransportTransfer.Builder, Diagnostics.TransportTransferOrBuilder> 
getTransferFieldBuilder() {
if (transferBuilder_ == null) {
transferBuilder_ = new RepeatedFieldBuilder<
Diagnostics.TransportTransfer, Diagnostics.TransportTransfer.Builder, Diagnostics.TransportTransferOrBuilder>(
transfer_,
((b0_ & 0x00000001) == 0x00000001),
getParentForChildren(),
isClean());
transfer_ = null;
}
return transferBuilder_;
}
private long totalBytesTransferred_ ;
public boolean hasTotalBytesTransferred() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getTotalBytesTransferred() {
return totalBytesTransferred_;
}
public Builder setTotalBytesTransferred(long value) {
b0_ |= 0x00000002;
totalBytesTransferred_ = value;
onChanged();
return this;
}
public Builder clearTotalBytesTransferred() {
b0_ = (b0_ & ~0x00000002);
totalBytesTransferred_ = 0L;
onChanged();
return this;
}
private long totalBytesErrored_ ;
public boolean hasTotalBytesErrored() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getTotalBytesErrored() {
return totalBytesErrored_;
}
public Builder setTotalBytesErrored(long value) {
b0_ |= 0x00000004;
totalBytesErrored_ = value;
onChanged();
return this;
}
public Builder clearTotalBytesErrored() {
b0_ = (b0_ & ~0x00000004);
totalBytesErrored_ = 0L;
onChanged();
return this;
}
}
static {
defaultInstance = new TransportTransferDiagnostics(true);
defaultInstance.initFields();
}
}
public interface TransportTransferOrBuilder extends
MessageOrBuilder {
boolean hasTransportId();
String getTransportId();
ByteString
getTransportIdBytes();
boolean hasBytesTransferred();
long getBytesTransferred();
boolean hasBytesErrored();
long getBytesErrored();
}
public static final class TransportTransfer extends
GeneratedMessage implements
TransportTransferOrBuilder {
private TransportTransfer(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TransportTransfer(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TransportTransfer defaultInstance;
public static TransportTransfer getDefaultInstance() {
return defaultInstance;
}
public TransportTransfer getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TransportTransfer(
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
case 10: {
ByteString bs = input.readBytes();
b0_ |= 0x00000001;
transportId_ = bs;
break;
}
case 16: {
b0_ |= 0x00000002;
bytesTransferred_ = input.readUInt64();
break;
}
case 24: {
b0_ |= 0x00000004;
bytesErrored_ = input.readUInt64();
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
return Diagnostics.internal_static_TransportTransfer_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransportTransfer_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransportTransfer.class, Diagnostics.TransportTransfer.Builder.class);
}
public static Parser<TransportTransfer> PARSER =
new AbstractParser<TransportTransfer>() {
public TransportTransfer parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TransportTransfer(input, er);
}
};
@Override
public Parser<TransportTransfer> getParserForType() {
return PARSER;
}
private int b0_;
public static final int TRANSPORT_ID_FIELD_NUMBER = 1;
private Object transportId_;
public boolean hasTransportId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getTransportId() {
Object ref = transportId_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
transportId_ = s;
}
return s;
}
}
public ByteString
getTransportIdBytes() {
Object ref = transportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
transportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int BYTES_TRANSFERRED_FIELD_NUMBER = 2;
private long bytesTransferred_;
public boolean hasBytesTransferred() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getBytesTransferred() {
return bytesTransferred_;
}
public static final int BYTES_ERRORED_FIELD_NUMBER = 3;
private long bytesErrored_;
public boolean hasBytesErrored() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getBytesErrored() {
return bytesErrored_;
}
private void initFields() {
transportId_ = "";
bytesTransferred_ = 0L;
bytesErrored_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasTransportId()) {
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
output.writeBytes(1, getTransportIdBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeUInt64(2, bytesTransferred_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, bytesErrored_);
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
.computeBytesSize(1, getTransportIdBytes());
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeUInt64Size(2, bytesTransferred_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, bytesErrored_);
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
public static Diagnostics.TransportTransfer parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransportTransfer parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransportTransfer parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransportTransfer parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransportTransfer parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransportTransfer parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TransportTransfer parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TransportTransfer parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TransportTransfer parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransportTransfer parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TransportTransfer prototype) {
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
Diagnostics.TransportTransferOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TransportTransfer_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransportTransfer_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransportTransfer.class, Diagnostics.TransportTransfer.Builder.class);
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
transportId_ = "";
b0_ = (b0_ & ~0x00000001);
bytesTransferred_ = 0L;
b0_ = (b0_ & ~0x00000002);
bytesErrored_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TransportTransfer_descriptor;
}
public Diagnostics.TransportTransfer getDefaultInstanceForType() {
return Diagnostics.TransportTransfer.getDefaultInstance();
}
public Diagnostics.TransportTransfer build() {
Diagnostics.TransportTransfer result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TransportTransfer buildPartial() {
Diagnostics.TransportTransfer result = new Diagnostics.TransportTransfer(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.transportId_ = transportId_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.bytesTransferred_ = bytesTransferred_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.bytesErrored_ = bytesErrored_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TransportTransfer) {
return mergeFrom((Diagnostics.TransportTransfer)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TransportTransfer other) {
if (other == Diagnostics.TransportTransfer.getDefaultInstance()) return this;
if (other.hasTransportId()) {
b0_ |= 0x00000001;
transportId_ = other.transportId_;
onChanged();
}
if (other.hasBytesTransferred()) {
setBytesTransferred(other.getBytesTransferred());
}
if (other.hasBytesErrored()) {
setBytesErrored(other.getBytesErrored());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasTransportId()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TransportTransfer pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TransportTransfer) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Object transportId_ = "";
public boolean hasTransportId() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public String getTransportId() {
Object ref = transportId_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
transportId_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getTransportIdBytes() {
Object ref = transportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
transportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setTransportId(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
transportId_ = value;
onChanged();
return this;
}
public Builder clearTransportId() {
b0_ = (b0_ & ~0x00000001);
transportId_ = getDefaultInstance().getTransportId();
onChanged();
return this;
}
public Builder setTransportIdBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
transportId_ = value;
onChanged();
return this;
}
private long bytesTransferred_ ;
public boolean hasBytesTransferred() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public long getBytesTransferred() {
return bytesTransferred_;
}
public Builder setBytesTransferred(long value) {
b0_ |= 0x00000002;
bytesTransferred_ = value;
onChanged();
return this;
}
public Builder clearBytesTransferred() {
b0_ = (b0_ & ~0x00000002);
bytesTransferred_ = 0L;
onChanged();
return this;
}
private long bytesErrored_ ;
public boolean hasBytesErrored() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getBytesErrored() {
return bytesErrored_;
}
public Builder setBytesErrored(long value) {
b0_ |= 0x00000004;
bytesErrored_ = value;
onChanged();
return this;
}
public Builder clearBytesErrored() {
b0_ = (b0_ & ~0x00000004);
bytesErrored_ = 0L;
onChanged();
return this;
}
}
static {
defaultInstance = new TransportTransfer(true);
defaultInstance.initFields();
}
}
public interface FileTransferDiagnosticsOrBuilder extends
MessageOrBuilder {
List<Diagnostics.FileTransfer> 
getTransferList();
Diagnostics.FileTransfer getTransfer(int index);
int getTransferCount();
List<? extends Diagnostics.FileTransferOrBuilder> 
getTransferOrBuilderList();
Diagnostics.FileTransferOrBuilder getTransferOrBuilder(
int index);
}
public static final class FileTransferDiagnostics extends
GeneratedMessage implements
FileTransferDiagnosticsOrBuilder {
private FileTransferDiagnostics(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private FileTransferDiagnostics(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final FileTransferDiagnostics defaultInstance;
public static FileTransferDiagnostics getDefaultInstance() {
return defaultInstance;
}
public FileTransferDiagnostics getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private FileTransferDiagnostics(
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
case 10: {
if (!((mutable_b0_ & 0x00000001) == 0x00000001)) {
transfer_ = new ArrayList<Diagnostics.FileTransfer>();
mutable_b0_ |= 0x00000001;
}
transfer_.add(input.readMessage(Diagnostics.FileTransfer.PARSER, er));
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
transfer_ = Collections.unmodifiableList(transfer_);
}
this.unknownFields = unknownFields.build();
makeExtensionsImmutable();
}
}
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_FileTransferDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_FileTransferDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.FileTransferDiagnostics.class, Diagnostics.FileTransferDiagnostics.Builder.class);
}
public static Parser<FileTransferDiagnostics> PARSER =
new AbstractParser<FileTransferDiagnostics>() {
public FileTransferDiagnostics parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new FileTransferDiagnostics(input, er);
}
};
@Override
public Parser<FileTransferDiagnostics> getParserForType() {
return PARSER;
}
public static final int TRANSFER_FIELD_NUMBER = 1;
private List<Diagnostics.FileTransfer> transfer_;
public List<Diagnostics.FileTransfer> getTransferList() {
return transfer_;
}
public List<? extends Diagnostics.FileTransferOrBuilder> 
getTransferOrBuilderList() {
return transfer_;
}
public int getTransferCount() {
return transfer_.size();
}
public Diagnostics.FileTransfer getTransfer(int index) {
return transfer_.get(index);
}
public Diagnostics.FileTransferOrBuilder getTransferOrBuilder(
int index) {
return transfer_.get(index);
}
private void initFields() {
transfer_ = Collections.emptyList();
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
for (int i = 0; i < getTransferCount(); i++) {
if (!getTransfer(i).isInitialized()) {
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
for (int i = 0; i < transfer_.size(); i++) {
output.writeMessage(1, transfer_.get(i));
}
getUnknownFields().writeTo(output);
}
private int mss = -1;
public int getSerializedSize() {
int size = mss;
if (size != -1) return size;
size = 0;
for (int i = 0; i < transfer_.size(); i++) {
size += CodedOutputStream
.computeMessageSize(1, transfer_.get(i));
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
public static Diagnostics.FileTransferDiagnostics parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.FileTransferDiagnostics parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.FileTransferDiagnostics parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.FileTransferDiagnostics parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.FileTransferDiagnostics prototype) {
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
Diagnostics.FileTransferDiagnosticsOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_FileTransferDiagnostics_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_FileTransferDiagnostics_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.FileTransferDiagnostics.class, Diagnostics.FileTransferDiagnostics.Builder.class);
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
getTransferFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (transferBuilder_ == null) {
transfer_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
} else {
transferBuilder_.clear();
}
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_FileTransferDiagnostics_descriptor;
}
public Diagnostics.FileTransferDiagnostics getDefaultInstanceForType() {
return Diagnostics.FileTransferDiagnostics.getDefaultInstance();
}
public Diagnostics.FileTransferDiagnostics build() {
Diagnostics.FileTransferDiagnostics result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.FileTransferDiagnostics buildPartial() {
Diagnostics.FileTransferDiagnostics result = new Diagnostics.FileTransferDiagnostics(this);
int from_b0_ = b0_;
if (transferBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001)) {
transfer_ = Collections.unmodifiableList(transfer_);
b0_ = (b0_ & ~0x00000001);
}
result.transfer_ = transfer_;
} else {
result.transfer_ = transferBuilder_.build();
}
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.FileTransferDiagnostics) {
return mergeFrom((Diagnostics.FileTransferDiagnostics)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.FileTransferDiagnostics other) {
if (other == Diagnostics.FileTransferDiagnostics.getDefaultInstance()) return this;
if (transferBuilder_ == null) {
if (!other.transfer_.isEmpty()) {
if (transfer_.isEmpty()) {
transfer_ = other.transfer_;
b0_ = (b0_ & ~0x00000001);
} else {
ensureTransferIsMutable();
transfer_.addAll(other.transfer_);
}
onChanged();
}
} else {
if (!other.transfer_.isEmpty()) {
if (transferBuilder_.isEmpty()) {
transferBuilder_.dispose();
transferBuilder_ = null;
transfer_ = other.transfer_;
b0_ = (b0_ & ~0x00000001);
transferBuilder_ = 
GeneratedMessage.alwaysUseFieldBuilders ?
getTransferFieldBuilder() : null;
} else {
transferBuilder_.addAllMessages(other.transfer_);
}
}
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
for (int i = 0; i < getTransferCount(); i++) {
if (!getTransfer(i).isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.FileTransferDiagnostics pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.FileTransferDiagnostics) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private List<Diagnostics.FileTransfer> transfer_ =
Collections.emptyList();
private void ensureTransferIsMutable() {
if (!((b0_ & 0x00000001) == 0x00000001)) {
transfer_ = new ArrayList<Diagnostics.FileTransfer>(transfer_);
b0_ |= 0x00000001;
}
}
private RepeatedFieldBuilder<
Diagnostics.FileTransfer, Diagnostics.FileTransfer.Builder, Diagnostics.FileTransferOrBuilder> transferBuilder_;
public List<Diagnostics.FileTransfer> getTransferList() {
if (transferBuilder_ == null) {
return Collections.unmodifiableList(transfer_);
} else {
return transferBuilder_.getMessageList();
}
}
public int getTransferCount() {
if (transferBuilder_ == null) {
return transfer_.size();
} else {
return transferBuilder_.getCount();
}
}
public Diagnostics.FileTransfer getTransfer(int index) {
if (transferBuilder_ == null) {
return transfer_.get(index);
} else {
return transferBuilder_.getMessage(index);
}
}
public Builder setTransfer(
int index, Diagnostics.FileTransfer value) {
if (transferBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransferIsMutable();
transfer_.set(index, value);
onChanged();
} else {
transferBuilder_.setMessage(index, value);
}
return this;
}
public Builder setTransfer(
int index, Diagnostics.FileTransfer.Builder bdForValue) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.set(index, bdForValue.build());
onChanged();
} else {
transferBuilder_.setMessage(index, bdForValue.build());
}
return this;
}
public Builder addTransfer(Diagnostics.FileTransfer value) {
if (transferBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransferIsMutable();
transfer_.add(value);
onChanged();
} else {
transferBuilder_.addMessage(value);
}
return this;
}
public Builder addTransfer(
int index, Diagnostics.FileTransfer value) {
if (transferBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
ensureTransferIsMutable();
transfer_.add(index, value);
onChanged();
} else {
transferBuilder_.addMessage(index, value);
}
return this;
}
public Builder addTransfer(
Diagnostics.FileTransfer.Builder bdForValue) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.add(bdForValue.build());
onChanged();
} else {
transferBuilder_.addMessage(bdForValue.build());
}
return this;
}
public Builder addTransfer(
int index, Diagnostics.FileTransfer.Builder bdForValue) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.add(index, bdForValue.build());
onChanged();
} else {
transferBuilder_.addMessage(index, bdForValue.build());
}
return this;
}
public Builder addAllTransfer(
Iterable<? extends Diagnostics.FileTransfer> values) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
AbstractMessageLite.Builder.addAll(
values, transfer_);
onChanged();
} else {
transferBuilder_.addAllMessages(values);
}
return this;
}
public Builder clearTransfer() {
if (transferBuilder_ == null) {
transfer_ = Collections.emptyList();
b0_ = (b0_ & ~0x00000001);
onChanged();
} else {
transferBuilder_.clear();
}
return this;
}
public Builder removeTransfer(int index) {
if (transferBuilder_ == null) {
ensureTransferIsMutable();
transfer_.remove(index);
onChanged();
} else {
transferBuilder_.remove(index);
}
return this;
}
public Diagnostics.FileTransfer.Builder getTransferBuilder(
int index) {
return getTransferFieldBuilder().getBuilder(index);
}
public Diagnostics.FileTransferOrBuilder getTransferOrBuilder(
int index) {
if (transferBuilder_ == null) {
return transfer_.get(index);  } else {
return transferBuilder_.getMessageOrBuilder(index);
}
}
public List<? extends Diagnostics.FileTransferOrBuilder> 
getTransferOrBuilderList() {
if (transferBuilder_ != null) {
return transferBuilder_.getMessageOrBuilderList();
} else {
return Collections.unmodifiableList(transfer_);
}
}
public Diagnostics.FileTransfer.Builder addTransferBuilder() {
return getTransferFieldBuilder().addBuilder(
Diagnostics.FileTransfer.getDefaultInstance());
}
public Diagnostics.FileTransfer.Builder addTransferBuilder(
int index) {
return getTransferFieldBuilder().addBuilder(
index, Diagnostics.FileTransfer.getDefaultInstance());
}
public List<Diagnostics.FileTransfer.Builder> 
getTransferBuilderList() {
return getTransferFieldBuilder().getBuilderList();
}
private RepeatedFieldBuilder<
Diagnostics.FileTransfer, Diagnostics.FileTransfer.Builder, Diagnostics.FileTransferOrBuilder> 
getTransferFieldBuilder() {
if (transferBuilder_ == null) {
transferBuilder_ = new RepeatedFieldBuilder<
Diagnostics.FileTransfer, Diagnostics.FileTransfer.Builder, Diagnostics.FileTransferOrBuilder>(
transfer_,
((b0_ & 0x00000001) == 0x00000001),
getParentForChildren(),
isClean());
transfer_ = null;
}
return transferBuilder_;
}
}
static {
defaultInstance = new FileTransferDiagnostics(true);
defaultInstance.initFields();
}
}
public interface FileTransferOrBuilder extends
MessageOrBuilder {
boolean hasObject();
Diagnostics.TransferredObject getObject();
Diagnostics.TransferredObjectOrBuilder getObjectOrBuilder();
boolean hasDid();
ByteString getDid();
boolean hasUsingTransportId();
String getUsingTransportId();
ByteString
getUsingTransportIdBytes();
boolean hasPercentCompleted();
long getPercentCompleted();
boolean hasBytesCompleted();
long getBytesCompleted();
boolean hasTotalBytes();
long getTotalBytes();
}
public static final class FileTransfer extends
GeneratedMessage implements
FileTransferOrBuilder {
private FileTransfer(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private FileTransfer(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final FileTransfer defaultInstance;
public static FileTransfer getDefaultInstance() {
return defaultInstance;
}
public FileTransfer getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private FileTransfer(
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
case 10: {
Diagnostics.TransferredObject.Builder subBuilder = null;
if (((b0_ & 0x00000001) == 0x00000001)) {
subBuilder = object_.toBuilder();
}
object_ = input.readMessage(Diagnostics.TransferredObject.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(object_);
object_ = subBuilder.buildPartial();
}
b0_ |= 0x00000001;
break;
}
case 18: {
b0_ |= 0x00000002;
did_ = input.readBytes();
break;
}
case 26: {
ByteString bs = input.readBytes();
b0_ |= 0x00000004;
usingTransportId_ = bs;
break;
}
case 32: {
b0_ |= 0x00000008;
percentCompleted_ = input.readUInt64();
break;
}
case 40: {
b0_ |= 0x00000010;
bytesCompleted_ = input.readUInt64();
break;
}
case 48: {
b0_ |= 0x00000020;
totalBytes_ = input.readUInt64();
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
return Diagnostics.internal_static_FileTransfer_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_FileTransfer_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.FileTransfer.class, Diagnostics.FileTransfer.Builder.class);
}
public static Parser<FileTransfer> PARSER =
new AbstractParser<FileTransfer>() {
public FileTransfer parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new FileTransfer(input, er);
}
};
@Override
public Parser<FileTransfer> getParserForType() {
return PARSER;
}
private int b0_;
public static final int OBJECT_FIELD_NUMBER = 1;
private Diagnostics.TransferredObject object_;
public boolean hasObject() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.TransferredObject getObject() {
return object_;
}
public Diagnostics.TransferredObjectOrBuilder getObjectOrBuilder() {
return object_;
}
public static final int DID_FIELD_NUMBER = 2;
private ByteString did_;
public boolean hasDid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getDid() {
return did_;
}
public static final int USING_TRANSPORT_ID_FIELD_NUMBER = 3;
private Object usingTransportId_;
public boolean hasUsingTransportId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getUsingTransportId() {
Object ref = usingTransportId_;
if (ref instanceof String) {
return (String) ref;
} else {
ByteString bs = 
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
usingTransportId_ = s;
}
return s;
}
}
public ByteString
getUsingTransportIdBytes() {
Object ref = usingTransportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
usingTransportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public static final int PERCENT_COMPLETED_FIELD_NUMBER = 4;
private long percentCompleted_;
public boolean hasPercentCompleted() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getPercentCompleted() {
return percentCompleted_;
}
public static final int BYTES_COMPLETED_FIELD_NUMBER = 5;
private long bytesCompleted_;
public boolean hasBytesCompleted() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getBytesCompleted() {
return bytesCompleted_;
}
public static final int TOTAL_BYTES_FIELD_NUMBER = 6;
private long totalBytes_;
public boolean hasTotalBytes() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getTotalBytes() {
return totalBytes_;
}
private void initFields() {
object_ = Diagnostics.TransferredObject.getDefaultInstance();
did_ = ByteString.EMPTY;
usingTransportId_ = "";
percentCompleted_ = 0L;
bytesCompleted_ = 0L;
totalBytes_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasObject()) {
mii = 0;
return false;
}
if (!hasDid()) {
mii = 0;
return false;
}
if (!hasUsingTransportId()) {
mii = 0;
return false;
}
if (!getObject().isInitialized()) {
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
output.writeMessage(1, object_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, did_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeBytes(3, getUsingTransportIdBytes());
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeUInt64(4, percentCompleted_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeUInt64(5, bytesCompleted_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeUInt64(6, totalBytes_);
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
.computeMessageSize(1, object_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, did_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeBytesSize(3, getUsingTransportIdBytes());
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeUInt64Size(4, percentCompleted_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeUInt64Size(5, bytesCompleted_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeUInt64Size(6, totalBytes_);
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
public static Diagnostics.FileTransfer parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.FileTransfer parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.FileTransfer parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.FileTransfer parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.FileTransfer parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.FileTransfer parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.FileTransfer parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.FileTransfer parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.FileTransfer parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.FileTransfer parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.FileTransfer prototype) {
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
Diagnostics.FileTransferOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_FileTransfer_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_FileTransfer_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.FileTransfer.class, Diagnostics.FileTransfer.Builder.class);
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
getObjectFieldBuilder();
}
}
private static Builder create() {
return new Builder();
}
public Builder clear() {
super.clear();
if (objectBuilder_ == null) {
object_ = Diagnostics.TransferredObject.getDefaultInstance();
} else {
objectBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
did_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
usingTransportId_ = "";
b0_ = (b0_ & ~0x00000004);
percentCompleted_ = 0L;
b0_ = (b0_ & ~0x00000008);
bytesCompleted_ = 0L;
b0_ = (b0_ & ~0x00000010);
totalBytes_ = 0L;
b0_ = (b0_ & ~0x00000020);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_FileTransfer_descriptor;
}
public Diagnostics.FileTransfer getDefaultInstanceForType() {
return Diagnostics.FileTransfer.getDefaultInstance();
}
public Diagnostics.FileTransfer build() {
Diagnostics.FileTransfer result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.FileTransfer buildPartial() {
Diagnostics.FileTransfer result = new Diagnostics.FileTransfer(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
if (objectBuilder_ == null) {
result.object_ = object_;
} else {
result.object_ = objectBuilder_.build();
}
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.did_ = did_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.usingTransportId_ = usingTransportId_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.percentCompleted_ = percentCompleted_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.bytesCompleted_ = bytesCompleted_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.totalBytes_ = totalBytes_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.FileTransfer) {
return mergeFrom((Diagnostics.FileTransfer)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.FileTransfer other) {
if (other == Diagnostics.FileTransfer.getDefaultInstance()) return this;
if (other.hasObject()) {
mergeObject(other.getObject());
}
if (other.hasDid()) {
setDid(other.getDid());
}
if (other.hasUsingTransportId()) {
b0_ |= 0x00000004;
usingTransportId_ = other.usingTransportId_;
onChanged();
}
if (other.hasPercentCompleted()) {
setPercentCompleted(other.getPercentCompleted());
}
if (other.hasBytesCompleted()) {
setBytesCompleted(other.getBytesCompleted());
}
if (other.hasTotalBytes()) {
setTotalBytes(other.getTotalBytes());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasObject()) {
return false;
}
if (!hasDid()) {
return false;
}
if (!hasUsingTransportId()) {
return false;
}
if (!getObject().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.FileTransfer pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.FileTransfer) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Diagnostics.TransferredObject object_ = Diagnostics.TransferredObject.getDefaultInstance();
private SingleFieldBuilder<
Diagnostics.TransferredObject, Diagnostics.TransferredObject.Builder, Diagnostics.TransferredObjectOrBuilder> objectBuilder_;
public boolean hasObject() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Diagnostics.TransferredObject getObject() {
if (objectBuilder_ == null) {
return object_;
} else {
return objectBuilder_.getMessage();
}
}
public Builder setObject(Diagnostics.TransferredObject value) {
if (objectBuilder_ == null) {
if (value == null) {
throw new NullPointerException();
}
object_ = value;
onChanged();
} else {
objectBuilder_.setMessage(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder setObject(
Diagnostics.TransferredObject.Builder bdForValue) {
if (objectBuilder_ == null) {
object_ = bdForValue.build();
onChanged();
} else {
objectBuilder_.setMessage(bdForValue.build());
}
b0_ |= 0x00000001;
return this;
}
public Builder mergeObject(Diagnostics.TransferredObject value) {
if (objectBuilder_ == null) {
if (((b0_ & 0x00000001) == 0x00000001) &&
object_ != Diagnostics.TransferredObject.getDefaultInstance()) {
object_ =
Diagnostics.TransferredObject.newBuilder(object_).mergeFrom(value).buildPartial();
} else {
object_ = value;
}
onChanged();
} else {
objectBuilder_.mergeFrom(value);
}
b0_ |= 0x00000001;
return this;
}
public Builder clearObject() {
if (objectBuilder_ == null) {
object_ = Diagnostics.TransferredObject.getDefaultInstance();
onChanged();
} else {
objectBuilder_.clear();
}
b0_ = (b0_ & ~0x00000001);
return this;
}
public Diagnostics.TransferredObject.Builder getObjectBuilder() {
b0_ |= 0x00000001;
onChanged();
return getObjectFieldBuilder().getBuilder();
}
public Diagnostics.TransferredObjectOrBuilder getObjectOrBuilder() {
if (objectBuilder_ != null) {
return objectBuilder_.getMessageOrBuilder();
} else {
return object_;
}
}
private SingleFieldBuilder<
Diagnostics.TransferredObject, Diagnostics.TransferredObject.Builder, Diagnostics.TransferredObjectOrBuilder> 
getObjectFieldBuilder() {
if (objectBuilder_ == null) {
objectBuilder_ = new SingleFieldBuilder<
Diagnostics.TransferredObject, Diagnostics.TransferredObject.Builder, Diagnostics.TransferredObjectOrBuilder>(
getObject(),
getParentForChildren(),
isClean());
object_ = null;
}
return objectBuilder_;
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
onChanged();
return this;
}
public Builder clearDid() {
b0_ = (b0_ & ~0x00000002);
did_ = getDefaultInstance().getDid();
onChanged();
return this;
}
private Object usingTransportId_ = "";
public boolean hasUsingTransportId() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public String getUsingTransportId() {
Object ref = usingTransportId_;
if (!(ref instanceof String)) {
ByteString bs =
(ByteString) ref;
String s = bs.toStringUtf8();
if (bs.isValidUtf8()) {
usingTransportId_ = s;
}
return s;
} else {
return (String) ref;
}
}
public ByteString
getUsingTransportIdBytes() {
Object ref = usingTransportId_;
if (ref instanceof String) {
ByteString b = 
ByteString.copyFromUtf8(
(String) ref);
usingTransportId_ = b;
return b;
} else {
return (ByteString) ref;
}
}
public Builder setUsingTransportId(
String value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
usingTransportId_ = value;
onChanged();
return this;
}
public Builder clearUsingTransportId() {
b0_ = (b0_ & ~0x00000004);
usingTransportId_ = getDefaultInstance().getUsingTransportId();
onChanged();
return this;
}
public Builder setUsingTransportIdBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000004;
usingTransportId_ = value;
onChanged();
return this;
}
private long percentCompleted_ ;
public boolean hasPercentCompleted() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public long getPercentCompleted() {
return percentCompleted_;
}
public Builder setPercentCompleted(long value) {
b0_ |= 0x00000008;
percentCompleted_ = value;
onChanged();
return this;
}
public Builder clearPercentCompleted() {
b0_ = (b0_ & ~0x00000008);
percentCompleted_ = 0L;
onChanged();
return this;
}
private long bytesCompleted_ ;
public boolean hasBytesCompleted() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public long getBytesCompleted() {
return bytesCompleted_;
}
public Builder setBytesCompleted(long value) {
b0_ |= 0x00000010;
bytesCompleted_ = value;
onChanged();
return this;
}
public Builder clearBytesCompleted() {
b0_ = (b0_ & ~0x00000010);
bytesCompleted_ = 0L;
onChanged();
return this;
}
private long totalBytes_ ;
public boolean hasTotalBytes() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public long getTotalBytes() {
return totalBytes_;
}
public Builder setTotalBytes(long value) {
b0_ |= 0x00000020;
totalBytes_ = value;
onChanged();
return this;
}
public Builder clearTotalBytes() {
b0_ = (b0_ & ~0x00000020);
totalBytes_ = 0L;
onChanged();
return this;
}
}
static {
defaultInstance = new FileTransfer(true);
defaultInstance.initFields();
}
}
public interface TransferredObjectOrBuilder extends
MessageOrBuilder {
boolean hasStoreIndex();
long getStoreIndex();
boolean hasOid();
ByteString getOid();
boolean hasComponentIndex();
long getComponentIndex();
}
public static final class TransferredObject extends
GeneratedMessage implements
TransferredObjectOrBuilder {
private TransferredObject(GeneratedMessage.Builder<?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private TransferredObject(boolean noInit) { this.unknownFields = UnknownFieldSet.getDefaultInstance(); }
private static final TransferredObject defaultInstance;
public static TransferredObject getDefaultInstance() {
return defaultInstance;
}
public TransferredObject getDefaultInstanceForType() {
return defaultInstance;
}
private final UnknownFieldSet unknownFields;
@Override
public final UnknownFieldSet
getUnknownFields() {
return this.unknownFields;
}
private TransferredObject(
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
storeIndex_ = input.readUInt64();
break;
}
case 18: {
b0_ |= 0x00000002;
oid_ = input.readBytes();
break;
}
case 24: {
b0_ |= 0x00000004;
componentIndex_ = input.readUInt64();
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
return Diagnostics.internal_static_TransferredObject_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransferredObject_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransferredObject.class, Diagnostics.TransferredObject.Builder.class);
}
public static Parser<TransferredObject> PARSER =
new AbstractParser<TransferredObject>() {
public TransferredObject parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new TransferredObject(input, er);
}
};
@Override
public Parser<TransferredObject> getParserForType() {
return PARSER;
}
private int b0_;
public static final int STORE_INDEX_FIELD_NUMBER = 1;
private long storeIndex_;
public boolean hasStoreIndex() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getStoreIndex() {
return storeIndex_;
}
public static final int OID_FIELD_NUMBER = 2;
private ByteString oid_;
public boolean hasOid() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public ByteString getOid() {
return oid_;
}
public static final int COMPONENT_INDEX_FIELD_NUMBER = 3;
private long componentIndex_;
public boolean hasComponentIndex() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getComponentIndex() {
return componentIndex_;
}
private void initFields() {
storeIndex_ = 0L;
oid_ = ByteString.EMPTY;
componentIndex_ = 0L;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasStoreIndex()) {
mii = 0;
return false;
}
if (!hasOid()) {
mii = 0;
return false;
}
if (!hasComponentIndex()) {
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
output.writeUInt64(1, storeIndex_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, oid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeUInt64(3, componentIndex_);
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
.computeUInt64Size(1, storeIndex_);
}
if (((b0_ & 0x00000002) == 0x00000002)) {
size += CodedOutputStream
.computeBytesSize(2, oid_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeUInt64Size(3, componentIndex_);
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
public static Diagnostics.TransferredObject parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransferredObject parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransferredObject parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Diagnostics.TransferredObject parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Diagnostics.TransferredObject parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransferredObject parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Diagnostics.TransferredObject parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Diagnostics.TransferredObject parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Diagnostics.TransferredObject parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Diagnostics.TransferredObject parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Diagnostics.TransferredObject prototype) {
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
Diagnostics.TransferredObjectOrBuilder {
public static final Descriptors.Descriptor
getDescriptor() {
return Diagnostics.internal_static_TransferredObject_descriptor;
}
protected GeneratedMessage.FieldAccessorTable
internalGetFieldAccessorTable() {
return Diagnostics.internal_static_TransferredObject_fieldAccessorTable
.ensureFieldAccessorsInitialized(
Diagnostics.TransferredObject.class, Diagnostics.TransferredObject.Builder.class);
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
storeIndex_ = 0L;
b0_ = (b0_ & ~0x00000001);
oid_ = ByteString.EMPTY;
b0_ = (b0_ & ~0x00000002);
componentIndex_ = 0L;
b0_ = (b0_ & ~0x00000004);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Descriptors.Descriptor
getDescriptorForType() {
return Diagnostics.internal_static_TransferredObject_descriptor;
}
public Diagnostics.TransferredObject getDefaultInstanceForType() {
return Diagnostics.TransferredObject.getDefaultInstance();
}
public Diagnostics.TransferredObject build() {
Diagnostics.TransferredObject result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Diagnostics.TransferredObject buildPartial() {
Diagnostics.TransferredObject result = new Diagnostics.TransferredObject(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.storeIndex_ = storeIndex_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.oid_ = oid_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.componentIndex_ = componentIndex_;
result.b0_ = to_b0_;
onBuilt();
return result;
}
public Builder mergeFrom(Message other) {
if (other instanceof Diagnostics.TransferredObject) {
return mergeFrom((Diagnostics.TransferredObject)other);
} else {
super.mergeFrom(other);
return this;
}
}
public Builder mergeFrom(Diagnostics.TransferredObject other) {
if (other == Diagnostics.TransferredObject.getDefaultInstance()) return this;
if (other.hasStoreIndex()) {
setStoreIndex(other.getStoreIndex());
}
if (other.hasOid()) {
setOid(other.getOid());
}
if (other.hasComponentIndex()) {
setComponentIndex(other.getComponentIndex());
}
this.mergeUnknownFields(other.getUnknownFields());
return this;
}
public final boolean isInitialized() {
if (!hasStoreIndex()) {
return false;
}
if (!hasOid()) {
return false;
}
if (!hasComponentIndex()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Diagnostics.TransferredObject pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Diagnostics.TransferredObject) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private long storeIndex_ ;
public boolean hasStoreIndex() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public long getStoreIndex() {
return storeIndex_;
}
public Builder setStoreIndex(long value) {
b0_ |= 0x00000001;
storeIndex_ = value;
onChanged();
return this;
}
public Builder clearStoreIndex() {
b0_ = (b0_ & ~0x00000001);
storeIndex_ = 0L;
onChanged();
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
onChanged();
return this;
}
public Builder clearOid() {
b0_ = (b0_ & ~0x00000002);
oid_ = getDefaultInstance().getOid();
onChanged();
return this;
}
private long componentIndex_ ;
public boolean hasComponentIndex() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public long getComponentIndex() {
return componentIndex_;
}
public Builder setComponentIndex(long value) {
b0_ |= 0x00000004;
componentIndex_ = value;
onChanged();
return this;
}
public Builder clearComponentIndex() {
b0_ = (b0_ & ~0x00000004);
componentIndex_ = 0L;
onChanged();
return this;
}
}
static {
defaultInstance = new TransferredObject(true);
defaultInstance.initFields();
}
}
private static final Descriptors.Descriptor
internal_static_PBDumpStat_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_PBDumpStat_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_PBDumpStat_PBTransport_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_PBDumpStat_PBTransport_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_DeviceDiagnostics_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_DeviceDiagnostics_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_Store_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_Store_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_Device_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_Device_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_Transport_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_Transport_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TransportDiagnostics_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TransportDiagnostics_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TCPDiagnostics_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TCPDiagnostics_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TCPDevice_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TCPDevice_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TCPChannel_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TCPChannel_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_ZephyrDiagnostics_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_ZephyrDiagnostics_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_ZephyrDevice_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_ZephyrDevice_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_ZephyrChannel_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_ZephyrChannel_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_ServerStatus_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_ServerStatus_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_PBInetSocketAddress_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_PBInetSocketAddress_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TransportTransferDiagnostics_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TransportTransferDiagnostics_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TransportTransfer_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TransportTransfer_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_FileTransferDiagnostics_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_FileTransferDiagnostics_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_FileTransfer_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_FileTransfer_fieldAccessorTable;
private static final Descriptors.Descriptor
internal_static_TransferredObject_descriptor;
private static
GeneratedMessage.FieldAccessorTable
internal_static_TransferredObject_fieldAccessorTable;
public static Descriptors.FileDescriptor
getDescriptor() {
return descriptor;
}
private static Descriptors.FileDescriptor
descriptor;
static {
String[] descriptorData = {
"\n\021diagnostics.proto\"\334\001\n\nPBDumpStat\022\017\n\007up" +
"_time\030\001 \001(\004\022\032\n\022enabled_transports\030\002 \003(\t\022" +
"*\n\ttransport\030\003 \003(\0132\027.PBDumpStat.PBTransp" +
"ort\022\014\n\004misc\030\017 \001(\t\032g\n\013PBTransport\022\014\n\004name" +
"\030\001 \001(\t\022\020\n\010bytes_in\030\002 \001(\004\022\021\n\tbytes_out\030\003 " +
"\001(\004\022\022\n\nconnection\030\004 \003(\t\022\021\n\tdiagnosis\030\005 \001" +
"(\t\"s\n\021DeviceDiagnostics\022 \n\020available_sto" +
"res\030\001 \003(\0132\006.Store\022\"\n\022unavailable_stores\030" +
"\002 \003(\0132\006.Store\022\030\n\007devices\030\003 \003(\0132\007.Device\"" +
"@\n\005Store\022\023\n\013store_index\030\001 \002(\004\022\013\n\003sid\030\002 \002",
"(\014\022\025\n\rknown_on_dids\030\003 \003(\014\"_\n\006Device\022\013\n\003d" +
"id\030\001 \002(\014\022(\n\024available_transports\030\002 \003(\0132\n" +
".Transport\022\036\n\026preferred_transport_id\030\003 \001" +
"(\t\"\242\001\n\tTransport\022\024\n\014transport_id\030\001 \002(\t\022(" +
"\n\005state\030\002 \002(\0162\031.Transport.TransportState" +
"\022\033\n\023known_store_indexes\030\003 \003(\004\"8\n\016Transpo" +
"rtState\022\031\n\025POTENTIALLY_AVAILABLE\020\001\022\013\n\007PU" +
"LSING\020\002\"p\n\024TransportDiagnostics\022(\n\017tcp_d" +
"iagnostics\030\001 \001(\0132\017.TCPDiagnostics\022.\n\022zep" +
"hyr_diagnostics\030\003 \001(\0132\022.ZephyrDiagnostic",
"s\"h\n\016TCPDiagnostics\022/\n\021listening_address" +
"\030\001 \002(\0132\024.PBInetSocketAddress\022%\n\021reachabl" +
"e_devices\030\002 \003(\0132\n.TCPDevice\"6\n\tTCPDevice" +
"\022\013\n\003did\030\001 \002(\014\022\034\n\007channel\030\003 \003(\0132\013.TCPChan" +
"nel\"\303\001\n\nTCPChannel\022\034\n\005state\030\001 \001(\0162\r.Chan" +
"nelState\022\022\n\nbytes_sent\030\002 \001(\004\022\026\n\016bytes_re" +
"ceived\030\003 \001(\004\022\020\n\010lifetime\030\004 \001(\004\022\022\n\norigin" +
"ator\030\005 \001(\010\022,\n\016remote_address\030\006 \001(\0132\024.PBI" +
"netSocketAddress\022\027\n\017round_trip_time\030\007 \001(" +
"\004\"c\n\021ZephyrDiagnostics\022$\n\rzephyr_server\030",
"\002 \002(\0132\r.ServerStatus\022(\n\021reachable_device" +
"s\030\003 \003(\0132\r.ZephyrDevice\"<\n\014ZephyrDevice\022\013" +
"\n\003did\030\001 \002(\014\022\037\n\007channel\030\002 \003(\0132\016.ZephyrCha" +
"nnel\"\253\001\n\rZephyrChannel\022\034\n\005state\030\001 \001(\0162\r." +
"ChannelState\022\021\n\tzid_local\030\002 \001(\004\022\022\n\nzid_r" +
"emote\030\003 \001(\004\022\022\n\nbytes_sent\030\004 \001(\004\022\026\n\016bytes" +
"_received\030\005 \001(\004\022\020\n\010lifetime\030\006 \001(\004\022\027\n\017rou" +
"nd_trip_time\030\007 \001(\004\"k\n\014ServerStatus\022,\n\016se" +
"rver_address\030\001 \002(\0132\024.PBInetSocketAddress" +
"\022\021\n\treachable\030\002 \001(\010\022\032\n\022reachability_erro",
"r\030\003 \001(\t\"1\n\023PBInetSocketAddress\022\014\n\004host\030\001" +
" \001(\t\022\014\n\004port\030\002 \001(\r\"\202\001\n\034TransportTransfer" +
"Diagnostics\022$\n\010transfer\030\001 \003(\0132\022.Transpor" +
"tTransfer\022\037\n\027total_bytes_transferred\030\002 \001" +
"(\004\022\033\n\023total_bytes_errored\030\003 \001(\004\"[\n\021Trans" +
"portTransfer\022\024\n\014transport_id\030\001 \002(\t\022\031\n\021by" +
"tes_transferred\030\002 \001(\004\022\025\n\rbytes_errored\030\003" +
" \001(\004\":\n\027FileTransferDiagnostics\022\037\n\010trans" +
"fer\030\001 \003(\0132\r.FileTransfer\"\244\001\n\014FileTransfe" +
"r\022\"\n\006object\030\001 \002(\0132\022.TransferredObject\022\013\n",
"\003did\030\002 \002(\014\022\032\n\022using_transport_id\030\003 \002(\t\022\031" +
"\n\021percent_completed\030\004 \001(\004\022\027\n\017bytes_compl" +
"eted\030\005 \001(\004\022\023\n\013total_bytes\030\006 \001(\004\"N\n\021Trans" +
"ferredObject\022\023\n\013store_index\030\001 \002(\004\022\013\n\003oid" +
"\030\002 \002(\014\022\027\n\017component_index\030\003 \002(\004*8\n\014Chann" +
"elState\022\016\n\nCONNECTING\020\001\022\014\n\010VERIFIED\020\002\022\n\n" +
"\006CLOSED\020\003B\022\n\020com.aerofs.proto"
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
internal_static_PBDumpStat_descriptor =
getDescriptor().getMessageTypes().get(0);
internal_static_PBDumpStat_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_PBDumpStat_descriptor,
new String[] { "UpTime", "EnabledTransports", "Transport", "Misc", });
internal_static_PBDumpStat_PBTransport_descriptor =
internal_static_PBDumpStat_descriptor.getNestedTypes().get(0);
internal_static_PBDumpStat_PBTransport_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_PBDumpStat_PBTransport_descriptor,
new String[] { "Name", "BytesIn", "BytesOut", "Connection", "Diagnosis", });
internal_static_DeviceDiagnostics_descriptor =
getDescriptor().getMessageTypes().get(1);
internal_static_DeviceDiagnostics_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_DeviceDiagnostics_descriptor,
new String[] { "AvailableStores", "UnavailableStores", "Devices", });
internal_static_Store_descriptor =
getDescriptor().getMessageTypes().get(2);
internal_static_Store_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_Store_descriptor,
new String[] { "StoreIndex", "Sid", "KnownOnDids", });
internal_static_Device_descriptor =
getDescriptor().getMessageTypes().get(3);
internal_static_Device_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_Device_descriptor,
new String[] { "Did", "AvailableTransports", "PreferredTransportId", });
internal_static_Transport_descriptor =
getDescriptor().getMessageTypes().get(4);
internal_static_Transport_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_Transport_descriptor,
new String[] { "TransportId", "State", "KnownStoreIndexes", });
internal_static_TransportDiagnostics_descriptor =
getDescriptor().getMessageTypes().get(5);
internal_static_TransportDiagnostics_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TransportDiagnostics_descriptor,
new String[] { "TcpDiagnostics", "ZephyrDiagnostics", });
internal_static_TCPDiagnostics_descriptor =
getDescriptor().getMessageTypes().get(6);
internal_static_TCPDiagnostics_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TCPDiagnostics_descriptor,
new String[] { "ListeningAddress", "ReachableDevices", });
internal_static_TCPDevice_descriptor =
getDescriptor().getMessageTypes().get(7);
internal_static_TCPDevice_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TCPDevice_descriptor,
new String[] { "Did", "Channel", });
internal_static_TCPChannel_descriptor =
getDescriptor().getMessageTypes().get(8);
internal_static_TCPChannel_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TCPChannel_descriptor,
new String[] { "State", "BytesSent", "BytesReceived", "Lifetime", "Originator", "RemoteAddress", "RoundTripTime", });
internal_static_ZephyrDiagnostics_descriptor =
getDescriptor().getMessageTypes().get(9);
internal_static_ZephyrDiagnostics_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_ZephyrDiagnostics_descriptor,
new String[] { "ZephyrServer", "ReachableDevices", });
internal_static_ZephyrDevice_descriptor =
getDescriptor().getMessageTypes().get(10);
internal_static_ZephyrDevice_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_ZephyrDevice_descriptor,
new String[] { "Did", "Channel", });
internal_static_ZephyrChannel_descriptor =
getDescriptor().getMessageTypes().get(11);
internal_static_ZephyrChannel_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_ZephyrChannel_descriptor,
new String[] { "State", "ZidLocal", "ZidRemote", "BytesSent", "BytesReceived", "Lifetime", "RoundTripTime", });
internal_static_ServerStatus_descriptor =
getDescriptor().getMessageTypes().get(12);
internal_static_ServerStatus_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_ServerStatus_descriptor,
new String[] { "ServerAddress", "Reachable", "ReachabilityError", });
internal_static_PBInetSocketAddress_descriptor =
getDescriptor().getMessageTypes().get(13);
internal_static_PBInetSocketAddress_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_PBInetSocketAddress_descriptor,
new String[] { "Host", "Port", });
internal_static_TransportTransferDiagnostics_descriptor =
getDescriptor().getMessageTypes().get(14);
internal_static_TransportTransferDiagnostics_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TransportTransferDiagnostics_descriptor,
new String[] { "Transfer", "TotalBytesTransferred", "TotalBytesErrored", });
internal_static_TransportTransfer_descriptor =
getDescriptor().getMessageTypes().get(15);
internal_static_TransportTransfer_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TransportTransfer_descriptor,
new String[] { "TransportId", "BytesTransferred", "BytesErrored", });
internal_static_FileTransferDiagnostics_descriptor =
getDescriptor().getMessageTypes().get(16);
internal_static_FileTransferDiagnostics_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_FileTransferDiagnostics_descriptor,
new String[] { "Transfer", });
internal_static_FileTransfer_descriptor =
getDescriptor().getMessageTypes().get(17);
internal_static_FileTransfer_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_FileTransfer_descriptor,
new String[] { "Object", "Did", "UsingTransportId", "PercentCompleted", "BytesCompleted", "TotalBytes", });
internal_static_TransferredObject_descriptor =
getDescriptor().getMessageTypes().get(18);
internal_static_TransferredObject_fieldAccessorTable = new
GeneratedMessage.FieldAccessorTable(
internal_static_TransferredObject_descriptor,
new String[] { "StoreIndex", "Oid", "ComponentIndex", });
}
}
