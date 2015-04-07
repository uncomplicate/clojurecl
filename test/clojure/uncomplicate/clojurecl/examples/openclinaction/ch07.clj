(ns uncomplicate.clojurecl.examples.openclinaction.ch07
  (:require [midje.sweet :refer :all]
            [clojure.core.async :refer :all]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [profiling-info durations]]]
            [vertigo.bytes :refer [direct-buffer]]))

(let [program-source
      (slurp "test/opencl/examples/openclinaction/ch07/profile-read.cl")
      bytesize (Math/pow 2 20)
      notifications (chan)
      data-host (direct-buffer bytesize)
      ia (int-array [(/ (long bytesize) 16)])
      num-iterations 1
      work-sizes (work-size [1])
      platform (first (platforms))]
  (with-release [dev (first (devices platform))
                 ctx (context [dev])
                 cqueue (command-queue ctx dev :profiling)
                 dev-buffer (cl-buffer ctx bytesize :write-only)
                 prog (build-program! (program-with-source ctx [program-source]))
                 profile-read (kernel prog "profile_read")
                 profile-event (event)]
    (facts

     (set-args! profile-read dev-buffer ia) => profile-read

     ;;(dotimes [n num-iterations]
     (enqueue-nd-range cqueue profile-read work-sizes) => cqueue
     (enqueue-read cqueue dev-buffer data-host profile-event) => cqueue
     (follow notifications profile-event) => notifications
     (durations (profiling-info (:event (<!! notifications)))) => profile-event)))
