// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "FLTWKUIDelegate.h"

@implementation FLTWKUIDelegate {
  FlutterMethodChannel* _methodChannel;
}

- (instancetype)initWithChannel:(FlutterMethodChannel*)channel {
  self = [super init];
  if (self) {
    _methodChannel = channel;
  }
  return self;
}

- (void)webView:(WKWebView *)webView runJavaScriptAlertPanelWithMessage:(NSString *)message
                                                       initiatedByFrame:(WKFrameInfo *)frame
                                                      completionHandler:(void (^)(void))completionHandler
{

  NSDictionary* arguments = @{
    @"message" : message
  };

  [_methodChannel invokeMethod:@"javascriptAlert" arguments:arguments];

  completionHandler();

}
@end
