;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.examples.openclinaction.ch11
  (:require [midje.sweet :refer :all]
            [clojure.java.io :as io]
            [clojure.core.async :refer [chan <!!]]
            [uncomplicate.commons.core :refer [with-release]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info durations profiling-info opencl-c-version]]]))

(set! *unchecked-math* true)

(with-release [dev (first (filter #(= 2.0 (:version (opencl-c-version %)))
                                  (devices (first (platforms)))))
               ctx (context [dev])
               cqueue (command-queue ctx dev :profiling)]

  (facts
   "Chapter 11, Listing 11.1, Page 243."
   (let [program-source
         (slurp (io/resource "examples/openclinaction/ch11/string-search.cl"))
         kafka (byte-array
                (map byte
                     (slurp (io/resource "examples/openclinaction/ch11/kafka.txt"))))
         text-size (alength kafka)
         local-size (info dev :max-work-group-size)
         global-size (* local-size (info dev :max-compute-units))
         work-size (work-size [global-size] [local-size])
         pattern (byte-array (map byte "thatwithhavefrom"))
         chars-per-item (int-array [(inc (/ text-size global-size))])
         result (int-array 4)]
     (with-release [cl-text (cl-buffer ctx text-size :read-only)
                    cl-result (cl-buffer ctx (* 4 Integer/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source])
                                         "-cl-std=CL2.0" nil)
                    string-search (kernel prog "string_search")]
       (facts
        ;; ============ Naive reduction ======================================
        (set-args! string-search pattern cl-text chars-per-item
                   (* 4 Integer/BYTES) cl-result)
        => string-search
        (enq-write! cqueue cl-text kafka) => cqueue
        (enq-write! cqueue cl-result result) => cqueue
        (enq-nd! cqueue string-search work-size) => cqueue
        (enq-read! cqueue cl-result result) => cqueue
        (finish! cqueue) => cqueue
        (vec result) => [330 237 110 116])))))
