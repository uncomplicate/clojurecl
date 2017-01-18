;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.core-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.commons.core :refer [release with-release]]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info reference-count mem-base-addr-align opencl-c-version]]]
            [clojure.core.async :refer [go >! <! <!! chan]]
            [vertigo
             [bytes :refer [direct-buffer byte-seq]]
             [structs :refer [wrap-byte-seq float32]]])
  (:import [uncomplicate.clojurecl.core CLBuffer SVMBuffer]
           [org.jocl CL Pointer cl_device_id cl_context_properties cl_mem]
           [clojure.lang ExceptionInfo]
           [java.nio ByteBuffer]))

;; ================== Platform tests ========================
(facts
 "Platform tests."

 (< 0 (num-platforms)) => true

 (count (platforms)) => (num-platforms)

 (let [p (first (platforms))]
   (with-platform p
     (platform-info)) => (info p)))

;; ================== Device tests ========================
(facts
 "num-devices tests."

 (let [p (first (remove legacy? (platforms)))]
   (num-devices* p CL/CL_DEVICE_TYPE_ALL) => (num-devices p :all)
   ;;(num-devices* nil CL/CL_DEVICE_TYPE_ALL) => (throws ExceptionInfo) ;;Some platforms just use first p

   (< 0 (num-devices p :all)) => true
   (< 0 (num-devices p :cpu)) => true
   (< 0 (num-devices p :gpu)) => true

   (num-devices p :cpu :gpu :accelerator :custom) => (num-devices p :all)

   (+ (num-devices p :cpu) (num-devices p :gpu)
      (num-devices p :accelerator) (num-devices p :custom))
   => (num-devices p :all)

   (num-devices p) => (num-devices p :all)
   (with-platform p
     (num-devices :all) => (num-devices p :all)
     (num-devices) => (num-devices p :all))

   ;;(num-devices nil :all) => (throws ExceptionInfo);;Some platforms just use first p
   (num-devices p :unknown-device) => (throws NullPointerException)))

(facts
 "devices tests"

 (let [p (first (remove legacy? (platforms)))]
   (vec (devices* p CL/CL_DEVICE_TYPE_ALL)) => (devices p :all)
   ;;(devices* nil CL/CL_DEVICE_TYPE_ALL) => (throws ExceptionInfo);;Some platforms just use first p

   (count (devices p :all)) => (num-devices p :all)

   (devices p :gpu :cpu) => (concat (devices p :gpu) (devices p :cpu))
   (devices p :custom) => []

   (type (first (devices p :cpu))) => cl_device_id

   (with-platform p
     (devices :all) => (devices p :all)
     (devices :gpu) => (devices p :gpu)
     (devices) => (devices p :all))

   ;;(devices nil :all) => (throws ExceptionInfo);;Some platforms just use first p
   (devices p :unknown-device) => (throws NullPointerException)))

(facts
 "Root level devices resource management."

 (let [p (first (remove legacy? (platforms)))
       da (first (devices p))
       db (first (devices p))]
   (reference-count da) => 1
   (reference-count db) => 1
   (do (release da) (reference-count da)) => 1))

;; ================== Context tests ========================

(set! *warn-on-reflection* false)
(facts
 "CreateContextCallback tests"
 (let [ch (chan)]
   (->CreateContextCallback ch) =not=> nil
   (do (.function (->CreateContextCallback ch) "Some error"
                  (Pointer/to (int-array 1)) Integer/BYTES :some-data)
       (:errinfo (<!! ch)) => "Some error")))
(set! *warn-on-reflection* true)

