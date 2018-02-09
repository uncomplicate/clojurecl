---
title: "Get Started"
Author: Dragan Djuric
layout: article
---

ClojureCL uses native OpenCL drivers, so it is very important that you do not skip any part of this guide.

## How to Get Started
* Walk through this guide, set up your development environment, and try the examples.
* Familiarize yourself with ClojureCL's [more detailed tutorials](/articles/guides.html) and [API documentation](/codox).

## Usage

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

## Overview and Features

ClojureCL is a Clojure library for High Performance Computing with OpenCL, which supports:

* GPUs from AMD, nVidia, Intel;
* CPUs from Intel, AMD, ARM etc;
* Computing accelerators and embedded devices (Intel Xeon Phi, Parallella, etc.).

If you need higher-level high performance functionality, such as matrix computations, try [Neanderthal](https://neanderthal.uncomplicate.org).

### On the TODO List

* Images
* OpenGL interoperability

## Installation

### Install OpenCL SDK and Drivers
To use ClojureCL, you must have appropriate hardware (a recent Intel CPU will do, while a recent AMD's Radeon graphic cards usually give best speedups per dollar), and install the drivers for your platform:

* AMD fully supports OpenCL 2.0. You need a recent proprietary GPU driver from AMD.
* [Intel OpenCL SDK](https://software.intel.com/en-us/articles/opencl-drivers) fully supports OpenCL 2.0.
* [Apple](https://developer.apple.com/opencl/) OSX has a built-in support for OpenCL 1.2 - no additional drivers are necessary.
* [nVidia](https://developer.nvidia.com/opencl) latest drivers should support OpenCL 1.2. nVidia seems to intentionaly give much poorer support for OpenCL computing than AMD or Intel, to push its proprietary CUDA platform.

### Add ClojureCL jar

The most straightforward way to include ClojureCL in your project is with Leiningen. Add the following dependency to your `project.clj`:

![](https://clojars.org/uncomplicate/clojurecl/latest-version.svg)

ClojureCL currently works out of the box on Linux, Windows, and OS X. For other plaforms, contact us.

## Where to go next

Hopefully this guide got you started and now you'd like to learn more. OpenCL programming requires a lot of knowledge about the OpenCL model, devices and specifics of parallel computations. The best beginner's guide, in my opinion, is the [OpenCL in Action](https://www.amazon.com/OpenCL-Action-Accelerate-Graphics-Computations/dp/1617290173) book. I expect to build a comprehensive base of articles and references for exploring this topic, so please check the [All Guides](/articles/guides.html) page from time to time. Of course, you should also check the [ClojureCL API](/codox) for specific details, and feel free to take a glance at [the source](https://github.com/uncomplicate/clojurecl) while you are there.
