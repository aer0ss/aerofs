//
//  AsyncSocket.m
//
//  This class is in the public domain.
//  Originally created by Dustin Voss on Wed Jan 29 2003.
//  Updated and maintained by Deusty Designs and the Mac development community.
//
//  http://code.google.com/p/cocoaasyncsocket/
//

#if ! __has_feature(objc_arc)
#warning This file must be compiled with ARC. Use -fobjc-arc flag (or convert project to ARC).
#endif

#import "AsyncSocket.h"
#import <sys/socket.h>
#import <netinet/in.h>
#import <netdb.h>

#if TARGET_OS_IPHONE
// Note: You may need to add the CFNetwork Framework to your project
#import <CFNetwork/CFNetwork.h>
#endif

#pragma mark Declarations

#define DEFAULT_PREBUFFERING YES        // Whether pre-buffering is enabled by default

#define READQUEUE_CAPACITY  5           // Initial capacity
#define WRITEQUEUE_CAPACITY 5           // Initial capacity
#define READALL_CHUNKSIZE   256         // Incremental increase in buffer size
#define WRITE_CHUNKSIZE    (1024 * 4)   // Limit on size of each write pass

// AsyncSocket is RunLoop based, and is thus not thread-safe.
// You must always access your AsyncSocket instance from the thread/runloop in which the instance is running.
// You can use methods such as performSelectorOnThread to accomplish this.
// Failure to comply with these thread-safety rules may result in errors.
// You can enable this option to help diagnose where you are incorrectly accessing your socket.
#if DEBUG
  #define DEBUG_THREAD_SAFETY 1
#else
  #define DEBUG_THREAD_SAFETY 0
#endif
//
// If you constantly need to access your socket from multiple threads
// then you may consider using GCDAsyncSocket instead, which is thread-safe.

NSString *const AsyncSocketException = @"AsyncSocketException";
NSString *const AsyncSocketErrorDomain = @"AsyncSocketErrorDomain";


enum AsyncSocketFlags
{
    kEnablePreBuffering      = 1 <<  0,  // If set, pre-buffering is enabled
    kDidStartDelegate        = 1 <<  1,  // If set, disconnection results in delegate call
    kDidCompleteOpenForRead  = 1 <<  2,  // If set, open callback has been called for read stream
    kDidCompleteOpenForWrite = 1 <<  3,  // If set, open callback has been called for write stream
    kStartingReadTLS         = 1 <<  4,  // If set, we're waiting for TLS negotiation to complete
    kStartingWriteTLS        = 1 <<  5,  // If set, we're waiting for TLS negotiation to complete
    kForbidReadsWrites       = 1 <<  6,  // If set, no new reads or writes are allowed
    kDisconnectAfterReads    = 1 <<  7,  // If set, disconnect after no more reads are queued
    kDisconnectAfterWrites   = 1 <<  8,  // If set, disconnect after no more writes are queued
    kClosingWithError        = 1 <<  9,  // If set, the socket is being closed due to an error
    kDequeueReadScheduled    = 1 << 10,  // If set, a maybeDequeueRead operation is already scheduled
    kDequeueWriteScheduled   = 1 << 11,  // If set, a maybeDequeueWrite operation is already scheduled
    kSocketCanAcceptBytes    = 1 << 12,  // If set, we know socket can accept bytes. If unset, it's unknown.
    kSocketHasBytesAvailable = 1 << 13,  // If set, we know socket has bytes available. If unset, it's unknown.
};

@interface AsyncSocket (Private)

// Connecting
- (void)startConnectTimeout:(NSTimeInterval)timeout;
- (void)endConnectTimeout;
- (void)doConnectTimeout:(NSTimer *)timer;

// Socket Implementation
- (BOOL)createSocketForAddress:(NSData *)remoteAddr error:(NSError **)errPtr;
- (BOOL)attachSocketsToRunLoop:(NSRunLoop *)runLoop error:(NSError **)errPtr;
- (BOOL)configureSocketAndReturnError:(NSError **)errPtr;
- (BOOL)connectSocketToAddress:(NSData *)remoteAddr error:(NSError **)errPtr;
- (void)doSocketOpen:(CFSocketRef)sock withCFSocketError:(CFSocketError)err;

// Stream Implementation
- (BOOL)createStreamsFromNative:(CFSocketNativeHandle)native error:(NSError **)errPtr;
- (BOOL)attachStreamsToRunLoop:(NSRunLoop *)runLoop error:(NSError **)errPtr;
- (BOOL)openStreamsAndReturnError:(NSError **)errPtr;
- (void)doStreamOpen;
- (BOOL)setSocketFromStreamsAndReturnError:(NSError **)errPtr;

// Disconnect Implementation
- (void)closeWithError:(NSError *)err;
- (void)recoverUnreadData;
- (void)emptyQueues;
- (void)close;

// Errors
- (NSError *)getErrnoError;
- (NSError *)getAbortError;
- (NSError *)getStreamError;
- (NSError *)getSocketError;
- (NSError *)getConnectTimeoutError;
- (NSError *)getReadMaxedOutError;
- (NSError *)getReadTimeoutError;
- (NSError *)getWriteTimeoutError;
- (NSError *)errorFromCFStreamError:(CFStreamError)err;

// Diagnostics
- (BOOL)isDisconnected;
- (BOOL)areStreamsConnected;

// Reading
- (void)doBytesAvailable;
- (void)completeCurrentRead;
- (void)endCurrentRead;
- (void)scheduleDequeueRead;
- (void)maybeDequeueRead;
- (void)doReadTimeout:(NSTimer *)timer;

// Writing
- (void)doSendBytes;
- (void)completeCurrentWrite;
- (void)endCurrentWrite;
- (void)scheduleDequeueWrite;
- (void)maybeDequeueWrite;
- (void)doWriteTimeout:(NSTimer *)timer;

// Run Loop
- (void)runLoopAddSource:(CFRunLoopSourceRef)source;
- (void)runLoopRemoveSource:(CFRunLoopSourceRef)source;
- (void)runLoopAddTimer:(NSTimer *)timer;
- (void)runLoopUnscheduleReadStream;
- (void)runLoopUnscheduleWriteStream;

// Security
- (void)maybeStartTLS;
- (void)onTLSHandshakeSuccessful;

// Callbacks
- (void)doCFCallback:(CFSocketCallBackType)type
           forSocket:(CFSocketRef)sock withAddress:(NSData *)address withData:(const void *)pData;
- (void)doCFReadStreamCallback:(CFStreamEventType)type forStream:(CFReadStreamRef)stream;
- (void)doCFWriteStreamCallback:(CFStreamEventType)type forStream:(CFWriteStreamRef)stream;

@end

static void MyCFSocketCallback(CFSocketRef, CFSocketCallBackType, CFDataRef, const void *, void *);
static void MyCFReadStreamCallback(CFReadStreamRef stream, CFStreamEventType type, void *pInfo);
static void MyCFWriteStreamCallback(CFWriteStreamRef stream, CFStreamEventType type, void *pInfo);

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * The AsyncReadPacket encompasses the instructions for any given read.
 * The content of a read packet allows the code to determine if we're:
 *  - reading to a certain length
 *  - reading to a certain separator
 *  - or simply reading the first chunk of available data
**/
@interface AsyncReadPacket : NSObject
{
  @public
    NSMutableData *buffer;
    NSUInteger startOffset;
    NSUInteger bytesDone;
    NSUInteger maxLength;
    NSTimeInterval timeout;
    NSUInteger readLength;
    NSData *term;
    BOOL bufferOwner;
    NSUInteger originalBufferLength;
    long tag;
}
- (id)initWithData:(NSMutableData *)d
       startOffset:(NSUInteger)s
         maxLength:(NSUInteger)m
           timeout:(NSTimeInterval)t
        readLength:(NSUInteger)l
        terminator:(NSData *)e
               tag:(long)i;

- (NSUInteger)readLengthForNonTerm;
- (NSUInteger)readLengthForTerm;
- (NSUInteger)readLengthForTermWithPreBuffer:(NSData *)preBuffer found:(BOOL *)foundPtr;

- (NSUInteger)prebufferReadLengthForTerm;
- (NSInteger)searchForTermAfterPreBuffering:(NSUInteger)numBytes;
@end

@implementation AsyncReadPacket

- (id)initWithData:(NSMutableData *)d
       startOffset:(NSUInteger)s
         maxLength:(NSUInteger)m
           timeout:(NSTimeInterval)t
        readLength:(NSUInteger)l
        terminator:(NSData *)e
               tag:(long)i
{
    if((self = [super init]))
    {
        if (d)
        {
            buffer = d;
            startOffset = s;
            bufferOwner = NO;
            originalBufferLength = [d length];
        }
        else
        {
            if (readLength > 0)
                buffer = [[NSMutableData alloc] initWithLength:readLength];
            else
                buffer = [[NSMutableData alloc] initWithLength:0];

            startOffset = 0;
            bufferOwner = YES;
            originalBufferLength = 0;
        }

        bytesDone = 0;
        maxLength = m;
        timeout = t;
        readLength = l;
        term = [e copy];
        tag = i;
    }
    return self;
}

