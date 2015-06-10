(ns uncomplicate.clojurecl.core-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info reference-count mem-base-addr-align]]]
            [clojure.core.async :refer [go >! <! <!! chan]])
  (:import [uncomplicate.clojurecl.core CLBuffer]
           [org.jocl CL Pointer cl_device_id cl_context_properties cl_mem]
           [clojure.lang ExceptionInfo]))

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

 (let [p (first (platforms))]
   (num-devices* p CL/CL_DEVICE_TYPE_ALL) => (num-devices p :all)
   (num-devices* nil CL/CL_DEVICE_TYPE_ALL) => (throws ExceptionInfo)

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

   (num-devices nil :all) => (throws ExceptionInfo)
   (num-devices p :unknown-device) => (throws NullPointerException)))

(facts
 "devices tests"

 (let [p (first (platforms))]
   (vec (devices* p CL/CL_DEVICE_TYPE_ALL)) => (devices p :all)
   (devices* nil CL/CL_DEVICE_TYPE_ALL) => (throws ExceptionInfo)

   (count (devices p :all)) => (num-devices p :all)

   (devices p :gpu :cpu) => (concat (devices p :gpu) (devices p :cpu))
   (devices p :custom) => []

   (type (first (devices p :cpu))) => cl_device_id

   (with-platform p
     (devices :all) => (devices p :all)
     (devices :gpu) => (devices p :gpu)
     (devices) => (devices p :all))

   (devices nil :all) => (throws ExceptionInfo)
   (devices p :unknown-device) => (throws NullPointerException)))

(facts
 "Root level devices resource management."

 (let [p (first (platforms))
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

(let [p (first (platforms))]
  (with-platform p
    (with-release [devs (devices p)
                   dev (first devs)]

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
           (type (info queue :size)) => ExceptionInfo
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
   (let [alignment (mem-base-addr-align (first (devices (first (platforms)))))]
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

  ;; ================== Event tests ========================

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

    ;; ================== Program tests ========================

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
      ;; ================== Program tests ========================

      ;; TODO Some procedures might crash JVM if called on
      ;; unprepared objects (kernels of unbuilt program).
      ;; Solve and test such cases systematically in info.clj
      ;; in a similar way as kernels check for program binaries first.
      (facts
       (info (program-with-source [src])) =not=> nil)

      (facts
       "Kernel tests"
       (num-kernels program) => 1
       (with-release [dumb-kernel (kernel program "dumb_kernel")
                      all-kernels (kernel program)
                      cl-data (cl-buffer (* cnt Float/BYTES))]
         (info dumb-kernel :name) => (info (first all-kernels) :name)
         (kernel nil) => (throws ExceptionInfo)

         (set-arg! dumb-kernel 0 nil) => (throws IllegalArgumentException)

         (set-arg! dumb-kernel 0 cl-data) => dumb-kernel
         (set-arg! dumb-kernel 1 Integer/BYTES) => dumb-kernel
         (set-arg! dumb-kernel 2 (int-array [42])) => dumb-kernel

         (set-args! dumb-kernel cl-data Integer/BYTES) => dumb-kernel)))))
