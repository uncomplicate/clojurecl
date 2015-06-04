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
   (->CreateContextCallback ch) => truthy
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
       (context-properties {:platform p}) => truthy)

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

         (let [ch (chan)
               ctx (context* adevs props ch :some-data)]
           (reference-count ctx) => 1
           (command-queue ctx nil nil) => (throws ExceptionInfo)
           ;; TODO I am not sure how this CreateContextFunction mechanism work.
           ;; It is implemented, but I do not know how to raise an error that
           ;; shoud then reported through the channel. Test it later.)
           )

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
         (release (context devs {:platform p} (chan) :some-data)) => true

         (with-context (context devs)
           (context-info))))

      ;; ================== Buffer tests ========================

      (facts
       "cl-buffer tests."

       (with-release [ctx (context [dev])]

         (let [cl-buf (cl-buffer* ctx Float/BYTES 0)]
           (type (cl-mem cl-buf)) => cl_mem
           (type (ptr cl-buf)) => Pointer
           (size cl-buf) => Float/BYTES
           (cl-context cl-buf) => ctx
           (release cl-buf) => true)

         (let [cl-buf (cl-buffer ctx Float/BYTES :read-write)]
           (type (cl-mem cl-buf)) => cl_mem
           (type (ptr cl-buf)) => Pointer
           (size cl-buf) => Float/BYTES
           (cl-context cl-buf) => ctx
           (release cl-buf) => true)

         (cl-buffer nil 4 :read-write) => (throws ExceptionInfo)
         (cl-buffer ctx 0 :read-write) => (throws ExceptionInfo)
         (cl-buffer ctx 4 :unknown) => (throws NullPointerException)))

      (facts
       "cl-buffer and cl-sub-buffer reading/writing tests."
       (let [alignment (mem-base-addr-align dev)]
         (with-context (context [dev])
           (with-release [cl-buf (cl-buffer (* 4 alignment Float/BYTES))
                          cl-subbuf (cl-sub-buffer cl-buf (* alignment Float/BYTES)
                                                   (* alignment Float/BYTES))
                          queue (command-queue dev)]
             (type cl-subbuf) => CLBuffer
             (let [data-arr (float-array (range (* 4 alignment)))
                   buf-arr (float-array (* 4 alignment))
                   subbuf-arr (float-array alignment)]
               (enq-write! queue cl-buf data-arr)
               (enq-read! queue cl-buf buf-arr)
               (enq-read! queue cl-subbuf subbuf-arr)
               (vec buf-arr) => (vec data-arr)
               (vec subbuf-arr) => (map float (range alignment (* 2 alignment))))))))

      (facts
       "event tests."
       )

     )))
