#import <Foundation/Foundation.h>

@interface AeroUtils : NSObject

+ (BOOL)swizzleClassMethod:(SEL)targetSel fromClass:(Class)targetClass withMethod:(SEL)newSel fromClass:(Class)otherClass;
+ (BOOL)swizzleInstanceMethod:(SEL)targetSel fromClass:(Class)targetClass withMethod:(SEL)newSel fromClass:(Class)otherClass;

@end

IMP replace_method(Class cls, SEL sel, IMP imp);
