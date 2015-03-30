(ns uncomplicate.clojurecl.utils
  (:require [clojure.string :as str])
  (:import clojure.lang.ExceptionInfo))

;; ============= Error Handling  ============================================
(defn error-string [^long code]
  (case code
    0 "CL_SUCCESS CL_SUCCESS"
    -1 "CL_DEVICE_NOT_FOUND CL_DEVICE_NOT_FOUND"
    -2 "CL_DEVICE_NOT_AVAILABLE"
    -3 "CL_COMPILER_NOT_AVAILABLE"
    -4 "CL_MEM_OBJECT_ALLOCATION_FAILURE"
    -5 "CL_OUT_OF_RESOURCES"
    -6 "CL_OUT_OF_HOST_MEMORY"
    -7 "CL_PROFILING_INFO_NOT_AVAILABLE"
    -8 "CL_MEM_COPY_OVERLAP"
    -9 "CL_IMAGE_FORMAT_MISMATCH"
    -10 "CL_IMAGE_FORMAT_NOT_SUPPORTED"
    -11 "CL_BUILD_PROGRAM_FAILURE"
    -12 "CL_MAP_FAILURE"
    -13 "CL_MISALIGNED_SUB_BUFFER_OFFSET"
    -14 "CL_EXEC_STATUS_ERROR_FOR_EVENTS_IN_WAIT_LIST"
    -15 "CL_COMPILE_PROGRAM_FAILURE"
    -16 "CL_LINKER_NOT_AVAILABLE"
    -17 "CL_LINK_PROGRAM_FAILURE"
    -18 "CL_DEVICE_PARTITION_FAILED"
    -19 "CL_KERNEL_ARG_INFO_NOT_AVAILABLE"
    -30 "CL_INVALID_VALUE"
    -31 "CL_INVALID_DEVICE_TYPE"
    -32 "CL_INVALID_PLATFORM"
    -33 "CL_INVALID_DEVICE"
    -34 "CL_INVALID_CONTEXT"
    -35 "CL_INVALID_QUEUE_PROPERTIES"
    -36 "CL_INVALID_COMMAND_QUEUE"
    -37 "CL_INVALID_HOST_PTR"
    -38 "CL_INVALID_MEM_OBJECT"
    -39 "CL_INVALID_IMAGE_FORMAT_DESCRIPTOR"
    -40 "CL_INVALID_IMAGE_SIZE"
    -41 "CL_INVALID_SAMPLER"
    -42 "CL_INVALID_BINARY"
    -43 "CL_INVALID_BUILD_OPTIONS"
    -44 "CL_INVALID_PROGRAM"
    -45 "CL_INVALID_PROGRAM_EXECUTABLE"
    -46 "CL_INVALID_KERNEL_NAME"
    -47 "CL_INVALID_KERNEL_DEFINITION"
    -48 "CL_INVALID_KERNEL"
    -49 "CL_INVALID_ARG_INDEX"
    -50 "CL_INVALID_ARG_VALUE"
    -51 "CL_INVALID_ARG_SIZE"
    -52 "CL_INVALID_KERNEL_ARGS"
    -53 "CL_INVALID_WORK_DIMENSION"
    -54 "CL_INVALID_WORK_GROUP_SIZE"
    -55 "CL_INVALID_WORK_ITEM_SIZE"
    -56 "CL_INVALID_GLOBAL_OFFSET"
    -57 "CL_INVALID_EVENT_WAIT_LIST"
    -58 "CL_INVALID_EVENT"
    -59 "CL_INVALID_OPERATION"
    -60 "CL_INVALID_GL_OBJECT"
    -61 "CL_INVALID_BUFFER_SIZE"
    -62 "CL_INVALID_MIP_LEVEL"
    -63 "CL_INVALID_GLOBAL_WORK_SIZE"
    -64 "CL_INVALID_PROPERTY"
    -65 "CL_INVALID_IMAGE_DESCRIPTOR"
    -66 "CL_INVALID_COMPILER_OPTIONS"
    -67 "CL_INVALID_LINKER_OPTIONS"
    -68 "CL_INVALID_DEVICE_PARTITION_COUNT"
    -69 "CL_INVALID_PIPE_SIZE"
    -70 "CL_INVALID_DEVICE_QUEUE"
    -16384 "CL_JOCL_INTERNAL_ERROR"
    -1000 "CL_INVALID_GL_SHAREGROUP_REFERENCE_KHR"
    -1001 "CL_PLATFORM_NOT_FOUND_KHR"
    "UNKNOWN OpenCL ERROR!"))

(defn mask ^long [table flags]
  (apply bit-or (map table flags)))

(defn unmask [table ^long mask]
  (filter identity
          (map (fn [[k v]]
                 (if (= 0 (bit-and mask (long v)))
                   nil
                   k))
               table)))

(defn unmask1 [table ^long mask]
  (some identity
        (map (fn [[k v]]
               (if (= 0 (bit-and mask (long v)))
                   nil
                   k))
             table)))

(defn error [err-code]
  (let [err (error-string err-code)]
    (ex-info (format "OpenCL error: %s." err)
             {:name err :code err-code :type :opencl-error})))

(defmacro with-check-arr [err-code form]
  `(with-check (aget (ints ~err-code) 0) ~form))

(defmacro with-check [err-code form]
  `(if (= 0 ~err-code)
     ~form
     (throw (error ~err-code))))

(defmacro maybe [form]
  `(try ~form
         (catch ExceptionInfo ex-info#
           (if (= :opencl-error (:type (ex-data ex-info#)))
             ex-info#
             (throw ex-info#)))))
