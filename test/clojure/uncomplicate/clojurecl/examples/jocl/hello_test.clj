;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.examples.jocl.hello-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.commons.core :refer [with-release]]
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
                 cqueue (command-queue ctx dev)
                 mem-objects [(cl-buffer ctx bytesize :read-only)
                              (cl-buffer ctx bytesize :read-only)
                              (cl-buffer ctx bytesize :write-only)]
                 prog (build-program! (program-with-source ctx [program-source]))
                 sample-kernel (kernel prog "sampleKernel")]
    (facts

     (apply set-args! sample-kernel mem-objects) => sample-kernel

     (-> cqueue
         (enq-write! (mem-objects 0) src-array-a)
         (enq-write! (mem-objects 1) src-array-b)
         (enq-kernel! sample-kernel work-sizes)
         (enq-read! (mem-objects 2) dest-array))
     => cqueue

     (finish! cqueue)
     (seq dest-array) => (map float (range 0 200 2)))

    (with-release [mem-object-a (cl-buffer ctx bytesize :read-only)
                   mem-object-b (cl-buffer ctx bytesize :read-only)
                   mem-object-dest (cl-buffer ctx bytesize :read-only)]

      (let [src-buffer-a (enq-map-buffer! cqueue mem-object-a :write)]
        (.putFloat ^ByteBuffer src-buffer-a 0 46)
        (.putFloat ^ByteBuffer src-buffer-a 4 100)
        (enq-unmap! cqueue mem-object-a src-buffer-a))

      (let [src-buffer-b (enq-map-buffer! cqueue mem-object-b :write)]
        (.putFloat ^ByteBuffer src-buffer-b 0 56)
        (.putFloat ^ByteBuffer src-buffer-b 4 200)
        (enq-unmap! cqueue mem-object-b src-buffer-b))

      (facts
       (set-arg! sample-kernel 0 mem-object-a) => sample-kernel
       (set-arg! sample-kernel 1 mem-object-b) => sample-kernel
       (set-arg! sample-kernel 2 mem-object-dest) => sample-kernel

       (enq-kernel! cqueue sample-kernel work-sizes) => cqueue

       (let [dest-buffer (enq-map-buffer! cqueue mem-object-dest :read)]
         (.getFloat ^ByteBuffer dest-buffer 0) => 102.0
         (.getFloat ^ByteBuffer dest-buffer 4) => 300.0
         (enq-unmap! cqueue mem-object-dest dest-buffer) => cqueue)))))
