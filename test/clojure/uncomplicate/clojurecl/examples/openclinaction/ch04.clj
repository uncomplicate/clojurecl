(ns uncomplicate.clojurecl.examples.openclinaction.ch04
  (:require [midje.sweet :refer :all]
            [clojure.core.async :refer [chan <!!]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info]]]
            [vertigo
             [bytes :refer [direct-buffer byte-seq]]
             [structs :refer [wrap-byte-seq int8]]]))

(def program-source
  (slurp "test/opencl/examples/openclinaction/ch04/hello-kernel.cl"))
(def notifications (chan))
(def host-msg (direct-buffer 16))
(def work-sizes (work-size [1]))
(def p (first (platforms)))

(with-release [dev (first (devices p))
               ctx (context [dev])
               cqueue (command-queue ctx dev nil)
               cl-msg (cl-buffer ctx 16 :write-only)
               prog (build-program! (program-with-source ctx [program-source]))
               hello-kernel (kernel prog "hello_kernel")
               read-complete (event)]

  (facts
   (set-args! hello-kernel cl-msg) => hello-kernel
   (enqueue-nd-range cqueue hello-kernel work-sizes) => cqueue
   (enqueue-read cqueue cl-msg host-msg read-complete) => cqueue
   (follow notifications read-complete host-msg) => notifications))

(facts
 (apply str (map char (wrap-byte-seq int8 (byte-seq (:data (<!! notifications))))))
 => "Hello kernel!!!\0")
