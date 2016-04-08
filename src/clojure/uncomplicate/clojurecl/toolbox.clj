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
  ([queue main-kernel reduction-kernel n local-n]
   (loop [queue (enq-nd! queue main-kernel (work-size-1d n local-n))
          global-size (count-work-groups local-n n)]
     (if (= 1 global-size)
       queue
       (recur
        (enq-nd! queue reduction-kernel (work-size-1d global-size local-n))
        (count-work-groups local-n global-size)))))
  ([queue main-kernel reduction-kernel m n local-m local-n & [wgs-m wgs-n]]
   (let [queue (enq-nd! queue main-kernel (work-size-2d m n local-m local-n))
         [m n local-m local-n] (if (and wgs-m wgs-n)
                                 [n (count-work-groups local-m m) wgs-m wgs-n]
                                 [m (count-work-groups local-n n) local-m local-n])]
     (if (or (< 1 local-n) (= 1 n))
       (loop [queue queue n n]
         (if (= 1 n)
           queue
           (recur (enq-nd! queue reduction-kernel (work-size-2d m n local-m local-n))
                  (count-work-groups local-n n))))
       (throw (IllegalArgumentException.
               (format "local-n %d would cause infinite recursion for n:%d." local-n n)))))))

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
