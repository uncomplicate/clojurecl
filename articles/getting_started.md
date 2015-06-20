---
title: "Get Started"
Author: Dragan Djuric
layout: article
---

ClojureCL uses native OpenCL drivers, so it is very important that you do not skip any part of this guide.

# How to get started
* Walk through this guide, set up your development environment, and try the examples.
* Familiarize yourself with ClojureCL's [more detailed tutorials](/articles/guides.html) and [API documentation](/codox).

# Overview and features

ClojureCL is a Clojure library for High Performance Computing with OpenCL, which supports:

* GPUs from AMD, nVidia, Intel;
* CPUs from Intel, AMD, ARM etc;
* Computing accelerators and embedded devices (Intel Xeon Phi, Parallella, etc.).

## On the TODO list

* Images
* OpenGL interoperability

# Installation

## Install OpenCL SDK and drivers
To use ClojureCL, you must have appropriate hardware (a recent Intel CPU will do, while a recent AMD's Radeon graphic cards usually give best speedups per dollar), and install the drivers and OpenCL SDK for your platform:

* [AMD OpenCL SDK](http://developer.amd.com/tools-and-sdks/opencl-zone/amd-accelerated-parallel-processing-app-sdk/) fully supports OpenCL 2.0. You also need a recent Catalyst driver.
* [Intel OpenCL SDK](http://software.intel.com/en-us/articles/opencl-drivers) fully supports OpenCL 2.0.
* [Apple](http://developer.apple.com/opencl/) OSX has a built-in support for OpenCL 1.2 - no additional drivers are necessary.
* [nVidia](http://developer.nvidia.com/opencl) latest drivers should support OpenCL 1.2. nVidia seems to intentionaly give much poorer support for OpenCL computing than AMD or Intel, to push its proprietary CUDA platform.

## Add ClojureCL jar

The most straightforward way to include ClojureCL in your project is with Leiningen. Add the following dependency to your `project.clj`:

```clojure
[uncomplicate/clojurecl "0.1.2"]
```

ClojureCL uses [JOCL](http://jocl.org) as low-level bindings with the native drivers. JOCL is available in
the central Maven repository, and will be fetched automatically. If you're on Windows or Linux, you do not need
to worry about this. If you're on some other platform, you will need to manually build JOCL with make or CMake.
If you do that, please contact the JOCL author and contribute the binaries for your platform to the official build.

# Usage

First `use` or `require` `uncomplicate.clojurecl.core` and/or `uncomplicate.clojurecl.info` in your namespace, and you'll be able to call appropriate functions from the ClojureCL library.

```clojure
(ns example
  (:use [uncomplicate.clojurecl core info]))
```

Now you can work with OpenCL platforms, devices, contexts, queues etc.

Here we get info on all available platforms and devices

```clojure
(map info (platforms))
(map info (devices (first (platforms))))
```

# Where to go next

Hopefully this guide got you started and now you'd like to learn more. OpenCL programming requires a lot of knowledge about the OpenCL model, devices and specifics of parallel computations. The best beginner's guide, in my opinion, is the [OpenCL in Action](http://www.amazon.com/OpenCL-Action-Accelerate-Graphics-Computations/dp/1617290173) book. I expect to build a comprehensive base of articles and references for exploring this topic, so please check the [All Guides](/articles/guides.html) page from time to time. Of course, you should also check the [ClojureCL API](/codox) for specific details, and feel free to take a gander at [the source](https://github.com/uncomplicate/neanderthal) while you are there.
