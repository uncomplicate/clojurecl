(ns uncomplicate.clojurecl.info
  (:require [clojure.string :as str]
            [uncomplicate.clojurecl.utils :refer :all]
            [vertigo
             [bytes :refer [buffer byte-seq byte-count]]
             [structs :refer [int32 int64 wrap-byte-seq]]])
  (:import [org.jocl CL cl_platform_id  cl_device_id cl_context cl_command_queue
            cl_mem cl_program cl_kernel cl_sampler cl_event
            Sizeof Pointer]
           [java.nio ByteBuffer]))

;; =================== Info* utility macros ===============================

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

(defn ^:private to-set [s]
  (if (str/blank? s)
    #{}
    (apply hash-set (str/split s #" "))))

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
     (case info-type
       :profile (profile p)
       :version (version p)
       :name (name-info p)
       :vendor (vendor p)
       :extensions (extensions p)
       :icd-suffix-khr (icd-suffix-khr p)))
    ([p]
     (->PlatformInfo (profile p) (version p) (name-info p) (vendor p)
                     (extensions p) (icd-suffix-khr p))))
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

(defn address-bits ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_ADDRESS_BITS))

(defn available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_AVAILABLE))

(defn built-in-kernels [device]
  (to-set (info-string* CL/clGetDeviceInfo device
                       CL/CL_DEVICE_BUILT_IN_KERNELS)))

(defn compiler-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_COMPILER_AVAILABLE))

(defn ^:private mask* [^long mask ^long code key]
  (if (= 0 (bit-and mask code)) nil key))

(defn double-fp-config [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_DOUBLE_FP_CONFIG)]
    (disj (hash-set (mask* mask CL/CL_FP_DENORM :denorm)
                    (mask* mask CL/CL_FP_INF_NAN :inf-nan)
                    (mask* mask CL/CL_FP_ROUND_TO_NEAREST :round-to-nearest)
                    (mask* mask CL/CL_FP_ROUND_TO_ZERO :round-to-zero)
                    (mask* mask CL/CL_FP_ROUND_TO_INF :round-to-inf)
                    (mask* mask CL/CL_FP_FMA :fma)
                    (mask* mask CL/CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
                           :correctly-rounded-divide-sqrt)
                    (mask* mask CL/CL_FP_SOFT_FLOAT :soft-float))
          nil)))

(defn endian-little [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ENDIAN_LITTLE))

(defn error-correction-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ERROR_CORRECTION_SUPPORT))


(defn execution-capabilities [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_EXECUTION_CAPABILITIES)]
    (disj (hash-set (mask* mask CL/CL_EXEC_KERNEL :kernel)
                    (mask* mask CL/CL_EXEC_NATIVE_KERNEL :native-kernel))
          nil)))

(defn global-mem-cache-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHE_SIZE))

(defn global-mem-cache-type [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_GLOBAL_MEM_CACHE_TYPE)]
    (cond
     (= CL/CL_NONE (bit-and mask CL/CL_NONE)) :none
     (= CL/CL_READ_ONLY_CACHE (bit-and mask CL/CL_READ_ONLY_CACHE)) :read-only
     (= CL/CL_READ_WRITE_CACHE (bit-and mask CL/CL_READ_WRITE_CACHE)) :read-write)))

(defn global-mem-cacheline-size ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHELINE_SIZE))

(defn global-mem-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_SIZE))

;; TODO Opencl 2.0
(defn global-variable-preferred-total-size [device]
  nil)

(defn image2d-max-height ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE2D_MAX_HEIGHT))

(defn image2d-max-width ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE2D_MAX_WIDTH))

(defn image3d-max-depth ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_DEPTH))

(defn image3d-max-height ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_HEIGHT))

(defn image3d-max-width ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE3D_MAX_WIDTH))

;; TODO Opencl 2.0
(defn image-base-address-alignment [device]
  nil)

(defn image-max-array-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_ARRAY_SIZE))

