(ns uncomplicate.clojurecl.examples.jocl.hello-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl.core :refer :all])
  (:import [java.nio ByteBuffer]))


(def program-source (slurp "test/opencl/examples/jocl/hello-kernel.cl"))

(let [n 100
      bytesize (* (long n) Float/BYTES)
      src-array-a (float-array (range n))
      src-array-b (float-array (range n))
      dest-array (float-array n)
      work-sizes (work-size [n] [1])]
  (with-release [devs (devices (first (platforms)))
                 dev (first devs)
                 ctx (context devs)
                 cqueue (command-queue ctx dev nil)
                 mem-objects [(cl-buffer ctx bytesize :read-only)
                              (cl-buffer ctx bytesize :read-only)
                              (cl-buffer ctx bytesize :write-only)]
                 prog (build-program! (program-with-source ctx [program-source]))
                 sample-kernel (kernel prog "sampleKernel")]
    (facts

     (apply set-args! sample-kernel mem-objects) => sample-kernel

     (-> cqueue
         (enqueue-write (mem-objects 0) src-array-a)
         (enqueue-write (mem-objects 1) src-array-b)
         (enqueue-nd-range sample-kernel work-sizes)
         (enqueue-read (mem-objects 2) dest-array))
     => cqueue

     (finish cqueue)
     (seq dest-array) => (map float (range 0 200 2)))

    (with-release [mem-object-a (cl-buffer ctx bytesize :read-only)
                   mem-object-b (cl-buffer ctx bytesize :read-only)
                   mem-object-dest (cl-buffer ctx bytesize :read-only)]

      (let [src-buffer-a (enqueue-map-buffer cqueue mem-object-a :write)]
        (.putFloat ^ByteBuffer src-buffer-a 0 46)
        (.putFloat ^ByteBuffer src-buffer-a 4 100)
        (enqueue-unmap-mem-object cqueue mem-object-a src-buffer-a))

      (let [src-buffer-b (enqueue-map-buffer cqueue mem-object-b :write)]
        (.putFloat ^ByteBuffer src-buffer-b 0 56)
        (.putFloat ^ByteBuffer src-buffer-b 4 200)
        (enqueue-unmap-mem-object cqueue mem-object-b src-buffer-b))

      (facts
       (set-arg! sample-kernel 0 mem-object-a) => sample-kernel
       (set-arg! sample-kernel 1 mem-object-b) => sample-kernel
       (set-arg! sample-kernel 2 mem-object-dest) => sample-kernel

       (enqueue-nd-range cqueue sample-kernel work-sizes) => cqueue

       (let [dest-buffer (enqueue-map-buffer cqueue mem-object-dest :read)]
         (.getFloat ^ByteBuffer dest-buffer 0) => 102.0
         (.getFloat ^ByteBuffer dest-buffer 4) => 300.0
         (enqueue-unmap-mem-object cqueue mem-object-dest dest-buffer) => cqueue)))))
