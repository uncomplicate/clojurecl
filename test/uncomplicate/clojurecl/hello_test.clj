(ns uncomplicate.clojurecl.hello-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl.core :refer :all])
  (:import [org.jocl  Sizeof Pointer CL]
           [java.nio ByteBuffer ByteOrder]))

(def n 100)
(def bytesize (* (long n) Sizeof/cl_float))
(def src-array-a (float-array (range n)))
(def src-array-b (float-array (range n)))
(def dest-array  (float-array n))

(def program-source "__kernel void sampleKernel(__global const float *a, __global const float *b, __global float *c) { int gid = get_global_id(0); c[gid] = a[gid] + b[gid] + 1.0;}")

(with-platform (first (platforms))

  (with-cls [devs (devices)
             dev (first devs)]

    (with-context (context devs)
      (with-cls [cqueue (command-queue dev 0)
                 mem-objects [(cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                              (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                              (cl-buffer bytesize CL/CL_MEM_WRITE_ONLY)]
                 #_[(bond src-array-a
                                       (bit-or CL/CL_MEM_READ_ONLY CL/CL_MEM_COPY_HOST_PTR))
                              (bond src-array-b
                                      (bit-or CL/CL_MEM_READ_ONLY CL/CL_MEM_COPY_HOST_PTR))
                              (bond dest-array CL/CL_MEM_WRITE_ONLY )]
                 prog (build-program! (program-with-source  [program-source]))
                 k (kernels prog "sampleKernel")
                 mem-object-a (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                 mem-object-b (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                 mem-object-dest (cl-buffer bytesize CL/CL_MEM_WRITE_ONLY)]

        (apply set-args! k mem-objects)

        (enqueue-write cqueue (mem-objects 0) src-array-a)
        (enqueue-write cqueue (mem-objects 1) src-array-b)

        (let [global-work-size (long-array [n])
              local-work-size (long-array [1])]

          (enqueue-nd-range cqueue k 1 nil global-work-size
                            local-work-size 0 nil nil))

        (enqueue-read cqueue (mem-objects 2) dest-array)

        (println "Dest array: " (seq dest-array))

        (println "==================== MAPPING =====================")
        (comment
          (def src-buffer-a (enqueue-map-buffer cqueue mem-object-a CL/CL_TRUE
                                                CL/CL_MAP_WRITE 0
                                                (* n Sizeof/cl_float) 0 nil nil))
          (.order src-buffer-a ByteOrder/LITTLE_ENDIAN)
          (.putFloat src-buffer-a 0 46)
          (enqueue-unmap-mem-object cqueue mem-object-a src-buffer-a 0 nil nil)

          (def src-buffer-b (enqueue-map-buffer cqueue mem-object-b CL/CL_TRUE
                                                CL/CL_MAP_WRITE 0
                                                (* n Sizeof/cl_float) 0 nil nil))
          (.order src-buffer-b ByteOrder/LITTLE_ENDIAN)
          (.putFloat src-buffer-b 0 55)
          (enqueue-unmap-mem-object cqueue mem-object-b src-buffer-b 0 nil nil)

          (set-arg! k 0 mem-object-a)
          (set-arg! k 1 mem-object-b)
          (set-arg! k 2 mem-object-dest)

          (enqueue-nd-range cqueue k 1 nil global-work-size
                            local-work-size 0 nil nil)

          (def dest-buffer (enqueue-map-buffer cqueue mem-object-dest CL/CL_TRUE
                                               CL/CL_MAP_READ 0
                                               (* n Sizeof/cl_float) 0 nil nil))
          (.order dest-buffer ByteOrder/LITTLE_ENDIAN)
          (println "Dest buffer: " (.getFloat dest-buffer 0))
          (enqueue-unmap-mem-object cqueue mem-object-dest dest-buffer 0 nil nil))



        )))





  )
