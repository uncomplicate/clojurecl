(ns uncomplicate.clojurecl.examples.openclinaction.ch05
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info]]]
            [vertigo
             [bytes :refer [direct-buffer byte-seq]]
             [structs :refer [wrap-byte-seq int8]]]))

(with-release [dev (first (devices (first (platforms))))
               ctx (context [dev])
               cqueue (command-queue ctx dev nil)]

  (facts
   "Listing 5.1, Page 96."
   (let [host-output (int-array 4)
         work-sizes (work-size [1])
         program-source
         (slurp "test/opencl/examples/openclinaction/ch05/op-test.cl")]
     (with-release [cl-output (cl-buffer ctx (* 4 Integer/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    op-test-kernel (kernel prog "op_test")]

       (set-args! op-test-kernel cl-output) => op-test-kernel
       (enq-nd! cqueue op-test-kernel work-sizes) => cqueue
       (enq-read! cqueue cl-output host-output) => cqueue
       (vec host-output) => [-1 0 0 4])))

  (facts
   "Listing 5.2, Page 100."
   (let [host-output (float-array 24)
         work-sizes (work-size [6 4] [3 2] [3 5])
         program-source
         (slurp "test/opencl/examples/openclinaction/ch05/id-check.cl")]
     (with-release [cl-output (cl-buffer ctx (* 24 Float/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    id-check-kernel (kernel prog "id_check")]

       (set-args! id-check-kernel cl-output) => id-check-kernel
       (enq-nd! cqueue id-check-kernel work-sizes) => cqueue
       (enq-read! cqueue cl-output host-output) => cqueue
       (seq host-output)
       => (just (roughly 35.0) (roughly 45.1) (roughly 55.2)
                (roughly 65.0) (roughly 75.1) (roughly 85.2)
                (roughly 36.01) (roughly 46.109997) (roughly 56.21)
                (roughly 66.01) (roughly 76.11) (roughly 86.21)
                (roughly 37.0) (roughly 47.1) (roughly 57.2)
                (roughly 67.0) (roughly 77.1) (roughly 87.2)
                (roughly 38.01) (roughly 48.109997) (roughly 58.21)
                (roughly 68.01) (roughly 78.11) (roughly 88.21)))))

  (facts
   "Listing 5.3, Page 104."
   (let [host-mod-input (float-array [317 23])
         host-mod-output (float-array 2)
         host-round-input (float-array [6.5 -3.5 3.5 6.5])
         host-round-output (float-array 20)
         work-sizes (work-size [1])
         program-source
         (slurp "test/opencl/examples/openclinaction/ch05/mod-round.cl")]
     (with-release [cl-mod-input (cl-buffer ctx (* 2 Float/BYTES) :read-only)
                    cl-mod-output (cl-buffer ctx (* 2 Float/BYTES) :write-only)
                    cl-round-input (cl-buffer ctx (* 20 Float/BYTES) :read-only)
                    cl-round-output (cl-buffer ctx (* 20 Float/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    mod-round-kernel (kernel prog "mod_round")]

       (set-args! mod-round-kernel cl-mod-input cl-mod-output
                  cl-round-input cl-round-output)
       => mod-round-kernel
       (enq-write! cqueue cl-mod-input host-mod-input) => cqueue
       (enq-write! cqueue cl-round-input host-round-input) => cqueue
       (enq-nd! cqueue mod-round-kernel work-sizes) => cqueue
       (enq-read! cqueue cl-mod-output host-mod-output) => cqueue
       (enq-read! cqueue cl-round-output host-round-output) => cqueue
       (seq host-mod-output) => '(18.0 -5.0)
       (vec host-round-output) => [6.0 -4.0 4.0 6.0
                                   7.0 -4.0 4.0 7.0
                                   7.0 -3.0 4.0 7.0
                                   6.0 -4.0 3.0 6.0
                                   6.0 -3.0 3.0 6.0])))

  (facts
   "Listing 5.4, Page 108."
   (let [rvals (float-array [2 1 3 4])
         angles (float-array [(* (double 3/8) Math/PI) (* (double 3/4) Math/PI)
                              (* (double 4/3) Math/PI) (* (double 11/6) Math/PI)])
         xcoords (float-array 4)
         ycoords (float-array 4)
         work-sizes (work-size [1])
         program-source
         (slurp "test/opencl/examples/openclinaction/ch05/polar-rect.cl")]
     (with-release [cl-rvals (cl-buffer ctx (* 4 Float/BYTES) :read-only)
                    cl-angles (cl-buffer ctx (* 4 Float/BYTES) :read-only)
                    cl-xcoords (cl-buffer ctx (* 4 Float/BYTES) :write-only)
                    cl-ycoords (cl-buffer ctx (* 4 Float/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    polar-rect-kernel (kernel prog "polar_rect")]

       (set-args! polar-rect-kernel cl-rvals cl-angles cl-xcoords cl-ycoords)
       => polar-rect-kernel
       (enq-write! cqueue cl-rvals rvals) => cqueue
       (enq-write! cqueue cl-angles angles) => cqueue
       (enq-nd! cqueue polar-rect-kernel work-sizes) => cqueue
       (enq-read! cqueue cl-xcoords xcoords) => cqueue
       (enq-read! cqueue cl-ycoords ycoords) => cqueue
       (seq xcoords) => (just (roughly 0.76536685) (roughly -0.70710677)
                              (roughly -1.4999998) (roughly 3.4641013))
       (seq ycoords) => (just (roughly 1.847759) (roughly 0.70710677)
                              (roughly -2.5980763) (roughly -2.0000007)))))

  (facts
   "Listing 5.5, Page 112."
   (let [output (int-array 2)
         work-sizes (work-size [1])
         program-source
         (slurp "test/opencl/examples/openclinaction/ch05/mad-test.cl")]
     (with-release [cl-output (cl-buffer ctx (* 2 Integer/BYTES) :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    mad-test-kernel (kernel prog "mad_test")]

       (set-args! mad-test-kernel cl-output) => mad-test-kernel
       (enq-nd! cqueue mad-test-kernel work-sizes) => cqueue
       (enq-read! cqueue cl-output output) => cqueue
       (vec output) => [-396694989 1118792])))

  (facts
   "Listing 5.6, Page 116."
   (let [s1 (float-array 4)
         s2 (byte-array 2)
         work-sizes (work-size [1])
         program-source
         (slurp "test/opencl/examples/openclinaction/ch05/select-test.cl")]
     (with-release [cl-s1 (cl-buffer ctx (* 4 Float/BYTES) :write-only)
                    cl-s2 (cl-buffer ctx 8 :write-only)
                    prog (build-program! (program-with-source ctx [program-source]))
                    select-test-kernel (kernel prog "select_test")]

       (set-args! select-test-kernel cl-s1 cl-s2) => select-test-kernel
       (enq-nd! cqueue select-test-kernel work-sizes) => cqueue
       (enq-read! cqueue cl-s1 s1) => cqueue
       (enq-read! cqueue cl-s2 s2) => cqueue
       (vec s1) => [1.25 0.5 1.75 1.0]
       (vec s2) => [2r00100111 2r00011011]))))
