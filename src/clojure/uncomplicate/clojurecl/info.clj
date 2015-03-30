(ns uncomplicate.clojurecl.info
  (:require [clojure.string :as str]
            [uncomplicate.clojurecl.utils :refer :all]
            [vertigo
             [bytes :refer [buffer byte-seq byte-count]]
             [structs :refer [int32 int64 wrap-byte-seq]]])
  (:import [org.jocl CL cl_platform_id  cl_device_id cl_context cl_command_queue
            cl_mem cl_program cl_kernel cl_sampler cl_event
            cl_device_partition_property
            Sizeof Pointer]
           [java.nio ByteBuffer]))

;; TODO OpenCL 2.0
(def CL_QUEUE_ON_DEVICE (bit-shift-left 1 2))
(def CL_QUEUE_ON_DEVICE_DEFAULT (bit-shift-left 1 3))
(def CL_DEVICE_GLOBAL_VARIABLE_PREFERRED_TOTAL_SIZE 0x1054)
(def CL_DEVICE_IMAGE_BASE_ADDRESS_ALIGNMENT 0x104B)

(def CL_DEVICE_IMAGE_PITCH_ALIGNMENT 0x104A)
(def CL_DEVICE_MAX_GLOBAL_VARIABLE_SIZE 0x104D)
(def CL_DEVICE_MAX_ON_DEVICE_EVENTS 0x1052)
(def CL_DEVICE_MAX_ON_DEVICE_QUEUES 0x1051)
(def CL_DEVICE_MAX_PIPE_ARGS 0x1055)
(def CL_DEVICE_MAX_READ_WRITE_IMAGE_ARGS 0x104C)
(def CL_DEVICE_PIPE_MAX_ACTIVE_RESERVATIONS 0x1056)
(def CL_DEVICE_PIPE_MAX_PACKET_SIZE 0x1057)
(def CL_DEVICE_PREFERRED_GLOBAL_ATOMIC_ALIGNMENT 0x1059)
(def CL_DEVICE_PREFERRED_LOCAL_ATOMIC_ALIGNMENT 0x105A)
(def CL_DEVICE_PREFERRED_PLATFORM_ATOMIC_ALIGNMENT 0x1058)
(def CL_DEVICE_QUEUE_ON_DEVICE_MAX_SIZE 0x1050)
(def CL_DEVICE_QUEUE_ON_DEVICE_PREFERRED_SIZE 0x104F)
(def CL_DEVICE_QUEUE_ON_DEVICE_PROPERTIES 0x104E)
(def CL_DEVICE_QUEUE_ON_HOST_PROPERTIES 0x102A)
(def CL_DEVICE_SPIR_VERSIONS 0x40E0)
(def CL_DEVICE_SVM_CAPABILITIES 0x1053)
(def CL_DEVICE_SVM_COARSE_GRAIN_BUFFER (bit-shift-left 1 0) )
(def CL_DEVICE_SVM_FINE_GRAIN_BUFFER (bit-shift-left 1 1));
(def CL_DEVICE_SVM_FINE_GRAIN_SYSTEM (bit-shift-left 1 2))
(def CL_DEVICE_SVM_ATOMICS (bit-shift-left 1 3))
(def CL_TERMINATE_CAPABILITY_KHR 0x200F)

(def CL_QUEUE_SIZE 0x1094)

(def CL_MEM_USES_SVM_POINTER 0x1109)

;; =================== Info* utility macros ===============================

