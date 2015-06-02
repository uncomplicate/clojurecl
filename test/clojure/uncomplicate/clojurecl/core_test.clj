(ns uncomplicate.clojurecl.core-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl
             [core :refer :all]
             [info :refer [info]]])
  (:import [org.jocl CL cl_device_id]
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

   (with-platform p
     (devices :all) => (devices p :all)
     (devices :gpu) => (devices p :gpu)
     (devices) => (devices p :all))

   (devices nil :all) => (throws ExceptionInfo)
   (devices p :unknown-device) => (throws NullPointerException))
 )
