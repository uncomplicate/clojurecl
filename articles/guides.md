---
title: "Guides"
Author: Dragan Djuric
layout: article
---

## Making sense of OpenCL

OpenCL is a standard for heterogeneous parallel computing and GPGPU. It is similar to CUDA, but open and supported on
a multitude of hardware platforms instead of proprietary CUDA. It brings a lot of power, but do not expect it
to be an easy ride if you've never programmed anything on the GPU or embedded devices. With ClojureCL, it is
not as difficult as in C (OpenCL Hello World in C is a hundred lines of source code, in ClojureCL it's only a few), but
you still have to grasp the concepts of parallel programming that are different than your usual x86 CPU Java, C,
Clojure, C#, Python or Ruby code. **The good news is that you can use any OpenCL book to learn ClojureCL, and we
even provide [ClojureCL code](https://github.com/uncomplicate/clojurecl/tree/master/test/clojure/uncomplicate/clojurecl/examples/openclinaction) for the examples used in the [OpenCL in Action](https://www.amazon.com/OpenCL-Action-Accelerate-Graphics-Computations/dp/1617290173) book.**

Once you get past the beginner's steep learning curve, it makes sense, and opens a whole new world of high-performance
computing - you practically have a supercomputer on your desktop.

## Where to find OpenCL books, tutorials, and documentation

Learning OpenCL programming requires learning the details of OpenCL C language and OpenCL API, but even more important is learning the main concept of high performance computing, generally applicable in OpenCL, CUDA, Open MPI
or other technologies.

1. In my opinion, [OpenCL in Action](https://www.amazon.com/OpenCL-Action-Accelerate-Graphics-Computations/dp/1617290173) is by far the best, especially if you're a beginner.
2. When you get past the beginning, you'll probably need a specific optimization guide for the platform you use. Look for is at the OpenCL page of your hardware vendor.
3. [OpenCL API Specification](https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/) and [The OpenCL C Specification](https://www.khronos.org/registry/cl/specs/opencl-2.0-openclc.pdf)(used for programming the kernels) are good references once you know what to look for.
4. [There is a bunch of other OpenCL books](https://streamcomputing.eu/knowledge/for-developers/books/) that you might find useful after you've check out resources 1-3.
5. Algorithms for parallel computations are generally different than classic algorithms from the textbook, and are
usually platform-agnostic. You'll usually find a solution to your computation problem in a scientific paper or a general HPC book regardless of whether it is written for OpenCL, CUDA, or is thechnology neutral.

## ClojureCL Reference

1. ClojureCL comes with [detailed documentation](/codox). Be sure to check it, it also includes examples and foreign links.
2. ClojureCL comes with a bunch of [Midje tests](https://github.com/uncomplicate/clojurecl/tree/master/test/clojure/uncomplicate/clojurecl/). When you're not sure how to use some feature, consult the tests.