(defmacro ^:private info-count*
  ([method clobject info sizeof]
   `(/ (info-count* ~method ~clobject ~info) ~sizeof))
  ([method clobject info]
   `(long (let [res# (long-array 1)
                err# (~method ~clobject ~info 0 nil res#)]
            (with-check err# (aget res# 0))))))

(defmacro ^:private info-string* [method clobject info]
  `(let [size# (info-count* ~method ~clobject ~info)
         res# (byte-array size#)
         err# (~method ~clobject ~info
                       (alength res#) (Pointer/to res#)
                       nil)]
     (with-check err#
       (String. res# 0 (dec size#)))))

(defn ^:private to-set [s]
  (if (str/blank? s)
    #{}
    (apply hash-set (str/split s #" "))))

(let [native-pointer (fn [^"[Lorg.jocl.NativePointerObject;" np]
                       (Pointer/to np))]
  (defmacro ^:private info-native* [method clobject info type size]
    `(let [bytesize# (info-count* ~method ~clobject ~info)
           res# (make-array ~type (/ bytesize# ~size))
           err# (~method ~clobject ~info
                         bytesize# (~native-pointer res#)
                         nil)]
       (with-check err# res#))))

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

;; =================== Protocols ==================================

(defprotocol Info
  (info [this info-type] [this]))

(defprotocol InfoExtensions
  (extensions [this]))

(defprotocol InfoName
  (name-info [this]))

(defprotocol InfoProfile
  (profile [this]))

(defprotocol InfoVendor
  (vendor [this]))

(defprotocol InfoReferenceCount
  (reference-count [this]))

(defprotocol InfoProperties
  (properties [this]))

;; =================== Platform ===================================

(defn version [platform]
  (info-string* CL/clGetPlatformInfo platform CL/CL_PLATFORM_VERSION))

(defn icd-suffix-khr [platform]
  (info-string* CL/clGetPlatformInfo platform CL/CL_PLATFORM_ICD_SUFFIX_KHR))

(defrecord PlatformInfo [profile version name vendor extensions icd-suffix-khr])

(extend-type cl_platform_id
  Info
  (info
    ([p info-type]
     (maybe
      (case info-type
        :profile (profile p)
        :version (version p)
        :name (name-info p)
        :vendor (vendor p)
        :extensions (extensions p)
        :icd-suffix-khr (icd-suffix-khr p)
        nil)))
    ([p]
     (->PlatformInfo (maybe (profile p)) (maybe (version p)) (maybe (name-info p))
                     (maybe (vendor p)) (maybe (extensions p))
                     (maybe (icd-suffix-khr p)))))
  InfoExtensions
  (extensions [p]
    (to-set (info-string* CL/clGetPlatformInfo p CL/CL_PLATFORM_EXTENSIONS)))
  InfoName
  (name-info [p]
    (info-string* CL/clGetPlatformInfo p CL/CL_PLATFORM_NAME))
  InfoProfile
  (profile [p]
    (info-string* CL/clGetPlatformInfo p CL/CL_PLATFORM_PROFILE))
  InfoVendor
  (vendor [p]
    (info-string* CL/clGetPlatformInfo p CL/CL_PLATFORM_VENDOR)))

;; =================== Device ==============================================

(defn address-bits [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_ADDRESS_BITS))

(defn available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_AVAILABLE))

(defn built-in-kernels [device]
  (to-set (info-string* CL/clGetDeviceInfo device
                       CL/CL_DEVICE_BUILT_IN_KERNELS)))

(defn compiler-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_COMPILER_AVAILABLE))

(def fp-config
  {:denorm CL/CL_FP_DENORM
   :inf-nan CL/CL_FP_INF_NAN
   :round-to-nearest CL/CL_FP_ROUND_TO_NEAREST
   :round-to-zero CL/CL_FP_ROUND_TO_ZERO
   :round-to-inf CL/CL_FP_ROUND_TO_INF
   :fma CL/CL_FP_FMA
   :correctly-rounded-divide-sqrt CL/CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
   :soft-float CL/CL_FP_SOFT_FLOAT})

(defn double-fp-config [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_DOUBLE_FP_CONFIG))

(defn endian-little [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ENDIAN_LITTLE))

(defn error-correction-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ERROR_CORRECTION_SUPPORT))

(def exec-capabilities
  {:kernel CL/CL_EXEC_KERNEL
   :native-kernel CL/CL_EXEC_NATIVE_KERNEL})

(defn execution-capabilities [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_EXECUTION_CAPABILITIES))

(defn global-mem-cache-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHE_SIZE))

(def device-mem-cache-type
  {:none CL/CL_NONE
   :read-only CL/CL_READ_ONLY_CACHE
   :read-write CL/CL_READ_WRITE_CACHE})

(defn global-mem-cache-type [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHE_TYPE))

(defn global-mem-cacheline-size [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHELINE_SIZE))

(defn global-mem-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_SIZE))

(defn global-variable-preferred-total-size [device]
  (info-size* CL/clGetDeviceInfo device
              CL_DEVICE_GLOBAL_VARIABLE_PREFERRED_TOTAL_SIZE))

(defn image2d-max-height [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE2D_MAX_HEIGHT))

(defn image2d-max-width [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE2D_MAX_WIDTH))

(defn image3d-max-depth [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_DEPTH))

(defn image3d-max-height [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_HEIGHT))

(defn image3d-max-width [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_WIDTH))

(defn image-base-address-alignment [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_IMAGE_BASE_ADDRESS_ALIGNMENT))

(defn image-max-array-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_ARRAY_SIZE))

(defn image-max-buffer-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_BUFFER_SIZE))

(defn image-pitch-alignment [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_IMAGE_PITCH_ALIGNMENT))

(defn image-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_SUPPORT))

(defn linker-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_LINKER_AVAILABLE))

(defn local-mem-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_LOCAL_MEM_SIZE))

(def cl-local-mem-type
  {:local CL/CL_LOCAL
   :global CL/CL_GLOBAL
   :none CL/CL_NONE})

(defn local-mem-type [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_LOCAL_MEM_TYPE))

(defn max-clock-frequency [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CLOCK_FREQUENCY))

(defn max-compute-units [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_COMPUTE_UNITS))

(defn max-constant-args [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_ARGS))

(defn max-constant-buffer-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE))

(defn max-global-variable-size [device]
  (info-size* CL/clGetDeviceInfo device CL_DEVICE_MAX_GLOBAL_VARIABLE_SIZE))

(defn max-mem-aloc-size [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_MEM_ALLOC_SIZE))

(defn max-on-device-events [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_MAX_ON_DEVICE_EVENTS))

(defn max-on-device-queues [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_MAX_ON_DEVICE_QUEUES))

(defn max-parameter-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_PARAMETER_SIZE))

(defn max-pipe-args [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_MAX_PIPE_ARGS))

(defn max-read-image-args [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_READ_IMAGE_ARGS))

(defn max-read-write-image-args [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_MAX_READ_WRITE_IMAGE_ARGS))

(defn max-samplers [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_SAMPLERS))

(defn max-work-group-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_GROUP_SIZE))