/**
 * For read packets without a set terminator, returns the safe length of data that can be read
 * without exceeding the maxLength, or forcing a resize of the buffer if at all possible.
**/
- (NSUInteger)readLengthForNonTerm
{
    NSAssert(term == nil, @"This method does not apply to term reads");

    if (readLength > 0)
    {
        // Read a specific length of data

        return readLength - bytesDone;

        // No need to avoid resizing the buffer.
        // It should be resized if the buffer space is less than the requested read length.
    }
    else
    {
        // Read all available data

        NSUInteger result = READALL_CHUNKSIZE;

        if (maxLength > 0)
        {
            result = MIN(result, (maxLength - bytesDone));
        }

        if (!bufferOwner)
        {
            // We did NOT create the buffer.
            // It is owned by the caller.
            // Avoid resizing the buffer if at all possible.

            if ([buffer length] == originalBufferLength)
            {
                NSUInteger buffSize = [buffer length];
                NSUInteger buffSpace = buffSize - startOffset - bytesDone;

                if (buffSpace > 0)
                {
                    result = MIN(result, buffSpace);
                }
            }
        }

        return result;
    }
}

/**
 * For read packets with a set terminator, returns the safe length of data that can be read
 * without going over a terminator, or the maxLength, or forcing a resize of the buffer if at all possible.
 *
 * It is assumed the terminator has not already been read.
**/
- (NSUInteger)readLengthForTerm
{
    NSAssert(term != nil, @"This method does not apply to non-term reads");

    // What we're going to do is look for a partial sequence of the terminator at the end of the buffer.
    // If a partial sequence occurs, then we must assume the next bytes to arrive will be the rest of the term,
    // and we can only read that amount.
    // Otherwise, we're safe to read the entire length of the term.

    NSUInteger termLength = [term length];

    // Shortcuts
    if (bytesDone == 0) return termLength;
    if (termLength == 1) return termLength;

    // i = index within buffer at which to check data
    // j = length of term to check against

    NSUInteger i, j;
    if (bytesDone >= termLength)
    {
        i = bytesDone - termLength + 1;
        j = termLength - 1;
    }
    else
    {
        i = 0;
        j = bytesDone;
    }

    NSUInteger result = termLength;

    void *buf = [buffer mutableBytes];
    const void *termBuf = [term bytes];

    while (i < bytesDone)
    {
        void *subbuf = buf + startOffset + i;

        if (memcmp(subbuf, termBuf, j) == 0)
        {
            result = termLength - j;
            break;
        }

        i++;
        j--;
    }

    if (maxLength > 0)
    {
        result = MIN(result, (maxLength - bytesDone));
    }

    if (!bufferOwner)
    {
        // We did NOT create the buffer.
        // It is owned by the caller.
        // Avoid resizing the buffer if at all possible.

        if ([buffer length] == originalBufferLength)
        {
            NSUInteger buffSize = [buffer length];
            NSUInteger buffSpace = buffSize - startOffset - bytesDone;

            if (buffSpace > 0)
            {
                result = MIN(result, buffSpace);
            }
        }
    }

    return result;
}

/**
 * For read packets with a set terminator,
 * returns the safe length of data that can be read from the given preBuffer,
 * without going over a terminator or the maxLength.
 *
 * It is assumed the terminator has not already been read.
**/
- (NSUInteger)readLengthForTermWithPreBuffer:(NSData *)preBuffer found:(BOOL *)foundPtr
{
    NSAssert(term != nil, @"This method does not apply to non-term reads");
    NSAssert([preBuffer length] > 0, @"Invoked with empty pre buffer!");

    // We know that the terminator, as a whole, doesn't exist in our own buffer.
    // But it is possible that a portion of it exists in our buffer.
    // So we're going to look for the terminator starting with a portion of our own buffer.
    //
    // Example:
    //
    // term length      = 3 bytes
    // bytesDone        = 5 bytes
    // preBuffer length = 5 bytes
    //
    // If we append the preBuffer to our buffer,
    // it would look like this:
    //
    // ---------------------
    // |B|B|B|B|B|P|P|P|P|P|
    // ---------------------
    //
    // So we start our search here:
    //
    // ---------------------
    // |B|B|B|B|B|P|P|P|P|P|
    // -------^-^-^---------
    //
    // And move forwards...
    //
    // ---------------------
    // |B|B|B|B|B|P|P|P|P|P|
    // ---------^-^-^-------
    //
    // Until we find the terminator or reach the end.
    //
    // ---------------------
    // |B|B|B|B|B|P|P|P|P|P|
    // ---------------^-^-^-

    BOOL found = NO;

    NSUInteger termLength = [term length];
    NSUInteger preBufferLength = [preBuffer length];

    if ((bytesDone + preBufferLength) < termLength)
    {
        // Not enough data for a full term sequence yet
        return preBufferLength;
    }

    NSUInteger maxPreBufferLength;
    if (maxLength > 0) {
        maxPreBufferLength = MIN(preBufferLength, (maxLength - bytesDone));

        // Note: maxLength >= termLength
    }
    else {
        maxPreBufferLength = preBufferLength;
    }

    Byte seq[termLength];
    const void *termBuf = [term bytes];

    NSUInteger bufLen = MIN(bytesDone, (termLength - 1));
    void *buf = [buffer mutableBytes] + startOffset + bytesDone - bufLen;

    NSUInteger preLen = termLength - bufLen;
    void *pre = (void *)[preBuffer bytes];

    NSUInteger loopCount = bufLen + maxPreBufferLength - termLength + 1; // Plus one. See example above.

    NSUInteger result = preBufferLength;

    NSUInteger i;
    for (i = 0; i < loopCount; i++)
    {
        if (bufLen > 0)
        {
            // Combining bytes from buffer and preBuffer

            memcpy(seq, buf, bufLen);
            memcpy(seq + bufLen, pre, preLen);

            if (memcmp(seq, termBuf, termLength) == 0)
            {
                result = preLen;
                found = YES;
                break;
            }

            buf++;
            bufLen--;
            preLen++;
        }
        else
        {
            // Comparing directly from preBuffer

            if (memcmp(pre, termBuf, termLength) == 0)
            {
                NSUInteger preOffset = pre - [preBuffer bytes]; // pointer arithmetic

                result = preOffset + termLength;
                found = YES;
                break;
            }

            pre++;
        }
    }

    // There is no need to avoid resizing the buffer in this particular situation.

    if (foundPtr) *foundPtr = found;
    return result;
}

/**
 * Assuming pre-buffering is enabled, returns the amount of data that can be read
 * without going over the maxLength.
**/
- (NSUInteger)prebufferReadLengthForTerm
{
    NSAssert(term != nil, @"This method does not apply to non-term reads");

    NSUInteger result = READALL_CHUNKSIZE;

    if (maxLength > 0)
    {
        result = MIN(result, (maxLength - bytesDone));
    }

    if (!bufferOwner)
    {
        // We did NOT create the buffer.
        // It is owned by the caller.
        // Avoid resizing the buffer if at all possible.

        if ([buffer length] == originalBufferLength)
        {
            NSUInteger buffSize = [buffer length];
            NSUInteger buffSpace = buffSize - startOffset - bytesDone;

            if (buffSpace > 0)
            {
                result = MIN(result, buffSpace);
            }
        }
    }

    return result;
}

/**
 * For read packets with a set terminator, scans the packet buffer for the term.
 * It is assumed the terminator had not been fully read prior to the new bytes.
 *
 * If the term is found, the number of excess bytes after the term are returned.
 * If the term is not found, this method will return -1.
 *
 * Note: A return value of zero means the term was found at the very end.
**/
- (NSInteger)searchForTermAfterPreBuffering:(NSUInteger)numBytes
{
    NSAssert(term != nil, @"This method does not apply to non-term reads");
    NSAssert(bytesDone >= numBytes, @"Invoked with invalid numBytes!");

    // We try to start the search such that the first new byte read matches up with the last byte of the term.
    // We continue searching forward after this until the term no longer fits into the buffer.

    NSUInteger termLength = [term length];
    const void *termBuffer = [term bytes];

    // Remember: This method is called after the bytesDone variable has been updated.

    NSUInteger prevBytesDone = bytesDone - numBytes;

    NSUInteger i;
    if (prevBytesDone >= termLength)
        i = prevBytesDone - termLength + 1;
    else
        i = 0;

    while ((i + termLength) <= bytesDone)
    {
        void *subBuffer = [buffer mutableBytes] + startOffset + i;

        if(memcmp(subBuffer, termBuffer, termLength) == 0)
        {
            return bytesDone - (i + termLength);
        }

        i++;
    }

    return -1;
}


@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * The AsyncWritePacket encompasses the instructions for any given write.
**/
@interface AsyncWritePacket : NSObject
{
  @public
    NSData *buffer;
    NSUInteger bytesDone;
    long tag;
    NSTimeInterval timeout;
}
- (id)initWithData:(NSData *)d timeout:(NSTimeInterval)t tag:(long)i;
@end

@implementation AsyncWritePacket

- (id)initWithData:(NSData *)d timeout:(NSTimeInterval)t tag:(long)i
{
    if((self = [super init]))
    {
        buffer = d;
        timeout = t;
        tag = i;
        bytesDone = 0;
    }
    return self;
}


@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * The AsyncSpecialPacket encompasses special instructions for interruptions in the read/write queues.
 * This class my be altered to support more than just TLS in the future.
**/
@interface AsyncSpecialPacket : NSObject
{
  @public
    NSDictionary *tlsSettings;
}
- (id)initWithTLSSettings:(NSDictionary *)settings;
@end

@implementation AsyncSpecialPacket

