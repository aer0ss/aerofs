//
//  AsyncSocket.h
//
//  This class is in the public domain.
//  Originally created by Dustin Voss on Wed Jan 29 2003.
//  Updated and maintained by Deusty Designs and the Mac development community.
//
//  http://code.google.com/p/cocoaasyncsocket/
//

#import <Foundation/Foundation.h>

@class AsyncSocket;
@class AsyncReadPacket;
@class AsyncWritePacket;

extern NSString *const AsyncSocketException;
extern NSString *const AsyncSocketErrorDomain;

enum AsyncSocketError
{
    AsyncSocketCFSocketError = kCFSocketError,  // From CFSocketError enum.
    AsyncSocketNoError = 0,                     // Never used.
    AsyncSocketCanceledError,                   // onSocketWillConnect: returned NO.
    AsyncSocketConnectTimeoutError,
    AsyncSocketReadMaxedOutError,               // Reached set maxLength without completing
    AsyncSocketReadTimeoutError,
    AsyncSocketWriteTimeoutError
};
typedef enum AsyncSocketError AsyncSocketError;

@protocol AsyncSocketDelegate
@optional

/**
 * In the event of an error, the socket is closed.
 * You may call "unreadData" during this call-back to get the last bit of data off the socket.
 * When connecting, this delegate method may be called
 * before"onSocket:didAcceptNewSocket:" or "onSocket:didConnectToHost:".
**/
- (void)onSocket:(AsyncSocket *)sock willDisconnectWithError:(NSError *)err;

/**
 * Called when a socket disconnects with or without error.  If you want to release a socket after it disconnects,
 * do so here. It is not safe to do that during "onSocket:willDisconnectWithError:".
 *
 * If you call the disconnect method, and the socket wasn't already disconnected,
 * this delegate method will be called before the disconnect method returns.
**/
- (void)onSocketDidDisconnect:(AsyncSocket *)sock;

/**
 * Called when a socket accepts a connection.  Another socket is spawned to handle it. The new socket will have
 * the same delegate and will call "onSocket:didConnectToHost:port:".
**/
- (void)onSocket:(AsyncSocket *)sock didAcceptNewSocket:(AsyncSocket *)newSocket;

/**
 * Called when a new socket is spawned to handle a connection.  This method should return the run-loop of the
 * thread on which the new socket and its delegate should operate. If omitted, [NSRunLoop currentRunLoop] is used.
**/
- (NSRunLoop *)onSocket:(AsyncSocket *)sock wantsRunLoopForNewSocket:(AsyncSocket *)newSocket;

/**
 * Called when a socket is about to connect. This method should return YES to continue, or NO to abort.
 * If aborted, will result in AsyncSocketCanceledError.
 *
 * If the connectToHost:onPort:error: method was called, the delegate will be able to access and configure the
 * CFReadStream and CFWriteStream as desired prior to connection.
 *
 * If the connectToAddress:error: method was called, the delegate will be able to access and configure the
 * CFSocket and CFSocketNativeHandle (BSD socket) as desired prior to connection. You will be able to access and
 * configure the CFReadStream and CFWriteStream in the onSocket:didConnectToHost:port: method.
**/
- (BOOL)onSocketWillConnect:(AsyncSocket *)sock;

/**
 * Called when a socket connects and is ready for reading and writing.
**/
- (void)onSocketDidConnect:(AsyncSocket *)sock;

/**
 * Called when a socket has completed reading the requested data into memory.
 * Not called if there is an error.
**/
- (void)onSocket:(AsyncSocket *)sock didReadData:(NSData *)data withTag:(long)tag;

/**
 * Called when a socket has read in data, but has not yet completed the read.
 * This would occur if using readToData: or readToLength: methods.
 * It may be used to for things such as updating progress bars.
**/
- (void)onSocket:(AsyncSocket *)sock didReadPartialDataOfLength:(NSUInteger)partialLength tag:(long)tag;

