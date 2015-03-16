(ns uncomplicate.clojurecl.core
  (:require [clojure.string :as str]
            [vertigo
             [bytes :refer [buffer byte-seq byte-count]]
             [structs :refer [int32 int64 wrap-byte-seq]]]
            [uncomplicate.fluokitten jvm
             [core :refer [fmap]]])
  (:import [org.jocl CL cl_platform_id cl_context_properties cl_device_id
            cl_context cl_command_queue cl_mem cl_program cl_kernel cl_sampler
            cl_event
            Sizeof Pointer CreateContextFunction]
           [java.nio ByteBuffer ByteOrder]))

(def ^:dynamic *platform*)
(def ^:dynamic *context*)
(def ^:dynamic *command-queue*)

(def device-types
  {:gpu CL/CL_DEVICE_TYPE_GPU
   :cpu CL/CL_DEVICE_TYPE_CPU
   :all CL/CL_DEVICE_TYPE_ALL
   :default CL/CL_DEVICE_TYPE_DEFAULT
   :accelerator CL/CL_DEVICE_TYPE_ACCELERATOR
   :custom CL/CL_DEVICE_TYPE_CUSTOM})

(def device-types-table
  (into {} (map (fn [[k v]] [v k]) device-types)))

(def platform-info-table
  {:profile CL/CL_PLATFORM_PROFILE
   :version CL/CL_PLATFORM_VERSION
   :name CL/CL_PLATFORM_NAME
   :vendor CL/CL_PLATFORM_VENDOR
   :extensions CL/CL_PLATFORM_EXTENSIONS
   :icd-suffix-khr CL/CL_PLATFORM_ICD_SUFFIX_KHR})

;;TODO update for JOCL 2.0
(def context-prop-table
  {:platform CL/CL_CONTEXT_PLATFORM
   :interop-user-sync CL/CL_CONTEXT_INTEROP_USER_SYNC
   :gl-context-khr CL/CL_GL_CONTEXT_KHR
   :cgl-sharegroup-khr CL/CL_CGL_SHAREGROUP_KHR
   :egl-display-khr CL/CL_EGL_DISPLAY_KHR
   :glx-display-khr CL/CL_GLX_DISPLAY_KHR
   :wgl-hdc-khr CL/CL_WGL_HDC_KHR})

(def error-codes
  {CL/CL_INVALID_VALUE "CL_INVALID_VALUE"
   CL/CL_OUT_OF_HOST_MEMORY "CL_OUT_OF_HOST_MEMORY"
   CL/CL_PLATFORM_NOT_FOUND_KHR "CL_PLATFORM_NOT_FOUND_KHR"
   CL/CL_INVALID_PLATFORM "CL_INVALID_PLATFORM"
   CL/CL_INVALID_DEVICE_TYPE "CL_INVALID_DEVICE_TYPE"
   CL/CL_DEVICE_NOT_FOUND "CL_DEVICE_NOT_FOUND"
   CL/CL_OUT_OF_RESOURCES "CL_OUT_OF_RESOURCES"
   CL/CL_INVALID_DEVICE "CL_INVALID_DEVICE"
   CL/CL_DEVICE_NOT_AVAILABLE "CL_DEVICE_NOT_AVAILABLE"
   CL/CL_INVALID_CONTEXT "CL_INVALID_CONTEXT"
   CL/CL_INVALID_PROGRAM "CL_INVALID_PROGRAM"
   CL/CL_INVALID_BINARY "CL_INVALID_BINARY"
   CL/CL_INVALID_BUILD_OPTIONS "CL_INVALID_BUILD_OPTIONS"
   CL/CL_INVALID_OPERATION "CL_INVALID_OPERATION"
   CL/CL_COMPILER_NOT_AVAILABLE "CL_COMPILER_NOT_AVAILABLE"
   CL/CL_BUILD_PROGRAM_FAILURE "CL_BUILD_PROGRAM_FAILURE"
   CL/CL_INVALID_BUFFER_SIZE "CL_INVALID_BUFFER_SIZE"
   CL/CL_INVALID_HOST_PTR "CL_INVALID_HOST_PTR"
   CL/CL_MEM_USE_HOST_PTR "CL_MEM_USE_HOST_PTR"
   CL/CL_MEM_OBJECT_ALLOCATION_FAILURE "CL_MEM_OBJECT_ALLOCATION_FAILURE"
   CL/CL_INVALID_PROGRAM_EXECUTABLE "CL_INVALID_PROGRAM_EXECUTABLE"
   CL/CL_INVALID_KERNEL_NAME "CL_INVALID_KERNEL_NAME"
   CL/CL_INVALID_KERNEL_DEFINITION "CL_INVALID_KERNEL_DEFINITION"
   CL/CL_INVALID_ARG_INDEX "CL_CL_INVALID_ARG_INDEX"})

