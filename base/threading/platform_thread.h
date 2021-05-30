// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// WARNING: You should *NOT* be using this class directly.  PlatformThread is
// the low-level platform-specific abstraction to the OS's threading interface.
// You should instead be using a message-loop driven Thread, see thread.h.

#ifndef BASE_THREADING_PLATFORM_THREAD_H_
#define BASE_THREADING_PLATFORM_THREAD_H_

#include "base/base_export.h"
#include "base/basictypes.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#elif defined(OS_POSIX)
#include <pthread.h>
#include <unistd.h>
#endif

namespace base {

// Used for logging. Always an integer value.
#if defined(OS_WIN)
typedef DWORD PlatformThreadId;
#elif defined(OS_POSIX)
typedef pid_t PlatformThreadId;
#endif

// Used for thread checking and debugging.
// Meant to be as fast as possible.
// These are produced by PlatformThread::CurrentRef(), and used to later
// check if we are on the same thread or not by using ==. These are safe
// to copy between threads, but can't be copied to another process as they
// have no meaning there. Also, the internal identifier can be re-used
// after a thread dies, so a PlatformThreadRef cannot be reliably used
// to distinguish a new thread from an old, dead thread.
class PlatformThreadRef {
 public:
#if defined(OS_WIN)
  typedef DWORD RefType;
#elif defined(OS_POSIX)
  typedef pthread_t RefType;
#endif
  PlatformThreadRef()
      : id_(0) {
  }

  explicit PlatformThreadRef(RefType id)
      : id_(id) {
  }

  bool operator==(PlatformThreadRef other) const {
    return id_ == other.id_;
  }

  bool is_null() const {
    return id_ == 0;
  }
 private:
  RefType id_;
};

// Used to operate on threads.
class PlatformThreadHandle {
 public:
#if defined(OS_WIN)
  typedef void* Handle;
#elif defined(OS_POSIX)
  typedef pthread_t Handle;
#endif

  PlatformThreadHandle()
      : handle_(0),
        id_(0) {
  }

  explicit PlatformThreadHandle(Handle handle)
      : handle_(handle),
        id_(0) {
  }

  PlatformThreadHandle(Handle handle,
                       PlatformThreadId id)
      : handle_(handle),
        id_(id) {
  }

  bool is_equal(const PlatformThreadHandle& other) const {
    return handle_ == other.handle_;
  }

  bool is_null() const {
    return !handle_;
  }

  Handle platform_handle() const {
    return handle_;
  }

 private:
  friend class PlatformThread;

  Handle handle_;
  PlatformThreadId id_;
};

const PlatformThreadId kInvalidThreadId(0);

// A namespace for low-level thread functions.
class BASE_EXPORT PlatformThread {
 public:
  // Yield the current thread so another thread can be scheduled.
  static void YieldCurrentThread();
 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(PlatformThread);
};

}  // namespace base

#endif  // BASE_THREADING_PLATFORM_THREAD_H_
