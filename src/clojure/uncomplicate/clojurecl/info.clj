(ns ^{:author "Dragan Djuric"}
  uncomplicate.clojurecl.info
  "Info functions for all OpenCL objects (platforms, devices, etc...).

  The OpenCL standard defines info functions for all cl structures. Typically
  in OpenCL C, you would have a reference to an object representing, for example,
  platform, and then call a dedicated info function, in this case
  [`clGetPlatformInfo`](http://www.jocl.org/doc/org/jocl/CL.html#clGetPlatformInfo-org.jocl.cl_platform_id-int-long-org.jocl.Pointer-long:A-)
  with a parameter param_name that specifies which of the several available
  informations you want about that object. If you need all information, then you
  need to call this function as many times as different kinds of information there is.

  ClojureCL provides many conveniences for obtaining information about cl objects:

  1. **There is a universal, high-level, [[info]] function** that works for all kinds
     of cl objects (platform, context, device, memory, etc.) and displays all available
     information. This function also accepts a keyword argument for returning
     only a specific kind of information, not all information. The information
     will be converted from low-level C enums to a Clojre-friendly format that
     uses keywords, sequences, sets, etc. It will release all additional cl objects
     that it has to use to obtain information. If there is an OpenCL error in obtaining
     the information, which may happen if the driver does not support that kind of
     information, the [ExceptionInfo](http://clojuredocs.org/clojure.core/ex-info)
     will be returned as a result for that particular information, instead of
     raising an exception. This function is useful in when the information
     is going to be displayed to the user.

  2. For each information kind, there is a dedicated, low-level, function that
     returns the raw, unconverted information. If the result is a cl object that
     needs to be released after use, it is the responsibility of the caller to
     call the [[core/release]] function. If the information is not supported,
     the exception is raised. These functions are convenient in the parts
     of the program where the returned info is used by other parts of the program,
     for example to calculate some parameters for an algorithm.

  3. Some information is not only about the objects, for example program, but
     about the specific use of that object, for example a program build. In that
     case, aditional X-info function is provided, for example [[build-info]].

  Most keywords in the [[info]] function are exactly the same as the corresponding
  low-level function name, except in a few cases where that would produce a clash
  with some other functionality. You can check the available keywords in
  the documentation of appropriate positional methods:
  [[->PlatformInfo]], [[->DeviceInfo]], [[->CommandQueueInfo]], [[->ContextInfo]],
  [[->KernelInfo]], [[->KernelArgInfo]], [[->ProgramInfo]], [[->ProgramBuildinfo]],
  [[->EventInfo]], [[->Profilinginfo]], [[->MemObjectInfo]], etc...

  ###Cheat Sheet

  #### Low-level info functions grouped by resource type:

  * [`cl_platform_id`](http://www.jocl.org/doc/org/jocl/cl_platform_id.html) info:
  [[version]], [[icd-suffix-khr]], [[profile]], [[name-info]], [[vendor]],
  [[extensions]]

  * [`cl_device_id`] (http://www.jocl.org/doc/org/jocl/cl_device_id.html) info:
  [[address-bits]], [[available]], [[built-in-kernels]], [[compiler-available]],
  [[double-fp-config]], [[endian-little]], [[error-correction-support]],
  [[execution-capabilities]], [[global-mem-cache-size]], [[global-=mem-cache-type]],
  [[global-mem-cacheline-size]], [[global-mem-size]],
  [[global-variable-preferred-total-size]], [[image2d-max-height]],
  [[image2d-max-width]], [[image3d-max-depth]], [[image3d-max-height]],
  [[image3d-max-width]], [[image-base-address-alignment]], [[image-max-array-size]],
  [[image-max-array-size]], [[image-max-buffer-size]], [[image-pitch-alignment]],
  [[image-support]], [[linker-available]], [[local-mem-size]], [[local-mem-type]],
  [[max-clock-frequency]], [[max-compute-units]], [[max-constant-args]],
  [[max-constant-buffer-size]], [[max-global-variable-size]], [[max-mem-aloc-size]],
  [[max-on-device-events]], [[max-on-device-queues]], [[max-parameter-size]],
  [[max-pipe-args]], [[max-read-image-args]], [[max-read-write-image-args]],
  [[max-samplers]], [[max-work-group-size]], [[max-work-item-dimensions]],
  [[max-work-item-sizes]], [[max-write-image-args]], [[mem-base-addr-align]],
  [[native-vector-width-char]], [[native-vector-width-short]],
  [[native-vector-width-int]], [[native-vector-width-long]],
  [[native-vector-width-float]], [[native-vector-width-double]],
  [[native-vector-width-half]], [[opencl-c-version]], [[parent-device]],
  [[partition-affinity-domain]], [[partition-max-sub-devices]],
  [[partition-properties]], [[partition-type]],[[pipe-max-active-reservations]],
  [[pipe-max-packet-size]], [[platform]], [[preferred-global-atomic-alignment]],
  [[preferred-interop-user-sync]], [[preferred-local-atomic-alignment]],
  [[preferred-platform-atomic-alignment]], [[preferred-vector-width-char]],
  [[preferred-vector-width-short]], [[preferred-vector-width-int]],
  [[preferred-vector-width-long]], [[preferred-vector-width-float]],
  [[preferred-vector-width-double]], [[preferred-vector-width-half]],
  [[printf-buffer-size]], [[profiling-timer-resolution]], [[queue-on-device-max-size]],
  [[queue-on-device-properties]], [[queue-on-host-properties]],
  [[single-fp-config]], [[spir-versions]], [[svm-capabilities]],
  [[device-type]], [[vendor-id]], [[device-version]],
  [[driver-version]], [[extensions]], [[name-info]], [[profile]], [[vendor]],
  [[reference-count]]

  * [`cl_context`] (http://www.jocl.org/doc/org/jocl/cl_context.html) info:
  [[num-devices-in-context]], [[devices-in-context]], [[properties]],
  [[reference-count]]

  * [`cl_command_queue`] (http://www.jocl.org/doc/org/jocl/cl_command_queue.html) info:
  [[queue-context]], [[queue-device]], [[queue-size]], [[properties]],
  [[reference-count]]

  * [`cl_event`] (http://www.jocl.org/doc/org/jocl/cl_event.html) info:
  [[event-command-queue]], [[event-context]], [[command-type]], [[execution-status]],
  [[reference-count]]

  * profiling event info: **[[profiling-info]]**,
  [[queued]], [[submit]], [[start]], [[end]]

  * [`cl_kernel`] (http://www.jocl.org/doc/org/jocl/cl_kernel.html) info:
  [[function-name]], [[num-args]], [[kernel-context]], [[kernel-program]],
  [[attributes]], [[reference-count]]

  * kernel argument info: **[[arg-info]]**
  [[arg-address-qualifier]], [[arg-access-qualifier]], [[arg-type-name]],
  [[arg-type-qualifier]], [[arg-name]]

  * [`cl_mem`] (http://www.jocl.org/doc/org/jocl/cl_mem.html) info:
  [[mem-type]], [[flags]], [[mem-size]], [[map-count]], [[mem-context]],
  [[associated-memobject]], [[offset]], [[uses-svm-pointer]], [[reference-count]]

  * [`cl_program`] (http://www.jocl.org/doc/org/jocl/cl_program.html) info:
  [[program-context]], [[program-num-devices]], [[program-devices]],
  [[program-source]], [[binary-sizes]], [[binaries]], [[program-num-kernels]],
  [[kernel-names]], [[reference-count]]

  * program build info: **[[build-info]]**,
  [[build-status]], [[build-options]], [[build-log]], [[binary-type]],
  [[global-variable-total-size]]

  #### Hihg-level info and keywords (in a few cases different than low-level function names)

  [[->PlatformInfo]], [[->DeviceInfo]], [[->CommandQueueInfo]], [[->ContextInfo]],
  [[->KernelInfo]], [[->KernelArgInfo]], [[->ProgramInfo]], [[->ProgramBuildinfo]],
  [[->EventInfo]], [[->Profilinginfo]], [[->MemObjectInfo]],
  "
  (:require [clojure.string :as str]
            [uncomplicate.clojurecl
             [constants :refer :all]
             [utils :refer :all]]
            [vertigo
             [bytes :refer [buffer direct-buffer byte-seq byte-count slice]]
             [structs :refer [int32 int64 wrap-byte-seq]]])
  (:import [org.jocl CL cl_platform_id  cl_device_id cl_context cl_command_queue
            cl_program cl_kernel cl_sampler cl_event cl_device_partition_property
            cl_mem Sizeof Pointer]
           [java.nio ByteBuffer]))

;; TODO Check for memory leaks. Some of the returned resources should be released
;; after showing them in the big info function (contexts, devices, etc...)
;;actually, this is not that hard: info should pick up which data to show, for example
;;just name of the device, and release the resource immediately. the low-level function
;;should just return the raw id, of which the user should be responsible.

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

(defn ^:private native-pointer
  [^"[Lorg.jocl.NativePointerObject;" np]
  (Pointer/to np))

(defmacro ^:private info-native* [method clobject info type size]
  `(let [bytesize# (info-count* ~method ~clobject ~info)
         res# (make-array ~type (/ bytesize# ~size))
         err# (~method ~clobject ~info
                       bytesize# (~native-pointer res#)
                       nil)]
     (with-check err# res#)))

(defn ^:private pointer-to-buffer [^ByteBuffer b]
  (Pointer/toBuffer b))

(defmacro ^:private info-size*
  ([method clobject info num]
   `(let [res# (buffer (* Sizeof/size_t (long ~num)))
          err# (~method ~clobject ~info
                        (* Sizeof/size_t (long ~num))
                        (~pointer-to-buffer res#)
                        nil)]
      (with-check err#
        (wrap-byte-seq (if (= 8 Sizeof/size_t)
                         int64
                         int32)
                       (byte-seq res#)))))
  ([method clobject info]
   `(first (info-size* ~method ~clobject ~info 1))))

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

(defn address-bits ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_ADDRESS_BITS))

(defn available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_AVAILABLE))

(defn built-in-kernels [device]
  (to-set (info-string* CL/clGetDeviceInfo device
                       CL/CL_DEVICE_BUILT_IN_KERNELS)))

(defn compiler-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_COMPILER_AVAILABLE))

(defn double-fp-config ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_DOUBLE_FP_CONFIG))

(defn endian-little [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ENDIAN_LITTLE))

(defn error-correction-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_ERROR_CORRECTION_SUPPORT))

(defn execution-capabilities ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_EXECUTION_CAPABILITIES))

(defn global-mem-cache-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHE_SIZE))

(defn global-mem-cache-type ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHE_TYPE))

(defn global-mem-cacheline-size ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_CACHELINE_SIZE))

(defn global-mem-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_GLOBAL_MEM_SIZE))

(defn global-variable-preferred-total-size ^long [device]
  (info-size* CL/clGetDeviceInfo device
              CL/CL_DEVICE_GLOBAL_VARIABLE_PREFERRED_TOTAL_SIZE))

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

(defn image-base-address-alignment ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_BASE_ADDRESS_ALIGNMENT))

(defn image-max-array-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_ARRAY_SIZE))

(defn image-max-buffer-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_MAX_BUFFER_SIZE))

(defn image-pitch-alignment ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_PITCH_ALIGNMENT))

(defn image-support [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_IMAGE_SUPPORT))

(defn linker-available [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_LINKER_AVAILABLE))

(defn local-mem-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_LOCAL_MEM_SIZE))

(defn local-mem-type ^long [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_LOCAL_MEM_TYPE))

(defn max-clock-frequency ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CLOCK_FREQUENCY))

(defn max-compute-units ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_COMPUTE_UNITS))

(defn max-constant-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_ARGS))

(defn max-constant-buffer-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE))

(defn max-global-variable-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_GLOBAL_VARIABLE_SIZE))

(defn max-mem-aloc-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_MEM_ALLOC_SIZE))

(defn max-on-device-events ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_ON_DEVICE_EVENTS))

(defn max-on-device-queues ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_ON_DEVICE_QUEUES))

(defn max-parameter-size ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_PARAMETER_SIZE))

(defn max-pipe-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_PIPE_ARGS))

(defn max-read-image-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_READ_IMAGE_ARGS))

(defn max-read-write-image-args ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_MAX_READ_WRITE_IMAGE_ARGS))

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
    {:version (Double/parseDouble (info 2))
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

(defn partition-affinity-domain ^long [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_PARTITION_AFFINITY_DOMAIN))

(defn partition-max-sub-devices ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PARTITION_MAX_SUB_DEVICES))

(defn partition-properties [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_PARTITION_PROPERTIES
              (info-count* CL/clGetDeviceInfo device
                           CL/CL_DEVICE_PARTITION_PROPERTIES
                           Sizeof/cl_long)))

;;TODO
(defn partition-type [device]
  (info-long* CL/clGetDeviceInfo device
              CL/CL_DEVICE_PARTITION_TYPE
              (info-count* CL/clGetDeviceInfo device
                           CL/CL_DEVICE_PARTITION_TYPE
                           Sizeof/cl_long)) )

(defn pipe-max-active-reservations ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PIPE_MAX_ACTIVE_RESERVATIONS))

(defn pipe-max-packet-size ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_PIPE_MAX_PACKET_SIZE))

(defn platform [device]
  (let [p (cl_platform_id.)
        err (CL/clGetDeviceInfo device CL/CL_DEVICE_PLATFORM
                                Sizeof/cl_platform_id
                                (Pointer/to p)
                                nil)]
    (with-check err p)))

(defn preferred-global-atomic-alignment ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_GLOBAL_ATOMIC_ALIGNMENT))

(defn preferred-interop-user-sync [device]
  (info-bool* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_INTEROP_USER_SYNC))

(defn preferred-local-atomic-alignment ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_LOCAL_ATOMIC_ALIGNMENT))

(defn preferred-platform-atomic-alignment ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_PREFERRED_PLATFORM_ATOMIC_ALIGNMENT))

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

(defn profiling-timer-resolution ^long [device]
  (info-size* CL/clGetDeviceInfo device CL/CL_DEVICE_PROFILING_TIMER_RESOLUTION))

(defn queue-on-device-max-size ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_QUEUE_ON_DEVICE_MAX_SIZE))

(defn queue-on-device-preferred-size ^long [device]
  (info-int* CL/clGetDeviceInfo device CL/CL_DEVICE_QUEUE_ON_DEVICE_PREFERRED_SIZE))

(defn queue-on-device-properties ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_QUEUE_ON_DEVICE_PROPERTIES))

(defn queue-on-host-properties ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_QUEUE_ON_HOST_PROPERTIES))

(defn single-fp-config ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_SINGLE_FP_CONFIG))

(defn spir-versions [device]
  (apply hash-set
         (map #(Double/parseDouble %)
              (str/split (info-string* CL/clGetDeviceInfo
                                       device CL_DEVICE_SPIR_VERSIONS)
                         #" "))))

(defn svm-capabilities ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_SVM_CAPABILITIES))

(defn device-type ^long [device]
  (info-long* CL/clGetDeviceInfo device CL/CL_DEVICE_TYPE))

(defn vendor-id ^long [device]
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
        :double-fp-config (set (unmask cl-device-fp-config (double-fp-config d)))
        :endian-little (endian-little d)
        :error-correction-support (error-correction-support d)
        :execution-capabilities
        (set (unmask cl-device-exec-capabilities (execution-capabilities d)))
        :extensions (extensions d)
        :global-mem-cache-size (global-mem-cache-size d)
        :global-mem-cache-type
        (unmask1 cl-device-mem-cache-type (global-mem-cache-type d))
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
        (set (unmask cl-device-affinity-domain (partition-affinity-domain d)))
        :partition-max-sub-devices (partition-max-sub-devices d)
        :partition-properties (map dec-device-partition-property (partition-properties d))
        :partition-type (map dec-device-partition-property (partition-type d))
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
        (set (unmask cl-command-queue-properties (queue-on-device-properties d)))
        :queue-on-host-properties
        (set (unmask cl-command-queue-properties (queue-on-host-properties d)))
        :reference-count (reference-count d)
        :single-fp-config
        (set (unmask cl-device-fp-config (single-fp-config d)))
        :spir-versions (spir-versions d)
        :svm-capabilities
        (set (unmask cl-device-svm-capabilities (svm-capabilities d)))
        :device-type
        (unmask1 cl-device-type (device-type d))
        :vendor (vendor d)
        :vendor-id (vendor-id d)
        :device-version (device-version d)
        :driver-version (driver-version d)
        nil)))
    ([d]
     (->DeviceInfo
      (maybe (address-bits d))
      (maybe (available d))
      (maybe (built-in-kernels d))
      (maybe (compiler-available d))
      (maybe (set (unmask cl-device-fp-config (double-fp-config d))))
      (maybe (endian-little d))
      (maybe (error-correction-support d))
      (maybe (set (unmask cl-device-exec-capabilities (execution-capabilities d))))
      (maybe (extensions d))
      (maybe (global-mem-cache-size d))
      (maybe (unmask1 cl-device-mem-cache-type (global-mem-cache-type d)))
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
      (maybe (set (unmask cl-device-affinity-domain (partition-affinity-domain d))))
      (maybe (partition-max-sub-devices d))
      (maybe (map dec-device-partition-property (partition-properties d)))
      (maybe (map dec-device-partition-property (partition-type d)))
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
      (maybe (set (unmask cl-command-queue-properties
                          (queue-on-device-properties d))))
      (maybe (set (unmask cl-command-queue-properties
                          (queue-on-host-properties d))))
      (maybe (reference-count d))
      (maybe (set (unmask cl-device-fp-config (single-fp-config d))))
      (maybe (spir-versions d))
      (maybe (set (unmask cl-device-svm-capabilities (svm-capabilities d))))
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

(defn num-devices-in-context ^long [context]
  (info-int* CL/clGetContextInfo context CL/CL_CONTEXT_NUM_DEVICES))

(defn devices-in-context [context]
  (vec (info-native* CL/clGetContextInfo context CL/CL_CONTEXT_DEVICES
                     cl_device_id Sizeof/cl_device_id)))

(defrecord ContextInfo [num-devices reference-count devices properties])

(extend-type cl_context
  Info
  (info
    ([c info-type]
     (maybe
      (case info-type
        :num-devices (num-devices-in-context c)
        :reference-count (reference-count c)
        :devices (devices-in-context c)
        :properties (map dec-context-properties (remove zero? (properties c)))
        nil)))
    ([c]
     (->ContextInfo (maybe (num-devices-in-context c))
                    (maybe (reference-count c))
                    (maybe (devices-in-context c))
                    (maybe (map dec-context-properties (remove zero? (properties c)))))))
  InfoProperties
  (properties [c]
    (info-long* CL/clGetContextInfo c
                CL/CL_CONTEXT_PROPERTIES
                (info-count* CL/clGetContextInfo c
                             CL/CL_CONTEXT_PROPERTIES
                             Sizeof/cl_long)))
  InfoReferenceCount
  (reference-count [c]
    (info-int* CL/clGetContextInfo c CL/CL_CONTEXT_REFERENCE_COUNT)))

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

(defn queue-size ^long [queue]
  (info-int* CL/clGetCommandQueueInfo queue CL/CL_QUEUE_SIZE))

(defrecord CommandQueueInfo [context device reference-count
                             properties size])

(extend-type cl_command_queue
  Info
  (info
    ([cq info-type]
     (maybe
      (case info-type
        :context (queue-context cq)
        :device (queue-device cq)
        :reference-count (reference-count cq)
        :properties (set (unmask cl-command-queue-properties (properties cq)))
        :size (queue-size cq)
        nil)))
    ([cq]
     (->CommandQueueInfo (maybe (queue-context cq)) (maybe (queue-device cq))
                         (maybe (reference-count cq))
                         (maybe (set (unmask cl-command-queue-properties
                                             (properties cq))))
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
  (info-int* CL/clGetEventInfo event CL/CL_EVENT_COMMAND_TYPE))

(defn execution-status [event]
  (info-int* CL/clGetEventInfo event
             CL/CL_EVENT_COMMAND_EXECUTION_STATUS))

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
        :command-type (dec-command-type (command-type e))
        :execution-status (dec-command-execution-status (execution-status e))
        :reference-count (reference-count e)
        nil)))
    ([e]
     (->EventInfo (maybe (event-command-queue e)) (maybe (event-context e))
                  (maybe (dec-command-type (command-type e)))
                  (maybe (dec-command-execution-status (execution-status e)))
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
         (case info
           :queued (queued event)
           :submit (submit event)
           :start (start event)
           :end (end event)
           nil))

  ([event]
   (->ProfilingInfo  (queued event) (submit event) (start event) (end event))))

(defn durations [^ProfilingInfo pi]
  (->ProfilingInfo 0
                   (- (.submit pi) (.queued pi))
                   (- (.start pi) (.submit pi))
                   (- (.end pi) (.start pi))))

;; ===================== Image ================================================

;; TODO

;; ===================== Kernel ===============================================

(defn function-name [kernel]
  (info-string* CL/clGetKernelInfo kernel CL/CL_KERNEL_FUNCTION_NAME))

(defn num-args ^long [kernel]
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

;; ===================== Kernel Arg ===========================================

;; -- Kernel Arg Info has special utility functions with one more parameter. --

(defmacro ^:private arg-info-string* [kernel arg info]
  `(let [cnt# (long-array 1)
         err# (CL/clGetKernelArgInfo ~kernel ~arg ~info 0 nil cnt#)]
     (with-check err#
       (let [res# (byte-array (aget cnt# 0))
             err# (CL/clGetKernelArgInfo ~kernel ~arg ~info
                                         (alength res#) (Pointer/to res#)
                                         nil)]
         (with-check err#
           (String. res# 0 (dec (alength res#))))))))

(defmacro ^:private arg-info-long* [kernel arg info]
  `(let [res# (long-array 1)
         err# (CL/clGetKernelArgInfo ~kernel ~arg ~info Sizeof/cl_long
                                     (Pointer/to res#) nil)]
     (with-check err# (aget res# 0))))

;; ----------- Kernel Arg Info functions -------------------------------------

(defn arg-address-qualifier ^long [kernel arg]
  (arg-info-long* kernel arg CL/CL_KERNEL_ARG_ADDRESS_QUALIFIER))

(defn arg-access-qualifier ^long [kernel arg]
  (arg-info-long* kernel arg CL/CL_KERNEL_ARG_ACCESS_QUALIFIER))

(defn arg-type-name ^long [kernel arg]
  (arg-info-string* kernel arg CL/CL_KERNEL_ARG_TYPE_NAME))

(defn arg-type-qualifier ^long [kernel arg]
  (arg-info-long* kernel arg CL/CL_KERNEL_ARG_TYPE_QUALIFIER))

(defn arg-name [kernel arg]
  (arg-info-string* kernel arg CL/CL_KERNEL_ARG_NAME))

(defrecord KernelArgInfo [address-qualifier access-qualifier type-name
                          type-qualifier name])

(defn arg-info
  ([kernel arg info-type]
   (maybe
    (case info-type
      :address-qualifier
      (dec-kernel-arg-address-qualifier (arg-address-qualifier kernel arg))
      :access-qualifier
      (dec-kernel-arg-access-qualifier (arg-access-qualifier kernel arg))
      :type-name (arg-type-name kernel arg)
      :type-qualifier
      (set (unmask cl-kernel-arg-type-qualifier (arg-type-qualifier kernel arg)))
      :name (arg-name kernel arg)
      nil)))
  ([kernel arg]
   (->KernelArgInfo (maybe (dec-kernel-arg-address-qualifier
                            (arg-address-qualifier kernel arg)))
                    (maybe (dec-kernel-arg-access-qualifier
                            (arg-access-qualifier kernel arg)))
                    (maybe (arg-type-name kernel arg))
                    (maybe (set (unmask cl-kernel-arg-type-qualifier
                                        (arg-type-qualifier kernel arg))))
                    (maybe (arg-name kernel arg))))
  ([kernel]
   (map (partial arg-info kernel) (range (num-args kernel)) )))

;; ===================== Kernel Sub Group =====================================

;; TODO

;; ===================== Kernel Work Group ====================================

;; TODO

;; ===================== Mem Object ===========================================

(defn mem-type ^long [mo]
  (info-int* CL/clGetMemObjectInfo mo CL/CL_MEM_TYPE))

(defn flags ^long [mo]
  (info-long* CL/clGetMemObjectInfo mo CL/CL_MEM_FLAGS))

(defn mem-size ^long [mo]
  (info-size* CL/clGetMemObjectInfo mo CL/CL_MEM_SIZE))

;;TODO see what to do with these voids, and whether they make sense with Java.
;;(defn mem-host-ptr [mo]
;;  (info-long* CL/clGetMemObjectInfo mo CL/CL_MEM_HOST_PTR))

(defn map-count ^long [mo]
  (info-int* CL/clGetMemObjectInfo mo CL/CL_MEM_MAP_COUNT))

(defn ^:private aget-first-np [^objects npa]
  (aget npa 0))

(defn mem-context [mo]
  (aget-first-np (info-native* CL/clGetMemObjectInfo mo CL/CL_MEM_CONTEXT
                               cl_context Sizeof/cl_context)))

(defn associated-memobject [mo]
  (aget-first-np (info-native* CL/clGetMemObjectInfo mo
                               CL/CL_MEM_ASSOCIATED_MEMOBJECT
                               cl_mem Sizeof/cl_mem)))

(defn offset ^long [mo]
  (info-size* CL/clGetMemObjectInfo mo CL/CL_MEM_OFFSET))

(defn uses-svm-pointer [mo]
  (info-bool* CL/clGetMemObjectInfo mo CL/CL_MEM_USES_SVM_POINTER))

(defrecord MemObjectInfo [type flags size map-count reference-count
                          context associated-memobject offset
                          uses-svm-pointer])

(extend-type cl_mem
  Info
  (info
    ([mo info-type]
     (maybe
      (case info-type
        :type (dec-mem-object-type (mem-type mo))
        :flags (set (unmask cl-mem-flags (flags mo)))
        :size (mem-size mo)
        :map-count (map-count mo)
        :reference-count (reference-count mo)
        :context (mem-context mo)
        :associated-memobject (associated-memobject mo)
        :offset (offset mo)
        :uses-svm-pointer (uses-svm-pointer mo)
        nil)))
    ([mo]
     (->MemObjectInfo (maybe (dec-mem-object-type (mem-type mo)))
                      (maybe (set (unmask cl-mem-flags (flags mo))))
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

;; -- Program Build Info has special utility functions with one more param ----

(defmacro ^:private pb-info-string* [program device info]
  `(let [cnt# (long-array 1)
         err# (CL/clGetProgramBuildInfo ~program ~device ~info 0 nil cnt#)]
     (with-check err#
       (let [res# (byte-array (aget cnt# 0))
             err# (CL/clGetProgramBuildInfo ~program ~device ~info
                                         (alength res#) (Pointer/to res#)
                                         nil)]
         (with-check err#
           (String. res# 0 (dec (alength res#))))))))

(defmacro ^:private pb-info-int* [program device info]
  `(let [res# (int-array 1)
         err# (CL/clGetProgramBuildInfo ~program ~device ~info Sizeof/cl_int
                                     (Pointer/to res#) nil)]
     (with-check err# (aget res# 0))))

(let [pointer-to-buffer (fn [^ByteBuffer b]
                          (Pointer/to b))]
  (defmacro ^:private pb-info-size* [program device info]
    `(let [res# (buffer Sizeof/size_t)
           err# (CL/clGetProgramBuildInfo
                 ~program ~device ~info Sizeof/size_t
                 (~pointer-to-buffer res#) nil)]
       (with-check err#
         (first (wrap-byte-seq (if (= 4 Sizeof/size_t)
                                 int32
                                 int64)
                               (byte-seq res#)))))))

;; -- Program Build Info functions --------------------------------------------

(defn build-status ^long [program device]
  (pb-info-int* program device CL/CL_PROGRAM_BUILD_STATUS))

(defn build-options [program device]
  (pb-info-string* program device CL/CL_PROGRAM_BUILD_OPTIONS))

(defn build-log [program device]
  (pb-info-string* program device CL/CL_PROGRAM_BUILD_LOG))

(defn binary-type ^long [program device]
  (pb-info-int* program device CL/CL_PROGRAM_BINARY_TYPE))

(defn global-variable-total-size ^long [program device]
  (pb-info-size* program device CL/CL_PROGRAM_BUILD_GLOBAL_VARIABLE_TOTAL_SIZE))

(defrecord ProgramBuildInfo [build-status build-options build-log
                             binary-type global-variable-total-size])

(defn build-info
  ([program device info-type]
   (maybe
    (case info-type
      :status (dec-build-status (build-status program device))
      :options (build-options program device)
      :log (build-log program device)
      :binary-type (dec-program-binary-type (binary-type program device))
      :global-variable-total-size (global-variable-total-size program device))))
  ([program device]
   (->ProgramBuildInfo (maybe (dec-build-status (build-status program device)))
                       (maybe (build-options program device))
                       (maybe (build-log program device))
                       (maybe (dec-program-binary-type
                               (binary-type program device)))
                       (maybe (global-variable-total-size program device)))))

;; ===================== Program ==============================================

(defn program-context [p]
  (aget-first-np (info-native* CL/clGetProgramInfo p CL/CL_PROGRAM_CONTEXT
                               cl_context Sizeof/cl_context)))

(defn program-num-devices ^long [p]
  (info-int* CL/clGetProgramInfo p CL/CL_PROGRAM_NUM_DEVICES))

(defn program-devices [p]
  (vec (info-native* CL/clGetProgramInfo p CL/CL_PROGRAM_DEVICES
                     cl_device_id Sizeof/cl_device_id)))

(defn program-source [p]
  (info-string* CL/clGetProgramInfo p CL/CL_PROGRAM_SOURCE))

(defn binary-sizes [p]
  (info-size* CL/clGetProgramInfo p CL/CL_PROGRAM_BINARY_SIZES
              (program-num-devices p)))

(defn binaries [p]
  (let [result-buffers (map direct-buffer (binary-sizes p))
        pointer (native-pointer (into-array Pointer (map pointer-to-buffer
                                                         result-buffers)))
        err (CL/clGetProgramInfo p CL/CL_PROGRAM_BINARIES
                                 (* (count result-buffers) Sizeof/POINTER)
                                 pointer nil)]
    (with-check err result-buffers)))

(defn program-num-kernels ^long [p]
  (if (some pos? (binary-sizes p))
    (info-size* CL/clGetProgramInfo p CL/CL_PROGRAM_NUM_KERNELS)
    0))

(defn kernel-names [p]
  (if (some pos? (binary-sizes p))
    (to-set (info-string* CL/clGetProgramInfo p CL/CL_PROGRAM_KERNEL_NAMES))
    #{}))

(defrecord ProgramInfo [reference-count context num-devices devices source
                        binary-sizes binaries num-kernels kernel-names])

(extend-type cl_program
  Info
  (info
    ([p info-type]
     (maybe
      (case info-type
        :reference-count (reference-count p)
        :context (program-context p)
        :num-devices (program-num-devices p)
        :devices (program-devices p)
        :source (program-source p)
        :binary-sizes (binary-sizes p)
        :binaries (binaries p)
        :num-kernels (program-num-kernels p)
        :kernel-names (kernel-names p)
        nil)))
    ([p]
     (->ProgramInfo (maybe (reference-count p)) (maybe (program-context p))
                    (maybe (program-num-devices p)) (maybe (program-devices p))
                    (maybe (program-source p)) (maybe (binary-sizes p))
                    (maybe (binaries p)) (maybe (program-num-kernels p))
                    (maybe (kernel-names p)))))
  InfoReferenceCount
  (reference-count [p]
    (info-int* CL/clGetProgramInfo p CL/CL_PROGRAM_REFERENCE_COUNT)))

;; ===================== Sampler ==============================================

;; TODO

;; ===================== GL Context ===========================================

;; TODO
;; ===================== GL Object ============================================

;; TODO

;; ===================== GL Texture ===========================================

;; TODO

;; ============================================================================
