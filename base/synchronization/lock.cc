// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file is used for debugging assertion support.  The Lock class
// is functionally a wrapper around the LockImpl class, so the only
// real intelligence in the class is in the debugging logic.

#if !defined(NDEBUG) || defined(DCHECK_ALWAYS_ON)

#include "base/synchronization/lock.h"
#include "glog/logging.h"

namespace base {

Lock::Lock() : lock_() {}

Lock::~Lock() {}

void Lock::AssertAcquired() const {}

void Lock::CheckHeldAndUnmark() {}

void Lock::CheckUnheldAndMark() {}

}  // namespace base

#endif  // !NDEBUG || DCHECK_ALWAYS_ON
