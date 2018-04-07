;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.examples.openclinaction.ch10
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.core.async :refer [chan <!!]]
            [uncomplicate.commons
             [core :refer [with-release info]]
             [utils :refer [direct-buffer]]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [durations profiling-info]]]))

(set! *unchecked-math* true)

(with-release [dev (nth  (sort-by-cl-version (devices (first (platforms)))) 0)
               ctx (context [dev])
               cqueue (command-queue ctx dev :profiling)]

  (facts
   "Listing on page 225."
   (let [program-source
         (slurp (io/resource "examples/openclinaction/ch10/reduction.cl"))
         num-items (Math/pow 2 20)
         bytesize (* num-items Float/BYTES)
         workgroup-size 256
         notifications (chan)
         follow (register notifications)
         data (let [d (direct-buffer bytesize)]
                (dotimes [n num-items]
                  (.putFloat ^java.nio.ByteBuffer d (* n Float/BYTES) 1.0))
                d)
         cl-partial-sums (* workgroup-size Float/BYTES)
         partial-output (float-array (/ bytesize workgroup-size))
         output (float-array 1)]
     (with-release [cl-data (cl-buffer ctx bytesize :read-only)
                    cl-output (cl-buffer ctx Float/BYTES :write-only)
                    cl-partial-output (cl-buffer ctx (/ bytesize workgroup-size)
                                                 :read-write)
                    prog (build-program! (program-with-source ctx [program-source]))
                    naive-reduction (kernel prog "naive_reduction")
                    reduction-scalar (kernel prog "reduction_scalar")
                    reduction-vector (kernel prog "reduction_vector")
                    profile-event (event)
                    profile-event1 (event)
                    profile-event2 (event)]
       (facts
        ;; ============ Naive reduction ======================================
        (set-args! naive-reduction cl-data cl-output) => naive-reduction
        (enq-write! cqueue cl-data data) => cqueue
        (enq-nd! cqueue naive-reduction (work-size [1]) nil profile-event)
        => cqueue
        (follow profile-event) => notifications
        (enq-read! cqueue cl-output output) => cqueue
        (finish! cqueue) => cqueue
        (println "Naive reduction time:"
                 (-> (<!! notifications) :event profiling-info durations :end))
        (aget output 0) => num-items
        ;; ============= Scalar reduction ====================================
        (set-args! reduction-scalar cl-data cl-partial-sums cl-partial-output)
        => reduction-scalar
        (enq-nd! cqueue reduction-scalar
                 (work-size [num-items] [workgroup-size])
                 nil profile-event)
        (follow profile-event)
        (enq-read! cqueue cl-partial-output partial-output)
        (finish! cqueue)
        (println "Scalar reduction time:"
                 (-> (<!! notifications) :event profiling-info durations :end))
        (long (first partial-output)) => workgroup-size
        ;; =============== Vector reduction ==================================
        (set-args! reduction-vector cl-data cl-partial-sums cl-partial-output)
        => reduction-vector
        (enq-nd! cqueue reduction-vector
                 (work-size [(/ num-items 4)] [workgroup-size])
                 nil profile-event1)
        (follow profile-event1)
        (set-args! reduction-vector cl-partial-output cl-partial-sums cl-partial-output)
        => reduction-vector
        (enq-nd! cqueue reduction-vector
                 (work-size [(/ num-items 4 workgroup-size 4)] [workgroup-size])
                 nil profile-event2)
        (follow profile-event2)
        (enq-read! cqueue cl-partial-output partial-output)
        (finish! cqueue)
        (println "Vector reduction time:"
                 (-> (<!! notifications) :event profiling-info durations :end)
                 (-> (<!! notifications) :event profiling-info durations :end))
        (first partial-output) => num-items)))))
