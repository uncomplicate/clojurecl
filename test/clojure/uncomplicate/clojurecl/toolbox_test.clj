;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.toolbox-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.commons.core :refer [release with-release]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [legacy :refer [command-queue-1]]
             [info :refer :all]
             [toolbox :refer :all]]
            [vertigo.bytes :refer [direct-buffer]])
  (:import java.nio.ByteBuffer))

(let [cnt-m 311
      cnt-n 9011
      cnt (* cnt-m cnt-n)
      program-source [(slurp "src/opencl/uncomplicate/clojurecl/kernels/reduction.cl")
                      (slurp "test/opencl/toolbox_test.cl")]]

  (with-release [dev (first (devices (first (remove legacy? (platforms)))))
                 ctx (context [dev])
                 queue (command-queue ctx dev)
                 wgs (max-work-group-size dev)
                 program (build-program! (program-with-source ctx program-source)
                                         (format "-cl-std=CL2.0 -DREAL=float -DACCUMULATOR=double -DWGS=%d" wgs)
                                         nil)
                 data (let [d (direct-buffer (* cnt Float/BYTES))]
                        (dotimes [n cnt]
                          (.putFloat ^ByteBuffer d (* n Float/BYTES) (float n)))
                        d)
                 cl-data (cl-buffer ctx (* cnt Float/BYTES) :read-only)]

    (enq-write! queue cl-data data)

    (let [acc-size (* Double/BYTES (max 1 (count-work-groups (max-work-group-size dev) cnt)))]
      (with-release [sum-reduction-kernel (kernel program "sum_reduction")
                     sum-reduce-kernel (kernel program "sum_reduce")
                     cl-acc (cl-buffer ctx acc-size :read-write)]

        (facts
         "Test 1D reduction."
         (set-arg! sum-reduction-kernel 0 cl-acc)
         (set-args! sum-reduce-kernel cl-acc cl-data)
         (enq-reduce! queue sum-reduce-kernel sum-reduction-kernel cnt wgs)
         (enq-read-double queue cl-acc) => 3926780329410.0)))

    (let [wgs-m 4
          wgs-n 32
          acc-size (* Double/BYTES (max 1 (* cnt-m (count-work-groups wgs-n cnt-n))))
          res (double-array cnt-m)]
      (with-release [sum-reduction-horizontal (kernel program "sum_reduction_horizontal")
                     sum-reduce-horizontal (kernel program "sum_reduce_horizontal")
                     cl-acc (cl-buffer ctx acc-size :read-write)]

        (facts
         (set-arg! sum-reduction-horizontal 0 cl-acc)
         (set-args! sum-reduce-horizontal cl-acc cl-data)
         (enq-reduce! queue sum-reduce-horizontal sum-reduction-horizontal cnt-m cnt-n wgs-m wgs-n)
         (enq-read! queue cl-acc res)
         (apply + (seq res)) => (roughly 3.92678032941E12))))

    (let [wgs-m 32
          wgs-n 4
          acc-size (* Double/BYTES (max 1 (* cnt-n (count-work-groups wgs-m cnt-m))))
          res (double-array cnt-n)]
      (with-release [sum-reduction-vertical (kernel program "sum_reduction_vertical")
                     sum-reduce-vertical (kernel program "sum_reduce_vertical")
                     cl-acc (cl-buffer ctx acc-size :read-write)]

        (facts
         (set-arg! sum-reduction-vertical 0 cl-acc)
         (set-args! sum-reduce-vertical cl-acc cl-data)
         (enq-reduce! queue sum-reduce-vertical sum-reduction-vertical cnt-n cnt-m wgs-n wgs-m)
         (enq-read! queue cl-acc res)
         (apply + (seq res)) => (roughly 3.92678032941E12))))))
