;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.utils-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.clojurecl.utils :refer :all]))

(facts
 "error tests"

 (ex-data (error 0))
 => {:code 0, :details nil, :name "CL_SUCCESS", :type :opencl-error}

 (ex-data (error 43))
 => {:code 43, :details nil, :name "UNKNOWN OpenCL ERROR!", :type :opencl-error}

 (ex-data (error 0 "Additional details"))
 => {:code 0, :details "Additional details", :name "CL_SUCCESS", :type :opencl-error})

(facts
 "with-check tests"
 (let [f (fn [x] (if x 0 -1))]
   (with-check (f 1) :success) => :success
   (with-check (f false) :success) => (throws clojure.lang.ExceptionInfo)))

(facts
 "with-check-arr tests"
 (let [f (fn [x ^ints err]
           (do (aset err 0 (if x 0 -1))
               x))
       err (int-array 1)]
   (let [res (f :success err)] (with-check-arr err res) => :success)
   (let [res (f false err)] (with-check-arr err res))) => (throws clojure.lang.ExceptionInfo))

(facts
 "maybe tests"
 (ex-data (maybe (throw (ex-info "Test Exception" {:data :test}))))
 => (throws clojure.lang.ExceptionInfo)

 (:type (ex-data (error -1 nil))) => :opencl-error)