(defn image-max-buffer-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_BUFFER_SIZE))

;; TODO Opencl 2.0
(defn image-pitch-alignment [device]
  nil)

(defn image-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_SUPPORT))

(defn linker-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_LINKER_AVAILABLE))

(defn local-mem-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_LOCAL_MEM_SIZE))

(defn local-mem-type [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_LOCAL_MEM_TYPE)]
    (cond
     (= CL/CL_LOCAL (bit-and mask CL/CL_LOCAL)) :local
     (= CL/CL_NONE (bit-and mask CL/CL_NONE)) :none
     (= CL/CL_GLOBAL (bit-and mask CL/CL_GLOBAL)) :global)))

(defn max-clock-frequency ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CLOCK_FREQUENCY))

(defn max-compute-units ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_COMPUTE_UNITS))

(defn max-constant-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_ARGS))

(defn max-constant-buffer-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE))

;; TODO Opencl 2.0
(defn max-global-variable-size [device]
  nil)

(defn max-mem-aloc-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_MEM_ALLOC_SIZE))

;; TODO Opencl 2.0
(defn max-on-device-events [device]
  nil)

;; TODO Opencl 2.0
(defn max-on-device-queues [device]
  nil)

(defn max-parameter-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_PARAMETER_SIZE))

;; TODO Opencl 2.0
(defn max-pipe-args [device]
  nil)

(defn max-read-image-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_READ_IMAGE_ARGS))

;; TODO Opencl 2.0
(defn max-read-write-image-args [device]
  nil)

(defn max-samplers ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_SAMPLERS))

(defn max-work-group-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_GROUP_SIZE))

(defn max-work-item-dimensions ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS))

(defn max-work-item-sizes [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WORK_ITEM_SIZES
              (max-work-item-dimensions device)))

(defn max-write-image-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_WRITE_IMAGE_ARGS))

(defn mem-base-addr-align ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MEM_BASE_ADDR_ALIGN))

(defn native-vector-width-char ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_CHAR))

(defn native-vector-width-short ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_SHORT))

(defn native-vector-width-int ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_INT))

(defn native-vector-width-long ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_LONG))

(defn native-vector-width-float ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_FLOAT))

(defn native-vector-width-double ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_NATIVE_VECTOR_WIDTH_DOUBLE))

(defn native-vector-width-half ^long [device]
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

(defn partition-affinity-domain [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_PARTITION_AFFINITY_DOMAIN)]
    (disj (hash-set (mask* mask CL/CL_DEVICE_AFFINITY_DOMAIN_NUMA :numa)
                    (mask* mask CL/CL_DEVICE_AFFINITY_DOMAIN_L1_CACHE :l1-cache)
                    (mask* mask CL/CL_DEVICE_AFFINITY_DOMAIN_L2_CACHE :l2-cache)
                    (mask* mask CL/CL_DEVICE_AFFINITY_DOMAIN_L3_CACHE :l3-cache)
                    (mask* mask CL/CL_DEVICE_AFFINITY_DOMAIN_L4_CACHE :l4-cache)
                    (mask* mask CL/CL_DEVICE_AFFINITY_DOMAIN_NEXT_PARTITIONABLE
                           :next-partitionable))
          nil)))

(defn partition-max-sub-devices ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_MAX_SUB_DEVICES))

;;TODO
(defn partition-properties [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_PROPERTIES))

;;TODO
(defn partition-type [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_TYPE))

;; TODO Opencl 2.0
(defn pipe-max-active-reservations [device]
  nil)

;; TODO Opencl 2.0
(defn pipe-max-packet-size [device]
  nil)

(defn platform [device]
  (let [p (cl_platform_id.)
        err (CL/clGetDeviceInfo device CL/CL_DEVICE_PLATFORM
                                Sizeof/cl_platform_id
                                (Pointer/to p)
                                nil)]
    (with-check err platform)))

;; TODO Opencl 2.0
(defn preferred-global-atomic-alignment [device]
  nil)