;; ============= Utils ============================================
(defn error [err-code]
  (let [err (error-codes err-code)]
    (ex-info (if err
               (format "OpenCL error: %s." err)
               "UNKNOWN OpenCL ERROR!")
             {:name err :code err-code :type :opencl-error})))

(defmacro with-check-arr [err-code form]
  `(with-check (aget (ints ~err-code) 0) ~form))

(defmacro with-check [err-code form]
  `(if (= 0 ~err-code)
     ~form
     (throw (error ~err-code))))

(defmacro ^:private info-string* [method clobject info]
  `(let [size# (long-array 1)
         err# (~method ~clobject ~info 0 nil size#)]
     (with-check err#
       (let [size# (aget size# 0)
             res# (byte-array size#)
             err# (~method ~clobject ~info
                           (alength res#) (Pointer/to res#)
                           nil)]
         (with-check err#
           (String. res# 0 (dec size#)))))))

(let [native-pointer (fn [^"[Lorg.jocl.NativePointerObject;" np]
                       (Pointer/to np))]
  (defmacro ^:private info-native* [method clobject info type size]
    `(let [size# (long-array 1)
           err# (~method ~clobject ~info 0 nil size#)]
       (with-check err#
         (let [size# (aget size# 0)
               res# (make-array ~type (/ size# ~size))
               err# (~method ~clobject ~info
                             size# (~native-pointer res#)
                             nil)]
           (with-check err# res#))))))

(defmacro info-mask* [method clobject info table]
  `(let [mask# (info-long* ~method ~clobject ~info)]
     (fmap (fn [^long config#]
             (not= 0 (bit-and mask# config#)))
           ~table)))

(defmacro info-mask-one* [method clobject info table]
  `(let [mask# (info-long* ~method ~clobject ~info)]
     (some (fn [[k# v#]]
             (if (= 0 (bit-and mask# (long v#)))
               false
               k#))
           ~table)))

(let [pointer-to-buffer (fn [^ByteBuffer b]
                          (Pointer/to b))]
  (defmacro ^:private info-size*
    ([method clobject info num]
     `(let [res# (buffer (* Sizeof/size_t (long ~num)))
            err# (~method ~clobject ~info
                          (* Sizeof/size_t (long ~num))
                          (~pointer-to-buffer res#)
                          nil)]
        (with-check err#
          (wrap-byte-seq (if (= 4 Sizeof/size_t)
                           int32
                           int64)
                         (byte-seq res#)))))
    ([method clobject info]
     `(first (info-size* ~method ~clobject ~info 1)))))

(defmacro ^:private info-long*
  ([method clobject info num]
   `(let [res# (long-array ~num)
          err# (~method ~clobject ~info
                        (* Sizeof/cl_long (long ~num))
                        (Pointer/to res#)
                        nil)]
      (with-check err# res#)))
  ([method clobject info]
   `(aget (longs (info-long* ~method ~clobject ~info 1)) 0)))

(defmacro ^:private info-int*
  ([method clobject info num]
   `(let [res# (int-array ~num)
          err# (~method ~clobject ~info
                        (* Sizeof/cl_int (long ~num))
                        (Pointer/to res#)
                        nil)]
      (with-check err# res#)))
  ([method clobject info]
   `(aget (ints (info-int* ~method ~clobject ~info 1)) 0)))

(defmacro ^:private info-bool* [method clobject info]
  `(not= 0 (info-int* ~method ~clobject ~info)))

;; =============== Release Functions  ===============================
(defprotocol Releaseable
  (close [this]))

(extend-type cl_command_queue
  Releaseable
  (close [q]
    (with-check (CL/clReleaseCommandQueue q) true)))

(extend-type cl_context
  Releaseable
  (close [c]
    (with-check (CL/clReleaseContext c) true)))

(extend-type cl_device_id
  Releaseable
  (close [d]
    (with-check (CL/clReleaseDevice d) true)))

(extend-type cl_event
  Releaseable
  (close [e]
    (with-check (CL/clReleaseEvent e) true)))

(extend-type cl_kernel
  Releaseable
  (close [k]
    (with-check (CL/clReleaseKernel k) true)))

(extend-type cl_mem
  Releaseable
  (close [m]
    (with-check (CL/clReleaseMemObject m) true)))

(extend-type cl_program
  Releaseable
  (close [p]
    (with-check (CL/clReleaseProgram p) true)))

(extend-type cl_sampler
  Releaseable
  (close [s]
    (with-check (CL/clReleaseSampler s) true)))

(defn close-seq [cl]
  (if (sequential? cl)
    (map close-seq cl)
    (close cl)))

(defmacro with-cl [bindings & body]
  `(let ~(vec bindings)
     (try
       (do
         ~@body)
       (finally
         (do
           ~@(map #(list 'close %) (take-nth 2 bindings)))))))

(defmacro with-cls [bindings & body]
  `(let ~(vec bindings)
     (try
       (do
         ~@body)
       (finally
         (do
           ~@(map #(list 'close-seq %) (take-nth 2 bindings)))))))

;; =============== Platform =========================================
(defn num-platforms []
  (let [res (int-array 1)
        err (CL/clGetPlatformIDs 0 nil res)]
    (with-check err (aget res 0))))

(defn platforms []
  (let [num-platforms (num-platforms)
        res (make-array cl_platform_id num-platforms)
        err (CL/clGetPlatformIDs num-platforms res nil)]
    (with-check err (vec res))))

(defn platform-info
  ([platform info]
   (info-string* CL/clGetPlatformInfo platform
                 (platform-info-table info)))
  ([platform]
   (fmap #(info-string* CL/clGetPlatformInfo platform %)
         platform-info-table))
  ([]
   (platform-info *platform*)))

(defmacro with-platform [platform & body]
  `(binding [*platform* ~platform]
    ~@body))

;; =============== Device ==========================================
(def fp-config-table
  {:denorm CL/CL_FP_DENORM
   :inf-nan CL/CL_FP_INF_NAN
   :round-to-nearest CL/CL_FP_ROUND_TO_NEAREST
   :round-to-zero CL/CL_FP_ROUND_TO_ZERO
   :round-to-inf CL/CL_FP_ROUND_TO_INF
   :fma CL/CL_FP_FMA
   :correctly-rounded-divide-sqrt CL/CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
   :soft-float CL/CL_FP_SOFT_FLOAT})

(def exec-capabilities-table
  {:kernel CL/CL_EXEC_KERNEL
   :native-kernel CL/CL_EXEC_NATIVE_KERNEL})

(def cache-type-table
  {:none CL/CL_NONE
   :read-only CL/CL_READ_ONLY_CACHE
   :read-write CL/CL_READ_WRITE_CACHE})

(def mem-type-table
  {:none CL/CL_NONE
   :global CL/CL_GLOBAL
   :local CL/CL_LOCAL})

(def affinity-domain-table
  {:numa CL/CL_DEVICE_AFFINITY_DOMAIN_NUMA
   :l4-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L4_CACHE
   :l3-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L3_CACHE
   :l2-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L2_CACHE
   :l1-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L1_CACHE
   :next-partitionable CL/CL_DEVICE_AFFINITY_DOMAIN_NEXT_PARTITIONABLE})

(defn info-address-bits [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_ADDRESS_BITS))

(defn info-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_AVAILABLE))

(defn info-built-in-kernels [device]
  (let [res (info-string* CL/clGetDeviceInfo device
                          CL/CL_DEVICE_BUILT_IN_KERNELS)]
    (if (str/blank? res)
      []
      (str/split res #" "))))

(defn info-compiler-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_COMPILER_AVAILABLE))

(defn info-double-fp-config [device]
  (info-mask* CL/clGetDeviceInfo device
              CL/CL_DEVICE_DOUBLE_FP_CONFIG
              fp-config-table))

(defn info-endian-little [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ENDIAN_LITTLE))

(defn info-error-correction-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ERROR_CORRECTION_SUPPORT))

(defn info-execution-capabilities [device]
  (info-mask* CL/clGetDeviceInfo device
              CL/CL_DEVICE_DOUBLE_FP_CONFIG
              exec-capabilities-table))

(defn info-extensions [clid]
  (str/split
   (cond
    (instance? cl_device_id clid)
    (info-string* CL/clGetDeviceInfo clid CL/CL_DEVICE_EXTENSIONS)
    (instance? cl_platform_id clid)
    (info-string* CL/clGetPlatformInfo clid CL/CL_PLATFORM_EXTENSIONS)
    :default nil)
   #" "))

(defn info-global-mem-cache-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHE_SIZE))

(defn info-global-mem-cache-type [device]
  (info-mask-one* CL/clGetDeviceInfo device
                  CL/CL_DEVICE_GLOBAL_MEM_CACHE_TYPE
                  cache-type-table))

(defn info-global-mem-cacheline-size ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHELINE_SIZE))

(defn info-global-mem-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_SIZE))

(defn info-global-variable-preferred-total-size [device]
  nil)

(defn info-image2d-max-height [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE2D_MAX_HEIGHT))

(defn info-image2d-max-width [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE2D_MAX_WIDTH))

(defn info-image3d-max-depth [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_DEPTH))

(defn info-image3d-max-height [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_HEIGHT))

(defn info-image3d-max-width [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_WIDTH))

(defn info-image-base-address-alignment [device]
  nil)

(defn info-image-max-array-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_ARRAY_SIZE))

(defn info-image-max-buffer-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_BUFFER_SIZE))

(defn info-image-pitch-alignment [device]
  nil)

(defn info-image-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_SUPPORT))

(defn info-linker-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_LINKER_AVAILABLE))

(defn info-local-mem-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_LOCAL_MEM_SIZE))

(defn info-local-mem-type [device]
  (info-mask-one* CL/clGetDeviceInfo device
                  CL/CL_DEVICE_LOCAL_MEM_TYPE
                  mem-type-table))

(defn info-max-clock-frequency [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CLOCK_FREQUENCY))

(defn info-max-compute-units [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_COMPUTE_UNITS))

(defn info-max-constant-args [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_ARGS))

(defn info-max-constant-buffer-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE))

(defn info-max-global-variable-size [device]
  nil)

(defn info-max-mem-aloc-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_MEM_ALLOC_SIZE))

(defn info-max-on-device-events [device]
  nil)

(defn info-max-on-device-queues [device]
  nil)

(defn info-max-parameter-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_PARAMETER_SIZE))

(defn info-max-pipe-args [device]
  nil)

(defn info-max-read-image-args [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_READ_IMAGE_ARGS))

(defn info-max-read-write-image-args [device]
  nil)

(defn info-max-samplers [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_SAMPLERS))

(defn info-max-work-group-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_GROUP_SIZE))

(defn info-max-work-item-dimensions [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS))

(defn info-max-work-item-sizes [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_ITEM_SIZES
              (info-max-work-item-dimensions device)))

(defn info-max-write-image-args [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WRITE_IMAGE_ARGS))

(defn info-mem-base-addr-align [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MEM_BASE_ADDR_ALIGN))

(defn info-name [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DEVICE_NAME))

(defn info-native-vector-width-char [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_CHAR))

(defn info-native-vector-width-short [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_SHORT))

(defn info-native-vector-width-int [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_INT))

(defn info-native-vector-width-long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_LONG))

(defn info-native-vector-width-float [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_FLOAT))

(defn info-native-vector-width-double [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_DOUBLE))

(defn info-native-vector-width-half [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_HALF))

(defn info-opencl-c-version [device]
  (let [info (str/split (info-string* CL/clGetDeviceInfo device
                                      CL/CL_DEVICE_OPENCL_C_VERSION)
                        #" ")]
    {:version (info 2)
     :vendor-specific-info (get info 3)}))

(defn info-parent-device [device]
  (let [parent (cl_device_id.)
        id (info-long* CL/clGetDeviceInfo device
                       CL/CL_DEVICE_PARENT_DEVICE)]
    (if (= 0 id)
      nil
      (let [parent (cl_device_id.)
            err (CL/clGetDeviceInfo device CL/CL_DEVICE_PARENT_DEVICE
                                    Sizeof/cl_device_id
                                    (Pointer/to parent)
                                    nil)]
        (with-check err parent)))))

(defn info-partition-affinity-domain [device]
  (info-mask* CL/clGetDeviceInfo device
              CL/CL_DEVICE_PARTITION_AFFINITY_DOMAIN
              affinity-domain-table))

(defn info-partition-max-sub-devices [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_MAX_SUB_DEVICES))

;;TODO
(defn info-partition-properties [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_PROPERTIES))

;;TODO
(defn info-partition-type [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_TYPE))

(defn info-pipe-max-active-reservations [device]
  nil)

(defn info-pipe-max-packet-size [device]
  nil)

(defn info-platform [device]
  (let [platform (cl_platform_id.)
        err (CL/clGetDeviceInfo device CL/CL_DEVICE_PLATFORM
                                Sizeof/cl_platform_id
                                (Pointer/to platform)
                                nil)]
    (with-check err platform)))

(defn info-preferred-global-atomic-alignment [device]
  nil)

(defn info-preferred-interop-user-sync [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_INTEROP_USER_SYNC))

(defn info-preferred-local-atomic-alignment [device]
  nil)

(defn info-preferred-platform-atomic-alignment [device]
  nil)

(defn info-preferred-vector-width-char [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR))

(defn info-preferred-vector-width-short [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT))

(defn info-preferred-vector-width-int [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT))

(defn info-preferred-vector-width-long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG))

(defn info-preferred-vector-width-float [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT))

(defn info-preferred-vector-width-double [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE))

(defn info-preferred-vector-width-half [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_HALF))

(defn info-printf-buffer-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PRINTF_BUFFER_SIZE))

(defn info-profile [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DEVICE_PROFILE))

(defn info-profiling-timer-resolution [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PROFILING_TIMER_RESOLUTION))

(defn info-queue-on-device-max-size [device]
  nil)

(defn info-queue-on-device-preferred-size [device]
  nil)

(defn info-queue-on-device-properties [device]
  nil)

(defn info-queue-on-host-properties [device]
  nil)

(defn info-reference-count [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_REFERENCE_COUNT))

(defn info-single-fp-config [device]
  (info-mask* CL/clGetDeviceInfo device
              CL/CL_DEVICE_SINGLE_FP_CONFIG
              fp-config-table))

(defn info-spir-versions [device]
  nil)

(defn info-svm-capabilities [device]
  nil)

(defn info-terminate-capability-khr [device]
  nil)

(defn info-device-type [device]
  (info-mask-one* CL/clGetDeviceInfo device
                  CL/CL_DEVICE_TYPE
                  device-types))

(defn info-vendor [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DEVICE_VENDOR))

(defn info-vendor-id [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_VENDOR_ID))

(defn info-device-version [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DEVICE_VERSION))

(defn info-driver-version [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DRIVER_VERSION))

(def device-info-table
  {:address-bits info-address-bits
   :available info-available
   :built-in-kernels info-built-in-kernels
   :compiler-available info-compiler-available
   :double-fp-config info-double-fp-config
   :endian-little info-endian-little
   :error-correction-support info-error-correction-support
   :execution-capabilities info-execution-capabilities
   :extensions info-extensions
   :global-mem-cache-size info-global-mem-cache-size
   :global-mem-cache-type info-global-mem-cache-type
   :global-mem-cacheline-size info-global-mem-cacheline-size
   :global-mem-size info-global-mem-size
   :global-variable-preferred-total-size info-global-variable-preferred-total-size
   :image2d-max-height info-image2d-max-height
   :image2d-max-width info-image2d-max-width
   :image3d-max-depth info-image3d-max-depth
   :image3d-max-height info-image3d-max-height
   :image3d-max-width info-image3d-max-width
   :image-base-address-alignment info-image-base-address-alignment
   :image-max-array-size info-image-max-array-size
   :image-max-buffer-size info-image-max-buffer-size
   :image-pitch-alignment info-image-pitch-alignment
   :image-support info-image-support
   :linker-available info-linker-available
   :local-mem-size info-local-mem-size
   :local-mem-type info-local-mem-type
   :max-clock-frequency info-max-clock-frequency
   :max-compute-units info-max-compute-units
   :max-constant-args info-max-constant-args
   :max-constant-buffer-size info-max-constant-buffer-size
   :max-global-variable-size info-max-global-variable-size
   :max-mem-alloc-size info-mem-base-addr-align
   :max-on-device-events info-max-on-device-events
   :max-parameter-queues info-max-on-device-queues
   :max-pipe-args info-max-pipe-args
   :max-read-image-args info-max-read-image-args
   :max-read-write-image-args info-max-read-image-args
   :max-samplers info-max-samplers
   :max-work-group-size info-max-work-group-size
   :max-work-item-dimensions info-max-work-item-dimensions
   :max-work-item-sizes info-max-work-item-sizes
   :max-write-image-args info-max-write-image-args
   :mem-base-addr-align info-mem-base-addr-align
   :name info-name
   :native-vector-width-char info-native-vector-width-char
   :native-vector-width-short info-native-vector-width-short
   :native-vector-width-int info-native-vector-width-int
   :native-vector-width-long info-native-vector-width-long
   :native-vector-width-double info-native-vector-width-double
   :native-vector-width-float info-native-vector-width-float
   :native-vector-width-half info-native-vector-width-half
   :opencl-c-version info-opencl-c-version
   :parent-device info-parent-device
   :partition-affinity-domain info-partition-affinity-domain
   :partition-max-sub-devices info-partition-max-sub-devices
   :partition-properties info-partition-properties
   :partition-type info-partition-type
   :pipe-max-active-reservations info-pipe-max-active-reservations
   :platform info-platform
   :preferred-global-atomic-alignment info-preferred-global-atomic-alignment
   :preferred-interop-user-sync info-preferred-interop-user-sync
   :preferred-local-atomic-alignment info-preferred-local-atomic-alignment
   :preferred-platform-atomic-alignment info-preferred-platform-atomic-alignment
   :preferred-vector-width-char info-preferred-vector-width-char
   :preferred-vector-width-shot info-preferred-vector-width-short
   :preferred-vector-width-int info-preferred-vector-width-int
   :preferred-vector-width-long info-preferred-vector-width-long
   :preferred-vector-width-double info-preferred-vector-width-double
   :preferred-vector-width-float info-preferred-vector-width-float
   :preferred-vector-width-half info-preferred-vector-width-half
   :printf-buffer-size info-printf-buffer-size
   :profile info-profile
   :profiling-timer-resolution info-profiling-timer-resolution
   :queue-on-device-max-size info-queue-on-device-max-size
   :queue-on-device-preferred-size info-queue-on-device-preferred-size
   :queue-on-device-properties info-queue-on-device-properties
   :queue-on-host-properties info-queue-on-host-properties
   :reference-count info-reference-count
   :single-fp-config info-single-fp-config
   :spir-versions info-spir-versions
   :svm-capabilities info-svm-capabilities
   :terminate-capability-khr info-terminate-capability-khr
   :device-type info-device-type
   :vendor info-vendor
   :vendor-id info-vendor-id
   :device-version info-device-version
   :driver-version info-driver-version})

(defn ^:private num-devices*
  [platform ^long device-type]
  (let [res (int-array 1)
        err (CL/clGetDeviceIDs platform device-type 0 nil res)]
    (with-check err
      (aget res 0))))

(defn num-devices
  ([platform device-type]
   (num-devices* platform (device-types device-type)))
  ([x]
   (if (integer? x)
     (num-devices *platform* x)
     (num-devices x CL/CL_DEVICE_TYPE_ALL)))
  ([]
   (num-devices *platform* CL/CL_DEVICE_TYPE_ALL)))

(defn num-gpus
  ([platform]
   (num-devices* platform CL/CL_DEVICE_TYPE_GPU))
  ([]
   (num-gpus *platform*)))

(defn ^:private devices* [platform ^long device-type]
  (let [num-devices (num-devices* platform device-type)
        res (make-array cl_device_id num-devices)
        err (CL/clGetDeviceIDs platform device-type
                               num-devices res nil)]
    (with-check err res)))

(defn devices
  ([platform device-type]
   (vec (devices* platform (device-types device-type))))
  ([x]
   (if (integer? x)
     (devices *platform* x)
     (vec (devices* x CL/CL_DEVICE_TYPE_ALL))))
  ([]
   (devices *platform*)))

(defn device-info
  ([device info]
   (if-let [info-f (device-info-table info)]
     (info-f device)
     nil))
  ([device]
   (fmap #(% device) device-info-table)))

;; ============= Context ===========================================
(defn context-properties [props]
  (reduce (fn [^cl_context_properties cp [p v]]
            (doto cp (.addProperty (context-prop-table p) v)))
          (cl_context_properties.)
          props))

;;TODO Callback function
(defn ^:private context* [^objects devices ^objects properties]
  (let [err (int-array 1)
        res (CL/clCreateContext properties
                                (alength devices) devices
                                nil nil err)]
    (with-check-arr err res)))

(defn context
  ([devices properties]
   (context* (into-array devices) (context-properties properties)))
  ([devices]
   (context* (into-array devices) nil)))

(defn info-num-devices
  ([context]
   (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_NUM_DEVICES))
  ([]
   (info-num-devices *context*)))

(defn info-num-reference-count
  ([context]
   (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_REFERENCE_COUNT))
  ([]
   (info-num-reference-count *context*)))

(defn info-devices
  ([context]
   (vec (info-native* CL/clGetContextInfo context CL/CL_CONTEXT_DEVICES
                      cl_device_id Sizeof/cl_device_id)))
  ([]
   (info-devices *context*)))

;; TODO see how to retreive variable length objects.
(defn info-context-properties
  ([context]
   nil)
  ([]
   (info-context-properties *context*)))

(def context-info-table
  {:num-devices info-num-devices
   :num-reference-count info-num-reference-count
   :devices info-devices
   :properties info-context-properties})

(defn context-info
  ([context info]
   (if-let [info-f (context-info-table info)]
     (info-f context)
     nil))
  ([x]
   (fmap #(% context) context-info-table))
  ([]
   (context-info *context*)))

;;TODO assert
(defmacro with-context [context & body]
  `(binding [*context* ~context]
     (try ~@body
          (finally (close *context*)))))

;; ============= Program ==========================================
(defn program-with-source
  ([context source]
   (let [err (int-array 1)
         n (count source)
         res (CL/clCreateProgramWithSource
              context n (into-array String source) nil err)]
     (with-check-arr err res)))
  ([source]
   (program-with-source *context* source)))

;; TODO Callback function
(defn build-program! [program]
  (let [err (CL/clBuildProgram program 0 nil nil nil nil)]
    (with-check err program)))

;; ============== Kernel =========================================
(defn num-kernels [program]
  (let [res (int-array 1)
        err (CL/clCreateKernelsInProgram program 0 nil res)]
    (with-check err (aget res 0))))

(defn kernels
  ([program ^String name]
   (let [err (int-array 1)
         res (CL/clCreateKernel program name err)]
     (with-check-arr err res)))
  ([program]
   (let [nk (num-kernels program)
         res (make-array cl_kernel nk)
         err (CL/clCreateKernelsInProgram program nk res nil)]
     (vec (with-check err res)))))

(defn set-arg! [kernel ^long n arg]
  (with-check (CL/clSetKernelArg kernel n Sizeof/cl_mem arg) kernel))

;;  ============== Command Queue ===============================
;; TODO clCreateCommandQueue is deprecated in JOCL 0.2.0 use ccqWithProperties
(defn command-queue
  ([context device properties]
   (let [err (int-array 1)
         res (CL/clCreateCommandQueue context device properties err)]
     (with-check-arr err res)))
  ([device properties]
   (command-queue *context* device properties)))

(defn enqueue-nd-range [queue kernel work-dim
                        global-work-offset global-work-size local-work-size
                        num-events-in-wait-lis event-wait-list event]
  (with-check
    (CL/clEnqueueNDRangeKernel queue kernel work-dim
                               global-work-offset
                               global-work-size
                               local-work-size
                               num-events-in-wait-lis
                               event-wait-list
                               event)
    queue))

(defn enqueue-read-buffer
  [queue buffer blocking-read offset cb ptr num-events-in-wait-list
   event-wait-list event]
  (with-check
    (CL/clEnqueueReadBuffer queue buffer blocking-read offset
                            cb ptr num-events-in-wait-list
                            event-wait-list event)
    queue))

(defn enqueue-map-buffer
  [queue buffer blocking flags offset data-size num-events
   wait-list event]
  (let [err (int-array 1)
        res (CL/clEnqueueMapBuffer queue buffer blocking flags offset
                                   data-size num-events
                                   wait-list event err)]
    (with-check-arr err res)))

(defn enqueue-unmap-mem-object
  [queue mem-object mapped-ptr num-events-in-event-list wait-list event]
  (let [err (CL/clEnqueueUnmapMemObject
             queue mem-object mapped-ptr
             num-events-in-event-list wait-list event)]
    (with-check err queue)))

(defmacro with-queue [queue & body]
  `(binding [*command-queue* ~queue]
     (try ~@body
          (finally (close *queue*)))))
;; =============== Data =========================================
;; TODO
(defn float-buffer
  ([context _ ^floats o flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer context (apply bit-or flags)
                                (* Sizeof/cl_float (alength o))
                                (Pointer/to o) err)]
     (with-check-arr err res)))
  ([^long n flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer *context* (apply bit-or flags)
                                (* Sizeof/cl_float n)
                                nil err)]
     (with-check-arr err res))))
;; ================================================================
