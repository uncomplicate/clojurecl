;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    uncomplicate.clojurecl.internal.constants
  "Defines constants and mappings from/to OpenCL constants.

  OpenCL API defines and uses numerous int/long C-style constants as arguments
in functions calls, mostly for configuring various options. Clojure uses keywords
as an user friendly alternative. ClojureCL's `core` namespace contains primitive
functions suffixed with `*`, which still accept the low level constants
defined in `org.jocl.CL` Java class, but the preferred, easier, and natural way
is to use keywords. Another benefit of that method is that you can easily view
available options by printing an appropriate hash-map from this namespace.

  Most mappings are two-way. Hashmaps that convert keywords to number codes
  are named `cl-something-clish`, while functions that convert numbers to keywords
  are named `dec-something-clish`. You can see which keywords are available for
  a certain property by evaluate appropriate `cl-something-clish` hashmap.
  All hashmaps and functions contain brief doc and a web link to appropriate
  online OpenCL documentation with detailed explanations

  Also see the summary at
  http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/enums.html"
  (:import org.jocl.CL))

;; ===== OpenCL defines this, but JOCL 0.2.0 still misses it.
(def ^{:no-doc true :const true}
  CL_DEVICE_SPIR_VERSIONS 0x40E0)

(def ^{:no-doc true :const true}
  CL_TERMINATE_CAPABILITY_KHR 0x200F)

;; ============= Error Codes ===================================================