- (id)initWithTLSSettings:(NSDictionary *)settings
{
    if((self = [super init]))
    {
        tlsSettings = [settings copy];
    }
    return self;
}


@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@implementation AsyncSocket

- (id)init
{
    return [self initWithDelegate:nil userData:0];
}

- (id)initWithDelegate:(id)delegate
{
    return [self initWithDelegate:delegate userData:0];
}

// Designated initializer.
- (id)initWithDelegate:(id)delegate userData:(long)userData
{
    if((self = [super init]))
    {
        theFlags = DEFAULT_PREBUFFERING ? kEnablePreBuffering : 0;
        theDelegate = delegate;
        theUserData = userData;

        theNativeSocketUds = 0;

        theSocketUds = NULL;
        theSourceUds = NULL;

        theRunLoop = NULL;
        theReadStream = NULL;
        theWriteStream = NULL;

        theConnectTimer = nil;

        theReadQueue = [[NSMutableArray alloc] initWithCapacity:READQUEUE_CAPACITY];
        theCurrentRead = nil;
        theReadTimer = nil;

        partialReadBuffer = [[NSMutableData alloc] initWithCapacity:READALL_CHUNKSIZE];

        theWriteQueue = [[NSMutableArray alloc] initWithCapacity:WRITEQUEUE_CAPACITY];
        theCurrentWrite = nil;
        theWriteTimer = nil;

        // Socket context
        NSAssert(sizeof(CFSocketContext) == sizeof(CFStreamClientContext), @"CFSocketContext != CFStreamClientContext");
        theContext.version = 0;
        theContext.info = (__bridge void *)(self);
        theContext.retain = nil;
        theContext.release = nil;
        theContext.copyDescription = nil;

        // Default run loop modes
        theRunLoopModes = [NSArray arrayWithObject:NSDefaultRunLoopMode];
    }
    return self;
}