(defn preferred-interop-user-sync [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_INTEROP_USER_SYNC))

;; TODO Opencl 2.0
(defn preferred-local-atomic-alignment [device]
  nil)

;; TODO Opencl 2.0
(defn preferred-platform-atomic-alignment [device]
  nil)

(defn preferred-vector-width-char ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_CHAR))

(defn preferred-vector-width-short ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_SHORT))

(defn preferred-vector-width-int ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_INT))

(defn preferred-vector-width-long ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_LONG))

(defn preferred-vector-width-float ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_FLOAT))

(defn preferred-vector-width-double ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_DOUBLE))

(defn preferred-vector-width-half ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_VECTOR_WIDTH_HALF))

(defn printf-buffer-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PRINTF_BUFFER_SIZE))

(defn profiling-timer-resolution [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PROFILING_TIMER_RESOLUTION))

;; TODO Opencl 2.0
(defn queue-on-device-max-size [device]
  nil)

;; TODO Opencl 2.0
(defn queue-on-device-preferred-size [device]
  nil)

;; TODO Opencl 2.0
(defn queue-on-device-properties [device]
  nil)

;; TODO Opencl 2.0
(defn queue-on-host-properties [device]
  nil)

(defn single-fp-config [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_SINGLE_FP_CONFIG)]
    (disj (hash-set (mask* mask CL/CL_FP_DENORM :denorm)
                    (mask* mask CL/CL_FP_INF_NAN :inf-nan)
                    (mask* mask CL/CL_FP_ROUND_TO_NEAREST :round-to-nearest)
                    (mask* mask CL/CL_FP_ROUND_TO_ZERO :round-to-zero)
                    (mask* mask CL/CL_FP_ROUND_TO_INF :round-to-inf)
                    (mask* mask CL/CL_FP_FMA :fma)
                    (mask* mask CL/CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
                           :correctly-rounded-divide-sqrt)
                    (mask* mask CL/CL_FP_SOFT_FLOAT :soft-float))
          nil)))

;; TODO Opencl 2.0
(defn spir-versions [device]
  nil)

;; TODO Opencl 2.0
(defn svm-capabilities [device]
  nil)

;; TODO Opencl 2.0
(defn terminate-capability-khr [device]
  nil)

(defn device-type [device]
  (let [mask (info-long* CL/clGetDeviceInfo device
                         CL/CL_DEVICE_TYPE)]
    (cond
     (= CL/CL_DEVICE_TYPE_GPU (bit-and mask CL/CL_DEVICE_TYPE_GPU)) :gpu
     (= CL/CL_DEVICE_TYPE_CPU (bit-and mask CL/CL_DEVICE_TYPE_CPU)) :cpu
     (= CL/CL_DEVICE_TYPE_ALL (bit-and mask CL/CL_DEVICE_TYPE_ALL)) :all
     (= CL/CL_DEVICE_TYPE_DEFAULT (bit-and mask CL/CL_DEVICE_TYPE_DEFAULT))
     :default
     (= CL/CL_DEVICE_TYPE_ACCELERATOR
        (bit-and mask CL/CL_DEVICE_TYPE_ACCELERATOR))
     :accelerator
     (= CL/CL_DEVICE_TYPE_CUSTOM (bit-and mask CL/CL_DEVICE_TYPE_CUSTOM))
     :custom)))

(defn vendor-id ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_VENDOR_ID))

(defn device-version [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DEVICE_VERSION))

(defn driver-version [device]
  (info-string* CL/clGetDeviceInfo device CL/CL_DRIVER_VERSION))

