# [ClojureCL](http://clojurecl.uncomplicate.org) - notable changes between versions

## 0.12.0

* default-platform prefers a platform with 2.0+ GPU devices.

## 0.11.0

* Support ROCm OpenCL implementation.

## 0.10.0

### Breaking Changes

* enq-nd! renamed to enq-kernel!
* XXX* methods moved to internal implementation namespace.
* OpenCL 1 functions moved from legacy namespace to core.
* Wrapped/Wrappable protocols introduced.

### Enhancements

* JOCL objects are now wrapped to protect them from (accidental) repeated memory releases.
* Improved info.

## 0.9.0

* Simplified toolbox enq-reduce for 2D reductions
* Enhanced error info details.
* Removed vertigo dependency.

## 0.8.0

* Updated to Java 9 modules. Requires add-open jvm argument.
* Clojure dep updated to 1.9.0

## 0.7.2

* Fixed vertigo dependency.

## 0.7.0

* In info method, when device does not support specific information, exception cause is displayed instead of the ex-info object.
* Added legacy? method to core.

## 0.6.5

with-default-1 tries to get the best device, same as with-default

## 0.6.4

Fixed https://github.com/uncomplicate/clojurecl/issues/12

## 0.6.3

Fix core namespace imports in legacy.clj.

## 0.6.2

Fixed https://github.com/uncomplicate/clojurecl/issues/10

## 0.6.1

Bugfixes:

Fixed https://github.com/uncomplicate/clojurecl/issues/9

## 0.6.0

* Added support for OS X
* Toolbox enq-reduce improved and simplified

## 0.5.0

New features
* Now uses Realeaseable functions from uncomplicate/commons

## 0.4.0

New features
* sort-by-open-cl function orders devices by the version of OpenCL that they support.
* with-default function sorts devices before taking first.

## 0.3.0

New features

* New namespace for useful kernel helpers named toolbox
* specialized work-size-Xd functions

Bugfixes

* map-buffer now correctly returns an empty ByteBuffer when reqested size is 0.

## 0.2.0

New features

* set-args! now accept optional index to start from.

Breaking changes

* *opencl-2* setting removed. A new namespace legacy has been created to
contain things required in older versions of OpenCL, but unsupported in the current
version. Legacy function command-queue-1 introduced to support cases when you
need to support pre-2.0 platforms. For code that already targeted OpenCL 2.0,
nothing needs to be changed. Other code needs to replace all
calls to command-queue to the calls of command-queue-1 and with-default to
with-default-1.

Bugfixes:

* Fixed a possible buffer overflow in enq-map-buffer when offset is greater than 0.

## 0.1.2

New features:

* implemented enq-fill! function

Bugfixes:

* Primitive arrays now return Mem/size in bytes instead of count of elements

## 0.1.1

Bugfixes:

* Moved dependency to vertigo from :dev to main classpath in project.clj
