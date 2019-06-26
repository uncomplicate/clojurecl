;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
  uncomplicate.clojurecl.internal.utils
  "Utility functions used as helpers in other ClojureCL namespaces.
  The user of the ClojureCL library would probably not need to use
  any of the functions defined here."
  (:require [uncomplicate.commons.utils :as cu]
            [uncomplicate.clojurecl.internal.constants :refer [dec-error]])
  (:import clojure.lang.ExceptionInfo))


;; ========== Error handling ======================================

(defn error
  "Converts an OpenCL error code to an [ExceptionInfo]
  (http://clojuredocs.org/clojure.core/ex-info)
  with richer, user-friendly information.

  Accepts a long `err-code` that should be one of the codes defined in
  OpenCL standard, and an optional `details` argument that could be
  anything that you think is informative.

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
  "Evaluates `form` if `status` is zero (`CL_SUCCESS`), otherwise throws
  an appropriate `ExceptionInfo` with decoded informative details.
  It helps fith JOCL methods that return error codes directly, while
  returning computation results through side-effects in arguments.

  Example:

      (with-check (some-jocl-call-that-returns-error-code) result)
  "
  ([status form]
   `(cu/with-check error ~status ~form))
  ([status details form]
   `(let [status# ~status]
      (if (= 0 status#)
        ~form
        (throw (error status# ~details))))))

(defmacro with-check-arr
  "Evaluates `form` if the integer in the `status` primitive int array is `0`,
  Otherwise throws an exception corresponding to the error code.
  Similar to [[with-check]], but with the error code being held in an array instead
  of being a primitive number. It helps with JOCL methods that return results
  directly, and signal errors through side-effects in a primitive array argument.

      (let [err (int-array 1)
            res (some-jocl-call err)]
         (with-checl-arr err res))
  "
  ([status form]
   `(with-check (aget (ints ~status) 0) ~form))
  ([status details form]
   `(with-check (aget (ints ~status) 0) ~details ~form)))

(defmacro maybe
  "Evaluates form in try/catch block; if an OpenCL-related exception is caught,
  substitutes the result with the String identifying the error.

  Non-OpenCL exceptions are rethrown. Useful when we do not want to let a minor
  OpenCL error due to a driver incompatibility with the standard
  or an unimplemented feature in the actual driver crash the application.
  A String will be put in the place of the expected result."
  [form]
  `(try ~form
        (catch ExceptionInfo ex-info#
          (if (= :opencl-error (:type (ex-data ex-info#)))
            (:name (ex-data ex-info#))
            (throw ex-info#)))))
