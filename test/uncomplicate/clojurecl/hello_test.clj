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

  (with-release [devs (devices)
                 dev (first devs)]

    (with-context (context devs)

      (with-release [cqueue (command-queue dev 0)
                     mem-objects [(cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                                  (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                                  (cl-buffer bytesize CL/CL_MEM_WRITE_ONLY)]
                     prog (build-program! (program-with-source  [program-source]))
                     k (kernels prog "sampleKernel")]

        (println "==================== READ/WRITE  =====================")

        (apply set-args! k mem-objects)

        (enqueue-write cqueue (mem-objects 0) src-array-a)
        (enqueue-write cqueue (mem-objects 1) src-array-b)

        (let [global-work-size (long-array [n])
              local-work-size (long-array [1])]

          (enqueue-nd-range cqueue k 1 nil global-work-size
                            local-work-size 0 nil nil))

        (enqueue-read cqueue (mem-objects 2) dest-array)

        (println "Dest array: " (seq dest-array)))

      (with-release [cqueue (command-queue dev 0)
                     prog (build-program! (program-with-source  [program-source]))
                     k (kernels prog "sampleKernel")
                     mem-object-a (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                     mem-object-b (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                     mem-object-dest (cl-buffer bytesize CL/CL_MEM_WRITE_ONLY)]

        (println "==================== MAPPING =====================")

        (let [src-buffer-a (enqueue-map-buffer cqueue mem-object-a CL/CL_MAP_WRITE)]
          (.putFloat src-buffer-a 0 46)
          (enqueue-unmap-mem-object cqueue mem-object-a src-buffer-a))

        (let [src-buffer-b (enqueue-map-buffer cqueue mem-object-b CL/CL_MAP_WRITE)]
          (.putFloat src-buffer-b 0 56)
          (enqueue-unmap-mem-object cqueue mem-object-b src-buffer-b))


        (set-arg! k 0 mem-object-a)
        (set-arg! k 1 mem-object-b)
        (set-arg! k 2 mem-object-dest)

        (let [global-work-size (long-array [n])
              local-work-size (long-array [1])]

          (enqueue-nd-range cqueue k 1 nil global-work-size
                            local-work-size 0 nil nil))

        (let [dest-buffer (enqueue-map-buffer cqueue mem-object-dest CL/CL_MAP_READ)]
          (.getFloat dest-buffer 0)
          (println "Dest buffer: " (.getFloat dest-buffer 0))
          (enqueue-unmap-mem-object cqueue mem-object-dest dest-buffer)))
      )))
