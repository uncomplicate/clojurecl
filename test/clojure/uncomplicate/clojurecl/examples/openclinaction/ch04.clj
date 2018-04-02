;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.examples.openclinaction.ch04
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.core.async :refer [chan <!!]]
            [uncomplicate.commons
             [core :refer [with-release]]
             [utils :refer [direct-buffer]]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info endian-little]]]))

(let [notifications (chan)
      follow (register notifications)]

  (with-release [dev (first (devices (first (platforms))))
                 ctx (context [dev])
                 cqueue (command-queue ctx dev)]

    (facts
     "Section 4.1, Page 69."
     (let [host-msg (direct-buffer 16)
           work-sizes (work-size [1])
           program-source
           (slurp (io/resource "examples/openclinaction/ch04/hello-kernel.cl"))]
       (with-release [cl-msg (cl-buffer ctx 16 :write-only)
                      prog (build-program! (program-with-source ctx [program-source]))
                      hello-kernel (kernel prog "hello_kernel")
                      read-complete (event)]

         (set-args! hello-kernel cl-msg) => hello-kernel
         (enq-nd! cqueue hello-kernel work-sizes) => cqueue
         (enq-read! cqueue cl-msg host-msg read-complete) => cqueue
         (follow read-complete host-msg) => notifications
         (let [data ^java.nio.ByteBuffer (:data (<!! notifications))
               res ^bytes (make-array Byte/TYPE 16)]
           (dotimes [i 16] (aset res i (.get data i)))
           (apply str (map (comp char) res)))
         => "Hello kernel!!!\0")))

    (facts
     "Section 4.2, Page 72."
     (let [host-a (float-array [10])
           host-b (float-array [2])
           host-out (float-array 1)
           work-sizes (work-size [1])
           program-source
           (slurp (io/resource "examples/openclinaction/ch04/double-test.cl"))]
       (with-release [cl-a (cl-buffer ctx (* 2 Float/BYTES) :read-only)
                      cl-b (cl-buffer ctx (* 2 Float/BYTES) :read-only)
                      cl-out (cl-buffer ctx (* 2 Float/BYTES) :write-only)
                      prog (build-program! (program-with-source ctx [program-source])
                                           (if (contains? (info dev :extensions)
                                                          "cl_khr_fp64")
                                             "-DFP_64"
                                             "")
                                           notifications)
                      double-test (kernel prog "double_test")]

         (set-args! double-test cl-a cl-b cl-out) => double-test
         (enq-write! cqueue cl-a host-a) => cqueue
         (enq-write! cqueue cl-b host-b) => cqueue
         (enq-nd! cqueue double-test work-sizes) => cqueue
         (enq-read! cqueue cl-out host-out) => cqueue
         (seq host-out) => (map / host-a host-b))))

    (facts
     "Section 4.3, Page 77."
     (println "Single FP Config: " (info dev :single-fp-config)))

    (facts
     "Section 4.4.1, Page 79."
     (println "Preferred vector widths: "
              (select-keys (info dev) [:preferred-vector-width-char
                                       :preferred-vector-width-short
                                       :preferred-vector-width-int
                                       :preferred-vector-width-long
                                       :preferred-vector-width-float
                                       :preferred-vector-width-double
                                       :preferred-vector-width-long])))

    (facts
     "Section 4.4.4, Page 85."
     (let [host-data (byte-array 16)
           work-sizes (work-size [1])
           program-source
           (slurp (io/resource "examples/openclinaction/ch04/vector-bytes.cl"))]
       (with-release [cl-data (cl-buffer ctx 16 :write-only)
                      prog (build-program! (program-with-source ctx [program-source]))
                      vector-bytes (kernel prog "vector_bytes")]

         (set-args! vector-bytes cl-data) => vector-bytes
         (enq-write! cqueue cl-data host-data) => cqueue
         (enq-nd! cqueue vector-bytes work-sizes) => cqueue
         (enq-read! cqueue cl-data host-data) => cqueue
         (seq host-data) => (if (endian-little dev)
                              [3 2 1 0 7 6 5 4 11 10 9 8 15 14 13 12]
                              (range 16)))))))
