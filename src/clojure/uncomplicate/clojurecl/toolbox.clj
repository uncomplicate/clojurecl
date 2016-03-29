(ns ^{:author "Dragan Djuric"}
    uncomplicate.clojurecl.toolbox
  "Various helpers that are not needed by ClojureCL itself,
  but may be very helpful in applications. See Neanderthal library
  for the examples of how to use it."
  (:require [uncomplicate.clojurecl.core :refer :all]))

(defn count-work-groups ^long [^long max-local-size ^long n]
  (if (< max-local-size n)
    (quot (+ n (dec max-local-size)) max-local-size)
    1))

(defn enq-reduce
  ([queue main-kernel reduce-kernel max-local-size n]
   (loop [queue (enq-nd! queue main-kernel (work-size-1d n))
          global-size (count-work-groups max-local-size n)]
     (if (= 1 global-size)
       queue
       (recur
        (enq-nd! queue reduce-kernel (work-size-1d global-size))
        (count-work-groups max-local-size global-size)))))
  ([queue main-kernel reduce-kernel max-local-size m n]
   (loop [queue (enq-nd! queue main-kernel (work-size-2d m n))
          folded (count-work-groups max-local-size m)]
     (if (= 1 folded)
       queue
       (recur
        (enq-nd! queue reduce-kernel (work-size-2d folded n))
        (count-work-groups max-local-size folded)))))
  ([queue main-kernel reduce-kernel max-local-size m n orthogonal]
   (loop [queue (enq-nd! queue main-kernel (work-size-2d m n))
          folded (count-work-groups max-local-size n)]
     (if (= 1 folded)
       queue
       (recur
        (enq-nd! queue reduce-kernel (work-size-2d m folded))
        (count-work-groups max-local-size folded))))))

(defn enq-read-int ^long [queue cl-buf]
  (let [res (int-array 1)]
    (enq-read! queue cl-buf res)
    (aget res 0)))

(defn enq-read-long ^long [queue cl-buf]
  (let [res (long-array 1)]
    (enq-read! queue cl-buf res)
    (aget res 0)))

(defn enq-read-double ^double [queue cl-buf]
  (let [res (double-array 1)]
    (enq-read! queue cl-buf res)
    (aget res 0)))

(defn enq-read-float ^double [queue cl-buf]
  (let [res (float-array 1)]
    (enq-read! queue cl-buf res)
    (aget res 0)))
