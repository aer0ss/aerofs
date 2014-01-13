//
// Created by hugues on 10/19/12.
//
//

#import "AeroOverlayCache.h"

#include "OverlayCache.h"

/**
 * Convert NSString to std::wstring for use in OverlayCache
 *
 * TODO: refactor OverlayCache to store a different string representation that can handle multiple platforms?
 */
static inline std::wstring NSStringToWString(NSString* s)
{
    NSData* pSData = [s dataUsingEncoding:CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingUTF32LE)];
    return std::wstring((wchar_t*)[pSData bytes], [pSData length] / sizeof(wchar_t ));
}

static inline NSString* StringWToNSString(const std::wstring& s)
{
    NSString* pString = [[NSString alloc]
            initWithBytes : (char*)s.data()
                   length : s.size() * sizeof(wchar_t)
                 encoding : CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingUTF32LE)];
    return pString;
}

class EvictionForwarder : public OverlayCache::EvictionDelegate
{
public:
    id<AeroEvictionDelegate> delegate;
    void evicted(const std::wstring& key, int value) const
    {
        [delegate evicted:StringWToNSString(key) withValue:value];
    }
};

struct AeroOverlayCachePrivate {
    OverlayCache* cache;
    EvictionForwarder* forward;
};

@implementation AeroOverlayCache

- (id)init
{
    return [self initWithLimit:1000];
}

- (void)dealloc
{
    delete d->cache;
    delete d->forward;
    delete d;
}

- (id)initWithLimit:(int)limit
{
    d = new AeroOverlayCachePrivate;
    d->cache = new OverlayCache(limit);
    d->forward = new EvictionForwarder;
    d->cache->setEvictionDelegate(d->forward);
    return self;
}

- (NSUInteger)countLimit
{
    return (NSUInteger)d->cache->limit();
}

- (void)setCountLimit:(NSUInteger)limit
{
    @synchronized (self) {
        d->cache->setLimit(limit);
    }
}

- (void)clear
{
    @synchronized (self) {
        d->cache->clear();
    }
}

- (int)overlayForPath:(NSString*)path
{
    @synchronized (self) {
        return [self overlayForPath:path withDefault:-1];
    }
}

- (int)overlayForPath:(NSString*)path withDefault:(int)defaultValue
{
    @synchronized (self) {
        int value = d->cache->value(NSStringToWString(path), defaultValue);
        return value;
    }
}

- (void)setOverlay:(int)value forPath:(NSString*)path
{
    @synchronized (self) {
        d->cache->insert(NSStringToWString(path), value);
    }
}

- (void)setDelegate:(id <AeroEvictionDelegate>)delegate
{
    d->forward->delegate = delegate;
}

@end