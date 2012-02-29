    default: {
      NSAssert(false, @"Unknown reply");
      return;
    }
  }

  [invocation performSelectorOnMainThread:@selector(invoke) withObject:nil waitUntilDone:NO];
}
