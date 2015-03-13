(ns uncomplicate.clojurecl.hello-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl.core :refer :all])
  (:import [org.jocl  Sizeof Pointer CL]
           [java.nio ByteBuffer ByteOrder]))

(def n 1)
(def src-array-a (float-array [3.333333333]))
(def src-array-b (float-array [3.333333333]))
(def dest-array  (float-array [3.333333333]))

(def srca (Pointer/to src-array-a))
(def srcb (Pointer/to src-array-b))
(def dest (Pointer/to dest-array))

(def program-source "__kernel void sampleKernel(__global const float *a, __global const float *b, __global float *c) { int gid = get_global_id(0); c[gid] = a[gid] + b[gid] + 1.0;}")

(def plat (first (platforms)))

(def devs (devices plat))
(def dev (first devs))

(def ctx (context devs))

(def cqueue (command-queue ctx dev 0))

(def mem-objects
  [(float-buffer ctx nil src-array-a
                 [CL/CL_MEM_READ_ONLY CL/CL_MEM_COPY_HOST_PTR])
   (float-buffer ctx nil src-array-b
                 [CL/CL_MEM_READ_ONLY CL/CL_MEM_COPY_HOST_PTR])
   (float-buffer ctx n
                 [CL/CL_MEM_READ_WRITE 0])])

(def prog (build-program! (program-with-source ctx [program-source])))

(def k (kernels prog "sampleKernel"))

(set-arg! k 0 (Pointer/to (mem-objects 0)))
(set-arg! k 1 (Pointer/to (mem-objects 1)))
(set-arg! k 2 (Pointer/to (mem-objects 2)))


(def global-work-size (long-array [n]))
(def local-work-size (long-array [1]))

(enqueue-nd-range cqueue k 1 nil global-work-size
                           local-work-size 0 nil nil)

(enqueue-read-buffer cqueue (mem-objects 2) CL/CL_TRUE 0
                     (* n Sizeof/cl_float) dest 0 nil nil)





;;(map #(CL/clReleaseMemObject (mem-objects %)) [0 1 2])
;;(CL/clReleaseKernel kernel)
;;(CL/clReleaseProgram program)
;;(CL/clReleaseCommandQueue command-queue)
;;(CL/clReleaseContext context)



;;XSdoes not work - src-array-c is stil full of zeroes!
