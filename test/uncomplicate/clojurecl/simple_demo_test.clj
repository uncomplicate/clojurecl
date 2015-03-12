(ns uncomplicate.clojurecl.simple-demo-test
  (:require [midje.sweet :refer :all]
            [vertigo.bytes :refer :all])
  (:import [org.jocl CL cl_platform_id cl_context_properties cl_device_id
            cl_context cl_command_queue cl_mem cl_program cl_kernel Sizeof Pointer]
           [java.nio ByteBuffer ByteOrder]))

(def n 1)
(def src-array-a (float-array [3.333333333]))
(def src-array-b (float-array [3.333333333]))
(def src-array-c  (float-array [3.333333333]))

(def srca (Pointer/to src-array-a))
(def srcb (Pointer/to src-array-b))
(def srcc (Pointer/to src-array-c))

(def program-source "__kernel void sampleKernel(__global const float *a, __global const float *b, __global float *c) { int gid = get_global_id(0); c[gid] = a[gid] + b[gid] + 1.0;}")

(def num-platforms-array (int-array 1))
(CL/clGetPlatformIDs 0 nil num-platforms-array)
(def num-platforms (aget ^ints num-platforms-array 0))

(def platforms (make-array cl_platform_id num-platforms))
(CL/clGetPlatformIDs num-platforms platforms nil)
(def platform (aget ^objects platforms 0))

(def ^cl_context_properties context-properties (cl_context_properties.))
(.addProperty context-properties CL/CL_CONTEXT_PLATFORM platform)

(def num-devices-array (int-array 1))
(CL/clGetDeviceIDs platform CL/CL_DEVICE_TYPE_GPU 0 nil num-devices-array)
(def num-devices (aget ^ints num-devices-array 0))

(def devices (make-array cl_device_id num-devices))
(CL/clGetDeviceIDs platform CL/CL_DEVICE_TYPE_GPU num-devices devices nil)
(def device (get devices 0))

(def context (CL/clCreateContext context-properties num-devices devices nil nil nil))
(def command-queue (CL/clCreateCommandQueue context device 0 nil))

(def mem-objects
  [(CL/clCreateBuffer context
                      (bit-or CL/CL_MEM_READ_ONLY CL/CL_MEM_COPY_HOST_PTR)
                      (* Sizeof/cl_float n) srca nil)
   (CL/clCreateBuffer context
                      (bit-or CL/CL_MEM_READ_ONLY CL/CL_MEM_COPY_HOST_PTR)
                      (* Sizeof/cl_float n) srcb nil)
   (CL/clCreateBuffer context
                      CL/CL_MEM_READ_WRITE
                      (* Sizeof/cl_float n) nil nil)])

(def program (CL/clCreateProgramWithSource context 1
                                           (let [a (make-array String 1)]
                                             (aset a 0 program-source)
                                             a)
                                           nil nil))

(CL/clBuildProgram program 0 nil nil nil nil)

(def kernel (CL/clCreateKernel program "sampleKernel" nil))

(CL/clSetKernelArg kernel 0 Sizeof/cl_mem (Pointer/to (mem-objects 0)))
(CL/clSetKernelArg kernel 1 Sizeof/cl_mem (Pointer/to (mem-objects 1)))
(CL/clSetKernelArg kernel 2 Sizeof/cl_mem (Pointer/to (mem-objects 2)))


(def global-work-size (long-array [n]))
(def local-work-size (long-array [1]))

(CL/clEnqueueNDRangeKernel command-queue kernel 1 nil global-work-size
                           local-work-size 0 nil nil)

(CL/clEnqueueReadBuffer command-queue (mem-objects 2) CL/CL_TRUE 0
                        (* n Sizeof/cl_float) srcc 0 nil nil)





;;(map #(CL/clReleaseMemObject (mem-objects %)) [0 1 2])
;;(CL/clReleaseKernel kernel)
;;(CL/clReleaseProgram program)
;;(CL/clReleaseCommandQueue command-queue)
;;(CL/clReleaseContext context)



;;XSdoes not work - src-array-c is stil full of zeroes!
