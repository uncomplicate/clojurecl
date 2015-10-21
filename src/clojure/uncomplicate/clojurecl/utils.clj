(ns ^{:author "Dragan Djuric"}
  uncomplicate.clojurecl.utils
  "Utility functions used as helpers in other ClojureCL namespaces.
  The user of the ClojureCL library would probably not need to use
  any of the functions defined here."
  (:require [uncomplicate.clojurecl.constants :refer [dec-error]])
  (:import clojure.lang.ExceptionInfo
           [java.nio ByteBuffer DirectByteBuffer]))

;; ========= Bitfild masks ========================================

(defn mask
  "Converts keywords to a bitfield mask.

  Given one or more keyword `flag`s, creates a long bitmask
  that can be consumed by JOCL functions. Needs a hashmap `table` that
  contains possible long mappings, 0-2 keywords, and a (possibly empty)
  list of additional keywords.
  If called with `nil` table or an unknown keyword, throws `Illegalargumentexception`.
  The inverse function is [[unmask]].

  Examples:

      (mask {:a 1 :b 2 :c 4} [:a :c]) => 5
      (mask {:a 1 :b 2 :c 4} :a [:c]) => 5
      (mask {:a 1 :b 2 :c 4} :a :c []) => 5
  "
  (^long [table flag1 flag2 flags]
         (apply bit-or (table flag1) (table flag2) (map table flags)))
  (^long [table flag flags]
         (apply bit-or 0 (table flag) (map table flags)))
  (^long [table flags]
         (apply bit-or 0 0 (map table flags))))

(defn unmask
  "Converts a bitfield `mask` to keywords.

  Given a mapping `table` and a bitfield `mask`, returns a lazy sequence
  with decoded keyword flags contained in the bitmask.
  The reverse function is [[mask]].

  Examples:

      (unmask {:a 1 :b 2 :c 4} 5) =>  '(:a :c)
  "
  [table ^long mask]
  (filter identity
          (map (fn [[k v]]
                 (if (= 0 (bit-and mask (long v)))
                   nil
                   k))
               table)))

(defn unmask1
  "Converts a bitfield `mask` to one keyword.

  Given a mapping `table` and a bitfield `mask`, returns the first decoded keyword
  that is contained in the bitmask. This is useful when we know that just
  one of the values in the table fits the bitmask, so the result of [[unmask]]
  would contain one element anyway.
  The reverse function is [[mask]].

  Examples:

      (unmask1 {:a 1 :b 2 :c 4} 2) => :b
  "
  [table ^long mask]
  (some identity
        (map (fn [[k v]]
               (if (= 0 (bit-and mask (long v)))
                   nil
                   k))
             table)))

;; ========== Error handling ======================================

(defn error
  "Converts an OpenCL error code to an [ExceptionInfo]
  (http://clojuredocs.org/clojure.core/ex-info)
  with richer, user-friendly information.

  Accepts a long `err-code` that should be one of the codes defined in
  OpenCL standard, and an optional `details` argument that could be anything that you
  think is informative.

  See the available codes in the source of [[constants/dec-error]].
  Also see the discussion about
  [OpenCL error codes](http://streamcomputing.eu/blog/2013-04-28/opencl-1-2-error-codes/).

  Examples:

      (error 0) => an ExceptionInfo instance
      (error -5 {:comment \"Why here?\"\"}) => an ExceptionInfo instance
  "
  ([^long err-code details]
   (let [err (dec-error err-code)]
     (ex-info (format "OpenCL error: %s." err)
              {:name err :code err-code :type :opencl-error :details details})))
  ([err-code]
   (error err-code nil)))

(defmacro with-check
  "Evaluates `form` if `err-code` is not zero (`CL_SUCCESS`), otherwise throws
  an appropriate `ExceptionInfo` with decoded informative details.
  It helps fith JOCL methods that return error codes directly, while
  returning computation results through side-effects in arguments.

  Example:

      (with-check (some-jocl-call-that-returns-error-code) result)
  "
  [err-code form]
  `(if (= 0 ~err-code)
     ~form
     (throw (error ~err-code))))

(defmacro with-check-arr
  "Evaluates `form` if the integer in the `err-code` primitive int array is `0`,
  Otherwise throws an exception corresponding to the error code.
  Similar to [[with-check]], but with the error code being held in an array instead
  of being a primitive number. It helps with JOCL methods that return results
  directly, and signal errors through side-effects in a primitive array argument.

      (let [err (int-array 1)
            res (some-jocl-call err)]
         (with-checl-arr err res))
  "
  [err-code form]
  `(with-check (aget (ints ~err-code) 0) ~form))

(defmacro maybe
  "Evaluates form in try/catch block; if an OpenCL-related exception is caught,
  substitutes the result with the [ExceptionInfo](http://clojuredocs.org/clojure.core/ex-info)
  object.
  Non-OpenCL exceptions are rethrown. Useful when we do not want to let a minor
  OpenCL error due to a driver incompatibility with the standard
  or an unimplemented feature in the actual driver crash the application.
  An [ExceptionInfo](http://clojuredocs.org/clojure.core/ex-info) object will be
  put in the place of the expected result."
  [form]
  `(try ~form
         (catch ExceptionInfo ex-info#
           (if (= :opencl-error (:type (ex-data ex-info#)))
             ex-info#
             (throw ex-info#)))))

(defn clean-buffer
  "Cleans the direct byte buffer using JVM's cleaner, and releases the memory
  that resides outside JVM, wihich might otherwise linger very long until garbage
  collected. See the Java documentation for DirectByteBuffer for more info."
  [^ByteBuffer buffer]
  (do
    (if (.isDirect buffer)
      (.clean (.cleaner ^DirectByteBuffer buffer)))
    true))