(defn max-work-item-dimensions [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS))

(defn max-work-item-sizes [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_ITEM_SIZES
              (max-work-item-dimensions device)))

(defn max-write-image-args [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WRITE_IMAGE_ARGS))

(defn mem-base-addr-align [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MEM_BASE_ADDR_ALIGN))

(defn native-vector-width-char [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_CHAR))

(defn native-vector-width-short [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_SHORT))

(defn native-vector-width-int [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_INT))

(defn native-vector-width-long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_LONG))

(defn native-vector-width-float [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_FLOAT))

(defn native-vector-width-double [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_DOUBLE))

(defn native-vector-width-half [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_HALF))

(defn opencl-c-version [device]
  (let [info (str/split (info-string* CL/clGetDeviceInfo device
                                      CL/CL_DEVICE_OPENCL_C_VERSION)
                        #" ")]
    {:version (info 2)
     :vendor-specific-info (get info 3)}))

(defn parent-device [device]
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

(def affinity-domain
  {:numa CL/CL_DEVICE_AFFINITY_DOMAIN_NUMA
   :l1-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L1_CACHE
   :l2-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L2_CACHE
   :l3-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L3_CACHE
   :l4-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L4_CACHE
   :next-partitionable CL/CL_DEVICE_AFFINITY_DOMAIN_NEXT_PARTITIONABLE})

(defn partition-affinity-domain [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_PARTITION_AFFINITY_DOMAIN))

(defn partition-max-sub-devices [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_MAX_SUB_DEVICES))

(defn partition-property [^long code]
  (case code
    0x1086 :partition-equally
    0x1087 :partition-by-counts
    0x1088 :partition-by-affinity-domain))

(defn partition-properties [device]
  (map partition-property
       (info-long* CL/clGetDeviceInfo device
                   CL/CL_DEVICE_PARTITION_PROPERTIES
                   (info-count* CL/clGetDeviceInfo device
                              CL/CL_DEVICE_PARTITION_PROPERTIES
                              Sizeof/cl_long))))

;;TODO
(defn partition-type [device]
  (map partition-property
       (info-long* CL/clGetDeviceInfo device
                   CL/CL_DEVICE_PARTITION_TYPE
                   (info-count* CL/clGetDeviceInfo device
                              CL/CL_DEVICE_PARTITION_TYPE
                              Sizeof/cl_long))) )

(defn pipe-max-active-reservations [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_PIPE_MAX_ACTIVE_RESERVATIONS))

(defn pipe-max-packet-size [device]
  (info-long* CL/clGetDeviceInfo device CL_DEVICE_PIPE_MAX_PACKET_SIZE))

(defn platform [device]
  (let [p (cl_platform_id.)
        err (CL/clGetDeviceInfo device CL/CL_DEVICE_PLATFORM
                                Sizeof/cl_platform_id
                                (Pointer/to p)
                                nil)]
    (with-check err p)))

(defn preferred-global-atomic-alignment [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_PREFERRED_GLOBAL_ATOMIC_ALIGNMENT))

(defn preferred-interop-user-sync [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_INTEROP_USER_SYNC))

(defn preferred-local-atomic-alignment [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_PREFERRED_LOCAL_ATOMIC_ALIGNMENT))

(defn preferred-platform-atomic-alignment [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_PREFERRED_PLATFORM_ATOMIC_ALIGNMENT))

(defn preferred-vector-width-char [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR))

(defn preferred-vector-width-short [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT))

(defn preferred-vector-width-int [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT))

(defn preferred-vector-width-long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG))

(defn preferred-vector-width-float [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT))

(defn preferred-vector-width-double [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE))

(defn preferred-vector-width-half [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_HALF))

(defn printf-buffer-size [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PRINTF_BUFFER_SIZE))

(defn profiling-timer-resolution [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PROFILING_TIMER_RESOLUTION))

(defn queue-on-device-max-size [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_QUEUE_ON_DEVICE_MAX_SIZE))

(defn queue-on-device-preferred-size [device]
  (info-int* CL/clGetDeviceInfo device CL_DEVICE_QUEUE_ON_DEVICE_PREFERRED_SIZE))

(def queue-properties
  {:out-of-order-exec-mode CL/CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE
   :profiling CL/CL_QUEUE_PROFILING_ENABLE})

(defn queue-on-device-properties [device]
  (info-long* CL/clGetDeviceInfo device CL_DEVICE_QUEUE_ON_DEVICE_PROPERTIES))

(defn queue-on-host-properties [device]
  (info-long* CL/clGetDeviceInfo device CL_DEVICE_QUEUE_ON_HOST_PROPERTIES))

(defn single-fp-config [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_SINGLE_FP_CONFIG))

(defn spir-versions [device]
  (to-set (info-string* CL/clGetDeviceInfo device CL_DEVICE_SPIR_VERSIONS)))

(def svm-capabilities-table
  {:coarse-grain-buffer CL_DEVICE_SVM_COARSE_GRAIN_BUFFER
   :fine-grain-buffer CL_DEVICE_SVM_FINE_GRAIN_BUFFER
   :fine-grain-system CL_DEVICE_SVM_FINE_GRAIN_SYSTEM
   :svm-atomics CL_DEVICE_SVM_ATOMICS})

(defn svm-capabilities [device]
  (info-long* CL/clGetDeviceInfo device CL_DEVICE_SVM_CAPABILITIES))

(defn terminate-capability-khr [device]
  (info-long* CL/clGetDeviceInfo device CL_TERMINATE_CAPABILITY_KHR))

(def cl-device-type
  {:gpu CL/CL_DEVICE_TYPE_GPU
   :cpu CL/CL_DEVICE_TYPE_CPU
   :all CL/CL_DEVICE_TYPE_ALL
   :default CL/CL_DEVICE_TYPE_DEFAULT
   :accelerator CL/CL_DEVICE_TYPE_ACCELERATOR
   :custom CL/CL_DEVICE_TYPE_CUSTOM})

(defn device-type [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_TYPE))

(defn vendor-id [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_VENDOR_ID))

(defn device-version [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DEVICE_VERSION))

(defn driver-version [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DRIVER_VERSION))

(defrecord DeviceInfo
    [address-bits
     available
     built-in-kernels
     compiler-available
     double-fp-config
     endian-little
     error-correction-support
     execution-capabilities
     extensions
     global-mem-cache-size
     global-mem-cache-type
     global-mem-cacheline-size
     global-mem-size
     global-variable-preferred-total-size
     image2d-max-height
     image2d-max-width
     image3d-max-depth
     image3d-max-height
     image3d-max-width
     image-base-address-alignment
     image-max-array-size
     image-max-buffer-size
     image-pitch-alignment
     image-support
     linker-available
     local-mem-size
     local-mem-type
     max-clock-frequency
     max-compute-units
     max-constant-args
     max-constant-buffer-size
     max-global-variable-size
     max-mem-aloc-size
     max-on-device-events
     max-on-device-queues
     max-parameter-size
     max-pipe-args
     max-read-image-args
     max-read-write-image-args
     max-samplers
     max-work-group-size
     max-work-item-dimensions
     max-work-item-sizes
     max-write-image-args
     mem-base-addr-align
     name
     native-vector-width-char
     native-vector-width-short
     native-vector-width-int
     native-vector-width-long
     native-vector-width-double
     native-vector-width-float
     native-vector-width-half
     opencl-c-version
     parent-device
     partition-affinity-domain
     partition-max-sub-devices
     partition-properties
     partition-type
     pipe-max-active-reservations
     pipe-max-packet-size
     platform
     preferred-global-atomic-alignment
     preferred-interop-user-sync
     preferred-local-atomic-alignment
     preferred-platform-atomic-alignment
     preferred-vector-width-char
     preferred-vector-width-short
     preferred-vector-width-int
     preferred-vector-width-long
     preferred-vector-width-double
     preferred-vector-width-float
     preferred-vector-width-half
     printf-buffer-size
     profile
     profiling-timer-resolution
     queue-on-device-max-size
     queue-on-device-preferred-size
     queue-on-device-properties
     queue-on-host-properties
     reference-count
     single-fp-config
     spir-versions
     svm-capabilities
     terminate-capability-khr
     device-type
     vendor
     vendor-id
     device-version
     driver-version])

(extend-type cl_device_id
  Info
  (info
    ([d info-type]
     (maybe
      (case info-type
        :address-bits (address-bits d)
        :available (available d)
        :built-in-kernels (built-in-kernels d)
        :compiler-available (compiler-available d)
        :double-fp-config (unmask fp-config (double-fp-config d))
        :endian-little (endian-little d)
        :error-correction-support (error-correction-support d)
        :execution-capabilities
        (set (unmask exec-capabilities (execution-capabilities d)))
        :extensions (extensions d)
        :global-mem-cache-size (global-mem-cache-size d)
        :global-mem-cache-type
        (unmask1 device-mem-cache-type (global-mem-cache-type d))
        :global-mem-cacheline-size (global-mem-cacheline-size d)
        :global-mem-size (global-mem-size d)
        :global-variable-preferred-total-size
        (global-variable-preferred-total-size d)
        :image2d-max-height (image2d-max-height d)
        :image2d-max-width (image2d-max-width d)
        :image3d-max-depth (image3d-max-depth d)
        :image3d-max-height (image3d-max-height d)
        :image3d-max-width (image3d-max-width d)
        :image-base-address-alignment (image-base-address-alignment d)
        :image-max-array-size (image-max-array-size d)
        :image-max-buffer-size (image-max-buffer-size d)
        :image-pitch-alignment (image-pitch-alignment d)
        :image-support (image-support d)
        :linker-available (linker-available d)
        :local-mem-size (local-mem-size d)
        :local-mem-type
        (unmask1 cl-local-mem-type (local-mem-type d))
        :max-clock-frequency (max-clock-frequency d)
        :max-compute-units (max-compute-units d)
        :max-constant-args (max-constant-args d)
        :max-constant-buffer-size (max-constant-buffer-size d)
        :max-global-variable-size (max-global-variable-size d)
        :max-mem-alloc-size (max-mem-aloc-size d)
        :max-on-device-events (max-on-device-events d)
        :max-parameter-queues (max-on-device-queues d)
        :max-parameter-size (max-parameter-size d)
        :max-pipe-args (max-pipe-args d)
        :max-read-image-args (max-read-image-args d)
        :max-read-write-image-args (max-read-write-image-args d)
        :max-samplers (max-samplers d)
        :max-work-group-size (max-work-group-size d)
        :max-work-item-dimensions (max-work-item-dimensions d)
        :max-work-item-sizes (max-work-item-sizes d)
        :max-write-image-args (max-write-image-args d)
        :mem-base-addr-align (mem-base-addr-align d)
        :name (name-info d)
        :native-vector-width-char (native-vector-width-char d)
        :native-vector-width-short (native-vector-width-short d)
        :native-vector-width-int (native-vector-width-int d)
        :native-vector-width-long (native-vector-width-long d)
        :native-vector-width-double (native-vector-width-double d)
        :native-vector-width-float (native-vector-width-float d)
        :native-vector-width-half (native-vector-width-half d)
        :opencl-c-version (opencl-c-version d)
        :parent-device (parent-device d)
        :partition-affinity-domain
        (set (unmask affinity-domain (partition-affinity-domain d)))
        :partition-max-sub-devices (partition-max-sub-devices d)
        :partition-properties (partition-properties d)
        :partition-type (partition-type d)
        :pipe-max-active-reservations (pipe-max-active-reservations d)
        :pipe-max-packet-size (pipe-max-packet-size d)
        :platform (platform d)
        :preferred-global-atomic-alignment (preferred-global-atomic-alignment d)
        :preferred-interop-user-sync (preferred-interop-user-sync d)
        :preferred-local-atomic-alignment (preferred-local-atomic-alignment d)
        :preferred-platform-atomic-alignment (preferred-platform-atomic-alignment d)
        :preferred-vector-width-char (preferred-vector-width-char d)
        :preferred-vector-width-short (preferred-vector-width-short d)
        :preferred-vector-width-int (preferred-vector-width-int d)
        :preferred-vector-width-long (preferred-vector-width-long d)
        :preferred-vector-width-double (preferred-vector-width-double d)
        :preferred-vector-width-float (preferred-vector-width-float d)
        :preferred-vector-width-half (preferred-vector-width-half d)
        :printf-buffer-size (printf-buffer-size d)
        :profile (profile d)
        :profiling-timer-resolution (profiling-timer-resolution d)
        :queue-on-device-max-size (queue-on-device-max-size d)
        :queue-on-device-preferred-size (queue-on-device-preferred-size d)
        :queue-on-device-properties
        (set (unmask queue-properties (queue-on-device-properties d)))
        :queue-on-host-properties
        (set (unmask queue-properties (queue-on-host-properties d)))
        :reference-count (reference-count d)
        :single-fp-config
        (set (unmask fp-config (single-fp-config d)))
        :spir-versions (spir-versions d)
        :svm-capabilities
        (set (unmask svm-capabilities-table (svm-capabilities d)))
        :terminate-capability-khr (terminate-capability-khr d)
        :device-type
        (unmask1 cl-device-type (device-type d))
        :vendor (vendor d)
        :vendor-id (vendor-id d)
        :device-version (device-version d)
        "driver-version" (driver-version d)
        nil)))
    ([d]
     (->DeviceInfo
      (maybe (address-bits d))
      (maybe (available d))
      (maybe (built-in-kernels d))
      (maybe (compiler-available d))
      (maybe (double-fp-config d))
      (maybe (endian-little d))
      (maybe (error-correction-support d))
      (maybe (set (unmask exec-capabilities (execution-capabilities d))))
      (maybe (extensions d))
      (maybe (global-mem-cache-size d))
      (maybe (unmask1 device-mem-cache-type (global-mem-cache-type d)))
      (maybe (global-mem-cacheline-size d))
      (maybe (global-mem-size d))
      (maybe (global-variable-preferred-total-size d))
      (maybe (image2d-max-height d))
      (maybe (image2d-max-width d))
      (maybe (image3d-max-depth d))
      (maybe (image3d-max-height d))
      (maybe (image3d-max-width d))
      (maybe (image-base-address-alignment d))
      (maybe (image-max-array-size d))
      (maybe (image-max-buffer-size d))
      (maybe (image-pitch-alignment d))
      (maybe (image-support d))
      (maybe (linker-available d))
      (maybe (local-mem-size d))
      (maybe (unmask1 cl-local-mem-type (local-mem-type d)))
      (maybe (max-clock-frequency d))
      (maybe (max-compute-units d))
      (maybe (max-constant-args d))
      (maybe (max-constant-buffer-size d))
      (maybe (max-global-variable-size d))
      (maybe (max-mem-aloc-size d))
      (maybe (max-on-device-events d))
      (maybe (max-on-device-queues d))
      (maybe (max-parameter-size d))
      (maybe (max-pipe-args d))
      (maybe (max-read-image-args d))
      (maybe (max-read-write-image-args d))
      (maybe (max-samplers d))
      (maybe (max-work-group-size d))
      (maybe (max-work-item-dimensions d))
      (maybe (max-work-item-sizes d))
      (maybe (max-write-image-args d))
      (maybe (mem-base-addr-align d))
      (maybe (name-info d))
      (maybe (native-vector-width-char d))
      (maybe (native-vector-width-short d))
      (maybe (native-vector-width-int d))
      (maybe (native-vector-width-long d))
      (maybe (native-vector-width-double d))
      (maybe (native-vector-width-float d))
      (maybe (native-vector-width-half d))
      (maybe (opencl-c-version d))
      (maybe (parent-device d))
      (maybe (set (unmask affinity-domain (partition-affinity-domain d))))
      (maybe (partition-max-sub-devices d))
      (maybe (partition-properties d))
      (maybe (partition-type d))
      (maybe (pipe-max-active-reservations d))
      (maybe (pipe-max-packet-size d))
      (maybe (platform d))
      (maybe (preferred-global-atomic-alignment d))
      (maybe (preferred-interop-user-sync d))
      (maybe (preferred-local-atomic-alignment d))
      (maybe (preferred-platform-atomic-alignment d))
      (maybe (preferred-vector-width-char d))
      (maybe (preferred-vector-width-short d))
      (maybe (preferred-vector-width-int d))
      (maybe (preferred-vector-width-long d))
      (maybe (preferred-vector-width-double d))
      (maybe (preferred-vector-width-float d))
      (maybe (preferred-vector-width-half d))
      (maybe (printf-buffer-size d))
      (maybe (profile d))
      (maybe (profiling-timer-resolution d))
      (maybe (queue-on-device-max-size d))
      (maybe (queue-on-device-preferred-size d))
      (maybe (set (unmask queue-properties (queue-on-device-properties d))))
      (maybe (set (unmask queue-properties (queue-on-host-properties d))))
      (maybe (reference-count d))
      (maybe (set (unmask fp-config (single-fp-config d))))
      (maybe (spir-versions d))
      (maybe (set (unmask svm-capabilities-table (svm-capabilities d))))
      (maybe (terminate-capability-khr d))
      (maybe (unmask1 cl-device-type (device-type d)))
      (maybe (vendor d))
      (maybe (vendor-id d))
      (maybe (device-version d))
      (maybe (driver-version d)))))
  InfoExtensions
  (extensions [d]
    (to-set (info-string* CL/clGetDeviceInfo d CL/CL_DEVICE_EXTENSIONS)))
  InfoName
  (name-info [d]
    (info-string* CL/clGetDeviceInfo d CL/CL_DEVICE_NAME))
  InfoProfile
  (profile [d]
    (info-string* CL/clGetDeviceInfo d CL/CL_DEVICE_PROFILE))
  InfoVendor
  (vendor [d]
    (info-string* CL/clGetDeviceInfo d CL/CL_DEVICE_VENDOR))
  InfoReferenceCount
  (reference-count [d]
    (info-int* CL/clGetDeviceInfo d CL/CL_DEVICE_REFERENCE_COUNT)))

;; =================== Context =============================================
(defn num-reference-count [context]
  (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_REFERENCE_COUNT))

(defn num-devices-in-context [context]
  (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_NUM_DEVICES))

(defn devices-in-context [context]
  (vec (info-native* CL/clGetContextInfo context CL/CL_CONTEXT_DEVICES
                     cl_device_id Sizeof/cl_device_id)))

(defn context-property [^long code]
  (case code
    0x1004 :platform
    0x1005 :interop-user-sync))

(defrecord ContextInfo [num-devices num-reference-count devices properties])

(extend-type cl_context
  Info
  (info
    ([c info-type]
     (maybe
      (case info-type
        :num-devices (num-devices-in-context c)
        :num-reference-count (num-reference-count c)
        :devices (devices-in-context c)
        :properties (properties c)
        nil)))
    ([c]
     (->ContextInfo (maybe (num-devices-in-context c))
                    (maybe (num-reference-count c))
                    (maybe (devices-in-context c))
                    (maybe (properties c)))))
  InfoProperties
  (properties [c]
    (map context-property
         (info-long* CL/clGetContextInfo c
                     CL/CL_CONTEXT_PROPERTIES
                     (info-count* CL/clGetContextInfo c
                                CL/CL_CONTEXT_PROPERTIES
                                Sizeof/cl_long)))))

;; =================== Command Queue =======================================

(defn queue-context [queue]
  (let [c (cl_context.)
        err (CL/clGetCommandQueueInfo queue CL/CL_QUEUE_CONTEXT
                                      Sizeof/cl_context
                                      (Pointer/to c)
                                      nil)]
    (with-check err c)))

(defn queue-device [queue]
  (let [d (cl_device_id.)
        err (CL/clGetCommandQueueInfo queue CL/CL_QUEUE_DEVICE
                                      Sizeof/cl_device_id
                                      (Pointer/to d)
                                      nil)]
    (with-check err d)))

(defn queue-size [queue]
  (info-int* CL/clGetCommandQueueInfo queue CL_QUEUE_SIZE))

(defrecord CommandQueueInfo [context device reference-count
                             properties size])

(def queue-properties
  {:out-of-order-exec-mode CL/CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE
   :profiling CL/CL_QUEUE_PROFILING_ENABLE
   :queue-on-device CL_QUEUE_ON_DEVICE
   :queue-on-device-default CL_QUEUE_ON_DEVICE_DEFAULT})

(extend-type cl_command_queue
  Info
  (info
    ([cq info-type]
     (maybe
      (case info-type
        :context (queue-context cq)
        :device (queue-device cq)
        :reference-count (reference-count cq)
        :properties (unmask queue-properties (properties cq))
        :size (queue-size cq)
        nil)))
    ([cq]
     (->CommandQueueInfo (maybe (queue-context cq)) (maybe (queue-device cq))
                         (maybe (reference-count cq))
                         (maybe (unmask queue-properties (properties cq)))
                         (maybe (queue-size cq)))))
  InfoReferenceCount
  (reference-count [cq]
    (info-int* CL/clGetCommandQueueInfo cq CL/CL_QUEUE_REFERENCE_COUNT))
  InfoProperties
  (properties [cq]
    (info-long* CL/clGetCommandQueueInfo cq CL/CL_QUEUE_PROPERTIES)))

;; =================== Event ===============================================

(defn event-command-queue [event]
  (let [cq (cl_command_queue.)
        err (CL/clGetEventInfo event CL/CL_EVENT_COMMAND_QUEUE
                               Sizeof/cl_command_queue
                               (Pointer/to cq)
                               nil)]
    (with-check err cq)))

(defn event-context [event]
  (let [c (cl_context.)
        err (CL/clGetEventInfo event CL/CL_EVENT_CONTEXT
                               Sizeof/cl_context
                               (Pointer/to c)
                               nil)]
    (with-check err c)))

(defn command-type [event]
  (case (info-int* CL/clGetEventInfo event CL/CL_EVENT_COMMAND_TYPE)
    0x11F0 :ndrange-kernel
    0x11F1 :task
    0x11F2 :native-kernel
    0x11F3 :read-buffer
    0x11F4 :write-buffer
    0x11F5 :copy-buffer
    0x11F6 :read-image
    0x11F7 :write-image
    0x11F8 :copy-image
    0x11F9 :copy-image-to-buffer
    0x11FA :copy-buffer-to-image
    0x11FB :map-buffer
    0x11FC :map-image
    0x11FD :unmap-mem-object
    0x11FE :marker
    0x11FF :acquire-gl-objects
    0x1200 :release-gl-objects
    0x1201 :read-buffer-rect
    0x1202 :write-buffer-rect
    0x1203 :copy-buffer-rect
    0x1204 :user
    0x1205 :barrier
    0x1206 :migrate-mem-objects
    0x1207 :fill-buffer
    0x1208 :fill-image
    0x1209 :svm-free
    0x120A :svm-memcpy
    0x120B :svm-memfill
    0x120C :svm-map
    0x120D :svm-unmap
    0x200D :gl-fence-sync-object-khr))

(defn execution-status [event]
  (case (info-int* CL/clGetEventInfo event
                   CL/CL_EVENT_COMMAND_EXECUTION_STATUS)
    0x0 :complete
    0x1 :running
    0x2 :submitted
    0x3 :queued))

(defrecord EventInfo [command-queue context command-type
                      execution-status reference-count])

(extend-type cl_event
  Info
  (info
    ([e info-type]
     (maybe
      (case info-type
        :command-queue (event-command-queue e)
        :context (event-context e)
        :command-type (command-type e)
        :execution-status (execution-status e)
        :reference-count (reference-count e)
        nil)))
    ([e]
     (->EventInfo (maybe (event-command-queue e)) (maybe (event-context e))
                  (maybe (command-type e)) (maybe (execution-status e))
                  (maybe (reference-count e)))))
  InfoReferenceCount
  (reference-count [e]
    (info-int* CL/clGetEventInfo e CL/CL_EVENT_REFERENCE_COUNT)))

;; =================== Event Profiling =====================================

(defn queued ^long [event]
  (info-long* CL/clGetEventProfilingInfo event CL/CL_PROFILING_COMMAND_QUEUED))

(defn submit ^long [event]
  (info-long* CL/clGetEventProfilingInfo event CL/CL_PROFILING_COMMAND_SUBMIT))

(defn start ^long [event]
  (info-long* CL/clGetEventProfilingInfo event CL/CL_PROFILING_COMMAND_START))

(defn end ^long [event]
  (info-long* CL/clGetEventProfilingInfo event CL/CL_PROFILING_COMMAND_END))

(defrecord ProfilingInfo [^long queued ^long submit ^long start ^long end])

(defn profiling-info
  (^long [event info]
         (maybe
          (case info
            :queued (queued event)
            :submit (submit event)
            :start (start event)
            :end (end event)
            nil)))

  ([event]
   (->ProfilingInfo (maybe (queued event)) (maybe (submit event))
                    (maybe (start event)) (maybe (end event)))))

(defn durations [^ProfilingInfo pi]
  (->ProfilingInfo 0
                   (- (.submit pi) (.queued pi))
                   (- (.start pi) (.submit pi))
                   (- (.end pi) (.start pi))))

;; ===================== Image ================================================

;; TODO

;; ===================== Kernel Arg ===========================================

;; TODO

;; ===================== Kernel ===============================================

(defn function-name [kernel]
  (info-string* CL/clGetKernelInfo kernel CL/CL_KERNEL_FUNCTION_NAME))

(defn num-args [kernel]
  (info-int* CL/clGetKernelInfo kernel CL/CL_KERNEL_NUM_ARGS))

(defn kernel-context [kernel]
  (let [c (cl_context.)
        err (CL/clGetKernelInfo kernel CL/CL_KERNEL_CONTEXT
                                Sizeof/cl_context
                                (Pointer/to c)
                                nil)]
    (with-check err c)))

(defn kernel-program [kernel]
  (let [p (cl_program.)
        err (CL/clGetKernelInfo kernel CL/CL_KERNEL_PROGRAM
                                Sizeof/cl_program
                                (Pointer/to p)
                                nil)]
    (with-check err p)))

(defn attributes [kernel]
  (to-set (info-string* CL/clGetKernelInfo kernel CL/CL_KERNEL_ATTRIBUTES)))

(defrecord KernelInfo [function-name num-args reference-count
                       context program attributes])

(extend-type cl_kernel
  Info
  (info
    ([k info-type]
     (maybe
      (case info-type
        :function-name (function-name k)
        :num-args (num-args k)
        :reference-count (reference-count k)
        :context (kernel-context k)
        :program (kernel-program k)
        :attributes (attributes k)
        nil)))
    ([k]
     (->KernelInfo (maybe (function-name k)) (maybe (num-args k))
                   (maybe (reference-count k)) (maybe (kernel-context k))
                   (maybe (kernel-program k)) (maybe (attributes k)))))
  InfoReferenceCount
  (reference-count [k]
    (info-int* CL/clGetKernelInfo k CL/CL_KERNEL_REFERENCE_COUNT)))

;; ===================== Kernel Sub Group =====================================

;; TODO

;; ===================== Kernel Work Group ====================================

;; TODO

;; ===================== Mem Object ===========================================

(defn cl-mem-object-type [^long code]
  (case code
    0x10F0 :buffer
    0x10F1 :image2d
    0x10F2 :image3d
    0x10F3 :image2d-array
    0x10F4 :image1d
    0x10F5 :image1d-array
    0x10F6 :image1d-buffer
    0x10F7 :pipe))

(defn mem-type [mo]
  (info-int* CL/clGetMemObjectInfo mo CL/CL_MEM_TYPE))

(def cl-mem-flags
  {:read-write CL/CL_MEM_READ_WRITE
   :write-only CL/CL_MEM_WRITE_ONLY
   :read-only CL/CL_MEM_READ_ONLY
   :use-host-ptr CL/CL_MEM_USE_HOST_PTR
   :alloc-host-ptr CL/CL_MEM_ALLOC_HOST_PTR
   :copy-host-ptr CL/CL_MEM_COPY_HOST_PTR
   :host-write-only CL/CL_MEM_HOST_WRITE_ONLY
   :host-read-only CL/CL_MEM_HOST_READ_ONLY
   :host-no-access CL/CL_MEM_HOST_NO_ACCESS})

(defn flags [mo]
  (info-long* CL/clGetMemObjectInfo mo CL/CL_MEM_FLAGS))

(defn mem-size [mo]
  (info-size* CL/clGetMemObjectInfo mo CL/CL_MEM_SIZE))

;;TODO see what to do with these voids, and whether they make sense with Java.
;;(defn mem-host-ptr [mo]
;;  (info-long* CL/clGetMemObjectInfo mo CL/CL_MEM_HOST_PTR))

(defn map-count [mo]
  (info-int* CL/clGetMemObjectInfo mo CL/CL_MEM_MAP_COUNT))

(defn mem-context [mo]
  (info-native* CL/clGetMemObjectInfo mo CL/CL_MEM_CONTEXT
                cl_context Sizeof/cl_context))

(defn associated-memobject [mo]
  (info-native* CL/clGetMemObjectInfo mo CL/CL_MEM_ASSOCIATED_MEMOBJECT
                cl_mem Sizeof/cl_mem))

(defn offset [mo]
  (info-size* CL/clGetMemObjectInfo mo CL/CL_MEM_OFFSET))

(defn uses-svm-pointer [mo]
  (info-bool* CL/clGetMemObjectInfo mo CL_MEM_USES_SVM_POINTER))

(defrecord MemObjectInfo [type flags size map-count reference-count
                          context associated-memobject offset
                          uses-svm-pointer])

(extend-type cl_mem
  Info
  (info
    ([mo info-type]
     (maybe
      (case info-type
        :type (cl-mem-object-type (type mo))
        :flags (unmask cl-mem-flags (flags mo))
        :size (mem-size mo)
        :map-count (map-count mo)
        :reference-count (reference-count mo)
        :context (mem-context mo)
        :associated-memobject (associated-memobject mo)
        :offset (offset mo)
        :uses-svm-pointer (uses-svm-pointer mo)
        nil)))
    ([mo]
     (->MemObjectInfo (maybe (cl-mem-object-type (mem-type mo)))
                      (maybe (unmask cl-mem-flags (flags mo)))
                      (maybe (mem-size mo)) (maybe (map-count mo))
                      (maybe (reference-count mo)) (maybe (mem-context mo))
                      (maybe (associated-memobject mo)) (maybe (offset mo))
                      (maybe (uses-svm-pointer mo)))))
  InfoReferenceCount
  (reference-count [mo]
    (info-int* CL/clGetMemObjectInfo mo CL/CL_MEM_REFERENCE_COUNT)))

;; ===================== Pipe =================================================

;; TODO

;; ===================== Program Build ========================================

;; ===================== Program ==============================================

;; ===================== Sampler ==============================================

;; TODO

;; ===================== GL Context ===========================================

;; TODO
;; ===================== GL Object ============================================

;; TODO

;; ===================== GL Texture ===========================================

;; TODO

;; ============================================================================