(defrecord DeviceInfo
    [^long address-bits
     available
     built-in-kernels
     compiler-available
     double-fp-config
     endian-little
     error-correction-support
     execution-capabilities
     extensions
     ^long global-mem-cache-size
     global-mem-cache-type
     ^long global-mem-cacheline-size
     ^long global-mem-size
     global-variable-preferred-total-size
     ^long image2d-max-height
     ^long image2d-max-width
     ^long image3d-max-depth
     ^long image3d-max-height
     ^long image3d-max-width
     image-base-address-alignment
     ^long image-max-array-size
     ^long image-max-buffer-size
     image-pitch-alignment
     image-support
     linker-available
     ^long local-mem-size
     local-mem-type
     ^long max-clock-frequency
     ^long max-compute-units
     ^long max-constant-args
     ^long max-constant-buffer-size
      max-global-variable-size
     ^long max-mem-aloc-size
     max-on-device-events
     max-on-device-queues
     ^long max-parameter-size
     max-pipe-args
     ^long max-read-image-args
     max-read-write-image-args
     ^long max-samplers
     ^long max-work-group-size
     ^long max-work-item-dimensions
     max-work-item-sizes
     ^long max-write-image-args
     ^long mem-base-addr-align
     name
     ^long native-vector-width-char
     ^long native-vector-width-short
     ^long native-vector-width-int
     ^long native-vector-width-long
     ^long native-vector-width-double
     ^long native-vector-width-float
     ^long native-vector-width-half
     opencl-c-version
     parent-device
     partition-affinity-domain
     ^long partition-max-sub-devices
     ;;:partition-properties partition-properties ;;TODO raises exception when called on CPU
     partition-type
     pipe-max-active-reservations
     pipe-max-packet-size
     platform
     preferred-global-atomic-alignment
     preferred-interop-user-sync
     preferred-local-atomic-alignment
     preferred-platform-atomic-alignment
     ^long preferred-vector-width-char
     ^long preferred-vector-width-short
     ^long preferred-vector-width-int
     ^long preferred-vector-width-long
     ^long preferred-vector-width-double
     ^long preferred-vector-width-float
     ^long preferred-vector-width-half
     ^long printf-buffer-size
     profile
     profiling-timer-resolution
     queue-on-device-max-size
     queue-on-device-preferred-size
     queue-on-device-properties
     queue-on-host-properties
     ^long reference-count
     single-fp-config
     spir-versions
     svm-capabilities
     terminate-capability-khr
     device-type
     vendor
     ^long vendor-id
     device-version
     driver-version])