/**
 * Called when a socket has completed writing the requested data. Not called if there is an error.
**/
- (void)onSocket:(AsyncSocket *)sock didWriteDataWithTag:(long)tag;

/**
 * Called when a socket has written some data, but has not yet completed the entire write.
 * It may be used to for things such as updating progress bars.
**/
- (void)onSocket:(AsyncSocket *)sock didWritePartialDataOfLength:(NSUInteger)partialLength tag:(long)tag;

/**
 * Called if a read operation has reached its timeout without completing.
 * This method allows you to optionally extend the timeout.
 * If you return a positive time interval (> 0) the read's timeout will be extended by the given amount.
 * If you don't implement this method, or return a non-positive time interval (<= 0) the read will timeout as usual.
 *
 * The elapsed parameter is the sum of the original timeout, plus any additions previously added via this method.
 * The length parameter is the number of bytes that have been read so far for the read operation.
 *
 * Note that this method may be called multiple times for a single read if you return positive numbers.
**/
- (NSTimeInterval)onSocket:(AsyncSocket *)sock
  shouldTimeoutReadWithTag:(long)tag
                   elapsed:(NSTimeInterval)elapsed
                 bytesDone:(NSUInteger)length;

/**
 * Called if a write operation has reached its timeout without completing.
 * This method allows you to optionally extend the timeout.
 * If you return a positive time interval (> 0) the write's timeout will be extended by the given amount.
 * If you don't implement this method, or return a non-positive time interval (<= 0) the write will timeout as usual.
 *
 * The elapsed parameter is the sum of the original timeout, plus any additions previously added via this method.
 * The length parameter is the number of bytes that have been written so far for the write operation.
 *
 * Note that this method may be called multiple times for a single write if you return positive numbers.
**/
- (NSTimeInterval)onSocket:(AsyncSocket *)sock
 shouldTimeoutWriteWithTag:(long)tag
                   elapsed:(NSTimeInterval)elapsed
                 bytesDone:(NSUInteger)length;

/**
 * Called after the socket has successfully completed SSL/TLS negotiation.
 * This method is not called unless you use the provided startTLS method.
 *
 * If a SSL/TLS negotiation fails (invalid certificate, etc) then the socket will immediately close,
 * and the onSocket:willDisconnectWithError: delegate method will be called with the specific SSL error code.
**/
- (void)onSocketDidSecure:(AsyncSocket *)sock;

@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#pragma mark -
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@interface AsyncSocket : NSObject
{
    CFSocketNativeHandle theNativeSocketUds;

    CFSocketRef theSocketUds;

    CFReadStreamRef theReadStream;
    CFWriteStreamRef theWriteStream;

    CFRunLoopSourceRef theSourceUds;
    CFRunLoopRef theRunLoop;
    CFSocketContext theContext;
    NSArray *theRunLoopModes;

    NSTimer *theConnectTimer;

    NSMutableArray *theReadQueue;
    AsyncReadPacket *theCurrentRead;
    NSTimer *theReadTimer;
    NSMutableData *partialReadBuffer;

    NSMutableArray *theWriteQueue;
    AsyncWritePacket *theCurrentWrite;
    NSTimer *theWriteTimer;

    id theDelegate;
    UInt16 theFlags;

    long theUserData;
}

- (id)init;
- (id)initWithDelegate:(id)delegate;
- (id)initWithDelegate:(id)delegate userData:(long)userData;

- (void)setDelegate:(id)delegate;

// Once one of the accept or connect methods are called, the AsyncSocket instance is locked in
// and the other accept/connect methods can't be called without disconnecting the socket first.
// If the attempt fails or times out, these methods either return NO or
// call "onSocket:willDisconnectWithError:" and "onSockedDidDisconnect:".

