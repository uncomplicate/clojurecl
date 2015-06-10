(ns ^{:author "Dragan Djuric"}
  uncomplicate.clojurecl.core
  "Core ClojureCL functions for OpenCL **host** programming. The kernels should
  be provided as strings (that may be stored in files), written in OpenCL C.

  The OpenCL standard defines several datastructures (platform, device, etc.)
  that support the concepts defined in four OpenCL models (Platform Model,
  Memory Model, Execution Model, and Programming Model). ClojureCL uses
  a low-level JNI-based library [JOCL](http://www.jocl.org) for calling
  native OpenCL drivers - the datastructures are therefore defined in JOCL.
  They can be found in [`org.jocl`]
  (http://www.jocl.org/doc/org/jocl/package-tree.html) package.

  Some functions are available in two versions:

  * High-level, which works with clojure-friendly arguments - vectors,
  sequences, keywords, etc. These are preferred to low-level alternatives.
  * Low-level, suffexed by `*`, which works with primitive arguments
  and gives primitive results. These functions are useful when you
  already have primitive data and want to avoid unnecessary conversions

  ### Cheat Sheet

  * resource management: TODO
  [[with-platform]], [[with-context]], [[with-queue]], [[with-default]]

  * [`cl_platform_id`](http://www.jocl.org/doc/org/jocl/cl_platform_id.html):
  [[num-platforms]], [[platforms]], [[platform-info]], [[with-platform]]

  * [`cl_device_id`](http://www.jocl.org/doc/org/jocl/cl_device_id.html):
  [[devices]], [[num-devices]], [[devices*]], [[num-devices*]]

  * [`cl_context`](http://www.jocl.org/doc/org/jocl/cl_context.html):
  [[context]], [[context-info]], [[with-context]],
  [[context-properties]], [[context*]]

  * [`cl_mem`](http://www.jocl.org/doc/org/jocl/cl_mem.html):
  [[cl-buffer]], [[cl-sub-buffer]], [[cl-buffer*]], [[cl-sub-buffer*]],
  [[Mem]], [[CLMem]]

  * [`cl_event`](http://www.jocl.org/doc/org/jocl/cl_event.html):
  [[event]], [[host-event]], [[events]], [[register]], [[event-callback]],
  [[set-event-callback*]], [[set-status!]]

  * [`cl_program`](http://www.jocl.org/doc/org/jocl/cl_program.html):
  [[program-with-source]], [[build-program!]]

  * [`cl_kernel`](http://www.jocl.org/doc/org/jocl/cl_kernel.html):
  [[num-kernels]], [[kernel]], [[set-arg!]], [[set-args!]], [[set-arg]]

  * [`cl_command_queue`](http://www.jocl.org/doc/org/jocl/cl_kernel.html):
  [[command-queue]], [[command-queue*]], [[work-size]], [[enq-nd!]],
  [[enq-read!]], [[enq-write!]], [[enq-map-buffer!]], [[enq-map-buffer*]],
  [[enq-unmap!]], [[enq-marker!]], [[enq-wait!]], [[enq-barrier!]],
  [[finish!]], [[with-queue]]
  "
  (:require [uncomplicate.clojurecl
             [constants :refer :all]
             [utils :refer [with-check with-check-arr mask error]]
             [info :refer [info build-info program-devices]]]
            [clojure.string :as str]
            [clojure.core.async :refer [go >!]])
  (:import [org.jocl CL cl_platform_id cl_context_properties cl_device_id
            cl_context cl_command_queue cl_mem cl_program cl_kernel cl_sampler
            cl_event cl_buffer_region cl_queue_properties
            Sizeof Pointer CreateContextFunction EventCallbackFunction
            BuildProgramFunction]
           [java.nio ByteBuffer ByteOrder]))

(def ^{:dynamic true
       :doc "Dynamic var for binding the default platform."}
  *platform*)

(def ^{:dynamic true
       :doc "Dynamic var for binding the default context."}
  *context*)

(def ^{:dynamic true
       :doc "Dynamic var for binding the default command queue."}
  *command-queue*)

;; =============== Release CL Resources ==================================

(defprotocol Releaseable
  "Objects that hold resources that can be released after use. For OpenCL
  objects, releasing  means decrementing the reference count of the object.
  "
  (release [this]
    "Releases the resource held by `this`. For OpenCL objects,
calls the appropriate org.jocl.CL/clReleaseX method that decrements
`this`'s reference count."))

(extend-type cl_command_queue
  Releaseable
  (release [q]
    (with-check (CL/clReleaseCommandQueue q) true)))

(extend-type cl_context
  Releaseable
  (release [c]
    (with-check (CL/clReleaseContext c) true)))

(extend-type cl_device_id
  Releaseable
  (release [d]
    (with-check (CL/clReleaseDevice d) true)))

(extend-type cl_event
  Releaseable
  (release [e]
    (with-check (CL/clReleaseEvent e) true)))

(extend-type cl_kernel
  Releaseable
  (release [k]
    (with-check (CL/clReleaseKernel k) true)))

(extend-type cl_mem
  Releaseable
  (release [m]
    (with-check (CL/clReleaseMemObject m) true)))

(extend-type cl_program
  Releaseable
  (release [p]
    (with-check (CL/clReleaseProgram p) true)))

(extend-type cl_sampler
  Releaseable
  (release [s]
    (with-check (CL/clReleaseSampler s) true)))

(defn release-seq
  "if `cl` is an OpenCL object, releases it; if it is a (possibly nested)
  sequence of OpenCL objects, calls itself on each element.
  "
  [cl]
  (if (sequential? cl)
    (map release-seq cl)
    (release cl)))

(defmacro with-release
  "Binds `Releasable` elements to symbols (like `let` do), evaluates
  `body`, and at the end releases the resources held by the bindings. The bindings
  can also be deeply sequential (see examples) - they will be released properly.

  Examples:

  TODO.
  "
  [bindings & body]
  (assert (vector? bindings) "a vector for its binding")
  (assert (even? (count bindings)) "an even number of forms in binding vector")
  (cond
   (= (count bindings) 0) `(do ~@body)
   (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                             (try
                               (with-release ~(subvec bindings 2) ~@body)
                               (finally
                                 (release-seq ~(bindings 0)))))
   :else (throw (IllegalArgumentException.
                 "with-release only allows Symbols in bindings"))))

;; =============== Platform =========================================

(defn num-platforms
  "The number of available OpenCL platforms.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetPlatformIDs.html
  and http://www.jocl.org/doc/org/jocl/CL.html#clGetPlatformIDs-int-org.jocl.cl_platform_id:A-int:A-
  "
  ^long []
  (let [res (int-array 1)
        err (CL/clGetPlatformIDs 0 nil res)]
    (with-check err (aget res 0))))

(defn platforms
  "Returns a vector of all available OpenCL platforms (`cl_platform_id`s).
  `cl_platform_id` objects do not need to be released explicitly.

  Platforms are represented by the [`org.jocl.platform_id`]
(http://www.jocl.org/doc/org/jocl/cl_platform_id.html) datastructure.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetPlatformIDs.html
  and http://www.jocl.org/doc/org/jocl/CL.html#clGetPlatformIDs-int-org.jocl.cl_platform_id:A-int:A-
  "
  []
  (let [num-platforms (num-platforms)
        res (make-array cl_platform_id num-platforms)
        err (CL/clGetPlatformIDs num-platforms res nil)]
    (with-check err (vec res))))

(defn platform-info
  "Gets the [[info/info]] of the default platform [[*platform*]] (if it is bound).
  If [[*platform*]] is unbound, throws `Illegalargumentexception`."
  []
  (info *platform*))

(defmacro with-platform
  "Dynamically binds `platform` to the default platform [[*platform*]] and
  evaluates the body with that binding."
  [platform & body]
  `(binding [*platform* ~platform]
    ~@body))

;; =============== Device ==========================================

(defn num-devices*
  "Queries `platform` for the number of devices of `device-type`s. Device types
  are given as a bitfield, where each type is defined in the OpenCL standard.
  Available device types are accessible through `org.jocl.CL/CL_DEVICE_TYPE_X`
  constants. If there are no such devices, returns 0.

  NOTE: You should prefer a higher-level [[num-devices]] function, unless you
  already have a `device-type` in a long number form in your code.

  When called with an invalid platform, throws [ExceptionInfo]
  (http://clojuredocs.org/clojure.core/ex-info).

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceIDs.html
  and http://www.jocl.org/doc/org/jocl/CL.html#clGetDeviceIDs-int-org.jocl.cl_device_id:A-int:A-
  "
  ^long [platform ^long device-type]
  (let [res (int-array 1)
        err (CL/clGetDeviceIDs platform device-type 0 nil res)]
    (if (= CL/CL_DEVICE_NOT_FOUND err)
      0
      (with-check err (aget res 0)))))

(defn num-devices
  "Queries `platform` for the number of devices of one or a combination of
  several `device-type`s:
  `:gpu`, `:cpu`, `:all`, `:accelerator`, `:custom`, `:default`.

  When called with only one argument `x`:

  * if `x` is a keyword, returns the number of devices of type `x` in `*platform*`;
  * otherwise returns the number of all devices on the platform `x`.

  When called with no arguments, returns the number of all devices in `*platform*`.

  When called with an invalid platform, throws [ExceptionInfo]
  (http://clojuredocs.org/clojure.core/ex-info). When called with an unknown
  device type, throws `NullPointerException`

  See also [[num-devices*]].

  Examples:

      (devices)
      (devices (first (platforms)))
      (devices :gpu)
      (devices (first (platforms)) :gpu :cpu :accelerator)
  "
  ([platform device-type & device-types]
   (num-devices* platform (mask cl-device-type device-type device-types)))
  (^long [platform device-type]
         (num-devices* platform (cl-device-type device-type)))
  (^long [x]
         (if (keyword? x)
           (num-devices *platform* x)
           (num-devices* x CL/CL_DEVICE_TYPE_ALL)))
  (^long []
         (num-devices* *platform* CL/CL_DEVICE_TYPE_ALL)))

(defn devices*
  "Queries `platform` for the devices of `device-type`s, and returns them as an
  array of `cl_device_id`s. The types are given as a bitfield, where each type
  is a number constant defined in the OpenCL standard.
  Available device types are accessible through `org.jocl.CL/CL_DEVICE_TYPE_X`
  constants. If there are no such devices, returns a zero-length array.

  Root level devices do not need to be explicitly released.

  NOTE: You should prefer a higher-level [[devices]] function, unless you
  already have a `device-type` in a long number form in your code, and/or you
  want to get resulting devices in an array rather than in a vector.

  When called with an invalid platform, throws [ExceptionInfo]
  (http://clojuredocs.org/clojure.core/ex-info).

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clGetDeviceIDs.html
  and http://www.jocl.org/doc/org/jocl/CL.html#clGetDeviceIDs-int-org.jocl.cl_device_id:A-int:A-
  "
  [platform ^long device-type]
  (let [num-devices (num-devices* platform device-type)
        res (make-array cl_device_id num-devices)]
    (if (< 0 num-devices)
      (let [err (CL/clGetDeviceIDs platform device-type num-devices res nil)]
        (with-check err res))
      res)))

(defn devices
  "Queries `platform` for the devices of one or a combination of
  several `device-type`s:
  `:gpu`, `:cpu`, `:all`, `:accelerator`, `:custom`, `:default`,
  and returns them in a vector containing `cl_device_id` objects.

  When called with only one argument `x`:

  * if `x` is a keyword, returns the devices of type `x` in `*platform*`;
  * otherwise returns all devices on the platform `x`.

  When called with no arguments, returns all devices in `*platform*`.

  Root level devices do not need to be explicitly released.

  When called with an invalid platform, throws [ExceptionInfo]
  (http://clojuredocs.org/clojure.core/ex-info). When called with an unknown
  device type, throws `NullPointerException`

  See also [[devices*]].

  Examples:

      (devices)
      (devices (first (platforms)))
      (devices :gpu)
      (devices (first (platforms)) :gpu :cpu :accelerator)
  "
  ([platform device-type & device-types]
   (vec (devices* platform (mask cl-device-type device-type device-types))))
  ([x]
   (if (keyword? x)
     (devices *platform* x)
     (vec (devices* x CL/CL_DEVICE_TYPE_ALL))))
  ([]
   (vec (devices* *platform* CL/CL_DEVICE_TYPE_ALL))))

;; ========================= Context ===========================================

(defn context-properties
  "Creates `cl_context_properties` from a map of properties.
  Currently, this is not very useful, it is only here to
  support the full compatibility of [[context*]] function with
  the JOCL API."
  [props]
  (reduce (fn [^cl_context_properties cp [p v]]
            (do (.addProperty cp (cl-context-properties p) v)
                cp))
          (cl_context_properties.)
          props))

(defrecord CreateContextInfo [errinfo private-info data])

(deftype CreateContextCallback [ch]
  CreateContextFunction
  (function [this errinfo private-info cb data]
    (go (>! ch (->CreateContextInfo errinfo private-info data)))))

(defn context*
  "Creates `cl_context` for an array of `device`s, with optional
  `cl_context_properties`, error reporting core.async channel `ch`
  and user data that should accompany the error report.

  If `devices` is empty, throws `ExceptionInfo`.

  **Needs to be released after use.**

  This is a low-level alternative to [[context]].

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateContext.html
  See   throws `Illegalargumentexception`http://www.jocl.org/doc/org/jocl/CL.html#clCreateContext-org.jocl.cl_context_properties-int-org.jocl.cl_device_id:A-org.jocl.CreateContextFunction-java.lang.Object-int:A-
  "
  [^objects devices properties ch user-data]
  (let [err (int-array 1)
        res (CL/clCreateContext properties
                                (alength devices) devices
                                (if ch (->CreateContextCallback ch) nil)
                                user-data err)]
    (with-check-arr err res)))

(defn context
  "Creates `cl_context` for a vector of `device`s, with optional
  hashmap of `properties`, error reporting core.async channel `ch`
  and user data that should accompany the error report. If called with
  no arguments, creates a context using all devices of the default
  platform (`*platform`).

  If `devices` is empty, throws `ExceptionInfo`.

  **Needs to be released after use.** (see [[with-context]]).

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateContext.html
  , http://www.jocl.org/doc/org/jocl/CL.html#clCreateContext-org.jocl.cl_context_properties-int-org.jocl.cl_device_id:A-org.jocl.CreateContextFunction-java.lang.Object-int:A-

  Examples:

        (context)
        (context (devices (first (platforms))))
        (context (devices (first (platforms))) {:platform p} (chan) :my-data)
  "
  ([devices properties ch user-data]
   (context* (into-array cl_device_id devices)
             (context-properties properties) ch user-data))
  ([devices]
   (context devices nil nil nil))
  ([]
   (with-release [devs (devices)]
     (context devs))))

(defn context-info
  "Info of the default context ([[*context*]]). If [[*context*]] is unbound,
  throws `Illegalargumentexception`

  See also: [[with-context]]

  Example:

      (with-context (context devices)
          (context-info))
  "
  []
  (info *context*))

(defmacro with-context
  "Dynamically binds `context` to the default context [[*context*]].
  and evaluates the body with that binding. Releases the context
  in the `finally` block. Take care *not* to release that context in
  some other place; JVM might crash.

  Example:

      (with-context (context devices)
          (context-info))
  "
  [context & body]
  `(binding [*context* ~context]
     (try ~@body
          (finally (release *context*)))))

;; =========================== Memory  =========================================

(defprotocol CLMem
  "A wrapper for `cl_mem` objects, that also holds a `Pointer` to the cl mem
  object, context that created it, and size in bytes. It is useful in many
  functions that need that (redundant in Java) data because of the C background
  of OpenCL functions."
  (cl-mem [this]
    "The raw JOCL `cl_mem` object.")
  (cl-context [this]
    "Context that created this object.")
  (size [this]
    "Memory size of this cl object in bytes."))

(defprotocol Argument
  "Object that can be argument in OpenCL kernels. Built-in implementations:
  [[CLBuffer]], java numbers, primitive arrays and `ByteBuffer`s."
  (set-arg [arg kernel n]
    "Specific implementation of setting the kernel arguments."))

(defprotocol Mem
  "Object that represent memory that participates in OpenCL operations. It could
  be on the device ([[CLMem]]), or on the host.  Built-in implementations:
  cl buffer, Java primitive arrays and `ByteBuffer`s."
  (ptr [this]
    "JOCL `Pointer` to this object."))

(deftype CLBuffer [^cl_context ctx ^cl_mem cl ^Pointer cl* s]
  Releaseable
  (release [_]
    (release cl))
  Mem
  (ptr [_]
    cl*)
  CLMem
  (cl-mem [_]
    cl)
  (cl-context [_]
    ctx)
  (size [_]
    s)
  Argument
  (set-arg [_ kernel n]
    (with-check (CL/clSetKernelArg kernel n Sizeof/cl_mem cl*) kernel)))

(defn cl-buffer*
  "Creates a cl buffer object in `context`, given `size` in bytes and a bitfield
  `flags` describing memory allocation usage.

  Flags defined by the OpenCL standard are available as constants in the
  [org.jocl.CL](http://www.jocl.org/doc/org/jocl/CL.html) class.

  **Needs to be released after use.**

  This is a low-level alternative to [[cl-buffer*]]
  If  `context` is nil or the buffer size is invalid, throws `ExceptionInfo`.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateBuffer-org.jocl.cl_context-long-long-org.jocl.Pointer-int:A-

  Examples:

      (cl-buffer* 32 CL/CL_MEM_READ_WRITE)
      (cl-buffer* ctx 24 CL/CL_MEM_READ_ONLY)
  "
  ([context ^long size ^long flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer context flags size nil err)]
     (with-check-arr err (->CLBuffer context res (Pointer/to ^cl_mem res) size)))))

(defn cl-buffer
  "Creates a cl buffer object ([[CLMem]]) in `context`, given `size` in bytes
  and one or more memory allocation usage keyword flags: `:read-write`,
  `:read-only`, `:write-only`, `:use-host-ptr`, `:alloc-host-ptr`,
  `:copy-host-ptr`, `:host-write-only`, `:host-read-only`, `:host-no-access`.

  If called with two arguments, uses the default `*context*`
  (see [[with-context]]).

  **Needs to be released after use.**

  If  `context` is nil or the buffer size is invalid, throws `ExceptionInfo`.
  If some of the flags is invalid, throws `IllegalArgumentexception`.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateBuffer-org.jocl.cl_context-long-long-org.jocl.Pointer-int:A-

  Examples:

      (cl-buffer 32 :read-only)
      (cl-buffer ctx 24 :write-only)
  "
  ([context size flag & flags]
   (cl-buffer* context size (mask cl-mem-flags flag flags)))
  ([^long size flag]
   (cl-buffer* *context* size (cl-mem-flags flag)))
  ([^long size]
   (cl-buffer* *context* size 0)))

(defn cl-sub-buffer*
  "Creates a cl buffer object ([[CLMem]]) that shares data with an existing
  buffer object.

  * `cl-mem` has to be a valid low-level JOCL buffer object (`cl_mem`).
  * `flags` is a bitfield that specifies allocation usage (see [[cl-buffer*]]).
  * `create-type` is a type of buffer object to be created (in OpenCL 2.0, only
  `CL/CL_BUFFER_CREATE_TYPE_REGION` is supported).
  * `region` is a `cl_buffer_region` that specifies offset and size
  of the subbuffer.

  **Needs to be released after use.**

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateBuffer-org.jocl.cl_context-long-long-org.jocl.Pointer-int:A-

  Examples:

      (def cl-buff (cl-buffer ctx 32 :write-only))
      (def region (cl_buffer_region. 8 16))
      (cl-sub-buffer* cl-buff CL/CL_MEM_READ_WRITE
                      CL/CL_BUFFER_CREATE_TYPE_REGION region)
      (cl-sub-buffer* cl-buff CL/CL_MEM_READ_ONLY region)
  "
  ([^cl_mem cl-mem ^long flags ^long create-type ^cl_buffer_region region]
   (let [err (int-array 1)
         res (CL/clCreateSubBuffer cl-mem flags
                                   (int create-type)
                                   region err)]
     (with-check-arr err (->CLBuffer context res (Pointer/to ^cl_mem res) (.size region)))))
  ([cl-mem ^long flags region]
   (cl-sub-buffer* cl-mem flags CL/CL_BUFFER_CREATE_TYPE_REGION region)))

(defn cl-sub-buffer
  "Creates a cl buffer object ([[CLMem]]) that shares data with an existing
  buffer object.

  * `cl-mem` has to be a valid [[CLMem]] object.
  * `origin` and `size` are numbers that denote offset and size of the
  region in the origin buffer.
  * `flag` and `flags` are memory allocation usage keywords same as in
  [[cl-buffer]]

  **Needs to be released after use.**

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateSubBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateSubBuffer-org.jocl.cl_mem-long-int-org.jocl.cl_buffer_region-int:A-

  Examples:

      (def cl-buff (cl-buffer ctx 32 :write-only))
      (cl-sub-buffer cl-buff 8 16 :write-only)
      (cl-sub-buffer cl-buff 8 16)
  "
  ([buffer origin size flag & flags]
   (cl-sub-buffer* (cl-mem buffer) (mask cl-mem-flags flag flags)
                   (cl_buffer_region. origin size)))
  ([buffer origin size]
   (cl-sub-buffer* (cl-mem buffer) 0
                   (cl_buffer_region. origin size))))

(extend-type Number
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (long this) nil)
      kernel)))

(extend-type (Class/forName "[F")
  Mem
  (ptr [this]
    (Pointer/to ^floats this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Float/BYTES (alength ^floats this))
                                   (Pointer/to ^floats this))
      kernel)))

(extend-type (Class/forName "[D")
  Mem
  (ptr [this]
    (Pointer/to ^doubles this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Double/BYTES (alength ^doubles this))
                                   (Pointer/to ^doubles this))
      kernel)))

(extend-type (Class/forName "[I")
  Mem
  (ptr [this]
    (Pointer/to ^ints this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Integer/BYTES (alength ^ints this))
                                   (Pointer/to ^ints this))
      kernel)))

(extend-type (Class/forName "[J")
  Mem
  (ptr [this]
    (Pointer/to ^longs this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Long/BYTES (alength ^longs this))
                                   (Pointer/to ^longs this))
      kernel)))

(extend-type (Class/forName "[B")
  Mem
  (ptr [this]
    (Pointer/to ^bytes this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (alength ^bytes this)
                                   (Pointer/to ^bytes this))
      kernel)))

(extend-type (Class/forName "[S")
  Mem
  (ptr [this]
    (Pointer/to ^shorts this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (* Short/BYTES (alength ^shorts this))
                                   (Pointer/to ^shorts this))
      kernel)))

(extend-type (Class/forName "[C")
  Mem
  (ptr [this]
    (Pointer/to ^chars this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (* Character/BYTES (alength ^chars this))
                                   (Pointer/to ^chars this))
      kernel)))

(extend-type ByteBuffer
  Mem
  (ptr [this]
    (Pointer/toBuffer this)))

;; ============== Events ==========================================

(defn event
  "Creates new `cl_event`.

  See http://www.jocl.org/doc/org/jocl/cl_event.html.
  "
  []
  (cl_event.))

(defn host-event
  "Creates new `cl_event` on the host (in OpenCL terminology,
  known as \"user\" event.

  If called without `context` argument, uses [[*context*]].

  If `context` is `nil`, throws ExceptionInfo

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateUserEvent.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateUserEvent-org.jocl.cl_context-int:A-
  "
  ([]
   (host-event *context*))
  ([context]
   (let [err (int-array 1)
         res (CL/clCreateUserEvent context err)]
     (with-check-arr err res))))

(defn events
  "Creates an array of `cl_event`s. Arrays of events are
  used in enqueuing commands, not vectors or sequences."
  (^objects []
            (make-array cl_event 0))
  (^objects [e]
            (doto ^objects (make-array cl_event 1)
                  (aset 0 e)))
  (^objects [e0 e1]
            (doto ^objects (make-array cl_event 2)
                  (aset 0 e0)
                  (aset 1 e1)))
  (^objects [e0 e1 e2]
            (doto ^objects (make-array cl_event 3)
                  (aset 0 e0)
                  (aset 1 e1)
                  (aset 2 e2)))
  (^objects [e0 e1 e2 e3]
            (doto ^objects (make-array cl_event 4)
                  (aset 0 e0)
                  (aset 1 e1)
                  (aset 2 e2)
                  (aset 3 e3)))
  (^objects [e0 e1 e2 e3 e4]
            (doto ^objects (make-array cl_event 5)
                  (aset 0 e0)
                  (aset 1 e1)
                  (aset 2 e2)
                  (aset 3 e3)
                  (aset 4 e4)))
  (^objects [e0 e1 e2 e3 e4 & es]
            (let [len (+ 5 (long (count es)))
                  res (doto ^objects (make-array cl_event len)
                            (aset 0 e0)
                            (aset 1 e1)
                            (aset 2 e2)
                            (aset 3 e3)
                            (aset 4 e4))]
              (loop [i 5 es es]
                (if (< i len)
                  (do (aset res i (first es))
                      (recur (inc i) (next es)))
                  res)))))

(defrecord EventCallbackInfo [event status data])

(deftype EventCallback [ch]
  EventCallbackFunction
  (function [this event status data]
    (go (>! ch (->EventCallbackInfo
                event (dec-command-execution-status status) data)))))

(defn event-callback
  "Creates new `EventCallbackFunction` instance that puts
  [[EventCallbackInfo]] in the core.async channel when called.

  See also [[set-event-callback*]] and [[register]]"
  ^org.jocl.EventCallbackFunction [ch]
  (->EventCallback ch))

(defn set-event-callback*
  "Registers a callback function for an event and a specific command
  execution status. Returns the channel. MUST be called AFTER the event is
  used in the enqueue operation.

  If called without `callback-type` and `data`, registers [`CL/CL_COMPLETE`]
  (http://www.jocl.org/doc/org/jocl/CL.html#CL_COMPLETE) status.

  See [[event-callback]], [[register]], [[event]].
  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSetEventCallback.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clSetEventCallback-org.jocl.cl_event-int-org.jocl.EventCallbackFunction-java.lang.Object-

  Example:

      (set-event-callback* (user-event) (event-callback) CL/CL_COMPLETE :my-data)
      (set-event-callback* (user-event) (event-callback))
  "
  ([e ^EventCallback callback ^long callback-type data]
   (with-check
     (CL/clSetEventCallback e callback-type callback data)
     (.ch callback)))
  ([e ^EventCallback callback]
   (set-event-callback* e callback CL/CL_COMPLETE nil)))

(defn register
  "Creates a convenience function that registers callbacks for events.
  It is a high-level alternative to [[set-event-callback*]]. MUST be called
  AFTER the event is used in the enqueue operation.

  * `channel` is a channel for communicating asynchronous notifications
  * `callback-type` is an optional keyword that specifies the command execution
  status that will be the default for the resulting function: `:complete`,
  `:submitted`, or `running`.

  Returns a function with the following arguments:

  * `e` - user event that is being followed
  * `callback-type` - optional command execution status; if ommited, the default
  is used
  * `data` - optional notification data

  When called, the created function returns `channel` with registered callback.

  See [[event-callback]], [[set-event-callback*]], [[event]].
  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSetEventCallback.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clSetEventCallback-org.jocl.cl_event-int-org.jocl.EventCallbackFunction-java.lang.Object-

  Example:

      (def notifications (chan))
      (def follow (register notifications))
      (def e (event))
      (enq-read! comm-queue cl-object host-object e
      (follow e)
      (:event (<!! notifications))
"
  ([channel callback-type]
   (let [callback (->EventCallback channel)
         callb-type (get cl-command-execution-status
                         callback-type CL/CL_COMPLETE)]
     (fn
       ([e callback-type data]
        (set-event-callback* e callback
                            (cl-command-execution-status callback-type) data))
       ([e data]
        (set-event-callback* e callback callb-type data))
       ([e]
        (set-event-callback* e callback callb-type nil)))))
  ([channel]
   (register channel nil)))

(defn set-status!
  "Sets the status of a host event to indicate whether it is complete
  or there is an error (a negative value). It can be called only once to change
  the status. If called with only the first argument, sets the status to
  `CL/CL_COMPLETE`. Returns the event.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSetUsereventstatus.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clSetUserEventStatus-org.jocl.cl_event-int-

  Examples:

      (set-status! ev) ;; event's status will be CL_COMPLETE
      (set-status! ev -12) ;; indicates and error code -12
  "
  ([ev ^long status]
   (let [err (CL/clSetUserEventStatus ev (if (< status 0) status CL/CL_COMPLETE))]
     (with-check err ev)))
  ([ev]
   (set-status! ev CL/CL_COMPLETE)))

;; ============= Program ==========================================

(defrecord BuildCallbackInfo [program data])

(deftype BuildCallback [ch]
  BuildProgramFunction
  (function [this program data]
    (go (>! ch (->BuildCallbackInfo program data)))))

(defn program-with-source
  "Creates a `cl_program` for the context and loads the source code
  specified by the text strings given in the `source` sequence.
  When called with one argument, uses [[*context*]].

  In case of OpenCL errors during the program creation, throws
  `Exceptioninfo`.

  **Needs to be released after use.**

  See also [[build-program!]]

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateProgramWithSource.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateProgramWithSource-org.jocl.cl_context-int-java.lang.String:A-long:A-int:A-

  Example:

      (def source (slurp \"path-to-kernels/my_kernel.cl\"))
      (program-with-source ctx [source])
  "
  ([context source]
   (let [err (int-array 1)
         n (count source)
         res (CL/clCreateProgramWithSource
              context n (into-array String source) nil err)]
     (with-check-arr err res)))
  ([source]
   (program-with-source *context* source)))

(defn build-program!
  "Builds (compiles and links) a program executable; returns the program
  changed with side effects on `program` argument.

  Accepts the following arguments (nil is allowed for all optional arguments):

  * `program`: previously loaded `cl_program` that contains the program
  source or binary;
  * `devices` (optional): an optional sequence of `cl_device`s associated with
  the program (if not supplied, all devices are used);
  * `options` (optional): an optional string of compiler options
  (such as \"-Dname=value\");
  * `ch` (optional): core.async channel for notifications. If supplied,
  the build will be asynchronous;
  * `user-data` (optional): passed as part of notification data.

  In case of OpenCL errors during the program build, throws
  `Exceptioninfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clBuildProgram.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clBuildProgram-org.jocl.cl_program-int-org.jocl.cl_device_id:A-java.lang.String-org.jocl.BuildProgramFunction-java.lang.Object-

  Examples:

      (build-program! program) ; synchronous
      (build-program! program ch) ; asynchronous
      (build-program! program \"-cl-std=CL2.0\" ch) ; asynchronous
      (build-program! program [dev] \"-cl-std=CL2.0\" ch) ; async
      (build-program! program [dev] \"-cl-std=CL2.0\" ch :my-data) ; async
  "
  ([program devices options ch user-data]
   (let [err (CL/clBuildProgram program (count devices)
                                (if devices
                                  (into-array cl_device_id devices)
                                  nil)
                                options
                                (if ch (->BuildCallback ch) nil)
                                user-data)]
     (if (= CL/CL_SUCCESS err)
       program
       (throw (error err (map (partial build-info program)
                              (if devices
                                devices
                                (program-devices program))))))))
  ([program devices options ch]
   (build-program! program devices options ch nil))
  ([program options ch]
   (build-program! program nil options ch nil))
  ([program ch]
   (build-program! program nil nil ch nil))
  ([program]
   (build-program! program nil nil nil nil)))

;; ============== Kernel =========================================

(defn num-kernels
  "Returns the number of kernels in `program` (`cl_program`).
  "
  ^long [program]
  (let [res (int-array 1)
        err (CL/clCreateKernelsInProgram program 0 nil res)]
    (with-check err (aget res 0))))

(defn kernel
  "Creates `cl_kernel` objects for the kernel function specified by `name`,
  or, if the name is not specified, all kernel functions in a `program`.

  **Needs to be released after use.**

  In case of OpenCL errors during the program build, throws
  `Exceptioninfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateKernel.html,
  https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateKernelsInProgram.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateKernel-org.jocl.cl_program-java.lang.String-int:A-
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateKernelsInProgram-org.jocl.cl_program-int-org.jocl.cl_kernel:A-int:A-
  Examples:

      (kernel program \"dumb_kernel\") ; `cl_kernel` object
      (kernel program) ; all kernels in a vector
  "
  ([program name]
   (let [err (int-array 1)
         res (CL/clCreateKernel program name err)]
     (with-check-arr err res)))
  ([program]
   (let [nk (num-kernels program)
         res (make-array cl_kernel nk)
         err (CL/clCreateKernelsInProgram program nk res nil)]
     (vec (with-check err res)))))

(defn set-arg!
  "Sets the argument value for a specific positional argument of a kernel.
  Returns the changed `cl_kernel` object. `value` should implement [[Argument]]
  protocol.

  The arguement can be a [[Mem]] ([[CLBuffer]], [[CLImage]], Java primitive arrays),
  or a number.
  In the case of [[Mem]] objects, the memory object will be set as an argument.
  In the case the argument is a number, its long value will be used as a size
  of the local memory to be allocated on the device.

  In case of OpenCL errors during the program build, throws
  `Exceptioninfo`. if `value` is of the type that is not supported,
  throws `IllegalArgumentexception`.

  See [[kernel]], [[program]], [[Argument]], [[cl-buffer]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSetKernelArg.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clSetKernelArg-org.jocl.cl_kernel-int-long-org.jocl.Pointer-

  Examples:

      (set-arg! my-kernel 0 cl-buffer0)
      (set-arg! my-kernel 1 cl-buffer1)
      (set-arg! my-kernel 2 (int-array 8))
      (set-arg! my-kernel 3 42)
"
  [kernel n value]
  (set-arg value kernel n))

(defn set-args!
  "Sets all arguments of `kernel`, and returns the changed `cl_kernel` object.
  Equivalent to calling [[set-arg!]] for each argument.

  Examples:

      (set-args! my-kernel cl-buffer0)
      (set-args! my-kernel cl-buffer0 cl-buffer-1 (int-array 8) 42)
"
  ([kernel value]
   (set-arg! kernel 0 value))
  ([kernel value-0 value-1]
   (-> (set-arg! kernel 0 value-0)
       (set-arg! 1 value-1)))
  ([kernel value-0 value-1 value-2]
   (-> (set-arg! kernel 0 value-0)
       (set-arg! 1 value-1)
       (set-arg! 2 value-2)))
  ([kernel value-0 value-1 value-2 value-3 & values]
   (let [ker (-> (set-arg! kernel 0 value-0)
                 (set-arg! 1 value-1)
                 (set-arg! 2 value-2)
                 (set-arg! 3 value-3))]
     (loop [i 4 values values]
       (if-let [mem (first values)]
         (do (set-arg! ker i mem)
             (recur (inc i) (next values)))
         ker)))))

;; ============== Work Size ==================================

(defrecord WorkSize [^long workdim ^longs global ^longs local ^longs offset])

(defn work-size
  "Creates a [[WorkSize]] record, that sets global, local and offset
  parameters in enqueuing ND kernels. All arguments are sequences,
  holding as many arguments, as there are dimensions in the appropriate
  ND kernel. In OpenCL 2.0, it is usually 1, 2, or 3, depending on the device.

  See [[enq-nd!]]
  Examples:

      (work-size [102400 25600] [1024 128] [4 8])
      (work-size [1024 256] [16 16])
      (work-size [256])
"
  ([global local offset]
   (let [global-array (long-array global)
         local-array (long-array local)
         offset-array (long-array offset)
         dim (alength global-array)]
     (if (= dim (alength local-array) (alength offset-array))
       (->WorkSize dim global-array local-array offset-array)
       (throw (IllegalArgumentException.
               "All work-sizes must have the same work-dimension.")))))
  ([global local]
   (let [global-array (long-array global)
         local-array (long-array local)
         dim (alength global-array)]
     (if (= dim (alength local-array))
       (->WorkSize dim global-array local-array nil)
       (throw (IllegalArgumentException.
               "All work-sizes must have the same work-dimension.")))))
  ([sizes]
   (let [sizes-array (long-array sizes)]
     (->WorkSize (alength sizes-array) sizes-array sizes-array nil))))

;; ============== Command Queue ===============================

(defn command-queue*
  "Creates a host or device command queue on a specific device.

  It is important to take care which version of this method to use,
  depending on the OpenCL platform you have installed. If you run
  the wrong version, you may crash the virtual machine.

  * 3 - argument version supports **pre-2.0 OpenCL**;
  * 4 - argument version supports **OpenCL 2.0**;

  Arguments are:

  * `context` - the `cl_context` for the queue;
  * `device` - the `cl_device_id` for the queue;
  * `size` - the size of the (on device) queue;
  * `properties` - long bitmask containing properties, defined by the OpenCL
  standard are available as constants in the org.jocl.CL class.

  This is a low-level version of [[command-queue]].

  If called with invalid context or device, throws `ExceptionInfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateCommandQueueWithProperties.html,
  https://www.khronos.org/registry/cl/sdk/1.2/docs/man/xhtml/clCreateCommandQueue.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueueWithProperties-org.jocl.cl_context-org.jocl.cl_device_id-org.jocl.cl_queue_properties-int:A-
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueue-org.jocl.cl_context-org.jocl.cl_device_id-long-int:A-

  Examples:
      ;; OpenCL 2.0
      (command-queue* ctx dev 524288  (bit-or CL/CL_QUEUE_PROFILING_ENABLED
                                              CL/CL_QUEUE_ON_DEVICE))
      ;; OpenCL 1.0, 1.1, 1.2
      (command-queue* ctx dev CL/CL_QUEUE_PROFILING_ENABLED)
  "
  ([context device ^long properties]
   (let [err (int-array 1)
         res (CL/clCreateCommandQueue context device properties err)]
     (with-check-arr err res)))
  ([context device ^long size ^long properties]
   (let [err (int-array 1)
         props (let [clqp (cl_queue_properties.)]
                 (when (< 0 properties)
                   (.addProperty clqp CL/CL_QUEUE_PROPERTIES properties))
                 (when (< 0 size)
                   (.addProperty clqp CL/CL_QUEUE_SIZE size))
                 clqp)
         res (CL/clCreateCommandQueueWithProperties context device props err)]
     (with-check-arr err res))))

(defn command-queue
  "Creates a host or device command queue on a specific device.

  **This method supports only OpenCL 2.0 and higher.** If you have an older
  platform, calling this method may crash the Java virtual machine.
  For older versions, use [[command-queue*]].

  Arguments are:

  * `context` - the `cl_context` for the queue;
  * `device` - the `cl_device_id` for the queue;
  * `x` - if integer, the size of the (on device) queue, otherwise treated
  as property;
  * `properties` - additional optional keyword properties: `:profiling`,
  `:queue-on-device`, `:out-of-order-exec-mode`, and`queue-on-device-default`;

  **Needs to be released after use.**

  See also [[command-queue*]].

  If called with invalid context or device, throws `ExceptionInfo`.
  If called with any invalid property, throws NullPointerexception.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateCommandQueueWithProperties.html,
  https://www.khronos.org/registry/cl/sdk/1.2/docs/man/xhtml/clCreateCommandQueue.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueueWithProperties-org.jocl.cl_context-org.jocl.cl_device_id-org.jocl.cl_queue_properties-int:A-
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueue-org.jocl.cl_context-org.jocl.cl_device_id-long-int:A-

  Examples:

       (command-queue ctx)
       (command-queue ctx dev)
       (command-queue ctx dev :profiling :queue-on-device :out-of-order-execution-mode)
       (command-queue ctx dev 524288 :queue-on-device)

  "
  ([context device x & properties]
   (if (integer? x)
     (command-queue* context device x
                     (mask cl-command-queue-properties properties))
     (command-queue* context device 0
                     (mask cl-command-queue-properties x properties))))
  ([context device]
   (command-queue* context device 0 0))
  ([device]
   (command-queue* *context* device 0 0)))

(defn enq-nd!
  ([queue kernel ^WorkSize work-size ^objects wait-events event]
   (with-check
     (CL/clEnqueueNDRangeKernel queue kernel (.workdim work-size) (.offset work-size)
                                (.global work-size) (.local work-size)
                                (if wait-events (alength wait-events) 0)
                                wait-events event)
     queue))
  ([queue kernel work-size]
   (enq-nd! queue kernel work-size nil nil))
  ([kernel work-size]
   (enq-nd! *command-queue* kernel work-size nil nil)))

(defn enq-read!
  ([queue cl host blocking offset ^objects wait-events event]
   (with-check
     (CL/clEnqueueReadBuffer queue (cl-mem cl) blocking offset
                             (size cl) (ptr host)
 (if wait-events (alength wait-events) 0)
                             wait-events event)
     queue))
  ([queue cl host wait-events event]
   (enq-read! queue cl host false 0 wait-events event))
  ([queue cl host event]
   (enq-read! queue cl host false 0 nil event))
  ([queue cl host]
   (enq-read! queue cl host true 0 nil nil))
  ([cl host]
   (enq-read! *command-queue* cl host true 0 nil nil)))

(defn enq-write!
  ([queue cl host blocking offset ^objects wait-events event]
   (with-check
     (CL/clEnqueueWriteBuffer queue (cl-mem cl) blocking offset
                              (size cl) (ptr host)
                              (if wait-events (alength wait-events) 0)
                              wait-events event)
     queue))
  ([queue cl host wait-events event]
   (enq-write! queue cl host false 0 wait-events event))
  ([queue cl host event]
   (enq-write! queue cl host false 0 nil event))
  ([queue cl host]
   (enq-write! queue cl host true 0 nil nil))
  ([cl host]
   (enq-write! *command-queue* cl host true 0 nil nil)))

(defn enq-map-buffer* [queue cl blocking offset flags
                           ^longs wait-events event]
  (let [err (int-array 1)
        res (CL/clEnqueueMapBuffer queue (cl-mem cl) blocking flags offset
                                   (size cl)
                                   (if wait-events (alength wait-events) 0)
                                   wait-events event err)]
    (with-check-arr err (.order res (ByteOrder/nativeOrder)))))

(defn enq-map-buffer!
  ([queue cl blocking offset flags ^longs wait-events event]
   (enq-map-buffer* queue cl blocking offset (mask cl-map-flags flags)
                       wait-events event))
  ([queue cl flag wait-events queue]
   (enq-map-buffer* queue cl false 0 (cl-map-flags flag) wait-events event))
  ([queue cl flag event]
   (enq-map-buffer* queue cl false 0 (cl-map-flags flag) nil event))
  ([queue cl flag]
   (enq-map-buffer* queue cl true 0 (cl-map-flags flag) nil nil))
  ([cl flag]
   (enq-map-buffer* *command-queue* cl true 0 (cl-map-flags flag) nil nil)))

;; TODO with-mapping

(defn enq-unmap!
  ([queue cl host ^longs wait-events event]
   (let [err (CL/clEnqueueUnmapMemObject queue (cl-mem cl) host
                                         (if wait-events (alength wait-events) 0)
                                         wait-events event)]
     (with-check err queue)))
  ([queue cl host cqueue]
   (enq-unmap! queue cl host nil event))
  ([queue cl host]
   (enq-unmap! queue cl host nil nil))
  ([cl host]
   (enq-unmap! *command-queue* cl host nil nil)))

(defn enq-marker!
  ([queue ev]
   (with-check (CL/clEnqueueMarker queue ev) queue))
  ([queue ^objects wait-events ev]
   (with-check
     (CL/clEnqueueMarkerWithWaitList queue (alength wait-events) wait-events ev)
     queue)))

(defn enq-wait! [queue ^objects wait-events]
  (with-check
    (CL/clEnqueueWaitForEvents queue (alength wait-events) wait-events)
    queue))

(defn enq-barrier!
  ([queue]
   (with-check (CL/clEnqueueBarrier queue) queue))
  ([queue ^objects wait-events ev]
   (with-check
     (CL/clEnqueueBarrierWithWaitList queue (alength wait-events) wait-events ev)
     queue)))

(defn finish! [queue]
  (with-check (CL/clFinish queue) queue))

(defmacro with-queue [queue & body]
  `(binding [*command-queue* ~queue]
     (try ~@body
          (finally (release *command-queue*)))))

(defmacro with-default [& body]
  `(with-platform (first (platforms))
     (let [dev# (first (devices))]
       (with-context (context [dev#])
         (with-queue (command-queue dev#)
           ~@body)))))
