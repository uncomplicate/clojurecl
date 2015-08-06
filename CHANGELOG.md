# [ClojureCL](http://clojurecl.uncomplicate.org) - notable changes between versions

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

## 0.1.2

New features:

* implemented enq-fill! function

Bugfixes:

* Primitive arrays now return Mem/size in bytes instead of count of elements

## 0.1.1

Bugfixes:

* Moved dependency to vertigo from :dev to main classpath in project.clj