(extend-type cl_device_id
  Info
  (info
    ([d info-type]
     (case info-type
       :address-bits (address-bits d)
       :available (available d)
       :built-in-kernels (built-in-kernels d)
       :compiler-available (compiler-available d)
       :double-fp-config (double-fp-config d)
       :endian-little (endian-little d)
       :error-correction-support (error-correction-support d)
       :execution-capabilities (execution-capabilities d)
       :extensions (extensions d)
       :global-mem-cache-size (global-mem-cache-size d)
       :global-mem-cache-type (global-mem-cache-type d)
       :global-mem-cacheline-size (global-mem-cacheline-size d)
       :global-mem-size (global-mem-size d)
       :global-variable-preferred-total-size (global-variable-preferred-total-size d)
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
       :local-mem-type (local-mem-type d)
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
       :partition-affinity-domain (partition-affinity-domain d)
       :partition-max-sub-devices (partition-max-sub-devices d)
       ;;:partition-properties partition-properties ;;TODO raises exception when called on CPU
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
       :queue-on-device-properties (queue-on-device-properties d)
       :queue-on-host-properties (queue-on-host-properties d)
       :reference-count (reference-count d)
       :single-fp-config (single-fp-config d)
       :spir-versions (spir-versions d)
       :svm-capabilities (svm-capabilities d)
       :terminate-capability-khr (terminate-capability-khr d)
       :device-type (device-type d)
       :vendor (vendor d)
       :vendor-id (vendor-id d)
       :device-version (device-version d)
       :driver-version (driver-version d)))
    ([d]
     (->DeviceInfo
      (address-bits d)
      (available d)
      (built-in-kernels d)
      (compiler-available d)
      (double-fp-config d)
      (endian-little d)
      (error-correction-support d)
      (execution-capabilities d)
      (extensions d)
      (global-mem-cache-size d)
      (global-mem-cache-type d)
      (global-mem-cacheline-size d)
      (global-mem-size d)
      (global-variable-preferred-total-size d)
      (image2d-max-height d)
      (image2d-max-width d)
      (image3d-max-depth d)
      (image3d-max-height d)
      (image3d-max-width d)
      (image-base-address-alignment d)
      (image-max-array-size d)
      (image-max-buffer-size d)
      (image-pitch-alignment d)
      (image-support d)
      (linker-available d)
      (local-mem-size d)
      (local-mem-type d)
      (max-clock-frequency d)
      (max-compute-units d)
      (max-constant-args d)
      (max-constant-buffer-size d)
      (max-global-variable-size d)
      (max-mem-aloc-size d)
      (max-on-device-events d)
      (max-on-device-queues d)
      (max-parameter-size d)
      (max-pipe-args d)
      (max-read-image-args d)
      (max-read-write-image-args d)
      (max-samplers d)
      (max-work-group-size d)
      (max-work-item-dimensions d)
      (max-work-item-sizes d)
      (max-write-image-args d)
      (mem-base-addr-align d)
      (name-info d)
      (native-vector-width-char d)
      (native-vector-width-short d)
      (native-vector-width-int d)
      (native-vector-width-long d)
      (native-vector-width-double d)
      (native-vector-width-float d)
      (native-vector-width-half d)
      (opencl-c-version d)
      (parent-device d)
      (partition-affinity-domain d)
      (partition-max-sub-devices d)
     ;;:partition-properties partition-properties ;;TODO raises exception when called on CPU
      (partition-type d)
      (pipe-max-active-reservations d)
      (pipe-max-packet-size d)
      (platform d)
      (preferred-global-atomic-alignment d)
      (preferred-interop-user-sync d)
      (preferred-local-atomic-alignment d)
      (preferred-platform-atomic-alignment d)
      (preferred-vector-width-char d)
      (preferred-vector-width-short d)
      (preferred-vector-width-int d)
      (preferred-vector-width-long d)
      (preferred-vector-width-double d)
      (preferred-vector-width-float d)
      (preferred-vector-width-half d)
      (printf-buffer-size d)
      (profile d)
      (profiling-timer-resolution d)
      (queue-on-device-max-size d)
      (queue-on-device-preferred-size d)
      (queue-on-device-properties d)
      (queue-on-host-properties d)
      (reference-count d)
      (single-fp-config d)
      (spir-versions d)
      (svm-capabilities d)
      (terminate-capability-khr d)
      (device-type d)
      (vendor d)
      (vendor-id d)
      (device-version d)
      (driver-version d))))
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
(defn num-reference-count ^long [context]
  (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_REFERENCE_COUNT))

(defn num-devices-in-context ^long [context]
  (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_NUM_DEVICES))

(defn devices-in-context [context]
  (vec (info-native* CL/clGetContextInfo context CL/CL_CONTEXT_DEVICES
                     cl_device_id Sizeof/cl_device_id)))

(defrecord ContextInfo [^long num-devices ^long num-reference-count
                        devices properties])

(extend-type cl_context
  Info
  (info
    ([c info-type]
     (case info-type
       :num-devices (num-devices-in-context c)
       :num-reference-count (num-reference-count c)
       :devices (devices-in-context c)
       :properties (properties c)))
    ([c]
     (->ContextInfo (num-devices-in-context c) (num-reference-count c)
                    (devices-in-context c) (properties c))))
  InfoProperties
  (properties [c] ;; TODO see how to retreive variable length objects.
    nil))

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

;; TODO OpenCL 2.0
(defn queue-size [queue]
  nil #_(info-int* CL/clGetCommandQueueInfo queue CL/CL_QUEUE_SIZE))

(defrecord CommandQueueInfo [context device ^long reference-count
                             properties size])

(extend-type cl_command_queue
  Info
  (info
    ([cq info-type]
     (case info-type
       :context (queue-context cq)
       :device (queue-device cq)
       :reference-count (reference-count cq)
       :properties (properties cq)
       :size (queue-size cq)))
    ([cq]
     (->CommandQueueInfo (queue-context cq) (queue-device cq)
                         (reference-count cq) (properties cq)
                         (queue-size cq))))
  InfoReferenceCount
  (reference-count [cq]
    (info-int* CL/clGetCommandQueueInfo cq CL/CL_QUEUE_REFERENCE_COUNT))
  InfoProperties
  (properties [cq]
    (let [mask (info-long* CL/clGetCommandQueueInfo cq
                           CL/CL_QUEUE_PROPERTIES)]
      (disj (hash-set (mask* mask CL/CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE
                             :out-of-order-exec-mode)
                      (mask* mask CL/CL_QUEUE_PROFILING_ENABLE :profiling)
                      ;; TODO OpenCL 2.0 (mask* mask CL/CL_QUEUE_ON_DEVICE :queue-on-device)
                      #_(mask* mask CL/CL_QUEUE_ON_DEVICE_DEFAULT
                               :queue-on-device-default))
            nil))))

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
    0x200D :gl-fence-sync-object-khr
    :unknown-type))