// When an incoming connection is accepted, AsyncSocket invokes several delegate methods.
// These methods are (in chronological order):
// 1. onSocket:didAcceptNewSocket:
// 2. onSocket:wantsRunLoopForNewSocket:
// 3. onSocketWillConnect:
//
// Your server code will need to retain the accepted socket (if you want to accept it).
// The best place to do this is probably in the onSocket:didAcceptNewSocket: method.
//
// After the read and write streams have been setup for the newly accepted socket,
// the onSocket:didConnectToHost:port: method will be called on the proper run loop.
//
// Multithreading Note: If you're going to be moving the newly accepted socket to another run
// loop by implementing onSocket:wantsRunLoopForNewSocket:, then you should wait until the
// onSocket:didConnectToHost:port: method before calling read, write, or startTLS methods.
// Otherwise read/write events are scheduled on the incorrect runloop, and chaos may ensue.

/**
 * This method is the same as connectToAddress:error: with an additional timeout option.
 * To not time out use a negative time interval, or simply use the connectToAddress:error: method.
**/
- (BOOL)connectToAddress:(NSData *)remoteAddr withTimeout:(NSTimeInterval)timeout error:(NSError **)errPtr;

- (BOOL)connectToAddress:(NSData *)remoteAddr
     viaInterfaceAddress:(NSData *)interfaceAddr
             withTimeout:(NSTimeInterval)timeout
                   error:(NSError **)errPtr;

/**
 * Disconnects immediately. Any pending reads or writes are dropped.
 * If the socket is not already disconnected, the onSocketDidDisconnect delegate method
 * will be called immediately, before this method returns.
 *
 * Please note the recommended way of releasing an AsyncSocket instance (e.g. in a dealloc method)
 * [asyncSocket setDelegate:nil];
 * [asyncSocket disconnect];
 * [asyncSocket release];
**/
- (void)disconnect;

/* Returns YES if the socket and streams are open, connected, and ready for reading and writing. */
- (BOOL)isConnected;

// The readData and writeData methods won't block (they are asynchronous).
//
// When a read is complete the onSocket:didReadData:withTag: delegate method is called.
// When a write is complete the onSocket:didWriteDataWithTag: delegate method is called.
//
// You may optionally set a timeout for any read/write operation. (To not timeout, use a negative time interval.)
// If a read/write opertion times out, the corresponding "onSocket:shouldTimeout..." delegate method
// is called to optionally allow you to extend the timeout.
// Upon a timeout, the "onSocket:willDisconnectWithError:" method is called, followed by "onSocketDidDisconnect".
//
// The tag is for your convenience.
// You can use it as an array index, step number, state id, pointer, etc.

/**
 * Reads the given number of bytes.
 *
 * If the timeout value is negative, the read operation will not use a timeout.
 *
 * If the length is 0, this method does nothing and the delegate is not called.
**/
- (void)readDataToLength:(NSUInteger)length withTimeout:(NSTimeInterval)timeout tag:(long)tag;

/**
 * Reads the given number of bytes.
 * The bytes will be appended to the given byte buffer starting at the given offset.
 * The given buffer will automatically be increased in size if needed.
 *
 * If the timeout value is negative, the read operation will not use a timeout.
 * If the buffer if nil, a buffer will automatically be created for you.
 *
 * If the length is 0, this method does nothing and the delegate is not called.
 * If the bufferOffset is greater than the length of the given buffer,
 * the method will do nothing, and the delegate will not be called.
 *
 * If you pass a buffer, you must not alter it in any way while AsyncSocket is using it.
 * After completion, the data returned in onSocket:didReadData:withTag: will be a subset of the given buffer.
 * That is, it will reference the bytes that were appended to the given buffer.
**/
- (void)readDataToLength:(NSUInteger)length
             withTimeout:(NSTimeInterval)timeout
                  buffer:(NSMutableData *)buffer
            bufferOffset:(NSUInteger)offset
                     tag:(long)tag;

/**
 * Writes data to the socket, and calls the delegate when finished.
 *
 * If you pass in nil or zero-length data, this method does nothing and the delegate will not be called.
 * If the timeout value is negative, the write operation will not use a timeout.
**/
- (void)writeData:(NSData *)data withTimeout:(NSTimeInterval)timeout tag:(long)tag;

@end

