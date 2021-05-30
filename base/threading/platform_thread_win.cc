// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/platform_thread.h"

namespace base {

// static
void PlatformThread::YieldCurrentThread() {
  ::Sleep(0);
}

}  // namespace base
