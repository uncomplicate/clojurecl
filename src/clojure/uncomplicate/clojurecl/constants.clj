(ns uncomplicate.clojurecl.constants
  (:import org.jocl.CL))

;; TODO ========= OpenCL 2.0 Constants ======================================

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

(def CL_KERNEL_ARG_TYPE_PIPE (bit-shift-left 1 3))

(def CL_MEM_USES_SVM_POINTER 0x1109)
(def CL_MEM_SVM_FINE_GRAIN_BUFFER (bit-shift-left 1 10))
(def CL_MEM_SVM_ATOMICS (bit-shift-left 1 11))

(def CL_PROGRAM_BUILD_GLOBAL_VARIABLE_TOTAL_SIZE 0x1185)

(def CL_QUEUE_ON_DEVICE (bit-shift-left 1 2))
(def CL_QUEUE_ON_DEVICE_DEFAULT (bit-shift-left 1 3))
(def CL_QUEUE_SIZE 0x1094)

(def CL_TERMINATE_CAPABILITY_KHR 0x200F)

;; ============= Error Codes ===================================================

(defn dec-error [^long code]
  (case code
    0 "CL_SUCCESS"
    -1 "CL_DEVICE_NOT_FOUND"
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
;; ==================== Keyword mapping ======================================

(def cl-device-type
  {:gpu CL/CL_DEVICE_TYPE_GPU
   :cpu CL/CL_DEVICE_TYPE_CPU
   :default CL/CL_DEVICE_TYPE_DEFAULT
   :accelerator CL/CL_DEVICE_TYPE_ACCELERATOR
   :custom CL/CL_DEVICE_TYPE_CUSTOM
   :all CL/CL_DEVICE_TYPE_ALL})

(def cl-device-fp-config
  {:denorm CL/CL_FP_DENORM
   :inf-nan CL/CL_FP_INF_NAN
   :round-to-nearest CL/CL_FP_ROUND_TO_NEAREST
   :round-to-zero CL/CL_FP_ROUND_TO_ZERO
   :round-to-inf CL/CL_FP_ROUND_TO_INF
   :fma CL/CL_FP_FMA
   :correctly-rounded-divide-sqrt CL/CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
   :soft-float CL/CL_FP_SOFT_FLOAT})

(def cl-device-mem-cache-type
  {:none CL/CL_NONE
   :read-only CL/CL_READ_ONLY_CACHE
   :read-write CL/CL_READ_WRITE_CACHE})

(def cl-local-mem-type
  {:local CL/CL_LOCAL
   :global CL/CL_GLOBAL
   :none CL/CL_NONE})

(def cl-device-exec-capabilities
  {:kernel CL/CL_EXEC_KERNEL
   :native-kernel CL/CL_EXEC_NATIVE_KERNEL})

(def cl-command-queue-properties
  {:out-of-order-exec-mode CL/CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE
   :profiling CL/CL_QUEUE_PROFILING_ENABLE
   :queue-on-device CL_QUEUE_ON_DEVICE
   :queue-on-device-default CL_QUEUE_ON_DEVICE_DEFAULT})

(def cl-context-properties
  {:platform CL/CL_CONTEXT_PLATFORM
   :interop-user-sync CL/CL_CONTEXT_INTEROP_USER_SYNC
   :gl-context-khr CL/CL_GL_CONTEXT_KHR
   :cgl-sharegroup-khr CL/CL_CGL_SHAREGROUP_KHR
   :egl-display-khr CL/CL_EGL_DISPLAY_KHR
   :glx-display-khr CL/CL_GLX_DISPLAY_KHR
   :wgl-hdc-khr CL/CL_WGL_HDC_KHR})

(defn dec-context-properties [^long code]
  (case code
    0x1004 :platform
    0x1005 :interop-user-sync))

(defn dec-device-partition-property [^long code]
  (case code
    0x1086 :equally
    0x1087 :by-counts
    0x0 :by-counts-list-end
    0x1088 :by-affinity-domain))

(def cl-device-affinity-domain
  {:numa CL/CL_DEVICE_AFFINITY_DOMAIN_NUMA
   :l1-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L1_CACHE
   :l2-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L2_CACHE
   :l3-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L3_CACHE
   :l4-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L4_CACHE
   :next-partitionable CL/CL_DEVICE_AFFINITY_DOMAIN_NEXT_PARTITIONABLE})

(def cl-device-svm-capabilities
  {:coarse-grain-buffer CL_DEVICE_SVM_COARSE_GRAIN_BUFFER
   :fine-grain-buffer CL_DEVICE_SVM_FINE_GRAIN_BUFFER
   :fine-grain-system CL_DEVICE_SVM_FINE_GRAIN_SYSTEM
   :atomics CL_DEVICE_SVM_ATOMICS})

(def cl-mem-flags
  {:read-write CL/CL_MEM_READ_WRITE
   :write-only CL/CL_MEM_WRITE_ONLY
   :read-only CL/CL_MEM_READ_ONLY
   :use-host-ptr CL/CL_MEM_USE_HOST_PTR
   :alloc-host-ptr CL/CL_MEM_ALLOC_HOST_PTR
   :copy-host-ptr CL/CL_MEM_COPY_HOST_PTR
   :host-write-only CL/CL_MEM_HOST_WRITE_ONLY
   :host-read-only CL/CL_MEM_HOST_READ_ONLY
   :host-no-access CL/CL_MEM_HOST_NO_ACCESS
   :fine-grain-buffer CL_MEM_SVM_FINE_GRAIN_BUFFER
   :atomics CL_MEM_SVM_ATOMICS})

(defn dec-mem-object-type [^long code]
  (case code
    0x10F0 :buffer
    0x10F1 :image2d
    0x10F2 :image3d
    0x10F3 :image2d-array
    0x10F4 :image1d
    0x10F5 :image1d-array
    0x10F6 :image1d-buffer
    0x10F7 :pipe))

(defn dec-program-binary-type [^long code]
  (case code
    0x0 :none
    0x1 :compiled-object
    0x2 :library
    0x4 :executable
    0x40E1 :intermediate))

(defn dec-build-status [^long code]
  (case code
    0 :success
    -1 :none
    -2 :error
    -3 :in-progress))

(defn dec-kernel-arg-address-qualifier [^long code]
  (case code
    0x119B :global
    0x119C :local
    0x119D :constant
    0x119E :private))

(defn dec-kernel-arg-access-qualifier [^long code]
  (case code
    0x11A0 :read-only
    0x11A1 :write-only
    0x11A2 :read-write
    0x11A3 :none))

(def cl-kernel-arg-type-qualifier
  {:const CL/CL_KERNEL_ARG_TYPE_CONST
   :restrict CL/CL_KERNEL_ARG_TYPE_RESTRICT
   :volatile CL/CL_KERNEL_ARG_TYPE_VOLATILE
   :pipe CL_KERNEL_ARG_TYPE_PIPE
   :none CL/CL_KERNEL_ARG_TYPE_NONE})

(defn dec-command-type [^long code]
  (case code
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

(defn dec-command-execution-status [^long code]
  (case code
    0x0 :complete
    0x1 :running
    0x2 :submitted
    0x3 :queued))