(let [p (first (remove legacy? (platforms)))]
  (with-platform p
    (with-release [devs (devices p)
                   dev (first (filter #(<= 2.0 (:version (opencl-c-version %))) devs))]

      (facts
       "context-properties tests"
       (context-properties {:platform p}) =not=> nil)

      (facts
       "context tests"

       (let [adevs (devices* p CL/CL_DEVICE_TYPE_ALL)
             props (context-properties {:platform p})]

         (let [ctx (context* adevs nil nil nil)]
           (reference-count ctx) => 1
           (release ctx) => true)

         (let [ctx (context* adevs props nil nil)]
           (reference-count ctx) => 1
           (release ctx) => true)

         ;; TODO I am not sure how this CreateContextFunction mechanism work.
         ;; It is implemented, but I do not know how to raise an error that
         ;; shoud then reported through the channel. Test it later.)
         (let [ch (chan)
               ctx (context* adevs props ch :some-data)]
           (reference-count ctx) => 1
           (command-queue ctx nil) => (throws ExceptionInfo))

         (context* nil nil nil nil) => (throws NullPointerException)
         (context* (make-array cl_device_id 0) nil nil nil) => (throws ExceptionInfo)

         (let [ctx (context)]
           (reference-count ctx) => 1
           (release ctx) => true)

         (context nil) => (throws ExceptionInfo)

         (let [ctx (context [dev])]
           (reference-count ctx) => 1
           (release ctx) => true)

         (release (context devs)) => true
         (release (context devs {:platform p} (chan) :some-data)) => true))

      (facts
       "queue tests"
       (with-release [ctx (context devs)
                      cl-data (cl-buffer ctx Float/BYTES :read-write)]
         (let [queue (command-queue ctx dev)]
           (reference-count queue) => 1
           (info queue :context) => ctx
           (release queue) => true)

         (let [queue (command-queue* ctx dev 0)]
           (reference-count queue) => 1
           (info queue :context) => ctx
           (info queue :properties) => #{}
           (type (info queue :size)) => String
           (release queue) => true)

         (let [queue (command-queue* ctx dev 0 5)]
           (reference-count queue) => 1
           (info queue :context) => ctx
           (info queue :properties) => #{:queue-on-device :out-of-order-exec-mode}
           (info queue :size) => (info dev :queue-on-device-preferred-size)
           (release queue) => true)

         (with-context (context devs)
           (let [queue (command-queue dev)]
             (reference-count queue) => 1
             (info queue :context) => *context*
             (info queue :properties) => #{}
             (release queue) => true))

         (let [queue (command-queue ctx dev :queue-on-device
                                    :out-of-order-exec-mode :profiling)]
           (reference-count queue) => 1
           (info queue :context) => ctx
           (info queue :properties) => #{:profiling :out-of-order-exec-mode
                                         :queue-on-device}
           (release queue) => true)

         (let [queue (command-queue ctx dev 524288 :queue-on-device
                                    :out-of-order-exec-mode :profiling)]
           (reference-count queue) => 1
           (info queue :context) => ctx
           (info queue :properties) => #{:profiling :out-of-order-exec-mode
                                         :queue-on-device}
           (info queue :size) => 524288
           (release queue) => true)

         (command-queue ctx nil) => (throws ExceptionInfo)
         (command-queue nil dev) => (throws ExceptionInfo)
         (command-queue ctx dev :my-prop) => (throws NullPointerException))))))

(with-default

  (facts
   "cl-buffer and cl-sub-buffer reading/writing tests."
   (let [alignment (mem-base-addr-align
                    (first (filter #(<= 2.0 (:version (opencl-c-version %)))
                                   (devices (first (remove legacy? (platforms)))))))]
     (with-release [cl-buf (cl-buffer (* 4 alignment Float/BYTES))
                    cl-subbuf (cl-sub-buffer cl-buf (* alignment Float/BYTES)
                                             (* alignment Float/BYTES))]
       (type cl-subbuf) => CLBuffer
       (let [data-arr (float-array (range (* 4 alignment)))
             buf-arr (float-array (* 4 alignment))
             subbuf-arr (float-array alignment)]
         (enq-write! cl-buf data-arr)
         (enq-read! cl-buf buf-arr)
         (enq-read! cl-subbuf subbuf-arr)
         (vec buf-arr) => (vec data-arr)
         (vec subbuf-arr) => (map float (range alignment (* 2 alignment)))))))

  (facts
   "Event tests."
   (event) =not=> nil
   (host-event nil) => (throws ExceptionInfo)
   (host-event) =not=> nil

   (alength (events (host-event) (host-event))) => 2
   (alength ^objects (apply events (for [n (range 10)] (host-event))))
   => 10)

  (facts
   "EventCallback tests"
   (let [ch (chan)
         ev (host-event)]
     (->EventCallback ch) =not=> nil
     (do (.function (event-callback ch) ev CL/CL_QUEUED :my-data)
         (:event (<!! ch)) => ev))

   (with-release [cl-buf (cl-buffer Float/BYTES)]
     (let [ev (event)
           notifications (chan)
           follow (register notifications)]
       (enq-write! *command-queue* cl-buf (float-array 1) ev)
       (follow ev)
       (:event (<!! notifications)) => ev)))

  (let [src (slurp "test/opencl/core_test.cl")
        cnt 8
        data (float cnt)
        notifications (chan)
        follow (register notifications)]

    (facts
     "Program tests"
     (with-release [program (build-program! (program-with-source [src]))]
       program =not=> nil
       (:source (info program)) => src))

    (with-release [program (build-program!
                            (program-with-source
                             [src]) nil "-cl-std=CL2.0"
                             notifications :my-data)]
      (facts
       "Program build tests."
       program =not=> nil
       (info program :source) => src
       (:data (<!! notifications)) => :my-data)

      ;; TODO Some procedures might crash JVM if called on
      ;; unprepared objects (kernels of unbuilt program).
      ;; Solve and test such cases systematically in info.clj
      ;; in a similar way as kernels check for program binaries first.
      (facts
       (info (program-with-source [src])) =not=> nil)

      (with-release [dumb-kernel (kernel program "dumb_kernel")
                     all-kernels (kernel program)
                     cl-data (cl-buffer (* cnt Float/BYTES))
                     cl-copy-data (cl-buffer (size cl-data))]
        (facts
         "Kernel tests"
         (num-kernels program) => 1

         (info dumb-kernel :name) => (info (first all-kernels) :name)
         (kernel nil) => (throws ExceptionInfo)

         (set-arg! dumb-kernel 0 nil) => (throws IllegalArgumentException)

         (set-arg! dumb-kernel 0 cl-data) => dumb-kernel
         (set-arg! dumb-kernel 1 Integer/BYTES) => dumb-kernel
         (set-arg! dumb-kernel 2 (int-array [42])) => dumb-kernel

         (set-args! dumb-kernel cl-data Integer/BYTES) => dumb-kernel)

        (let [wsize (work-size [cnt])
              data (float-array (range cnt))
              copy-data (float-array cnt)]
          (facts
           "enq-nd!, enq-read!, enq-write!, enq-copy! enq-fill tests"
           (enq-write! cl-data data) => *command-queue*
           (enq-nd! dumb-kernel wsize) => *command-queue*
           (enq-read! cl-data data) => *command-queue*
           (vec data) => [84.0 86.0 88.0 90.0 92.0 94.0 96.0 98.0]
           (enq-copy! cl-data cl-copy-data) => *command-queue*
           (enq-read! cl-copy-data copy-data) => *command-queue*
           (vec copy-data) => (vec data)
           (enq-fill! cl-data (float-array [1 2 3 4])) => *command-queue*
           (enq-read! cl-data data) => *command-queue*
           (vec data) => [1.0 2.0 3.0 4.0 1.0 2.0 3.0 4.0]))))))

