// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file contains functions for launching subprocesses.

#ifndef BASE_PROCESS_LAUNCH_H_
#define BASE_PROCESS_LAUNCH_H_

#include <string>
#include <utility>
#include <vector>

#include "base/base_export.h"
#include "base/basictypes.h"
#include "base/strings/string_piece.h"

#if defined(OS_POSIX)
#include "base/posix/file_descriptor_shuffle.h"
#elif defined(OS_WIN)
#include <windows.h>
#endif

namespace base {

#if defined(OS_WIN)

// Output multi-process printf, cout, cerr, etc to the cmd.exe console that ran
// chrome. This is not thread-safe: only call from main thread.
BASE_EXPORT void RouteStdioToConsole();
#endif  // defined(OS_WIN)

}  // namespace base

#endif  // BASE_PROCESS_LAUNCH_H_