(defn dec-error
  "Decodes OpenCL error code to a meaningful string.
  If called with a number that is not recognized as an existing OpenCL error,
  returns `\"UNKNOWN OpenCL ERROR!\"`

  Also see the discussion at
  http://streamcomputing.eu/blog/2013-04-28/opencl-1-2-error-codes/"
  [^long code]
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

(def ^{:doc "Types of OpenCL devices defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceIDs.html"
       :const true}
  cl-device-type
  {:gpu CL/CL_DEVICE_TYPE_GPU
   :cpu CL/CL_DEVICE_TYPE_CPU
   :default CL/CL_DEVICE_TYPE_DEFAULT
   :accelerator CL/CL_DEVICE_TYPE_ACCELERATOR
   :custom CL/CL_DEVICE_TYPE_CUSTOM
   :all CL/CL_DEVICE_TYPE_ALL})

(def ^{:doc "Floating point capabilities of the device defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-device-fp-config
  {:denorm CL/CL_FP_DENORM
   :inf-nan CL/CL_FP_INF_NAN
   :round-to-nearest CL/CL_FP_ROUND_TO_NEAREST
   :round-to-zero CL/CL_FP_ROUND_TO_ZERO
   :round-to-inf CL/CL_FP_ROUND_TO_INF
   :fma CL/CL_FP_FMA
   :correctly-rounded-divide-sqrt CL/CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT
   :soft-float CL/CL_FP_SOFT_FLOAT})

(def
  ^{:doc "Types of global memory cache defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"}
  cl-device-mem-cache-type
  {:none CL/CL_NONE
   :read-only CL/CL_READ_ONLY_CACHE
   :read-write CL/CL_READ_WRITE_CACHE})

(def ^{:doc "Types of local memory defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-local-mem-type
  {:local CL/CL_LOCAL
   :global CL/CL_GLOBAL
   :none CL/CL_NONE})

(def ^{:doc "The execution capabilities of the device defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-device-exec-capabilities
  {:kernel CL/CL_EXEC_KERNEL
   :native-kernel CL/CL_EXEC_NATIVE_KERNEL})

(def ^{:doc "On device command-queue properties defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-command-queue-properties
  {:out-of-order-exec-mode CL/CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE
   :profiling CL/CL_QUEUE_PROFILING_ENABLE
   :queue-on-device CL/CL_QUEUE_ON_DEVICE
   :queue-on-device-default CL/CL_QUEUE_ON_DEVICE_DEFAULT})

(def ^{:doc "Context properties defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-context-properties
  {:platform CL/CL_CONTEXT_PLATFORM
   :interop-user-sync CL/CL_CONTEXT_INTEROP_USER_SYNC
   :gl-context-khr CL/CL_GL_CONTEXT_KHR
   :cgl-sharegroup-khr CL/CL_CGL_SHAREGROUP_KHR
   :egl-display-khr CL/CL_EGL_DISPLAY_KHR
   :glx-display-khr CL/CL_GLX_DISPLAY_KHR
   :wgl-hdc-khr CL/CL_WGL_HDC_KHR})

(defn dec-context-properties
  "Converts `cl_context_properties` code from number to keyword.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
  [^long code]
  (case code
    0x1004 :platform
    0x1005 :interop-user-sync
    code))

(defn dec-device-partition-property
  "Converts `cl_device_partition_property` code from number to keyword.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
  [^long code]
  (case code
    0x1086 :equally
    0x1087 :by-counts
    0x0 :by-counts-list-end
    0x1088 :by-affinity-domain
    code))

(def ^{:doc "Affinity domains for partitioning the device defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-device-affinity-domain
  {:numa CL/CL_DEVICE_AFFINITY_DOMAIN_NUMA
   :l1-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L1_CACHE
   :l2-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L2_CACHE
   :l3-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L3_CACHE
   :l4-cache CL/CL_DEVICE_AFFINITY_DOMAIN_L4_CACHE
   :next-partitionable CL/CL_DEVICE_AFFINITY_DOMAIN_NEXT_PARTITIONABLE})

(def ^{:doc "Context properties defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceInfo.html"
       :const true}
  cl-device-svm-capabilities
  {:coarse-grain-buffer CL/CL_DEVICE_SVM_COARSE_GRAIN_BUFFER
   :fine-grain-buffer CL/CL_DEVICE_SVM_FINE_GRAIN_BUFFER
   :fine-grain-system CL/CL_DEVICE_SVM_FINE_GRAIN_SYSTEM
   :atomics CL/CL_DEVICE_SVM_ATOMICS})

(def ^{:doc "Memory allocation and usage information defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateBuffer.html"
       :const true}
  cl-mem-flags
  {:read-write CL/CL_MEM_READ_WRITE
   :write-only CL/CL_MEM_WRITE_ONLY
   :read-only CL/CL_MEM_READ_ONLY
   :use-host-ptr CL/CL_MEM_USE_HOST_PTR
   :alloc-host-ptr CL/CL_MEM_ALLOC_HOST_PTR
   :copy-host-ptr CL/CL_MEM_COPY_HOST_PTR
   :host-write-only CL/CL_MEM_HOST_WRITE_ONLY
   :host-read-only CL/CL_MEM_HOST_READ_ONLY
   :host-no-access CL/CL_MEM_HOST_NO_ACCESS})

(def ^{:doc "Memory allocation and usage information defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSVMAlloc.html"
       :const true}
  cl-svm-mem-flags
  {:read-write CL/CL_MEM_READ_WRITE
   :write-only CL/CL_MEM_WRITE_ONLY
   :read-only CL/CL_MEM_READ_ONLY
   :fine-grain-buffer CL/CL_MEM_SVM_FINE_GRAIN_BUFFER
   :atomics CL/CL_MEM_SVM_ATOMICS})

(defn dec-mem-object-type
  "Converts `cl_mem_object_type` code from number to keyword.
  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetMemObjectInfo.html"
  [^long code]
  (case code
    0x10F0 :buffer
    0x10F1 :image2d
    0x10F2 :image3d
    0x10F3 :image2d-array
    0x10F4 :image1d
    0x10F5 :image1d-array
    0x10F6 :image1d-buffer
    0x10F7 :pipe
    code))

(def ^{:doc "Map flags used in enqueuing buffer mapping defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueMapBuffer.html"
       :const true}
  cl-map-flags
  {:read CL/CL_MAP_READ
   :write CL/CL_MAP_WRITE
   :write-invalidate-region CL/CL_MAP_WRITE_INVALIDATE_REGION})

(defn dec-program-binary-type
  "Converts `cl_program_binary_type` code from number to keyword.
  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetProgramBuildInfo.html"
    [^long code]
  (case code
    0x0 :none
    0x1 :compiled-object
    0x2 :library
    0x4 :executable
    0x40E1 :intermediate
    code))

(defn dec-build-status
  "Converts `cl_program_build_status` code from number to keyword.
  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetProgramBuildInfo.html"
  [^long code]
  (case code
    0 :success
    -1 :none
    -2 :error
    -3 :in-progress
    code))

(defn
  dec-kernel-arg-address-qualifier
  "Converts `cl_kernel_arg_address_qualifier` code from number to keyword.
  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetKernelArgInfo.html"
  [^long code]
  (case code
    0x119B :global
    0x119C :local
    0x119D :constant
    0x119E :private
    code))

(defn dec-kernel-arg-access-qualifier
  "Converts `cl_kernel_arg_access_qualifier` code from number to keyword.
  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetKernelArgInfo.html"
  [^long code]
  (case code
    0x11A0 :read-only
    0x11A1 :write-only
    0x11A2 :read-write
    0x11A3 :none
    code))

(def  ^{:doc "Type quilifiers specified for the argument, defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetKernelArgInfo.html"
        :const true}
  cl-kernel-arg-type-qualifier
  {:const CL/CL_KERNEL_ARG_TYPE_CONST
   :restrict CL/CL_KERNEL_ARG_TYPE_RESTRICT
   :volatile CL/CL_KERNEL_ARG_TYPE_VOLATILE
   :pipe CL/CL_KERNEL_ARG_TYPE_PIPE
   :none CL/CL_KERNEL_ARG_TYPE_NONE})

(defn dec-command-type
  "Converts `cl_event_command_type` code from number to keyword.
  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetEventInfo.html"
  [^long code]
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
    0x200D :gl-fence-sync-object-khr
    code))

(defn dec-command-execution-status
  "Converts `cl_event_command_execution_status` code from number to keyword.
  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetEventInfo.html"
  [^long code]
  (case code
    0x0 :complete
    0x1 :running
    0x2 :submitted
    0x3 :queued
    code))

(def
  ^{:doc "Execution statuses of commands, defined in OpenCL standard.
See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetEventInfo.html"}
  cl-command-execution-status
  {:complete CL/CL_COMPLETE
   :running CL/CL_RUNNING
   :submitted CL/CL_SUBMITTED
   :queued CL/CL_QUEUED})