(defn execution-status [event]
  (let [code ^long (info-int* CL/clGetEventInfo event
                              CL/CL_EVENT_COMMAND_EXECUTION_STATUS)]
    (case code
        0x0 :complete
        0x1 :running
        0x2 :submitted
        0x3 :queued
        (error-string code))))

(defrecord EventInfo [command-queue context command-type
                      execution-status reference-count])

(extend-type cl_event
  Info
  (info
    ([e info-type]
     (case info-type
       :command-queue (event-command-queue e)
       :context (event-context e)
       :command-type (command-type e)
       :execution-status (execution-status e)
       :reference-count (reference-count e)))
    ([e]
     (->EventInfo (event-command-queue e) (event-context e) (command-type e)
                  (execution-status e) (reference-count e))))
  InfoReferenceCount
  (reference-count [e]
    (info-int* CL/clGetEventInfo e CL/CL_EVENT_REFERENCE_COUNT))
  InfoProperties
  (properties [cq]
    (let [mask (info-long* CL/clGetCommandQueueInfo cq
                           CL/CL_QUEUE_PROPERTIES)]
      (disj (hash-set (mask* mask CL/CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE
                             :out-of-order-exec-mode)
                      (mask* mask CL/CL_QUEUE_PROFILING_ENABLE :profiling)
                      ;; TODO OpenCL 2.0 (mask* mask CL/CL_QUEUE_ON_DEVICE :queue-on-device)
                      #_(mask* mask CL/CL_QUEUE_ON_DEVICE_DEFAULT
                               :queue-on-device-default))
            nil))))

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
         (case info
           :queued (queued event)
           :submit (submit event)
           :start (start event)
           :end (end event)))

  ([event]
   (->ProfilingInfo (queued event) (submit event)
                    (start event) (end event))))

(defn durations [^ProfilingInfo pi]
  (->ProfilingInfo 0
                   (- (.submit pi) (.queued pi))
                   (- (.start pi) (.submit pi))
                   (- (.end pi) (.start pi))))

;; ===================== Image ================================================


;; ===================== Kernel Arg ===========================================


;; ===================== Kernel ===============================================

;; ===================== Kernel Sub Group =====================================

;; ===================== Kernel Work Group ====================================

;; ===================== Mem Object ===========================================


;; ===================== Pipe =================================================

;; ===================== Program Build ========================================

;; ===================== Program ==============================================

;; ===================== Sampler ==============================================


;; ===================== GL Context ===========================================

;; ===================== GL Object ============================================

;; ===================== GL Texture ===========================================

;; ================================================================
