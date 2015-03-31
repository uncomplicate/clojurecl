(ns uncomplicate.clojurecl.examples.openclinaction.ch07
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer :all]])
  (:import [org.jocl CL cl_event Pointer Sizeof]
           [java.nio ByteBuffer]))

(def program-source (slurp "test/opencl/examples/openclinaction/profile-read.cl"))
(def bytesize (Math/pow 2 20))
(def num-iterations 1)
(def global-work-size (long-array [1]))
(def local-work-size (long-array [1]))

(with-platform (first (platforms))
  (with-release [devs (devices)
                 dev (first devs)]
    (with-context (context devs)
      (let [prof-event (event)
            data-host (float-array (/ (long bytesize) Float/BYTES))]
        (with-release [cqueue (command-queue dev CL/CL_QUEUE_PROFILING_ENABLE)
                       data-buffer (cl-buffer bytesize CL/CL_MEM_WRITE_ONLY)
                       prog (build-program! (program-with-source [program-source]))
                       kern (kernels prog "profile_read")]

          (facts

           (set-arg! kern 0 data-buffer) => kern

           (set-arg! kern 1 (int-array [(/ (long bytesize) 16)]))

           (dotimes [n num-iterations]
             (-> cqueue
                 (enqueue-nd-range kern 1 nil global-work-size
                                   local-work-size 0 nil nil)
                 (enqueue-read data-buffer data-host CL/CL_TRUE 0 nil prof-event))))

          (durations (profiling-info prof-event))

           (build-info prog dev)




          )))))
