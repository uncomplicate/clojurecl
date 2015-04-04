(ns uncomplicate.clojurecl.examples.openclinaction.ch07
  (:require [midje.sweet :refer :all]
            [clojure.core.async :refer :all]
            [uncomplicate.clojurecl [core :refer :all]
             [info :refer [info]]]
            [vertigo.bytes :refer [direct-buffer]])
  (:import [org.jocl CL cl_event Pointer Sizeof]
           [java.nio ByteBuffer]))

(def program-source (slurp "test/opencl/examples/openclinaction/profile-read.cl"))
(def bytesize (Math/pow 2 20))
(def num-iterations 1)
(def global-work-size (long-array [1]))
(def local-work-size (long-array [1]))

(def ch (chan))

(with-platform (first (platforms))
  (with-release [devs (devices)
                 dev (first devs)]
    (with-context (context devs)
      (let [data-host (direct-buffer bytesize)]
        (with-release [cqueue (command-queue dev :profiling)
                       dev-buffer (cl-buffer bytesize :write-only)
                       prog (build-program! (program-with-source [program-source]))
                       profile-read (kernels prog "profile_read")]

          (facts

           (set-args! profile-read dev-buffer (int-array [(/ (long bytesize) 16)]))
           => profile-read

           ;;(dotimes [n num-iterations]
           (follow ch (enqueue-nd-range cqueue profile-read
                                        global-work-size local-work-size))
           => ch

           (follow ch (enqueue-read cqueue dev-buffer data-host)) => ch

           (println (info (:event (<!! ch))) "\n" (info (:event (<!! ch))))))))))
