// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/mac/sdk_forward_declarations.h"

// Replicate specific 10.7 SDK declarations for building with prior SDKs.
#if !defined(MAC_OS_X_VERSION_10_7) || \
    MAC_OS_X_VERSION_MAX_ALLOWED < MAC_OS_X_VERSION_10_7

NSString* const NSWindowWillEnterFullScreenNotification =
    @"NSWindowWillEnterFullScreenNotification";

NSString* const NSWindowWillExitFullScreenNotification =
    @"NSWindowWillExitFullScreenNotification";

NSString* const NSWindowDidEnterFullScreenNotification =
    @"NSWindowDidEnterFullScreenNotification";

NSString* const NSWindowDidExitFullScreenNotification =
    @"NSWindowDidExitFullScreenNotification";

NSString* const NSWindowDidFailToEnterFullScreenNotification =
    @"NSWindowDidFailToEnterFullScreenNotification";

NSString* const NSWindowDidFailToExitFullScreenNotification =
    @"NSWindowDidFailToExitFullScreenNotification";

#endif  // MAC_OS_X_VERSION_10_7

// Replicate specific 10.10 SDK declarations for building with prior SDKs.
#if !defined(MAC_OS_X_VERSION_10_10) || \
    MAC_OS_X_VERSION_MAX_ALLOWED < MAC_OS_X_VERSION_10_10

NSString* const NSUserActivityTypeBrowsingWeb =
    @"NSUserActivityTypeBrowsingWeb";

NSString* const NSAppearanceNameVibrantDark = @"NSAppearanceNameVibrantDark";

#endif  // MAC_OS_X_VERSION_10_10
