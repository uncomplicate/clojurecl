;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns uncomplicate.clojurecl.buffer-test
  (:require [midje.sweet :refer :all]
            [uncomplicate.commons
             [core :refer [release with-release info]]
             [utils :refer [direct-buffer put-float get-float]]]
            [uncomplicate.fluokitten.core :refer [fmap]]
            [uncomplicate.clojurecl
             [core :refer :all :as cl]
             [info :refer [reference-count mem-base-addr-align opencl-c-version queue-context]]]
            [uncomplicate.clojurecl.internal
             [protocols :refer [size ptr byte-buffer wrap extract]]
             [impl :refer :all]])
  (:import (java.nio ByteBuffer
                     ByteOrder)))

(defn default-context
  "creates the default context
 
   (default-context)"
  {:added "3.0"}
  ([]
   (-> (cl/platforms)
       first
       cl/devices
       default-context))
  ([devs]
   (cl/context devs)))

(defn select-gpu
  "selects the gpu
 
   (select-gpu)"
  {:added "3.0"}
  ([]
   (-> (cl/platforms)
       (first)
       (cl/devices)
       (select-gpu)))
  ([devs]
   (->> devs
        (filter (fn [id] (-> (info id)
                             :device-type
                             (= :gpu))))
        (first))))

(defrecord OpenCL [name code context queue kernel])

(defn opencl
  "constructor for the opencl object
   
   (def -mult-float-code-
     \"__kernel void mult(__global float * input,
                         float f){
       int gid = get_global_id(0);
       input[gid] = f * input[gid];
     }\")
   
   (def -mult-float-
     (opencl {:name \"mult\"
              :code  -mult-float-code-
              :worksize (fn [{:strs [src]}]
                         [[(count src)]])
              :arglist [{:name \"src\" :type :float-array, :input true, :output true}
                        {:name \"f\"   :type :float}]}))"
  {:added "3.0"}
  [{:keys [name code path arglist options] :as m}]
  (let [devs (-> (cl/platforms)
                 first
                 cl/devices)
        context (default-context devs)
        device  (or (select-gpu devs)
                    (first devs))
        queue   (cl/command-queue-1 context device)
        code    (or code (slurp path))
        program (-> (cl/program-with-source context [code])
                    (cl/build-program! options nil))
        kernel  (cl/kernel program name)]
    (-> m
        (assoc :name name               
               :code code
               :program program
               :context context
               :device device
               :queue queue
               :kernel kernel)
        (map->OpenCL))))

(fact "mult-float"

  (def -mult-float-code-
    "__kernel void mult(__global float * input,
                        float n){
      int gid = get_global_id(0);
      input[gid] = n * input[gid];
    }")
  
  (def -mult-float- (opencl {:name "mult"
                             :code  -mult-float-code-}))
  
  (let [data (-> (doto (ByteBuffer/allocateDirect 16)
                   (.order (ByteOrder/nativeOrder)))
                 (.asFloatBuffer)
                 (doto (.put (float-array [1.001 20.01 300.1 4001])) (.rewind)))
        {:keys [kernel queue context]} -mult-float-
        arg-src (cl/cl-buffer context 16 :read-write)
        arg-n   (float-array [10.1])
        _ (cl/set-args! kernel arg-src arg-n)
        _ (cl/enq-write! queue arg-src data)
        _ (cl/enq-kernel! queue kernel (cl/work-size [4]))
        _ (cl/enq-read! queue arg-src data)
        out (float-array 4)
        _   (.get data out)]
    (vec out))
  => (map float [10.110101 202.10101 3031.0103 40410.1]))


(fact "mult-int"

  (def -mult-int-code-
    "__kernel void mult(__global uint * input,
                        uint n){
      int gid = get_global_id(0);
      input[gid] = n * input[gid];
    }")
  
  (def -mult-int- (opencl {:name "mult"
                           :code  -mult-int-code-}))
  
  (let [data (-> (doto (ByteBuffer/allocateDirect 16)
                   (.order (ByteOrder/nativeOrder)))
                 (.asIntBuffer)
                 (doto (.put (int-array [1 20 300 4000])) (.rewind)))
        {:keys [kernel queue context]} -mult-int-
        arg-src (cl/cl-buffer context 16 :read-write)
        arg-n   (int-array [10])
        _ (cl/set-args! kernel arg-src arg-n)
        _ (cl/enq-write! queue arg-src data)
        _ (cl/enq-kernel! queue kernel (cl/work-size [4]))
        _ (cl/enq-read! queue arg-src data)
        out (int-array 4)
        _   (.get data out)]
    (vec out))
  => [10 200 3000 40000])


(comment ;; Benchmarks

  (def -dbuff-  (java.nio.DoubleBuffer/allocate 10))
  (def -tdbuff- (type dbuff))
  (def -trials- 1000000)
  
  (-> (dotimes [i -trials-]
        (component-size-raw (type -dbuff-)))
      time
      with-out-str)
  => "\"Elapsed time: 236.124617 msecs\"\n"
  
  (-> (dotimes [i -trials-]
        (component-size (type -dbuff-)))
      time
      with-out-str)
  => "\"Elapsed time: 215.91447 msecs\"\n"
  
  (-> (dotimes [i -trials-]
        (component-size tdbuff))
      time
      with-out-str)
  => "\"Elapsed time: 181.299622 msecs\"\n"

  (-> (dotimes [i -trials-]
        i)
      time
      with-out-str)
  => "\"Elapsed time: 4.037231 msecs\"\n"

  ;; type speed (ns):
  (- 215.9 181.3)
  => 34.6
  
  ;; memoize speed (ns):
  (- 215.9 4.0)
  => 211.9
  
  ;; non-memoized speed (ns):
  (- 236.12 4.0)
  => 232.12)
