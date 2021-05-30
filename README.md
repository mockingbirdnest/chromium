This repository contains a modified subset of the Chromium codebase used by Principia for stack tracing support in glog on Windows.

The contents of this repository were constructed as follows:
```powershell
git clone "https://chromium.googlesource.com/chromium/src.git" chromium -n --depth 1 -b "40.0.2193.1"
$GitPromptSettings.RepositoriesInWhichToDisableFileStatus += join-path  (gi -path .).FullName chromium
push-location -path "chromium"
git config core.sparsecheckout true
copy "..\..\Principia\documentation\setup files\chromium_sparse_checkout.txt" ".git/info/sparse-checkout"
git checkout
copy "..\..\Principia\documentation\setup files\chromium.patch"
git am "chromium.patch"
rm "chromium.patch"
```
where the files in `..\..\Principia\` are https://github.com/mockingbirdnest/Principia/blob/4a85594a4d7de4cfd1924a0fb386b26e6c99dfed/documentation/Setup%20Files/chromium.patch
and https://github.com/mockingbirdnest/Principia/blob/f2d972022fd6c7753aa78e912113508db7527e1a/documentation/Setup%20Files/chromium_sparse_checkout.txt
respectively.
