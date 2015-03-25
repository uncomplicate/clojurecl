(ns uncomplicate.clojurecl.hello-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl.core :refer :all])
  (:import [org.jocl CL]
           [java.nio ByteBuffer]))

(def n 100)
(def bytesize (* (long n) Float/BYTES))
(def src-array-a (float-array (range n)))
(def src-array-b (float-array (range n)))
(def dest-array  (float-array n))
(def global-work-size (long-array [n]))
(def local-work-size (long-array [1]))
(def program-source (slurp "test/opencl/hello-kernel.cl"))

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
        (facts
         (apply set-args! k mem-objects) => k

         (-> cqueue
             (enqueue-write (mem-objects 0) src-array-a)
             (enqueue-write (mem-objects 1) src-array-b)
             (enqueue-nd-range k 1 nil global-work-size
                               local-work-size 0 nil nil)
             (enqueue-read (mem-objects 2) dest-array))
         => cqueue

         (seq dest-array) => (map float (range 0 200 2))))

      (with-release [cqueue (command-queue dev 0)
                     prog (build-program! (program-with-source  [program-source]))
                     k (kernels prog "sampleKernel")
                     mem-object-a (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                     mem-object-b (cl-buffer bytesize CL/CL_MEM_READ_ONLY)
                     mem-object-dest (cl-buffer bytesize CL/CL_MEM_WRITE_ONLY)]

        (let [src-buffer-a (enqueue-map-buffer cqueue mem-object-a CL/CL_MAP_WRITE)]
          (.putFloat src-buffer-a 0 46)
          (enqueue-unmap-mem-object cqueue mem-object-a src-buffer-a))

        (let [src-buffer-b (enqueue-map-buffer cqueue mem-object-b CL/CL_MAP_WRITE)]
          (.putFloat src-buffer-b 0 56)
          (enqueue-unmap-mem-object cqueue mem-object-b src-buffer-b))

        (facts
         (set-arg! k 0 mem-object-a) => k
         (set-arg! k 1 mem-object-b) => k
         (set-arg! k 2 mem-object-dest) => k

         (enqueue-nd-range cqueue k 1 nil global-work-size
                           local-work-size 0 nil nil)
         => cqueue

         (let [dest-buffer (enqueue-map-buffer cqueue mem-object-dest CL/CL_MAP_READ)]
           (.getFloat dest-buffer 0) => 102.0
           (enqueue-unmap-mem-object cqueue mem-object-dest dest-buffer) => cqueue)))
      )))
