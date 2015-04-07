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
(def ch (chan))
(def host-msg (direct-buffer 16))
(def global-work-size (long-array [1]))
(def local-work-size (long-array [1]))
(def p (first (platforms)))

(with-release [dev (first (devices p))
               ctx (context [dev])
               cqueue (command-queue ctx dev 0)
               cl-msg (cl-buffer ctx 16 :write-only)
               prog (build-program! (program-with-source ctx [program-source]))
               hello-kernel (kernel prog "hello_kernel")
               read-complete (event)]

  (facts
   (set-args! hello-kernel cl-msg) => hello-kernel

   (enqueue-nd-range cqueue hello-kernel global-work-size local-work-size)
   => cqueue

   (enqueue-read cqueue cl-msg host-msg read-complete) => cqueue
   (follow ch read-complete host-msg) => ch

   (apply str (map char (wrap-byte-seq int8 (byte-seq (:data (<!! ch))))))
   => "Hello kernel!!!\0"))