(let [cnt 8
      src (slurp "test/opencl/core_test.cl")
      data (let [d (direct-buffer (* 8 Float/BYTES))]
             (dotimes [n cnt]
               (.putFloat ^ByteBuffer d (* n Float/BYTES) n))
             d)
      notifications (chan)
      follow (register notifications)
      ev-nd1 (event)
      ev-nd2 (event)
      ev-read (event)
      ev-write (event)
      wsize (work-size [8])]

  (with-release [devs (devices (first (remove legacy? (platforms))))
                 ctx (context devs)
                 queue1 (command-queue ctx (first devs))
                 queue2 (command-queue ctx (second devs))
                 cl-data (cl-buffer ctx (* cnt Float/BYTES) :read-write)
                 program (build-program! (program-with-source ctx [src]))
                 dumb-kernel (kernel program "dumb_kernel")]

    (facts
     "wait-events tests"
     (set-args! dumb-kernel cl-data Integer/BYTES (int-array [42]))
     (enq-write! queue1 cl-data data ev-write)
     (enq-nd! queue1 dumb-kernel wsize (events ev-write) ev-nd1)
     (enq-nd! queue2 dumb-kernel wsize (events ev-write ev-nd1) ev-nd2)
     (enq-read! queue1 cl-data data (events ev-nd2) ev-read)
     (follow ev-read)

     (:event (<!! notifications)) => ev-read

     (vec  (wrap-byte-seq float32 (byte-seq data)))
     => [168.0 171.0 174.0 177.0 180.0 183.0 186.0 189.0]

     (let [mapped-read (enq-map-buffer! queue1 cl-data :read)
           mapped-write (enq-map-buffer! queue1 cl-data :write)]
       (.getFloat ^ByteBuffer mapped-read 4) => 171.0
       (.getFloat ^ByteBuffer mapped-write 4) => 171.0
       (do (.putFloat ^ByteBuffer mapped-write 4 100.0)
           (.getFloat ^ByteBuffer mapped-write 4))
       => 100.0
       (.getFloat ^ByteBuffer mapped-read 4) => 100.0
       (do (.putFloat ^ByteBuffer mapped-read 4 100.0)
           (.getFloat ^ByteBuffer mapped-read 4))
       => 100.0
       (enq-unmap! queue1 cl-data mapped-read) => queue1
       (enq-unmap! queue1 cl-data mapped-write) => queue1)))

  (with-release [dev (first (filter #(= 2.0 (:version (opencl-c-version %)))
                                    (devices (first (remove legacy? (platforms))) :gpu)))
                 ctx (context [dev])
                 queue (command-queue ctx dev)
                 svm (svm-buffer ctx (* cnt Float/BYTES) 0)
                 program (build-program! (program-with-source ctx [src])
                                         "-cl-std=CL2.0" nil)
                 dumb-kernel (kernel program "dumb_kernel")]
    (facts
     "SVM tests" ;; ONLY BASIC TESTS, since i do not have an APU, and
     ;; my current platform (AMD) only supports OpenCL 1.2 for the CPU.
     (ptr svm) =not=> nil
     (set-args! dumb-kernel svm Integer/BYTES (int-array [42])) => dumb-kernel
     (enq-svm-map! queue svm :write)
     (do (.putFloat ^ByteBuffer (byte-buffer svm) 4 42.0)
         (.getFloat ^ByteBuffer (byte-buffer svm) 4))
     => 42.0
     (enq-svm-unmap! queue svm) => queue
     (enq-nd! queue dumb-kernel wsize) => queue
     (enq-svm-map! queue svm :read)
     (.getFloat ^ByteBuffer (byte-buffer svm) 4) => 127.0
     (enq-svm-unmap! queue svm) => queue

     (svm-buffer* nil 4 0) => (throws IllegalArgumentException)
     (svm-buffer* ctx 0 0) => (throws IllegalArgumentException)
     (svm-buffer ctx 4 0 :invalid-flag) => (throws NullPointerException))))