// The socket may been initialized in a connected state and auto-released, so this should close it down cleanly.
- (void)dealloc
{
    [self close];
    [NSObject cancelPreviousPerformRequestsWithTarget:self];
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Thread-Safety
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)checkForThreadSafety
{
    if (theRunLoop && (theRunLoop != CFRunLoopGetCurrent()))
    {
        // AsyncSocket is RunLoop based.
        // It is designed to be run and accessed from a particular thread/runloop.
        // As such, it is faster as it does not have the overhead of locks/synchronization.
        //
        // However, this places a minimal requirement on the developer to maintain thread-safety.
        // If you are seeing errors or crashes in AsyncSocket,
        // it is very likely that thread-safety has been broken.
        // This method may be enabled via the DEBUG_THREAD_SAFETY macro,
        // and will allow you to discover the place in your code where thread-safety is being broken.
        //
        // Note:
        //
        // If you find you constantly need to access your socket from various threads,
        // you may prefer to use GCDAsyncSocket which is thread-safe.

        [NSException raise:AsyncSocketException
                    format:@"Attempting to access AsyncSocket instance from incorrect thread."];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Accessors
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)setDelegate:(id)delegate
{
#if DEBUG_THREAD_SAFETY
    [self checkForThreadSafety];
#endif

    theDelegate = delegate;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Run Loop
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)runLoopAddSource:(CFRunLoopSourceRef)source
{
    for (NSString *runLoopMode in theRunLoopModes)
    {
        CFRunLoopAddSource(theRunLoop, source, (__bridge CFStringRef)runLoopMode);
    }
}

- (void)runLoopRemoveSource:(CFRunLoopSourceRef)source
{
    for (NSString *runLoopMode in theRunLoopModes)
    {
        CFRunLoopRemoveSource(theRunLoop, source, (__bridge CFStringRef)runLoopMode);
    }
}

- (void)runLoopAddTimer:(NSTimer *)timer
{
    for (NSString *runLoopMode in theRunLoopModes)
    {
        CFRunLoopAddTimer(theRunLoop, (__bridge CFRunLoopTimerRef)timer, (__bridge CFStringRef)runLoopMode);
    }
}

- (void)runLoopUnscheduleReadStream
{
    for (NSString *runLoopMode in theRunLoopModes)
    {
        CFReadStreamUnscheduleFromRunLoop(theReadStream, theRunLoop, (__bridge CFStringRef)runLoopMode);
    }
    CFReadStreamSetClient(theReadStream, kCFStreamEventNone, NULL, NULL);
}

- (void)runLoopUnscheduleWriteStream
{
    for (NSString *runLoopMode in theRunLoopModes)
    {
        CFWriteStreamUnscheduleFromRunLoop(theWriteStream, theRunLoop, (__bridge CFStringRef)runLoopMode);
    }
    CFWriteStreamSetClient(theWriteStream, kCFStreamEventNone, NULL, NULL);
}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Connecting
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/**
 * This method creates an initial CFSocket to the given address.
 * The connection is then opened, and the corresponding CFReadStream and CFWriteStream will be
 * created from the low-level sockets after the connection succeeds.
 *
 * Thus the delegate will have access to the CFSocket and CFSocketNativeHandle (BSD socket) prior to connection,
 * specifically in the onSocketWillConnect: method.
 *
 * Note: The NSData parameter is expected to be a sockaddr structure. For example, an NSData object returned from
 * NSNetService addresses method.
 * If you have an existing struct sockaddr you can convert it to an NSData object like so:
 * struct sockaddr sa  -> NSData *dsa = [NSData dataWithBytes:&remoteAddr length:remoteAddr.sa_len];
 * struct sockaddr *sa -> NSData *dsa = [NSData dataWithBytes:remoteAddr length:remoteAddr->sa_len];
**/
- (BOOL)connectToAddress:(NSData *)remoteAddr withTimeout:(NSTimeInterval)timeout error:(NSError **)errPtr
{
    return [self connectToAddress:remoteAddr viaInterfaceAddress:nil withTimeout:timeout error:errPtr];
}

/**
 * This method is similar to the one above, but allows you to specify which socket interface
 * the connection should run over. E.g. ethernet, wifi, bluetooth, etc.
**/
- (BOOL)connectToAddress:(NSData *)remoteAddr
     viaInterfaceAddress:(NSData *)interfaceAddr
             withTimeout:(NSTimeInterval)timeout
                   error:(NSError **)errPtr
{
    if (theDelegate == NULL)
    {
        [NSException raise:AsyncSocketException
                    format:@"Attempting to connect without a delegate. Set a delegate first."];
    }

    if (![self isDisconnected])
    {
        [NSException raise:AsyncSocketException
                    format:@"Attempting to connect while connected or accepting connections. Disconnect first."];
    }

    // Clear queues (spurious read/write requests post disconnect)
    [self emptyQueues];
    if(![self createSocketForAddress:remoteAddr error:errPtr])   goto Failed;
    if(![self attachSocketsToRunLoop:nil error:errPtr])          goto Failed;
    if(![self configureSocketAndReturnError:errPtr])             goto Failed;
    if(![self connectSocketToAddress:remoteAddr error:errPtr])   goto Failed;
    [self startConnectTimeout:timeout];
    theFlags |= kDidStartDelegate;
    return YES;

Failed:
    [self close];
    return NO;
}

- (void)startConnectTimeout:(NSTimeInterval)timeout
{
    if(timeout >= 0.0)
    {
        theConnectTimer = [NSTimer timerWithTimeInterval:timeout
                                                  target:self
                                                selector:@selector(doConnectTimeout:)
                                                userInfo:nil
                                                 repeats:NO];
        [self runLoopAddTimer:theConnectTimer];
    }
}

- (void)endConnectTimeout
{
    [theConnectTimer invalidate];
    theConnectTimer = nil;
}

- (void)doConnectTimeout:(NSTimer *)timer
{
    #pragma unused(timer)

    [self endConnectTimeout];
    [self closeWithError:[self getConnectTimeoutError]];
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Socket Implementation
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (BOOL)createSocketForAddress:(NSData *)remoteAddr error:(NSError **)errPtr
{
    struct sockaddr *pSockAddr = (struct sockaddr *)[remoteAddr bytes];

    if (pSockAddr->sa_family == AF_UNIX)
    {
        theNativeSocketUds = socket(pSockAddr->sa_family, SOCK_STREAM, 0 );
        CFSocketContext context = { 0, (__bridge void *)self, nil, nil, nil };

        theSocketUds = CFSocketCreateWithNative(nil,
                                                theNativeSocketUds,
                                                kCFSocketConnectCallBack,
                                                (CFSocketCallBack)&MyCFSocketCallback,
                                                &context);
        if(theSocketUds == NULL)
        {
            if (errPtr) *errPtr = [self getSocketError];
            return NO;
        }
    }
    else
    {
        if (errPtr)
        {
            NSString *errMsg = @"Remote address is not Unix Domain Socket";
            NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

            *errPtr = [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketCFSocketError userInfo:info];
        }
        return NO;
    }
    return YES;
}

/**
 * Adds the CFSocket's to the run-loop so that callbacks will work properly.
**/
- (BOOL)attachSocketsToRunLoop:(NSRunLoop *)runLoop error:(NSError **)errPtr
{
    #pragma unused(errPtr)

    // Get the CFRunLoop to which the socket should be attached.
    theRunLoop = (runLoop == nil) ? CFRunLoopGetCurrent() : [runLoop getCFRunLoop];

    if (theSocketUds)
    {
        theSourceUds = CFSocketCreateRunLoopSource (kCFAllocatorDefault, theSocketUds, 0);
        [self runLoopAddSource:theSourceUds];
    }

    return YES;
}

/**
 * Allows the delegate method to configure the CFSocket or CFNativeSocket as desired before we connect.
 * Note that the CFReadStream and CFWriteStream will not be available until after the connection is opened.
**/
- (BOOL)configureSocketAndReturnError:(NSError **)errPtr
{
    // Call the delegate method for further configuration.
    if([theDelegate respondsToSelector:@selector(onSocketWillConnect:)])
    {
        if([theDelegate onSocketWillConnect:self] == NO)
        {
            if (errPtr) *errPtr = [self getAbortError];
            return NO;
        }
    }
    return YES;
}

- (BOOL)connectSocketToAddress:(NSData *)remoteAddr error:(NSError **)errPtr
{
    // Start connecting to the given address in the background
    // The MyCFSocketCallback method will be called when the connection succeeds or fails
    if(theSocketUds)
    {
        CFSocketError err = CFSocketConnectToAddress(theSocketUds, (__bridge CFDataRef)remoteAddr, -1);
        if(err != kCFSocketSuccess)
        {
            if (errPtr) *errPtr = [self getSocketError];
            return NO;
        }

    }

    return YES;
}

/**
 * This method is called as a result of connectToAddress:withTimeout:error:.
 * At this point we have an open CFSocket from which we need to create our read and write stream.
**/
- (void)doSocketOpen:(CFSocketRef)sock withCFSocketError:(CFSocketError)socketError
{
    NSParameterAssert ((sock == theSocketUds));

    if(socketError == kCFSocketTimeout || socketError == kCFSocketError)
    {
        [self closeWithError:[self getSocketError]];
        return;
    }

    // Get the underlying native (BSD) socket
    CFSocketNativeHandle nativeSocket = CFSocketGetNative(sock);

    // Store a reference to it
    theNativeSocketUds = nativeSocket;

    // Setup the CFSocket so that invalidating it will not close the underlying native socket
    CFSocketSetSocketFlags(sock, 0);

    // Invalidate and release the CFSocket - All we need from here on out is the nativeSocket.
    // Note: If we don't invalidate the CFSocket (leaving the native socket open)
    // then theReadStream and theWriteStream won't function properly.
    // Specifically, their callbacks won't work, with the exception of kCFStreamEventOpenCompleted.
    //
    // This is likely due to the mixture of the CFSocketCreateWithNative method,
    // along with the CFStreamCreatePairWithSocket method.
    // The documentation for CFSocketCreateWithNative states:
    //
    //   If a CFSocket object already exists for sock,
    //   the function returns the pre-existing object instead of creating a new object;
    //   the context, callout, and callBackTypes parameters are ignored in this case.
    //
    // So the CFStreamCreateWithNative method invokes the CFSocketCreateWithNative method,
    // thinking that is creating a new underlying CFSocket for it's own purposes.
    // When it does this, it uses the context/callout/callbackTypes parameters to setup everything appropriately.
    // However, if a CFSocket already exists for the native socket,
    // then it is returned (as per the documentation), which in turn screws up the CFStreams.

    CFSocketInvalidate(sock);
    CFRelease(sock);
    theSocketUds = NULL;

    NSError *err;
    BOOL pass = YES;

    if(pass && ![self createStreamsFromNative:nativeSocket error:&err]) pass = NO;
    if(pass && ![self attachStreamsToRunLoop:nil error:&err])           pass = NO;
    if(pass && ![self openStreamsAndReturnError:&err])                  pass = NO;

    if(!pass)
    {
        [self closeWithError:err];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Stream Implementation
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Creates the CFReadStream and CFWriteStream from the given native socket.
 * The CFSocket may be extracted from either stream after the streams have been opened.
 *
 * Note: The given native socket must already be connected!
**/
- (BOOL)createStreamsFromNative:(CFSocketNativeHandle)native error:(NSError **)errPtr
{
    // Create the socket & streams.
    CFStreamCreatePairWithSocket(kCFAllocatorDefault, native, &theReadStream, &theWriteStream);
    if (theReadStream == NULL || theWriteStream == NULL)
    {
        NSError *err = [self getStreamError];

        NSLog(@"AsyncSocket %p couldn't create streams from accepted socket: %@", self, err);

        if (errPtr) *errPtr = err;
        return NO;
    }

    // Ensure the CF & BSD socket is closed when the streams are closed.
    CFReadStreamSetProperty(theReadStream, kCFStreamPropertyShouldCloseNativeSocket, kCFBooleanTrue);
    CFWriteStreamSetProperty(theWriteStream, kCFStreamPropertyShouldCloseNativeSocket, kCFBooleanTrue);

    return YES;
}

- (BOOL)attachStreamsToRunLoop:(NSRunLoop *)runLoop error:(NSError **)errPtr
{
    // Get the CFRunLoop to which the socket should be attached.
    theRunLoop = (runLoop == nil) ? CFRunLoopGetCurrent() : [runLoop getCFRunLoop];

    // Setup read stream callbacks

    CFOptionFlags readStreamEvents = kCFStreamEventHasBytesAvailable |
                                     kCFStreamEventErrorOccurred     |
                                     kCFStreamEventEndEncountered    |
                                     kCFStreamEventOpenCompleted;

    if (!CFReadStreamSetClient(theReadStream,
                               readStreamEvents,
                               (CFReadStreamClientCallBack)&MyCFReadStreamCallback,
                               (CFStreamClientContext *)(&theContext)))
    {
        NSError *err = [self getStreamError];

        NSLog (@"AsyncSocket %p couldn't attach read stream to run-loop,", self);
        NSLog (@"Error: %@", err);

        if (errPtr) *errPtr = err;
        return NO;
    }

    // Setup write stream callbacks

    CFOptionFlags writeStreamEvents = kCFStreamEventCanAcceptBytes |
                                      kCFStreamEventErrorOccurred  |
                                      kCFStreamEventEndEncountered |
                                      kCFStreamEventOpenCompleted;

    if (!CFWriteStreamSetClient (theWriteStream,
                                 writeStreamEvents,
                                 (CFWriteStreamClientCallBack)&MyCFWriteStreamCallback,
                                 (CFStreamClientContext *)(&theContext)))
    {
        NSError *err = [self getStreamError];

        NSLog (@"AsyncSocket %p couldn't attach write stream to run-loop,", self);
        NSLog (@"Error: %@", err);

        if (errPtr) *errPtr = err;
        return NO;
    }

    // Add read and write streams to run loop

    for (NSString *runLoopMode in theRunLoopModes)
    {
        CFReadStreamScheduleWithRunLoop(theReadStream, theRunLoop, (__bridge CFStringRef)runLoopMode);
        CFWriteStreamScheduleWithRunLoop(theWriteStream, theRunLoop, (__bridge CFStringRef)runLoopMode);
    }

    return YES;
}

- (BOOL)openStreamsAndReturnError:(NSError **)errPtr
{
    BOOL pass = YES;

    if(pass && !CFReadStreamOpen(theReadStream))
    {
        NSLog (@"AsyncSocket %p couldn't open read stream,", self);
        pass = NO;
    }

    if(pass && !CFWriteStreamOpen(theWriteStream))
    {
        NSLog (@"AsyncSocket %p couldn't open write stream,", self);
        pass = NO;
    }

    if(!pass)
    {
        if (errPtr) *errPtr = [self getStreamError];
    }

    return pass;
}

/**
 * Called when read or write streams open.
 * When the socket is connected and both streams are open, consider the AsyncSocket instance to be ready.
**/
- (void)doStreamOpen
{
    if ((theFlags & kDidCompleteOpenForRead) && (theFlags & kDidCompleteOpenForWrite))
    {
        NSError *err = nil;

        // Get the socket
        if (![self setSocketFromStreamsAndReturnError: &err])
        {
            NSLog (@"AsyncSocket %p couldn't get socket from streams, %@. Disconnecting.", self, err);
            [self closeWithError:err];
            return;
        }

        // Stop the connection attempt timeout timer
        [self endConnectTimeout];

        if ([theDelegate respondsToSelector:@selector(onSocketDidConnect:)])
        {
            [theDelegate onSocketDidConnect:self];
        }

        // Immediately deal with any already-queued requests.
        [self maybeDequeueRead];
        [self maybeDequeueWrite];
    }
}

- (BOOL)setSocketFromStreamsAndReturnError:(NSError **)errPtr
{
    // Get the CFSocketNativeHandle from theReadStream
    CFSocketNativeHandle native;
    CFDataRef nativeProp = CFReadStreamCopyProperty(theReadStream, kCFStreamPropertySocketNativeHandle);
    if(nativeProp == NULL)
    {
        if (errPtr) *errPtr = [self getStreamError];
        return NO;
    }

    CFIndex nativePropLen = CFDataGetLength(nativeProp);
    CFIndex nativeLen = (CFIndex)sizeof(native);

    CFIndex len = MIN(nativePropLen, nativeLen);

    CFDataGetBytes(nativeProp, CFRangeMake(0, len), (UInt8 *)&native);
    CFRelease(nativeProp);

    CFSocketRef theSocket = CFSocketCreateWithNative(kCFAllocatorDefault, native, 0, NULL, NULL);
    if(theSocket == NULL)
    {
        if (errPtr) *errPtr = [self getSocketError];
        return NO;
    }

    // Determine whether the connection was Unix Domain Socket.
    // We may already know if this was an accepted socket,
    // or if the connectToAddress method was used.
    // In either of the above two cases, the native socket variable would already be set.
    if (theNativeSocketUds > 0)
    {
        theSocketUds = theSocket;
        return YES;
    }

    CFDataRef peeraddr = CFSocketCopyPeerAddress(theSocket);
    if(peeraddr == NULL)
    {
        NSLog(@"AsyncSocket couldn't determine IP version of socket");

        CFRelease(theSocket);

        if (errPtr) *errPtr = [self getSocketError];
        return NO;
    }
    theSocketUds = theSocket;
    theNativeSocketUds = native;

    CFRelease(peeraddr);

    return YES;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Disconnect Implementation
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Sends error message and disconnects
- (void)closeWithError:(NSError *)err
{
    theFlags |= kClosingWithError;

    if (theFlags & kDidStartDelegate)
    {
        // Try to salvage what data we can.
        [self recoverUnreadData];

        // Let the delegate know, so it can try to recover if it likes.
        if ([theDelegate respondsToSelector:@selector(onSocket:willDisconnectWithError:)])
        {
            [theDelegate onSocket:self willDisconnectWithError:err];
        }
    }
    [self close];
}

// Prepare partially read data for recovery.
- (void)recoverUnreadData
{
    if(theCurrentRead != nil)
    {
        // We never finished the current read.
        // Check to see if it's a normal read packet (not AsyncSpecialPacket) and if it had read anything yet.

        if(([theCurrentRead isKindOfClass:[AsyncReadPacket class]]) && (theCurrentRead->bytesDone > 0))
        {
            // We need to move its data into the front of the partial read buffer.

            void *buffer = [theCurrentRead->buffer mutableBytes] + theCurrentRead->startOffset;

            [partialReadBuffer replaceBytesInRange:NSMakeRange(0, 0)
                                         withBytes:buffer
                                            length:theCurrentRead->bytesDone];
        }
    }

    [self emptyQueues];
}

- (void)emptyQueues
{
    if (theCurrentRead != nil)  [self endCurrentRead];
    if (theCurrentWrite != nil) [self endCurrentWrite];

    [theReadQueue removeAllObjects];
    [theWriteQueue removeAllObjects];

    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(maybeDequeueRead) object:nil];
    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(maybeDequeueWrite) object:nil];

    theFlags &= ~kDequeueReadScheduled;
    theFlags &= ~kDequeueWriteScheduled;
}

/**
 * Disconnects. This is called for both error and clean disconnections.
**/
- (void)close
{
    // Empty queues
    [self emptyQueues];

    // Clear partialReadBuffer (pre-buffer and also unreadData buffer in case of error)
    [partialReadBuffer replaceBytesInRange:NSMakeRange(0, [partialReadBuffer length]) withBytes:NULL length:0];

    [NSObject cancelPreviousPerformRequestsWithTarget:self selector:@selector(disconnect) object:nil];

    // Stop the connection attempt timeout timer
    if (theConnectTimer != nil)
    {
        [self endConnectTimeout];
    }

    // Close streams.
    if (theReadStream != NULL)
    {
        [self runLoopUnscheduleReadStream];
        CFReadStreamClose(theReadStream);
        CFRelease(theReadStream);
        theReadStream = NULL;
    }
    if (theWriteStream != NULL)
    {
        [self runLoopUnscheduleWriteStream];
        CFWriteStreamClose(theWriteStream);
        CFRelease(theWriteStream);
        theWriteStream = NULL;
    }

    // Close sockets.
    if (theSocketUds != NULL)
    {
        CFSocketInvalidate (theSocketUds);
        CFRelease (theSocketUds);
        theSocketUds = NULL;
    }

    // Closing the streams or sockets resulted in closing the underlying native socket
    theNativeSocketUds = 0;

    // Remove run loop sources
    if (theSourceUds != NULL)
    {
        [self runLoopRemoveSource:theSourceUds];
        CFRelease (theSourceUds);
        theSourceUds = NULL;
    }

    theRunLoop = NULL;

    // If the client has passed the connect/accept method, then the connection has at least begun.
    // Notify delegate that it is now ending.
    BOOL shouldCallDelegate = (theFlags & kDidStartDelegate);

    // Clear all flags (except the pre-buffering flag, which should remain as is)
    theFlags &= kEnablePreBuffering;

    if (shouldCallDelegate)
    {
        if ([theDelegate respondsToSelector: @selector(onSocketDidDisconnect:)])
        {
            [theDelegate onSocketDidDisconnect:self];
        }
    }

    // Do not access any instance variables after calling onSocketDidDisconnect.
    // This gives the delegate freedom to release us without returning here and crashing.
}

/**
 * Disconnects immediately. Any pending reads or writes are dropped.
**/
- (void)disconnect
{
#if DEBUG_THREAD_SAFETY
    [self checkForThreadSafety];
#endif

    [self close];
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Errors
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Returns a standard error object for the current errno value.
 * Errno is used for low-level BSD socket errors.
**/
- (NSError *)getErrnoError
{
    NSString *errorMsg = [NSString stringWithUTF8String:strerror(errno)];
    NSDictionary *userInfo = [NSDictionary dictionaryWithObject:errorMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:NSPOSIXErrorDomain code:errno userInfo:userInfo];
}

/**
 * Returns a standard error message for a CFSocket error.
 * Unfortunately, CFSocket offers no feedback on its errors.
**/
- (NSError *)getSocketError
{
    NSString *errMsg = NSLocalizedStringWithDefaultValue(@"AsyncSocketCFSocketError",
                                                         @"AsyncSocket", [NSBundle mainBundle],
                                                         @"General CFSocket error", nil);

    NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketCFSocketError userInfo:info];
}

- (NSError *)getStreamError
{
    CFStreamError err;
    if (theReadStream != NULL)
    {
        err = CFReadStreamGetError (theReadStream);
        if (err.error != 0) return [self errorFromCFStreamError: err];
    }

    if (theWriteStream != NULL)
    {
        err = CFWriteStreamGetError (theWriteStream);
        if (err.error != 0) return [self errorFromCFStreamError: err];
    }

    return nil;
}

/**
 * Returns a standard AsyncSocket abort error.
**/
- (NSError *)getAbortError
{
    NSString *errMsg = NSLocalizedStringWithDefaultValue(@"AsyncSocketCanceledError",
                                                         @"AsyncSocket", [NSBundle mainBundle],
                                                         @"Connection canceled", nil);

    NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketCanceledError userInfo:info];
}

/**
 * Returns a standard AsyncSocket connect timeout error.
**/
- (NSError *)getConnectTimeoutError
{
    NSString *errMsg = NSLocalizedStringWithDefaultValue(@"AsyncSocketConnectTimeoutError",
                                                         @"AsyncSocket", [NSBundle mainBundle],
                                                         @"Attempt to connect to host timed out", nil);

    NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketConnectTimeoutError userInfo:info];
}

/**
 * Returns a standard AsyncSocket maxed out error.
**/
- (NSError *)getReadMaxedOutError
{
    NSString *errMsg = NSLocalizedStringWithDefaultValue(@"AsyncSocketReadMaxedOutError",
                                                         @"AsyncSocket", [NSBundle mainBundle],
                                                         @"Read operation reached set maximum length", nil);

    NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketReadMaxedOutError userInfo:info];
}

