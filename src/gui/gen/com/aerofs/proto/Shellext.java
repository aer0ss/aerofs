package com.aerofs.proto;
import com.google.protobuf.*;
import com.google.common.util.concurrent.*;
import static com.google.common.util.concurrent.Futures.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
@SuppressWarnings("all") public final class Shellext {
private Shellext() {}
public static void registerAllExtensions(
ExtensionRegistryLite registry) {
}
public interface ShellextCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Shellext.ShellextCall.Type getType();
boolean hasGreeting();
Shellext.GreetingCall getGreeting();
boolean hasShareFolder();
Shellext.ShareFolderCall getShareFolder();
boolean hasSyncStatus();
Shellext.SyncStatusCall getSyncStatus();
boolean hasVersionHistory();
Shellext.VersionHistoryCall getVersionHistory();
boolean hasGetPathStatus();
Shellext.GetPathStatusCall getGetPathStatus();
boolean hasConflictResolution();
Shellext.ConflictResolutionCall getConflictResolution();
boolean hasCreateLink();
Shellext.CreateLinkCall getCreateLink();
}
public static final class ShellextCall extends
GeneratedMessageLite implements
ShellextCallOrBuilder {
private ShellextCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ShellextCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ShellextCall defaultInstance;
public static ShellextCall getDefaultInstance() {
return defaultInstance;
}
public ShellextCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ShellextCall(
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
Shellext.ShellextCall.Type value = Shellext.ShellextCall.Type.valueOf(rawValue);
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
Shellext.GreetingCall.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = greeting_.toBuilder();
}
greeting_ = input.readMessage(Shellext.GreetingCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(greeting_);
greeting_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
break;
}
case 26: {
Shellext.ShareFolderCall.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = shareFolder_.toBuilder();
}
shareFolder_ = input.readMessage(Shellext.ShareFolderCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(shareFolder_);
shareFolder_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
break;
}
case 34: {
Shellext.SyncStatusCall.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = syncStatus_.toBuilder();
}
syncStatus_ = input.readMessage(Shellext.SyncStatusCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(syncStatus_);
syncStatus_ = subBuilder.buildPartial();
}
b0_ |= 0x00000008;
break;
}
case 42: {
Shellext.VersionHistoryCall.Builder subBuilder = null;
if (((b0_ & 0x00000010) == 0x00000010)) {
subBuilder = versionHistory_.toBuilder();
}
versionHistory_ = input.readMessage(Shellext.VersionHistoryCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(versionHistory_);
versionHistory_ = subBuilder.buildPartial();
}
b0_ |= 0x00000010;
break;
}
case 50: {
Shellext.GetPathStatusCall.Builder subBuilder = null;
if (((b0_ & 0x00000020) == 0x00000020)) {
subBuilder = getPathStatus_.toBuilder();
}
getPathStatus_ = input.readMessage(Shellext.GetPathStatusCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(getPathStatus_);
getPathStatus_ = subBuilder.buildPartial();
}
b0_ |= 0x00000020;
break;
}
case 58: {
Shellext.ConflictResolutionCall.Builder subBuilder = null;
if (((b0_ & 0x00000040) == 0x00000040)) {
subBuilder = conflictResolution_.toBuilder();
}
conflictResolution_ = input.readMessage(Shellext.ConflictResolutionCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(conflictResolution_);
conflictResolution_ = subBuilder.buildPartial();
}
b0_ |= 0x00000040;
break;
}
case 66: {
Shellext.CreateLinkCall.Builder subBuilder = null;
if (((b0_ & 0x00000080) == 0x00000080)) {
subBuilder = createLink_.toBuilder();
}
createLink_ = input.readMessage(Shellext.CreateLinkCall.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(createLink_);
createLink_ = subBuilder.buildPartial();
}
b0_ |= 0x00000080;
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
public static Parser<ShellextCall> PARSER =
new AbstractParser<ShellextCall>() {
public ShellextCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ShellextCall(input, er);
}
};
@Override
public Parser<ShellextCall> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
GREETING(0, 0),
SHARE_FOLDER(1, 1),
SYNC_STATUS(2, 2),
VERSION_HISTORY(3, 3),
CONFLICT_RESOLUTION(4, 5),
CREATE_LINK(5, 6),
GET_PATH_STATUS(6, 4),
;
public static final int GREETING_VALUE = 0;
public static final int SHARE_FOLDER_VALUE = 1;
public static final int SYNC_STATUS_VALUE = 2;
public static final int VERSION_HISTORY_VALUE = 3;
public static final int CONFLICT_RESOLUTION_VALUE = 5;
public static final int CREATE_LINK_VALUE = 6;
public static final int GET_PATH_STATUS_VALUE = 4;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 0: return GREETING;
case 1: return SHARE_FOLDER;
case 2: return SYNC_STATUS;
case 3: return VERSION_HISTORY;
case 5: return CONFLICT_RESOLUTION;
case 6: return CREATE_LINK;
case 4: return GET_PATH_STATUS;
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
private Shellext.ShellextCall.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Shellext.ShellextCall.Type getType() {
return type_;
}
public static final int GREETING_FIELD_NUMBER = 2;
private Shellext.GreetingCall greeting_;
public boolean hasGreeting() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Shellext.GreetingCall getGreeting() {
return greeting_;
}
public static final int SHARE_FOLDER_FIELD_NUMBER = 3;
private Shellext.ShareFolderCall shareFolder_;
public boolean hasShareFolder() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Shellext.ShareFolderCall getShareFolder() {
return shareFolder_;
}
public static final int SYNC_STATUS_FIELD_NUMBER = 4;
private Shellext.SyncStatusCall syncStatus_;
public boolean hasSyncStatus() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Shellext.SyncStatusCall getSyncStatus() {
return syncStatus_;
}
public static final int VERSION_HISTORY_FIELD_NUMBER = 5;
private Shellext.VersionHistoryCall versionHistory_;
public boolean hasVersionHistory() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public Shellext.VersionHistoryCall getVersionHistory() {
return versionHistory_;
}
public static final int GET_PATH_STATUS_FIELD_NUMBER = 6;
private Shellext.GetPathStatusCall getPathStatus_;
public boolean hasGetPathStatus() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public Shellext.GetPathStatusCall getGetPathStatus() {
return getPathStatus_;
}
public static final int CONFLICT_RESOLUTION_FIELD_NUMBER = 7;
private Shellext.ConflictResolutionCall conflictResolution_;
public boolean hasConflictResolution() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public Shellext.ConflictResolutionCall getConflictResolution() {
return conflictResolution_;
}
public static final int CREATE_LINK_FIELD_NUMBER = 8;
private Shellext.CreateLinkCall createLink_;
public boolean hasCreateLink() {
return ((b0_ & 0x00000080) == 0x00000080);
}
public Shellext.CreateLinkCall getCreateLink() {
return createLink_;
}
private void initFields() {
type_ = Shellext.ShellextCall.Type.GREETING;
greeting_ = Shellext.GreetingCall.getDefaultInstance();
shareFolder_ = Shellext.ShareFolderCall.getDefaultInstance();
syncStatus_ = Shellext.SyncStatusCall.getDefaultInstance();
versionHistory_ = Shellext.VersionHistoryCall.getDefaultInstance();
getPathStatus_ = Shellext.GetPathStatusCall.getDefaultInstance();
conflictResolution_ = Shellext.ConflictResolutionCall.getDefaultInstance();
createLink_ = Shellext.CreateLinkCall.getDefaultInstance();
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
if (hasGreeting()) {
if (!getGreeting().isInitialized()) {
mii = 0;
return false;
}
}
if (hasShareFolder()) {
if (!getShareFolder().isInitialized()) {
mii = 0;
return false;
}
}
if (hasSyncStatus()) {
if (!getSyncStatus().isInitialized()) {
mii = 0;
return false;
}
}
if (hasVersionHistory()) {
if (!getVersionHistory().isInitialized()) {
mii = 0;
return false;
}
}
if (hasGetPathStatus()) {
if (!getGetPathStatus().isInitialized()) {
mii = 0;
return false;
}
}
if (hasConflictResolution()) {
if (!getConflictResolution().isInitialized()) {
mii = 0;
return false;
}
}
if (hasCreateLink()) {
if (!getCreateLink().isInitialized()) {
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
output.writeMessage(2, greeting_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, shareFolder_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, syncStatus_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
output.writeMessage(5, versionHistory_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
output.writeMessage(6, getPathStatus_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
output.writeMessage(7, conflictResolution_);
}
if (((b0_ & 0x00000080) == 0x00000080)) {
output.writeMessage(8, createLink_);
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
.computeMessageSize(2, greeting_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, shareFolder_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeMessageSize(4, syncStatus_);
}
if (((b0_ & 0x00000010) == 0x00000010)) {
size += CodedOutputStream
.computeMessageSize(5, versionHistory_);
}
if (((b0_ & 0x00000020) == 0x00000020)) {
size += CodedOutputStream
.computeMessageSize(6, getPathStatus_);
}
if (((b0_ & 0x00000040) == 0x00000040)) {
size += CodedOutputStream
.computeMessageSize(7, conflictResolution_);
}
if (((b0_ & 0x00000080) == 0x00000080)) {
size += CodedOutputStream
.computeMessageSize(8, createLink_);
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
public static Shellext.ShellextCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ShellextCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ShellextCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ShellextCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ShellextCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ShellextCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.ShellextCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.ShellextCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.ShellextCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ShellextCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.ShellextCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.ShellextCall, Builder>
implements
Shellext.ShellextCallOrBuilder {
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
type_ = Shellext.ShellextCall.Type.GREETING;
b0_ = (b0_ & ~0x00000001);
greeting_ = Shellext.GreetingCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
shareFolder_ = Shellext.ShareFolderCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
syncStatus_ = Shellext.SyncStatusCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
versionHistory_ = Shellext.VersionHistoryCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000010);
getPathStatus_ = Shellext.GetPathStatusCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000020);
conflictResolution_ = Shellext.ConflictResolutionCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000040);
createLink_ = Shellext.CreateLinkCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000080);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Shellext.ShellextCall getDefaultInstanceForType() {
return Shellext.ShellextCall.getDefaultInstance();
}
public Shellext.ShellextCall build() {
Shellext.ShellextCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.ShellextCall buildPartial() {
Shellext.ShellextCall result = new Shellext.ShellextCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.greeting_ = greeting_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.shareFolder_ = shareFolder_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.syncStatus_ = syncStatus_;
if (((from_b0_ & 0x00000010) == 0x00000010)) {
to_b0_ |= 0x00000010;
}
result.versionHistory_ = versionHistory_;
if (((from_b0_ & 0x00000020) == 0x00000020)) {
to_b0_ |= 0x00000020;
}
result.getPathStatus_ = getPathStatus_;
if (((from_b0_ & 0x00000040) == 0x00000040)) {
to_b0_ |= 0x00000040;
}
result.conflictResolution_ = conflictResolution_;
if (((from_b0_ & 0x00000080) == 0x00000080)) {
to_b0_ |= 0x00000080;
}
result.createLink_ = createLink_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.ShellextCall other) {
if (other == Shellext.ShellextCall.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasGreeting()) {
mergeGreeting(other.getGreeting());
}
if (other.hasShareFolder()) {
mergeShareFolder(other.getShareFolder());
}
if (other.hasSyncStatus()) {
mergeSyncStatus(other.getSyncStatus());
}
if (other.hasVersionHistory()) {
mergeVersionHistory(other.getVersionHistory());
}
if (other.hasGetPathStatus()) {
mergeGetPathStatus(other.getGetPathStatus());
}
if (other.hasConflictResolution()) {
mergeConflictResolution(other.getConflictResolution());
}
if (other.hasCreateLink()) {
mergeCreateLink(other.getCreateLink());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (hasGreeting()) {
if (!getGreeting().isInitialized()) {
return false;
}
}
if (hasShareFolder()) {
if (!getShareFolder().isInitialized()) {
return false;
}
}
if (hasSyncStatus()) {
if (!getSyncStatus().isInitialized()) {
return false;
}
}
if (hasVersionHistory()) {
if (!getVersionHistory().isInitialized()) {
return false;
}
}
if (hasGetPathStatus()) {
if (!getGetPathStatus().isInitialized()) {
return false;
}
}
if (hasConflictResolution()) {
if (!getConflictResolution().isInitialized()) {
return false;
}
}
if (hasCreateLink()) {
if (!getCreateLink().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Shellext.ShellextCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.ShellextCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Shellext.ShellextCall.Type type_ = Shellext.ShellextCall.Type.GREETING;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Shellext.ShellextCall.Type getType() {
return type_;
}
public Builder setType(Shellext.ShellextCall.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Shellext.ShellextCall.Type.GREETING;
return this;
}
private Shellext.GreetingCall greeting_ = Shellext.GreetingCall.getDefaultInstance();
public boolean hasGreeting() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Shellext.GreetingCall getGreeting() {
return greeting_;
}
public Builder setGreeting(Shellext.GreetingCall value) {
if (value == null) {
throw new NullPointerException();
}
greeting_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setGreeting(
Shellext.GreetingCall.Builder bdForValue) {
greeting_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeGreeting(Shellext.GreetingCall value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
greeting_ != Shellext.GreetingCall.getDefaultInstance()) {
greeting_ =
Shellext.GreetingCall.newBuilder(greeting_).mergeFrom(value).buildPartial();
} else {
greeting_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearGreeting() {
greeting_ = Shellext.GreetingCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
private Shellext.ShareFolderCall shareFolder_ = Shellext.ShareFolderCall.getDefaultInstance();
public boolean hasShareFolder() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Shellext.ShareFolderCall getShareFolder() {
return shareFolder_;
}
public Builder setShareFolder(Shellext.ShareFolderCall value) {
if (value == null) {
throw new NullPointerException();
}
shareFolder_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setShareFolder(
Shellext.ShareFolderCall.Builder bdForValue) {
shareFolder_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergeShareFolder(Shellext.ShareFolderCall value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
shareFolder_ != Shellext.ShareFolderCall.getDefaultInstance()) {
shareFolder_ =
Shellext.ShareFolderCall.newBuilder(shareFolder_).mergeFrom(value).buildPartial();
} else {
shareFolder_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearShareFolder() {
shareFolder_ = Shellext.ShareFolderCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
private Shellext.SyncStatusCall syncStatus_ = Shellext.SyncStatusCall.getDefaultInstance();
public boolean hasSyncStatus() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Shellext.SyncStatusCall getSyncStatus() {
return syncStatus_;
}
public Builder setSyncStatus(Shellext.SyncStatusCall value) {
if (value == null) {
throw new NullPointerException();
}
syncStatus_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setSyncStatus(
Shellext.SyncStatusCall.Builder bdForValue) {
syncStatus_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergeSyncStatus(Shellext.SyncStatusCall value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
syncStatus_ != Shellext.SyncStatusCall.getDefaultInstance()) {
syncStatus_ =
Shellext.SyncStatusCall.newBuilder(syncStatus_).mergeFrom(value).buildPartial();
} else {
syncStatus_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearSyncStatus() {
syncStatus_ = Shellext.SyncStatusCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
private Shellext.VersionHistoryCall versionHistory_ = Shellext.VersionHistoryCall.getDefaultInstance();
public boolean hasVersionHistory() {
return ((b0_ & 0x00000010) == 0x00000010);
}
public Shellext.VersionHistoryCall getVersionHistory() {
return versionHistory_;
}
public Builder setVersionHistory(Shellext.VersionHistoryCall value) {
if (value == null) {
throw new NullPointerException();
}
versionHistory_ = value;
b0_ |= 0x00000010;
return this;
}
public Builder setVersionHistory(
Shellext.VersionHistoryCall.Builder bdForValue) {
versionHistory_ = bdForValue.build();
b0_ |= 0x00000010;
return this;
}
public Builder mergeVersionHistory(Shellext.VersionHistoryCall value) {
if (((b0_ & 0x00000010) == 0x00000010) &&
versionHistory_ != Shellext.VersionHistoryCall.getDefaultInstance()) {
versionHistory_ =
Shellext.VersionHistoryCall.newBuilder(versionHistory_).mergeFrom(value).buildPartial();
} else {
versionHistory_ = value;
}
b0_ |= 0x00000010;
return this;
}
public Builder clearVersionHistory() {
versionHistory_ = Shellext.VersionHistoryCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000010);
return this;
}
private Shellext.GetPathStatusCall getPathStatus_ = Shellext.GetPathStatusCall.getDefaultInstance();
public boolean hasGetPathStatus() {
return ((b0_ & 0x00000020) == 0x00000020);
}
public Shellext.GetPathStatusCall getGetPathStatus() {
return getPathStatus_;
}
public Builder setGetPathStatus(Shellext.GetPathStatusCall value) {
if (value == null) {
throw new NullPointerException();
}
getPathStatus_ = value;
b0_ |= 0x00000020;
return this;
}
public Builder setGetPathStatus(
Shellext.GetPathStatusCall.Builder bdForValue) {
getPathStatus_ = bdForValue.build();
b0_ |= 0x00000020;
return this;
}
public Builder mergeGetPathStatus(Shellext.GetPathStatusCall value) {
if (((b0_ & 0x00000020) == 0x00000020) &&
getPathStatus_ != Shellext.GetPathStatusCall.getDefaultInstance()) {
getPathStatus_ =
Shellext.GetPathStatusCall.newBuilder(getPathStatus_).mergeFrom(value).buildPartial();
} else {
getPathStatus_ = value;
}
b0_ |= 0x00000020;
return this;
}
public Builder clearGetPathStatus() {
getPathStatus_ = Shellext.GetPathStatusCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000020);
return this;
}
private Shellext.ConflictResolutionCall conflictResolution_ = Shellext.ConflictResolutionCall.getDefaultInstance();
public boolean hasConflictResolution() {
return ((b0_ & 0x00000040) == 0x00000040);
}
public Shellext.ConflictResolutionCall getConflictResolution() {
return conflictResolution_;
}
public Builder setConflictResolution(Shellext.ConflictResolutionCall value) {
if (value == null) {
throw new NullPointerException();
}
conflictResolution_ = value;
b0_ |= 0x00000040;
return this;
}
public Builder setConflictResolution(
Shellext.ConflictResolutionCall.Builder bdForValue) {
conflictResolution_ = bdForValue.build();
b0_ |= 0x00000040;
return this;
}
public Builder mergeConflictResolution(Shellext.ConflictResolutionCall value) {
if (((b0_ & 0x00000040) == 0x00000040) &&
conflictResolution_ != Shellext.ConflictResolutionCall.getDefaultInstance()) {
conflictResolution_ =
Shellext.ConflictResolutionCall.newBuilder(conflictResolution_).mergeFrom(value).buildPartial();
} else {
conflictResolution_ = value;
}
b0_ |= 0x00000040;
return this;
}
public Builder clearConflictResolution() {
conflictResolution_ = Shellext.ConflictResolutionCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000040);
return this;
}
private Shellext.CreateLinkCall createLink_ = Shellext.CreateLinkCall.getDefaultInstance();
public boolean hasCreateLink() {
return ((b0_ & 0x00000080) == 0x00000080);
}
public Shellext.CreateLinkCall getCreateLink() {
return createLink_;
}
public Builder setCreateLink(Shellext.CreateLinkCall value) {
if (value == null) {
throw new NullPointerException();
}
createLink_ = value;
b0_ |= 0x00000080;
return this;
}
public Builder setCreateLink(
Shellext.CreateLinkCall.Builder bdForValue) {
createLink_ = bdForValue.build();
b0_ |= 0x00000080;
return this;
}
public Builder mergeCreateLink(Shellext.CreateLinkCall value) {
if (((b0_ & 0x00000080) == 0x00000080) &&
createLink_ != Shellext.CreateLinkCall.getDefaultInstance()) {
createLink_ =
Shellext.CreateLinkCall.newBuilder(createLink_).mergeFrom(value).buildPartial();
} else {
createLink_ = value;
}
b0_ |= 0x00000080;
return this;
}
public Builder clearCreateLink() {
createLink_ = Shellext.CreateLinkCall.getDefaultInstance();
b0_ = (b0_ & ~0x00000080);
return this;
}
}
static {
defaultInstance = new ShellextCall(true);
defaultInstance.initFields();
}
}
public interface GreetingCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasProtocolVersion();
int getProtocolVersion();
}
public static final class GreetingCall extends
GeneratedMessageLite implements
GreetingCallOrBuilder {
private GreetingCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private GreetingCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final GreetingCall defaultInstance;
public static GreetingCall getDefaultInstance() {
return defaultInstance;
}
public GreetingCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private GreetingCall(
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
protocolVersion_ = input.readInt32();
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
public static Parser<GreetingCall> PARSER =
new AbstractParser<GreetingCall>() {
public GreetingCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new GreetingCall(input, er);
}
};
@Override
public Parser<GreetingCall> getParserForType() {
return PARSER;
}
private int b0_;
public static final int PROTOCOL_VERSION_FIELD_NUMBER = 1;
private int protocolVersion_;
public boolean hasProtocolVersion() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getProtocolVersion() {
return protocolVersion_;
}
private void initFields() {
protocolVersion_ = 0;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasProtocolVersion()) {
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
output.writeInt32(1, protocolVersion_);
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
.computeInt32Size(1, protocolVersion_);
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
public static Shellext.GreetingCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.GreetingCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.GreetingCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.GreetingCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.GreetingCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.GreetingCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.GreetingCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.GreetingCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.GreetingCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.GreetingCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.GreetingCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.GreetingCall, Builder>
implements
Shellext.GreetingCallOrBuilder {
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
protocolVersion_ = 0;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Shellext.GreetingCall getDefaultInstanceForType() {
return Shellext.GreetingCall.getDefaultInstance();
}
public Shellext.GreetingCall build() {
Shellext.GreetingCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.GreetingCall buildPartial() {
Shellext.GreetingCall result = new Shellext.GreetingCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.protocolVersion_ = protocolVersion_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.GreetingCall other) {
if (other == Shellext.GreetingCall.getDefaultInstance()) return this;
if (other.hasProtocolVersion()) {
setProtocolVersion(other.getProtocolVersion());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasProtocolVersion()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Shellext.GreetingCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.GreetingCall) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private int protocolVersion_ ;
public boolean hasProtocolVersion() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public int getProtocolVersion() {
return protocolVersion_;
}
public Builder setProtocolVersion(int value) {
b0_ |= 0x00000001;
protocolVersion_ = value;
return this;
}
public Builder clearProtocolVersion() {
b0_ = (b0_ & ~0x00000001);
protocolVersion_ = 0;
return this;
}
}
static {
defaultInstance = new GreetingCall(true);
defaultInstance.initFields();
}
}
public interface CreateLinkCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
}
public static final class CreateLinkCall extends
GeneratedMessageLite implements
CreateLinkCallOrBuilder {
private CreateLinkCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private CreateLinkCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final CreateLinkCall defaultInstance;
public static CreateLinkCall getDefaultInstance() {
return defaultInstance;
}
public CreateLinkCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private CreateLinkCall(
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
public static Parser<CreateLinkCall> PARSER =
new AbstractParser<CreateLinkCall>() {
public CreateLinkCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new CreateLinkCall(input, er);
}
};
@Override
public Parser<CreateLinkCall> getParserForType() {
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
public static Shellext.CreateLinkCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.CreateLinkCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.CreateLinkCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.CreateLinkCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.CreateLinkCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.CreateLinkCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.CreateLinkCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.CreateLinkCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.CreateLinkCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.CreateLinkCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.CreateLinkCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.CreateLinkCall, Builder>
implements
Shellext.CreateLinkCallOrBuilder {
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
public Shellext.CreateLinkCall getDefaultInstanceForType() {
return Shellext.CreateLinkCall.getDefaultInstance();
}
public Shellext.CreateLinkCall build() {
Shellext.CreateLinkCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.CreateLinkCall buildPartial() {
Shellext.CreateLinkCall result = new Shellext.CreateLinkCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.CreateLinkCall other) {
if (other == Shellext.CreateLinkCall.getDefaultInstance()) return this;
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
Shellext.CreateLinkCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.CreateLinkCall) e.getUnfinishedMessage();
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
defaultInstance = new CreateLinkCall(true);
defaultInstance.initFields();
}
}
public interface ShareFolderCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
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
public static Shellext.ShareFolderCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ShareFolderCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ShareFolderCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ShareFolderCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ShareFolderCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ShareFolderCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.ShareFolderCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.ShareFolderCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.ShareFolderCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ShareFolderCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.ShareFolderCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.ShareFolderCall, Builder>
implements
Shellext.ShareFolderCallOrBuilder {
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
public Shellext.ShareFolderCall getDefaultInstanceForType() {
return Shellext.ShareFolderCall.getDefaultInstance();
}
public Shellext.ShareFolderCall build() {
Shellext.ShareFolderCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.ShareFolderCall buildPartial() {
Shellext.ShareFolderCall result = new Shellext.ShareFolderCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.ShareFolderCall other) {
if (other == Shellext.ShareFolderCall.getDefaultInstance()) return this;
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
Shellext.ShareFolderCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.ShareFolderCall) e.getUnfinishedMessage();
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
defaultInstance = new ShareFolderCall(true);
defaultInstance.initFields();
}
}
public interface SyncStatusCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
}
public static final class SyncStatusCall extends
GeneratedMessageLite implements
SyncStatusCallOrBuilder {
private SyncStatusCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private SyncStatusCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final SyncStatusCall defaultInstance;
public static SyncStatusCall getDefaultInstance() {
return defaultInstance;
}
public SyncStatusCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private SyncStatusCall(
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
public static Parser<SyncStatusCall> PARSER =
new AbstractParser<SyncStatusCall>() {
public SyncStatusCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new SyncStatusCall(input, er);
}
};
@Override
public Parser<SyncStatusCall> getParserForType() {
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
public static Shellext.SyncStatusCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.SyncStatusCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.SyncStatusCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.SyncStatusCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.SyncStatusCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.SyncStatusCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.SyncStatusCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.SyncStatusCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.SyncStatusCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.SyncStatusCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.SyncStatusCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.SyncStatusCall, Builder>
implements
Shellext.SyncStatusCallOrBuilder {
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
public Shellext.SyncStatusCall getDefaultInstanceForType() {
return Shellext.SyncStatusCall.getDefaultInstance();
}
public Shellext.SyncStatusCall build() {
Shellext.SyncStatusCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.SyncStatusCall buildPartial() {
Shellext.SyncStatusCall result = new Shellext.SyncStatusCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.SyncStatusCall other) {
if (other == Shellext.SyncStatusCall.getDefaultInstance()) return this;
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
Shellext.SyncStatusCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.SyncStatusCall) e.getUnfinishedMessage();
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
defaultInstance = new SyncStatusCall(true);
defaultInstance.initFields();
}
}
public interface VersionHistoryCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
}
public static final class VersionHistoryCall extends
GeneratedMessageLite implements
VersionHistoryCallOrBuilder {
private VersionHistoryCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private VersionHistoryCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final VersionHistoryCall defaultInstance;
public static VersionHistoryCall getDefaultInstance() {
return defaultInstance;
}
public VersionHistoryCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private VersionHistoryCall(
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
public static Parser<VersionHistoryCall> PARSER =
new AbstractParser<VersionHistoryCall>() {
public VersionHistoryCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new VersionHistoryCall(input, er);
}
};
@Override
public Parser<VersionHistoryCall> getParserForType() {
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
public static Shellext.VersionHistoryCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.VersionHistoryCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.VersionHistoryCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.VersionHistoryCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.VersionHistoryCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.VersionHistoryCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.VersionHistoryCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.VersionHistoryCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.VersionHistoryCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.VersionHistoryCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.VersionHistoryCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.VersionHistoryCall, Builder>
implements
Shellext.VersionHistoryCallOrBuilder {
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
public Shellext.VersionHistoryCall getDefaultInstanceForType() {
return Shellext.VersionHistoryCall.getDefaultInstance();
}
public Shellext.VersionHistoryCall build() {
Shellext.VersionHistoryCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.VersionHistoryCall buildPartial() {
Shellext.VersionHistoryCall result = new Shellext.VersionHistoryCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.VersionHistoryCall other) {
if (other == Shellext.VersionHistoryCall.getDefaultInstance()) return this;
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
Shellext.VersionHistoryCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.VersionHistoryCall) e.getUnfinishedMessage();
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
defaultInstance = new VersionHistoryCall(true);
defaultInstance.initFields();
}
}
public interface ConflictResolutionCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
}
public static final class ConflictResolutionCall extends
GeneratedMessageLite implements
ConflictResolutionCallOrBuilder {
private ConflictResolutionCall(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ConflictResolutionCall(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ConflictResolutionCall defaultInstance;
public static ConflictResolutionCall getDefaultInstance() {
return defaultInstance;
}
public ConflictResolutionCall getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ConflictResolutionCall(
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
public static Parser<ConflictResolutionCall> PARSER =
new AbstractParser<ConflictResolutionCall>() {
public ConflictResolutionCall parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ConflictResolutionCall(input, er);
}
};
@Override
public Parser<ConflictResolutionCall> getParserForType() {
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
public static Shellext.ConflictResolutionCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ConflictResolutionCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ConflictResolutionCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ConflictResolutionCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ConflictResolutionCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ConflictResolutionCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.ConflictResolutionCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.ConflictResolutionCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.ConflictResolutionCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ConflictResolutionCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.ConflictResolutionCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.ConflictResolutionCall, Builder>
implements
Shellext.ConflictResolutionCallOrBuilder {
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
public Shellext.ConflictResolutionCall getDefaultInstanceForType() {
return Shellext.ConflictResolutionCall.getDefaultInstance();
}
public Shellext.ConflictResolutionCall build() {
Shellext.ConflictResolutionCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.ConflictResolutionCall buildPartial() {
Shellext.ConflictResolutionCall result = new Shellext.ConflictResolutionCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.ConflictResolutionCall other) {
if (other == Shellext.ConflictResolutionCall.getDefaultInstance()) return this;
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
Shellext.ConflictResolutionCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.ConflictResolutionCall) e.getUnfinishedMessage();
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
defaultInstance = new ConflictResolutionCall(true);
defaultInstance.initFields();
}
}
public interface GetPathStatusCallOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
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
public static Shellext.GetPathStatusCall parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.GetPathStatusCall parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.GetPathStatusCall parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.GetPathStatusCall parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.GetPathStatusCall parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.GetPathStatusCall parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.GetPathStatusCall parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.GetPathStatusCall parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.GetPathStatusCall parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.GetPathStatusCall parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.GetPathStatusCall prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.GetPathStatusCall, Builder>
implements
Shellext.GetPathStatusCallOrBuilder {
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
public Shellext.GetPathStatusCall getDefaultInstanceForType() {
return Shellext.GetPathStatusCall.getDefaultInstance();
}
public Shellext.GetPathStatusCall build() {
Shellext.GetPathStatusCall result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.GetPathStatusCall buildPartial() {
Shellext.GetPathStatusCall result = new Shellext.GetPathStatusCall(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.GetPathStatusCall other) {
if (other == Shellext.GetPathStatusCall.getDefaultInstance()) return this;
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
Shellext.GetPathStatusCall pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.GetPathStatusCall) e.getUnfinishedMessage();
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
defaultInstance = new GetPathStatusCall(true);
defaultInstance.initFields();
}
}
public interface ShellextNotificationOrBuilder extends
MessageLiteOrBuilder {
boolean hasType();
Shellext.ShellextNotification.Type getType();
boolean hasRootAnchor();
Shellext.RootAnchorNotification getRootAnchor();
boolean hasPathStatus();
Shellext.PathStatusNotification getPathStatus();
boolean hasLinkSharingEnabled();
Shellext.LinkSharingEnabled getLinkSharingEnabled();
}
public static final class ShellextNotification extends
GeneratedMessageLite implements
ShellextNotificationOrBuilder {
private ShellextNotification(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private ShellextNotification(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final ShellextNotification defaultInstance;
public static ShellextNotification getDefaultInstance() {
return defaultInstance;
}
public ShellextNotification getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private ShellextNotification(
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
Shellext.ShellextNotification.Type value = Shellext.ShellextNotification.Type.valueOf(rawValue);
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
Shellext.RootAnchorNotification.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = rootAnchor_.toBuilder();
}
rootAnchor_ = input.readMessage(Shellext.RootAnchorNotification.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(rootAnchor_);
rootAnchor_ = subBuilder.buildPartial();
}
b0_ |= 0x00000002;
break;
}
case 26: {
Shellext.PathStatusNotification.Builder subBuilder = null;
if (((b0_ & 0x00000004) == 0x00000004)) {
subBuilder = pathStatus_.toBuilder();
}
pathStatus_ = input.readMessage(Shellext.PathStatusNotification.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(pathStatus_);
pathStatus_ = subBuilder.buildPartial();
}
b0_ |= 0x00000004;
break;
}
case 34: {
Shellext.LinkSharingEnabled.Builder subBuilder = null;
if (((b0_ & 0x00000008) == 0x00000008)) {
subBuilder = linkSharingEnabled_.toBuilder();
}
linkSharingEnabled_ = input.readMessage(Shellext.LinkSharingEnabled.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(linkSharingEnabled_);
linkSharingEnabled_ = subBuilder.buildPartial();
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
public static Parser<ShellextNotification> PARSER =
new AbstractParser<ShellextNotification>() {
public ShellextNotification parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new ShellextNotification(input, er);
}
};
@Override
public Parser<ShellextNotification> getParserForType() {
return PARSER;
}
public enum Type
implements Internal.EnumLite {
ROOT_ANCHOR(0, 1),
PATH_STATUS(1, 2),
CLEAR_STATUS_CACHE(2, 3),
LINK_SHARING_ENABLED(3, 4),
;
public static final int ROOT_ANCHOR_VALUE = 1;
public static final int PATH_STATUS_VALUE = 2;
public static final int CLEAR_STATUS_CACHE_VALUE = 3;
public static final int LINK_SHARING_ENABLED_VALUE = 4;
public final int getNumber() { return value; }
public static Type valueOf(int value) {
switch (value) {
case 1: return ROOT_ANCHOR;
case 2: return PATH_STATUS;
case 3: return CLEAR_STATUS_CACHE;
case 4: return LINK_SHARING_ENABLED;
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
private Shellext.ShellextNotification.Type type_;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Shellext.ShellextNotification.Type getType() {
return type_;
}
public static final int ROOT_ANCHOR_FIELD_NUMBER = 2;
private Shellext.RootAnchorNotification rootAnchor_;
public boolean hasRootAnchor() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Shellext.RootAnchorNotification getRootAnchor() {
return rootAnchor_;
}
public static final int PATH_STATUS_FIELD_NUMBER = 3;
private Shellext.PathStatusNotification pathStatus_;
public boolean hasPathStatus() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Shellext.PathStatusNotification getPathStatus() {
return pathStatus_;
}
public static final int LINK_SHARING_ENABLED_FIELD_NUMBER = 4;
private Shellext.LinkSharingEnabled linkSharingEnabled_;
public boolean hasLinkSharingEnabled() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Shellext.LinkSharingEnabled getLinkSharingEnabled() {
return linkSharingEnabled_;
}
private void initFields() {
type_ = Shellext.ShellextNotification.Type.ROOT_ANCHOR;
rootAnchor_ = Shellext.RootAnchorNotification.getDefaultInstance();
pathStatus_ = Shellext.PathStatusNotification.getDefaultInstance();
linkSharingEnabled_ = Shellext.LinkSharingEnabled.getDefaultInstance();
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
if (hasRootAnchor()) {
if (!getRootAnchor().isInitialized()) {
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
if (hasLinkSharingEnabled()) {
if (!getLinkSharingEnabled().isInitialized()) {
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
output.writeMessage(2, rootAnchor_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
output.writeMessage(3, pathStatus_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
output.writeMessage(4, linkSharingEnabled_);
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
.computeMessageSize(2, rootAnchor_);
}
if (((b0_ & 0x00000004) == 0x00000004)) {
size += CodedOutputStream
.computeMessageSize(3, pathStatus_);
}
if (((b0_ & 0x00000008) == 0x00000008)) {
size += CodedOutputStream
.computeMessageSize(4, linkSharingEnabled_);
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
public static Shellext.ShellextNotification parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ShellextNotification parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ShellextNotification parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.ShellextNotification parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.ShellextNotification parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ShellextNotification parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.ShellextNotification parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.ShellextNotification parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.ShellextNotification parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.ShellextNotification parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.ShellextNotification prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.ShellextNotification, Builder>
implements
Shellext.ShellextNotificationOrBuilder {
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
type_ = Shellext.ShellextNotification.Type.ROOT_ANCHOR;
b0_ = (b0_ & ~0x00000001);
rootAnchor_ = Shellext.RootAnchorNotification.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
pathStatus_ = Shellext.PathStatusNotification.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
linkSharingEnabled_ = Shellext.LinkSharingEnabled.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Shellext.ShellextNotification getDefaultInstanceForType() {
return Shellext.ShellextNotification.getDefaultInstance();
}
public Shellext.ShellextNotification build() {
Shellext.ShellextNotification result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.ShellextNotification buildPartial() {
Shellext.ShellextNotification result = new Shellext.ShellextNotification(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.type_ = type_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.rootAnchor_ = rootAnchor_;
if (((from_b0_ & 0x00000004) == 0x00000004)) {
to_b0_ |= 0x00000004;
}
result.pathStatus_ = pathStatus_;
if (((from_b0_ & 0x00000008) == 0x00000008)) {
to_b0_ |= 0x00000008;
}
result.linkSharingEnabled_ = linkSharingEnabled_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.ShellextNotification other) {
if (other == Shellext.ShellextNotification.getDefaultInstance()) return this;
if (other.hasType()) {
setType(other.getType());
}
if (other.hasRootAnchor()) {
mergeRootAnchor(other.getRootAnchor());
}
if (other.hasPathStatus()) {
mergePathStatus(other.getPathStatus());
}
if (other.hasLinkSharingEnabled()) {
mergeLinkSharingEnabled(other.getLinkSharingEnabled());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasType()) {
return false;
}
if (hasRootAnchor()) {
if (!getRootAnchor().isInitialized()) {
return false;
}
}
if (hasPathStatus()) {
if (!getPathStatus().isInitialized()) {
return false;
}
}
if (hasLinkSharingEnabled()) {
if (!getLinkSharingEnabled().isInitialized()) {
return false;
}
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Shellext.ShellextNotification pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.ShellextNotification) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private Shellext.ShellextNotification.Type type_ = Shellext.ShellextNotification.Type.ROOT_ANCHOR;
public boolean hasType() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public Shellext.ShellextNotification.Type getType() {
return type_;
}
public Builder setType(Shellext.ShellextNotification.Type value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000001;
type_ = value;
return this;
}
public Builder clearType() {
b0_ = (b0_ & ~0x00000001);
type_ = Shellext.ShellextNotification.Type.ROOT_ANCHOR;
return this;
}
private Shellext.RootAnchorNotification rootAnchor_ = Shellext.RootAnchorNotification.getDefaultInstance();
public boolean hasRootAnchor() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public Shellext.RootAnchorNotification getRootAnchor() {
return rootAnchor_;
}
public Builder setRootAnchor(Shellext.RootAnchorNotification value) {
if (value == null) {
throw new NullPointerException();
}
rootAnchor_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setRootAnchor(
Shellext.RootAnchorNotification.Builder bdForValue) {
rootAnchor_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeRootAnchor(Shellext.RootAnchorNotification value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
rootAnchor_ != Shellext.RootAnchorNotification.getDefaultInstance()) {
rootAnchor_ =
Shellext.RootAnchorNotification.newBuilder(rootAnchor_).mergeFrom(value).buildPartial();
} else {
rootAnchor_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearRootAnchor() {
rootAnchor_ = Shellext.RootAnchorNotification.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
private Shellext.PathStatusNotification pathStatus_ = Shellext.PathStatusNotification.getDefaultInstance();
public boolean hasPathStatus() {
return ((b0_ & 0x00000004) == 0x00000004);
}
public Shellext.PathStatusNotification getPathStatus() {
return pathStatus_;
}
public Builder setPathStatus(Shellext.PathStatusNotification value) {
if (value == null) {
throw new NullPointerException();
}
pathStatus_ = value;
b0_ |= 0x00000004;
return this;
}
public Builder setPathStatus(
Shellext.PathStatusNotification.Builder bdForValue) {
pathStatus_ = bdForValue.build();
b0_ |= 0x00000004;
return this;
}
public Builder mergePathStatus(Shellext.PathStatusNotification value) {
if (((b0_ & 0x00000004) == 0x00000004) &&
pathStatus_ != Shellext.PathStatusNotification.getDefaultInstance()) {
pathStatus_ =
Shellext.PathStatusNotification.newBuilder(pathStatus_).mergeFrom(value).buildPartial();
} else {
pathStatus_ = value;
}
b0_ |= 0x00000004;
return this;
}
public Builder clearPathStatus() {
pathStatus_ = Shellext.PathStatusNotification.getDefaultInstance();
b0_ = (b0_ & ~0x00000004);
return this;
}
private Shellext.LinkSharingEnabled linkSharingEnabled_ = Shellext.LinkSharingEnabled.getDefaultInstance();
public boolean hasLinkSharingEnabled() {
return ((b0_ & 0x00000008) == 0x00000008);
}
public Shellext.LinkSharingEnabled getLinkSharingEnabled() {
return linkSharingEnabled_;
}
public Builder setLinkSharingEnabled(Shellext.LinkSharingEnabled value) {
if (value == null) {
throw new NullPointerException();
}
linkSharingEnabled_ = value;
b0_ |= 0x00000008;
return this;
}
public Builder setLinkSharingEnabled(
Shellext.LinkSharingEnabled.Builder bdForValue) {
linkSharingEnabled_ = bdForValue.build();
b0_ |= 0x00000008;
return this;
}
public Builder mergeLinkSharingEnabled(Shellext.LinkSharingEnabled value) {
if (((b0_ & 0x00000008) == 0x00000008) &&
linkSharingEnabled_ != Shellext.LinkSharingEnabled.getDefaultInstance()) {
linkSharingEnabled_ =
Shellext.LinkSharingEnabled.newBuilder(linkSharingEnabled_).mergeFrom(value).buildPartial();
} else {
linkSharingEnabled_ = value;
}
b0_ |= 0x00000008;
return this;
}
public Builder clearLinkSharingEnabled() {
linkSharingEnabled_ = Shellext.LinkSharingEnabled.getDefaultInstance();
b0_ = (b0_ & ~0x00000008);
return this;
}
}
static {
defaultInstance = new ShellextNotification(true);
defaultInstance.initFields();
}
}
public interface RootAnchorNotificationOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
boolean hasUser();
String getUser();
ByteString
getUserBytes();
}
public static final class RootAnchorNotification extends
GeneratedMessageLite implements
RootAnchorNotificationOrBuilder {
private RootAnchorNotification(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private RootAnchorNotification(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final RootAnchorNotification defaultInstance;
public static RootAnchorNotification getDefaultInstance() {
return defaultInstance;
}
public RootAnchorNotification getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private RootAnchorNotification(
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
ByteString bs = input.readBytes();
b0_ |= 0x00000002;
user_ = bs;
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
public static Parser<RootAnchorNotification> PARSER =
new AbstractParser<RootAnchorNotification>() {
public RootAnchorNotification parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new RootAnchorNotification(input, er);
}
};
@Override
public Parser<RootAnchorNotification> getParserForType() {
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
public static final int USER_FIELD_NUMBER = 2;
private Object user_;
public boolean hasUser() {
return ((b0_ & 0x00000002) == 0x00000002);
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
private void initFields() {
path_ = "";
user_ = "";
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
if (((b0_ & 0x00000002) == 0x00000002)) {
output.writeBytes(2, getUserBytes());
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
.computeBytesSize(2, getUserBytes());
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
public static Shellext.RootAnchorNotification parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.RootAnchorNotification parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.RootAnchorNotification parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.RootAnchorNotification parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.RootAnchorNotification parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.RootAnchorNotification parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.RootAnchorNotification parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.RootAnchorNotification parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.RootAnchorNotification parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.RootAnchorNotification parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.RootAnchorNotification prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.RootAnchorNotification, Builder>
implements
Shellext.RootAnchorNotificationOrBuilder {
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
user_ = "";
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Shellext.RootAnchorNotification getDefaultInstanceForType() {
return Shellext.RootAnchorNotification.getDefaultInstance();
}
public Shellext.RootAnchorNotification build() {
Shellext.RootAnchorNotification result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.RootAnchorNotification buildPartial() {
Shellext.RootAnchorNotification result = new Shellext.RootAnchorNotification(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.user_ = user_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.RootAnchorNotification other) {
if (other == Shellext.RootAnchorNotification.getDefaultInstance()) return this;
if (other.hasPath()) {
b0_ |= 0x00000001;
path_ = other.path_;
}
if (other.hasUser()) {
b0_ |= 0x00000002;
user_ = other.user_;
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
Shellext.RootAnchorNotification pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.RootAnchorNotification) e.getUnfinishedMessage();
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
private Object user_ = "";
public boolean hasUser() {
return ((b0_ & 0x00000002) == 0x00000002);
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
b0_ |= 0x00000002;
user_ = value;
return this;
}
public Builder clearUser() {
b0_ = (b0_ & ~0x00000002);
user_ = getDefaultInstance().getUser();
return this;
}
public Builder setUserBytes(
ByteString value) {
if (value == null) {
throw new NullPointerException();
}
b0_ |= 0x00000002;
user_ = value;
return this;
}
}
static {
defaultInstance = new RootAnchorNotification(true);
defaultInstance.initFields();
}
}
public interface PathStatusNotificationOrBuilder extends
MessageLiteOrBuilder {
boolean hasPath();
String getPath();
ByteString
getPathBytes();
boolean hasStatus();
PathStatus.PBPathStatus getStatus();
}
public static final class PathStatusNotification extends
GeneratedMessageLite implements
PathStatusNotificationOrBuilder {
private PathStatusNotification(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private PathStatusNotification(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final PathStatusNotification defaultInstance;
public static PathStatusNotification getDefaultInstance() {
return defaultInstance;
}
public PathStatusNotification getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private PathStatusNotification(
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
PathStatus.PBPathStatus.Builder subBuilder = null;
if (((b0_ & 0x00000002) == 0x00000002)) {
subBuilder = status_.toBuilder();
}
status_ = input.readMessage(PathStatus.PBPathStatus.PARSER, er);
if (subBuilder != null) {
subBuilder.mergeFrom(status_);
status_ = subBuilder.buildPartial();
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
public static Parser<PathStatusNotification> PARSER =
new AbstractParser<PathStatusNotification>() {
public PathStatusNotification parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new PathStatusNotification(input, er);
}
};
@Override
public Parser<PathStatusNotification> getParserForType() {
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
public static final int STATUS_FIELD_NUMBER = 2;
private PathStatus.PBPathStatus status_;
public boolean hasStatus() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public PathStatus.PBPathStatus getStatus() {
return status_;
}
private void initFields() {
path_ = "";
status_ = PathStatus.PBPathStatus.getDefaultInstance();
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
if (!hasStatus()) {
mii = 0;
return false;
}
if (!getStatus().isInitialized()) {
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
output.writeMessage(2, status_);
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
.computeMessageSize(2, status_);
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
public static Shellext.PathStatusNotification parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.PathStatusNotification parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.PathStatusNotification parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.PathStatusNotification parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.PathStatusNotification parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.PathStatusNotification parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.PathStatusNotification parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.PathStatusNotification parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.PathStatusNotification parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.PathStatusNotification parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.PathStatusNotification prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.PathStatusNotification, Builder>
implements
Shellext.PathStatusNotificationOrBuilder {
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
status_ = PathStatus.PBPathStatus.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Shellext.PathStatusNotification getDefaultInstanceForType() {
return Shellext.PathStatusNotification.getDefaultInstance();
}
public Shellext.PathStatusNotification build() {
Shellext.PathStatusNotification result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.PathStatusNotification buildPartial() {
Shellext.PathStatusNotification result = new Shellext.PathStatusNotification(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.path_ = path_;
if (((from_b0_ & 0x00000002) == 0x00000002)) {
to_b0_ |= 0x00000002;
}
result.status_ = status_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.PathStatusNotification other) {
if (other == Shellext.PathStatusNotification.getDefaultInstance()) return this;
if (other.hasPath()) {
b0_ |= 0x00000001;
path_ = other.path_;
}
if (other.hasStatus()) {
mergeStatus(other.getStatus());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasPath()) {
return false;
}
if (!hasStatus()) {
return false;
}
if (!getStatus().isInitialized()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Shellext.PathStatusNotification pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.PathStatusNotification) e.getUnfinishedMessage();
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
private PathStatus.PBPathStatus status_ = PathStatus.PBPathStatus.getDefaultInstance();
public boolean hasStatus() {
return ((b0_ & 0x00000002) == 0x00000002);
}
public PathStatus.PBPathStatus getStatus() {
return status_;
}
public Builder setStatus(PathStatus.PBPathStatus value) {
if (value == null) {
throw new NullPointerException();
}
status_ = value;
b0_ |= 0x00000002;
return this;
}
public Builder setStatus(
PathStatus.PBPathStatus.Builder bdForValue) {
status_ = bdForValue.build();
b0_ |= 0x00000002;
return this;
}
public Builder mergeStatus(PathStatus.PBPathStatus value) {
if (((b0_ & 0x00000002) == 0x00000002) &&
status_ != PathStatus.PBPathStatus.getDefaultInstance()) {
status_ =
PathStatus.PBPathStatus.newBuilder(status_).mergeFrom(value).buildPartial();
} else {
status_ = value;
}
b0_ |= 0x00000002;
return this;
}
public Builder clearStatus() {
status_ = PathStatus.PBPathStatus.getDefaultInstance();
b0_ = (b0_ & ~0x00000002);
return this;
}
}
static {
defaultInstance = new PathStatusNotification(true);
defaultInstance.initFields();
}
}
public interface LinkSharingEnabledOrBuilder extends
MessageLiteOrBuilder {
boolean hasIsLinkSharingEnabled();
boolean getIsLinkSharingEnabled();
}
public static final class LinkSharingEnabled extends
GeneratedMessageLite implements
LinkSharingEnabledOrBuilder {
private LinkSharingEnabled(GeneratedMessageLite.Builder<?,?> bd) {
super(bd);
this.unknownFields = bd.getUnknownFields();
}
private LinkSharingEnabled(boolean noInit) { this.unknownFields = ByteString.EMPTY;}
private static final LinkSharingEnabled defaultInstance;
public static LinkSharingEnabled getDefaultInstance() {
return defaultInstance;
}
public LinkSharingEnabled getDefaultInstanceForType() {
return defaultInstance;
}
private final ByteString unknownFields;
private LinkSharingEnabled(
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
isLinkSharingEnabled_ = input.readBool();
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
public static Parser<LinkSharingEnabled> PARSER =
new AbstractParser<LinkSharingEnabled>() {
public LinkSharingEnabled parsePartialFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return new LinkSharingEnabled(input, er);
}
};
@Override
public Parser<LinkSharingEnabled> getParserForType() {
return PARSER;
}
private int b0_;
public static final int IS_LINK_SHARING_ENABLED_FIELD_NUMBER = 1;
private boolean isLinkSharingEnabled_;
public boolean hasIsLinkSharingEnabled() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getIsLinkSharingEnabled() {
return isLinkSharingEnabled_;
}
private void initFields() {
isLinkSharingEnabled_ = false;
}
private byte mii = -1;
public final boolean isInitialized() {
byte isInitialized = mii;
if (isInitialized == 1) return true;
if (isInitialized == 0) return false;
if (!hasIsLinkSharingEnabled()) {
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
output.writeBool(1, isLinkSharingEnabled_);
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
.computeBoolSize(1, isLinkSharingEnabled_);
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
public static Shellext.LinkSharingEnabled parseFrom(
ByteString data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.LinkSharingEnabled parseFrom(
ByteString data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.LinkSharingEnabled parseFrom(byte[] data)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data);
}
public static Shellext.LinkSharingEnabled parseFrom(
byte[] data,
ExtensionRegistryLite er)
throws InvalidProtocolBufferException {
return PARSER.parseFrom(data, er);
}
public static Shellext.LinkSharingEnabled parseFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.LinkSharingEnabled parseFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Shellext.LinkSharingEnabled parseDelimitedFrom(java.io.InputStream input)
throws IOException {
return PARSER.parseDelimitedFrom(input);
}
public static Shellext.LinkSharingEnabled parseDelimitedFrom(
java.io.InputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseDelimitedFrom(input, er);
}
public static Shellext.LinkSharingEnabled parseFrom(
CodedInputStream input)
throws IOException {
return PARSER.parseFrom(input);
}
public static Shellext.LinkSharingEnabled parseFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
return PARSER.parseFrom(input, er);
}
public static Builder newBuilder() { return Builder.create(); }
public Builder newBuilderForType() { return newBuilder(); }
public static Builder newBuilder(Shellext.LinkSharingEnabled prototype) {
return newBuilder().mergeFrom(prototype);
}
public Builder toBuilder() { return newBuilder(this); }
public static final class Builder extends
GeneratedMessageLite.Builder<
Shellext.LinkSharingEnabled, Builder>
implements
Shellext.LinkSharingEnabledOrBuilder {
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
isLinkSharingEnabled_ = false;
b0_ = (b0_ & ~0x00000001);
return this;
}
public Builder clone() {
return create().mergeFrom(buildPartial());
}
public Shellext.LinkSharingEnabled getDefaultInstanceForType() {
return Shellext.LinkSharingEnabled.getDefaultInstance();
}
public Shellext.LinkSharingEnabled build() {
Shellext.LinkSharingEnabled result = buildPartial();
if (!result.isInitialized()) {
throw newUninitializedMessageException(result);
}
return result;
}
public Shellext.LinkSharingEnabled buildPartial() {
Shellext.LinkSharingEnabled result = new Shellext.LinkSharingEnabled(this);
int from_b0_ = b0_;
int to_b0_ = 0;
if (((from_b0_ & 0x00000001) == 0x00000001)) {
to_b0_ |= 0x00000001;
}
result.isLinkSharingEnabled_ = isLinkSharingEnabled_;
result.b0_ = to_b0_;
return result;
}
public Builder mergeFrom(Shellext.LinkSharingEnabled other) {
if (other == Shellext.LinkSharingEnabled.getDefaultInstance()) return this;
if (other.hasIsLinkSharingEnabled()) {
setIsLinkSharingEnabled(other.getIsLinkSharingEnabled());
}
setUnknownFields(
getUnknownFields().concat(other.unknownFields));
return this;
}
public final boolean isInitialized() {
if (!hasIsLinkSharingEnabled()) {
return false;
}
return true;
}
public Builder mergeFrom(
CodedInputStream input,
ExtensionRegistryLite er)
throws IOException {
Shellext.LinkSharingEnabled pm = null;
try {
pm = PARSER.parsePartialFrom(input, er);
} catch (InvalidProtocolBufferException e) {
pm = (Shellext.LinkSharingEnabled) e.getUnfinishedMessage();
throw e;
} finally {
if (pm != null) {
mergeFrom(pm);
}
}
return this;
}
private int b0_;
private boolean isLinkSharingEnabled_ ;
public boolean hasIsLinkSharingEnabled() {
return ((b0_ & 0x00000001) == 0x00000001);
}
public boolean getIsLinkSharingEnabled() {
return isLinkSharingEnabled_;
}
public Builder setIsLinkSharingEnabled(boolean value) {
b0_ |= 0x00000001;
isLinkSharingEnabled_ = value;
return this;
}
public Builder clearIsLinkSharingEnabled() {
b0_ = (b0_ & ~0x00000001);
isLinkSharingEnabled_ = false;
return this;
}
}
static {
defaultInstance = new LinkSharingEnabled(true);
defaultInstance.initFields();
}
}
static {
}
}