/**
 * Returns a standard AsyncSocket read timeout error.
**/
- (NSError *)getReadTimeoutError
{
    NSString *errMsg = NSLocalizedStringWithDefaultValue(@"AsyncSocketReadTimeoutError",
                                                         @"AsyncSocket", [NSBundle mainBundle],
                                                         @"Read operation timed out", nil);

    NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketReadTimeoutError userInfo:info];
}

/**
 * Returns a standard AsyncSocket write timeout error.
**/
- (NSError *)getWriteTimeoutError
{
    NSString *errMsg = NSLocalizedStringWithDefaultValue(@"AsyncSocketWriteTimeoutError",
                                                         @"AsyncSocket", [NSBundle mainBundle],
                                                         @"Write operation timed out", nil);

    NSDictionary *info = [NSDictionary dictionaryWithObject:errMsg forKey:NSLocalizedDescriptionKey];

    return [NSError errorWithDomain:AsyncSocketErrorDomain code:AsyncSocketWriteTimeoutError userInfo:info];
}

- (NSError *)errorFromCFStreamError:(CFStreamError)err
{
    if (err.domain == 0 && err.error == 0) return nil;

    // Can't use switch; these constants aren't int literals.
    NSString *domain = @"CFStreamError (unlisted domain)";
    NSString *message = nil;

    if(err.domain == kCFStreamErrorDomainPOSIX) {
        domain = NSPOSIXErrorDomain;
    }
    else if(err.domain == kCFStreamErrorDomainMacOSStatus) {
        domain = NSOSStatusErrorDomain;
    }
    else if(err.domain == kCFStreamErrorDomainMach) {
        domain = NSMachErrorDomain;
    }
    else if(err.domain == kCFStreamErrorDomainNetDB)
    {
        domain = @"kCFStreamErrorDomainNetDB";
        message = [NSString stringWithCString:gai_strerror(err.error) encoding:NSASCIIStringEncoding];
    }
    else if(err.domain == kCFStreamErrorDomainNetServices) {
        domain = @"kCFStreamErrorDomainNetServices";
    }
    else if(err.domain == kCFStreamErrorDomainSOCKS) {
        domain = @"kCFStreamErrorDomainSOCKS";
    }
    else if(err.domain == kCFStreamErrorDomainSystemConfiguration) {
        domain = @"kCFStreamErrorDomainSystemConfiguration";
    }
    else if(err.domain == kCFStreamErrorDomainSSL) {
        domain = @"kCFStreamErrorDomainSSL";
    }

    NSDictionary *info = nil;
    if(message != nil)
    {
        info = [NSDictionary dictionaryWithObject:message forKey:NSLocalizedDescriptionKey];
    }
    return [NSError errorWithDomain:domain code:err.error userInfo:info];
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Diagnostics
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (BOOL)isDisconnected
{
#if DEBUG_THREAD_SAFETY
    [self checkForThreadSafety];
#endif

    if (theNativeSocketUds > 0) return NO;
    if (theSocketUds) return NO;

    if (theReadStream)  return NO;
    if (theWriteStream) return NO;

    return YES;
}

- (BOOL)isConnected
{
#if DEBUG_THREAD_SAFETY
    [self checkForThreadSafety];
#endif

    return [self areStreamsConnected];
}

- (BOOL)areStreamsConnected
{
    CFStreamStatus s;

    if (theReadStream != NULL)
    {
        s = CFReadStreamGetStatus(theReadStream);
        if ( !(s == kCFStreamStatusOpen || s == kCFStreamStatusReading || s == kCFStreamStatusError) )
            return NO;
    }
    else return NO;

    if (theWriteStream != NULL)
    {
        s = CFWriteStreamGetStatus(theWriteStream);
        if ( !(s == kCFStreamStatusOpen || s == kCFStreamStatusWriting || s == kCFStreamStatusError) )
            return NO;
    }
    else return NO;

    return YES;
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Reading
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)readDataToLength:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag
{
    [self readDataToLength:length withTimeout:timeout buffer:nil bufferOffset:0 tag:tag];
}

- (void)readDataToLength:(NSUInteger)length
             withTimeout:(NSTimeInterval)timeout
                  buffer:(NSMutableData *)buffer
            bufferOffset:(NSUInteger)offset
                     tag:(long)tag
{
#if DEBUG_THREAD_SAFETY
    [self checkForThreadSafety];
#endif

    if (length == 0) return;
    if (offset > [buffer length]) return;
    if (theFlags & kForbidReadsWrites) return;

    AsyncReadPacket *packet = [[AsyncReadPacket alloc] initWithData:buffer
                                                        startOffset:offset
                                                          maxLength:0
                                                            timeout:timeout
                                                         readLength:length
                                                         terminator:nil
                                                                tag:tag];
    [theReadQueue addObject:packet];
    [self scheduleDequeueRead];

}

/**
 * Puts a maybeDequeueRead on the run loop.
 * An assumption here is that selectors will be performed consecutively within their priority.
**/
- (void)scheduleDequeueRead
{
    if((theFlags & kDequeueReadScheduled) == 0)
    {
        theFlags |= kDequeueReadScheduled;
        [self performSelector:@selector(maybeDequeueRead) withObject:nil afterDelay:0 inModes:theRunLoopModes];
    }
}

/**
 * This method starts a new read, if needed.
 * It is called when a user requests a read,
 * or when a stream opens that may have requested reads sitting in the queue, etc.
**/
- (void)maybeDequeueRead
{
    // Unset the flag indicating a call to this method is scheduled
    theFlags &= ~kDequeueReadScheduled;

    // If we're not currently processing a read AND we have an available read stream
    if((theCurrentRead == nil) && (theReadStream != NULL))
    {
        if([theReadQueue count] > 0)
        {
            // Dequeue the next object in the write queue
            theCurrentRead = [theReadQueue objectAtIndex:0];
            [theReadQueue removeObjectAtIndex:0];

            if([theCurrentRead isKindOfClass:[AsyncSpecialPacket class]])
            {
                // Attempt to start TLS
                theFlags |= kStartingReadTLS;

                // This method won't do anything unless both kStartingReadTLS and kStartingWriteTLS are set
                [self maybeStartTLS];
            }
            else
            {
                // Start time-out timer
                if(theCurrentRead->timeout >= 0.0)
                {
                    theReadTimer = [NSTimer timerWithTimeInterval:theCurrentRead->timeout
                                                           target:self
                                                         selector:@selector(doReadTimeout:)
                                                         userInfo:nil
                                                          repeats:NO];
                    [self runLoopAddTimer:theReadTimer];
                }

                // Immediately read, if possible
                [self doBytesAvailable];
            }
        }
        else if(theFlags & kDisconnectAfterReads)
        {
            if(theFlags & kDisconnectAfterWrites)
            {
                if(([theWriteQueue count] == 0) && (theCurrentWrite == nil))
                {
                    [self disconnect];
                }
            }
            else
            {
                [self disconnect];
            }
        }
    }
}

/**
 * Call this method in doBytesAvailable instead of CFReadStreamHasBytesAvailable().
 * This method supports pre-buffering properly as well as the kSocketHasBytesAvailable flag.
**/
- (BOOL)hasBytesAvailable
{
    if ((theFlags & kSocketHasBytesAvailable) || ([partialReadBuffer length] > 0))
    {
        return YES;
    }
    else
    {
        return CFReadStreamHasBytesAvailable(theReadStream);
    }
}

/**
 * Call this method in doBytesAvailable instead of CFReadStreamRead().
 * This method support pre-buffering properly.
**/
- (CFIndex)readIntoBuffer:(void *)buffer maxLength:(NSUInteger)length
{
    if([partialReadBuffer length] > 0)
    {
        // Determine the maximum amount of data to read
        NSUInteger bytesToRead = MIN(length, [partialReadBuffer length]);

        // Copy the bytes from the partial read buffer
        memcpy(buffer, [partialReadBuffer bytes], (size_t)bytesToRead);

        // Remove the copied bytes from the partial read buffer
        [partialReadBuffer replaceBytesInRange:NSMakeRange(0, bytesToRead) withBytes:NULL length:0];

        return (CFIndex)bytesToRead;
    }
    else
    {
        // Unset the "has-bytes-available" flag
        theFlags &= ~kSocketHasBytesAvailable;

        return CFReadStreamRead(theReadStream, (UInt8 *)buffer, length);
    }
}

/**
 * This method is called when a new read is taken from the read queue or when new data becomes available on the stream.
**/
- (void)doBytesAvailable
{
    // If data is available on the stream, but there is no read request, then we don't need to process the data yet.
    // Also, if there is a read request but no read stream setup, we can't process any data yet.
    if((theCurrentRead == nil) || (theReadStream == NULL))
    {
        return;
    }

    // Note: This method is not called if theCurrentRead is an AsyncSpecialPacket (startTLS packet)

    NSUInteger totalBytesRead = 0;

    BOOL done = NO;
    BOOL socketError = NO;
    BOOL maxoutError = NO;

    while(!done && !socketError && !maxoutError && [self hasBytesAvailable])
    {
        BOOL didPreBuffer = NO;
        BOOL didReadFromPreBuffer = NO;

        // There are 3 types of read packets:
        //
        // 1) Read all available data.
        // 2) Read a specific length of data.
        // 3) Read up to a particular terminator.

        NSUInteger bytesToRead;

        if (theCurrentRead->term != nil)
        {
            // Read type #3 - read up to a terminator
            //
            // If pre-buffering is enabled we'll read a chunk and search for the terminator.
            // If the terminator is found, overflow data will be placed in the partialReadBuffer for the next read.
            //
            // If pre-buffering is disabled we'll be forced to read only a few bytes.
            // Just enough to ensure we don't go past our term or over our max limit.
            //
            // If we already have data pre-buffered, we can read directly from it.

            if ([partialReadBuffer length] > 0)
            {
                didReadFromPreBuffer = YES;
                bytesToRead = [theCurrentRead readLengthForTermWithPreBuffer:partialReadBuffer found:&done];
            }
            else
            {
                if (theFlags & kEnablePreBuffering)
                {
                    didPreBuffer = YES;
                    bytesToRead = [theCurrentRead prebufferReadLengthForTerm];
                }
                else
                {
                    bytesToRead = [theCurrentRead readLengthForTerm];
                }
            }
        }
        else
        {
            // Read type #1 or #2

            bytesToRead = [theCurrentRead readLengthForNonTerm];
        }

        // Make sure we have enough room in the buffer for our read

        NSUInteger buffSize = [theCurrentRead->buffer length];
        NSUInteger buffSpace = buffSize - theCurrentRead->startOffset - theCurrentRead->bytesDone;

        if (bytesToRead > buffSpace)
        {
            NSUInteger buffInc = bytesToRead - buffSpace;

            [theCurrentRead->buffer increaseLengthBy:buffInc];
        }

        // Read data into packet buffer

        void *buffer = [theCurrentRead->buffer mutableBytes] + theCurrentRead->startOffset;
        void *subBuffer = buffer + theCurrentRead->bytesDone;

        CFIndex result = [self readIntoBuffer:subBuffer maxLength:bytesToRead];

        // Check results
        if (result < 0)
        {
            socketError = YES;
        }
        else
        {
            CFIndex bytesRead = result;

            // Update total amount read for the current read
            theCurrentRead->bytesDone += bytesRead;

            // Update total amount read in this method invocation
            totalBytesRead += bytesRead;


            // Is packet done?
            if (theCurrentRead->readLength > 0)
            {
                // Read type #2 - read a specific length of data

                done = (theCurrentRead->bytesDone == theCurrentRead->readLength);
            }
            else if (theCurrentRead->term != nil)
            {
                // Read type #3 - read up to a terminator

                if (didPreBuffer)
                {
                    // Search for the terminating sequence within the big chunk we just read.

                    NSInteger overflow = [theCurrentRead searchForTermAfterPreBuffering:result];

                    if (overflow > 0)
                    {
                        // Copy excess data into partialReadBuffer
                        void *overflowBuffer = buffer + theCurrentRead->bytesDone - overflow;

                        [partialReadBuffer appendBytes:overflowBuffer length:overflow];

                        // Update the bytesDone variable.
                        theCurrentRead->bytesDone -= overflow;

                        // Note: The completeCurrentRead method will trim the buffer for us.
                    }

                    done = (overflow >= 0);
                }
                else if (didReadFromPreBuffer)
                {
                    // Our 'done' variable was updated via the readLengthForTermWithPreBuffer:found: method
                }
                else
                {
                    // Search for the terminating sequence at the end of the buffer

                    NSUInteger termlen = [theCurrentRead->term length];

                    if(theCurrentRead->bytesDone >= termlen)
                    {
                        void *bufferEnd = buffer + (theCurrentRead->bytesDone - termlen);

                        const void *seq = [theCurrentRead->term bytes];

                        done = (memcmp (bufferEnd, seq, termlen) == 0);
                    }
                }

                if(!done && theCurrentRead->maxLength > 0)
                {
                    // We're not done and there's a set maxLength.
                    // Have we reached that maxLength yet?

                    if(theCurrentRead->bytesDone >= theCurrentRead->maxLength)
                    {
                        maxoutError = YES;
                    }
                }
            }
            else
            {
                // Read type #1 - read all available data
                //
                // We're done when:
                // - we reach maxLength (if there is a max)
                // - all readable is read (see below)

                if (theCurrentRead->maxLength > 0)
                {
                    done = (theCurrentRead->bytesDone >= theCurrentRead->maxLength);
                }
            }
        }
    }

    if (theCurrentRead->readLength <= 0 && theCurrentRead->term == nil)
    {
        // Read type #1 - read all available data

        if (theCurrentRead->bytesDone > 0)
        {
            // Ran out of bytes, so the "read-all-available-data" type packet is done
            done = YES;
        }
    }

    if (done)
    {
        [self completeCurrentRead];
        if (!socketError) [self scheduleDequeueRead];
    }
    else if (totalBytesRead > 0)
    {
        // We're not done with the readToLength or readToData yet, but we have read in some bytes
        if ([theDelegate respondsToSelector:@selector(onSocket:didReadPartialDataOfLength:tag:)])
        {
            [theDelegate onSocket:self didReadPartialDataOfLength:totalBytesRead tag:theCurrentRead->tag];
        }
    }

    if(socketError)
    {
        CFStreamError err = CFReadStreamGetError(theReadStream);
        [self closeWithError:[self errorFromCFStreamError:err]];
        return;
    }

    if(maxoutError)
    {
        [self closeWithError:[self getReadMaxedOutError]];
        return;
    }
}

// Ends current read and calls delegate.
- (void)completeCurrentRead
{
    NSAssert(theCurrentRead, @"Trying to complete current read when there is no current read.");

    NSData *result;

    if (theCurrentRead->bufferOwner)
    {
        // We created the buffer on behalf of the user.
        // Trim our buffer to be the proper size.
        [theCurrentRead->buffer setLength:theCurrentRead->bytesDone];

        result = theCurrentRead->buffer;
    }
    else
    {
        // We did NOT create the buffer.
        // The buffer is owned by the caller.
        // Only trim the buffer if we had to increase its size.

        if ([theCurrentRead->buffer length] > theCurrentRead->originalBufferLength)
        {
            NSUInteger readSize = theCurrentRead->startOffset + theCurrentRead->bytesDone;
            NSUInteger origSize = theCurrentRead->originalBufferLength;

            NSUInteger buffSize = MAX(readSize, origSize);

            [theCurrentRead->buffer setLength:buffSize];
        }

        void *buffer = [theCurrentRead->buffer mutableBytes] + theCurrentRead->startOffset;

        result = [NSData dataWithBytesNoCopy:buffer length:theCurrentRead->bytesDone freeWhenDone:NO];
    }

    if([theDelegate respondsToSelector:@selector(onSocket:didReadData:withTag:)])
    {
        [theDelegate onSocket:self didReadData:result withTag:theCurrentRead->tag];
    }

    // Caller may have disconnected in the above delegate method
    if (theCurrentRead != nil)
    {
        [self endCurrentRead];
    }
}

// Ends current read.
- (void)endCurrentRead
{
    NSAssert(theCurrentRead, @"Trying to end current read when there is no current read.");

    [theReadTimer invalidate];
    theReadTimer = nil;

    theCurrentRead = nil;
}

- (void)doReadTimeout:(NSTimer *)timer
{
    #pragma unused(timer)

    NSTimeInterval timeoutExtension = 0.0;

    if([theDelegate respondsToSelector:@selector(onSocket:shouldTimeoutReadWithTag:elapsed:bytesDone:)])
    {
        timeoutExtension = [theDelegate onSocket:self shouldTimeoutReadWithTag:theCurrentRead->tag
                                                                       elapsed:theCurrentRead->timeout
                                                                     bytesDone:theCurrentRead->bytesDone];
    }

    if(timeoutExtension > 0.0)
    {
        theCurrentRead->timeout += timeoutExtension;

        theReadTimer = [NSTimer timerWithTimeInterval:timeoutExtension
                                               target:self
                                             selector:@selector(doReadTimeout:)
                                             userInfo:nil
                                              repeats:NO];
        [self runLoopAddTimer:theReadTimer];
    }
    else
    {
        // Do not call endCurrentRead here.
        // We must allow the delegate access to any partial read in the unreadData method.

        [self closeWithError:[self getReadTimeoutError]];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Writing
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)writeData:(NSData *)data withTimeout:(NSTimeInterval)timeout tag:(long)tag
{
#if DEBUG_THREAD_SAFETY
    [self checkForThreadSafety];
#endif

    if (data == nil || [data length] == 0) return;
    if (theFlags & kForbidReadsWrites) return;

    AsyncWritePacket *packet = [[AsyncWritePacket alloc] initWithData:data timeout:timeout tag:tag];

    [theWriteQueue addObject:packet];
    [self scheduleDequeueWrite];

}

- (void)scheduleDequeueWrite
{
    if((theFlags & kDequeueWriteScheduled) == 0)
    {
        theFlags |= kDequeueWriteScheduled;
        [self performSelector:@selector(maybeDequeueWrite) withObject:nil afterDelay:0 inModes:theRunLoopModes];
    }
}

/**
 * Conditionally starts a new write.
 *
 * IF there is not another write in process
 * AND there is a write queued
 * AND we have a write stream available
 *
 * This method also handles auto-disconnect post read/write completion.
**/
- (void)maybeDequeueWrite
{
    // Unset the flag indicating a call to this method is scheduled
    theFlags &= ~kDequeueWriteScheduled;

    // If we're not currently processing a write AND we have an available write stream
    if((theCurrentWrite == nil) && (theWriteStream != NULL))
    {
        if([theWriteQueue count] > 0)
        {
            // Dequeue the next object in the write queue
            theCurrentWrite = [theWriteQueue objectAtIndex:0];
            [theWriteQueue removeObjectAtIndex:0];

            if([theCurrentWrite isKindOfClass:[AsyncSpecialPacket class]])
            {
                // Attempt to start TLS
                theFlags |= kStartingWriteTLS;

                // This method won't do anything unless both kStartingReadTLS and kStartingWriteTLS are set
                [self maybeStartTLS];
            }
            else
            {
                // Start time-out timer
                if(theCurrentWrite->timeout >= 0.0)
                {
                    theWriteTimer = [NSTimer timerWithTimeInterval:theCurrentWrite->timeout
                                                            target:self
                                                          selector:@selector(doWriteTimeout:)
                                                          userInfo:nil
                                                           repeats:NO];
                    [self runLoopAddTimer:theWriteTimer];
                }

                // Immediately write, if possible
                [self doSendBytes];
            }
        }
        else if(theFlags & kDisconnectAfterWrites)
        {
            if(theFlags & kDisconnectAfterReads)
            {
                if(([theReadQueue count] == 0) && (theCurrentRead == nil))
                {
                    [self disconnect];
                }
            }
            else
            {
                [self disconnect];
            }
        }
    }
}

/**
 * Call this method in doSendBytes instead of CFWriteStreamCanAcceptBytes().
 * This method supports the kSocketCanAcceptBytes flag.
**/
- (BOOL)canAcceptBytes
{
    if (theFlags & kSocketCanAcceptBytes)
    {
        return YES;
    }
    else
    {
        return CFWriteStreamCanAcceptBytes(theWriteStream);
    }
}

- (void)doSendBytes
{
    if ((theCurrentWrite == nil) || (theWriteStream == NULL))
    {
        return;
    }

    // Note: This method is not called if theCurrentWrite is an AsyncSpecialPacket (startTLS packet)

    NSUInteger totalBytesWritten = 0;

    BOOL done = NO;
    BOOL error = NO;

    while (!done && !error && [self canAcceptBytes])
    {
        // Figure out what to write
        NSUInteger bytesRemaining = [theCurrentWrite->buffer length] - theCurrentWrite->bytesDone;
        NSUInteger bytesToWrite = (bytesRemaining < WRITE_CHUNKSIZE) ? bytesRemaining : WRITE_CHUNKSIZE;

        UInt8 *writestart = (UInt8 *)([theCurrentWrite->buffer bytes] + theCurrentWrite->bytesDone);

        // Write
        CFIndex result = CFWriteStreamWrite(theWriteStream, writestart, bytesToWrite);

        // Unset the "can accept bytes" flag
        theFlags &= ~kSocketCanAcceptBytes;

        // Check results
        if (result < 0)
        {
            error = YES;
        }
        else
        {
            CFIndex bytesWritten = result;

            // Update total amount read for the current write
            theCurrentWrite->bytesDone += bytesWritten;

            // Update total amount written in this method invocation
            totalBytesWritten += bytesWritten;

            // Is packet done?
            done = ([theCurrentWrite->buffer length] == theCurrentWrite->bytesDone);
        }
    }

    if(done)
    {
        [self completeCurrentWrite];
        [self scheduleDequeueWrite];
    }
    else if(error)
    {
        CFStreamError err = CFWriteStreamGetError(theWriteStream);
        [self closeWithError:[self errorFromCFStreamError:err]];
        return;
    }
    else if (totalBytesWritten > 0)
    {
        // We're not done with the entire write, but we have written some bytes
        if ([theDelegate respondsToSelector:@selector(onSocket:didWritePartialDataOfLength:tag:)])
        {
            [theDelegate onSocket:self didWritePartialDataOfLength:totalBytesWritten tag:theCurrentWrite->tag];
        }
    }
}

// Ends current write and calls delegate.
- (void)completeCurrentWrite
{
    NSAssert(theCurrentWrite, @"Trying to complete current write when there is no current write.");

    if ([theDelegate respondsToSelector:@selector(onSocket:didWriteDataWithTag:)])
    {
        [theDelegate onSocket:self didWriteDataWithTag:theCurrentWrite->tag];
    }
    if (theCurrentWrite != nil) [self endCurrentWrite]; // Caller may have disconnected.
}

// Ends current write.
- (void)endCurrentWrite
{
    NSAssert(theCurrentWrite, @"Trying to complete current write when there is no current write.");

    [theWriteTimer invalidate];
    theWriteTimer = nil;

    theCurrentWrite = nil;
}

- (void)doWriteTimeout:(NSTimer *)timer
{
    #pragma unused(timer)

    NSTimeInterval timeoutExtension = 0.0;

    if([theDelegate respondsToSelector:@selector(onSocket:shouldTimeoutWriteWithTag:elapsed:bytesDone:)])
    {
        timeoutExtension = [theDelegate onSocket:self shouldTimeoutWriteWithTag:theCurrentWrite->tag
                                                                        elapsed:theCurrentWrite->timeout
                                                                      bytesDone:theCurrentWrite->bytesDone];
    }

    if(timeoutExtension > 0.0)
    {
        theCurrentWrite->timeout += timeoutExtension;

        theWriteTimer = [NSTimer timerWithTimeInterval:timeoutExtension
                                                target:self
                                              selector:@selector(doWriteTimeout:)
                                              userInfo:nil
                                               repeats:NO];
        [self runLoopAddTimer:theWriteTimer];
    }
    else
    {
        [self closeWithError:[self getWriteTimeoutError]];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Security
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)maybeStartTLS
{
    // We can't start TLS until:
    // - All queued reads prior to the user calling StartTLS are complete
    // - All queued writes prior to the user calling StartTLS are complete
    //
    // We'll know these conditions are met when both kStartingReadTLS and kStartingWriteTLS are set

    if((theFlags & kStartingReadTLS) && (theFlags & kStartingWriteTLS))
    {
        AsyncSpecialPacket *tlsPacket = (AsyncSpecialPacket *)theCurrentRead;

        BOOL didStartOnReadStream = CFReadStreamSetProperty(theReadStream, kCFStreamPropertySSLSettings,
                                                           (__bridge CFDictionaryRef)tlsPacket->tlsSettings);
        BOOL didStartOnWriteStream = CFWriteStreamSetProperty(theWriteStream, kCFStreamPropertySSLSettings,
                                                             (__bridge CFDictionaryRef)tlsPacket->tlsSettings);

        if(!didStartOnReadStream || !didStartOnWriteStream)
        {
            [self closeWithError:[self getSocketError]];
        }
    }
}

- (void)onTLSHandshakeSuccessful
{
    if((theFlags & kStartingReadTLS) && (theFlags & kStartingWriteTLS))
    {
        theFlags &= ~kStartingReadTLS;
        theFlags &= ~kStartingWriteTLS;

        if([theDelegate respondsToSelector:@selector(onSocketDidSecure:)])
        {
            [theDelegate onSocketDidSecure:self];
        }

        [self endCurrentRead];
        [self endCurrentWrite];

        [self scheduleDequeueRead];
        [self scheduleDequeueWrite];
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark CF Callbacks
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

- (void)doCFSocketCallback:(CFSocketCallBackType)type
                 forSocket:(CFSocketRef)sock
               withAddress:(NSData *)address
                  withData:(const void *)pData
{
    #pragma unused(address)

    NSParameterAssert (sock == theSocketUds);
    switch (type)
    {
        case kCFSocketConnectCallBack:
            // The data argument is either NULL or a pointer to an SInt32 error code, if the connect failed.
            if(pData)
                [self doSocketOpen:sock withCFSocketError:kCFSocketError];
            else
                [self doSocketOpen:sock withCFSocketError:kCFSocketSuccess];
            break;
        default:
            NSLog(@"AsyncSocket %p received unexpected CFSocketCallBackType %i", self, (int)type);
            break;
    }
}

- (void)doCFReadStreamCallback:(CFStreamEventType)type forStream:(CFReadStreamRef)stream
{
    #pragma unused(stream)

    NSParameterAssert(theReadStream != NULL);

    CFStreamError err;
    switch (type)
    {
        case kCFStreamEventOpenCompleted:
            theFlags |= kDidCompleteOpenForRead;
            [self doStreamOpen];
            break;
        case kCFStreamEventHasBytesAvailable:
            if(theFlags & kStartingReadTLS) {
                [self onTLSHandshakeSuccessful];
            }
            else {
                theFlags |= kSocketHasBytesAvailable;
                [self doBytesAvailable];
            }
            break;
        case kCFStreamEventErrorOccurred:
        case kCFStreamEventEndEncountered:
            err = CFReadStreamGetError (theReadStream);
            [self closeWithError: [self errorFromCFStreamError:err]];
            break;
        default:
            NSLog(@"AsyncSocket %p received unexpected CFReadStream callback, CFStreamEventType %i", self, (int)type);
    }
}

- (void)doCFWriteStreamCallback:(CFStreamEventType)type forStream:(CFWriteStreamRef)stream
{
    #pragma unused(stream)

    NSParameterAssert(theWriteStream != NULL);

    CFStreamError err;
    switch (type)
    {
        case kCFStreamEventOpenCompleted:
            theFlags |= kDidCompleteOpenForWrite;
            [self doStreamOpen];
            break;
        case kCFStreamEventCanAcceptBytes:
            if(theFlags & kStartingWriteTLS) {
                [self onTLSHandshakeSuccessful];
            }
            else {
                theFlags |= kSocketCanAcceptBytes;
                [self doSendBytes];
            }
            break;
        case kCFStreamEventErrorOccurred:
        case kCFStreamEventEndEncountered:
            err = CFWriteStreamGetError (theWriteStream);
            [self closeWithError: [self errorFromCFStreamError:err]];
            break;
        default:
            NSLog(@"AsyncSocket %p received unexpected CFWriteStream callback, CFStreamEventType %i", self, (int)type);
    }
}

/**
 * This is the callback we setup for CFSocket.
 * This method does nothing but forward the call to it's Objective-C counterpart
**/
static void MyCFSocketCallback (CFSocketRef sref, CFSocketCallBackType type, CFDataRef inAddress, const void *pData, void *pInfo)
{
    @autoreleasepool {

        AsyncSocket *theSocket = (__bridge AsyncSocket *)pInfo;
        NSData *address = [(__bridge NSData *)inAddress copy];

        [theSocket doCFSocketCallback:type forSocket:sref withAddress:address withData:pData];

    }
}

/**
 * This is the callback we setup for CFReadStream.
 * This method does nothing but forward the call to it's Objective-C counterpart
**/
static void MyCFReadStreamCallback (CFReadStreamRef stream, CFStreamEventType type, void *pInfo)
{
    @autoreleasepool {

        AsyncSocket *theSocket = (__bridge AsyncSocket *)pInfo;
        [theSocket doCFReadStreamCallback:type forStream:stream];

    }
}

/**
 * This is the callback we setup for CFWriteStream.
 * This method does nothing but forward the call to it's Objective-C counterpart
**/
static void MyCFWriteStreamCallback (CFWriteStreamRef stream, CFStreamEventType type, void *pInfo)
{
    @autoreleasepool {

        AsyncSocket *theSocket = (__bridge AsyncSocket *)pInfo;
        [theSocket doCFWriteStreamCallback:type forStream:stream];

    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark Class Methods
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@end

