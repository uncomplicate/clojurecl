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

  The documentation given here is only a quick reminder; it is necessary
  to grasp OpenCL and parallel computing concepts to be able to use the
  library. Also, each function's doc entry have a link to much more detailed
  documentation available in OpenCL and JOCL reference - be sure to
  browse one of these documents wherever you are not sure about
  some of many important details.

  ### Cheat Sheet

  * resource management: [[with-release]]
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
  [[enq-read!]], [[enq-write!]], [[enq-copy!]], [[enq-fill!]]
  [[enq-map-buffer!]], [[enq-map-buffer*]], [[enq-unmap!]],
  [[enq-svm-map!]], [[enq-map-buffer*]], [[enq-svm-unmap!]],
  [[enq-marker!]], [[enq-wait!]], [[enq-barrier!]],
  [[finish!]], [[flush!]] [[with-queue]]
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
  "Binds [[Releasable]] elements to symbols (like `let` do), evaluates
  `body`, and at the end releases the resources held by the bindings. The bindings
  can also be deeply sequential (see examples) - they will be released properly.

  Example:

      (with-release [devs (devices (first (platforms)))
                     dev (first devs)
                     ctx (context devs)
                     queue (command-queue ctx dev)]
        (info dev)
        (info queue))
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

(defprotocol Mem
  "An object that represents memory that participates in OpenCL operations.
  It can be on the device ([[CLMem]]), or on the host.  Built-in implementations:
  cl buffer, Java primitive arrays and `ByteBuffer`s."
  (ptr [this]
    "JOCL `Pointer` to this object.")
  (size [this]
    "Memory size of this cl or host object in bytes."))

(defprotocol Contextual
  "An object that has some dependency on a `cl_context`."
  (cl-context [this]
    "Context that is related to this object."))

(defprotocol CLMem
  "A wrapper for `cl_mem` objects, that also holds a `Pointer` to the cl mem
  object, context that created it, and size in bytes. It is useful in many
  functions that need that (redundant in Java) data because of the C background
  of OpenCL functions."
  (cl-mem [this]
    "The raw JOCL `cl_mem` object.")
  (enq-copy* [this queue dst src-offset dst-offset cb wait-events ev]
    "A specific implementation for copying this `cl-mem` object to another cl mem.")
  (enq-fill* [this queue pattern offset multiplier wait-events ev]
    "A specific implementation for filling this `cl-mem` object."))

(defprotocol SVMMem
  "A wrapper for SVM Buffer objects, that also holds a context that created it,
  `Pointer`, size in bytes, and can create a `ByteBuffer`. It is useful in many
  functions that need that (redundant in Java) data because of the C background
  of OpenCL functions."
  (byte-buffer [this] [this offset size]
    "Creates a Java `ByteBuffer` for this SVM memory.")
  (enq-svm-copy [this]));;TODO

(defprotocol Argument
  "Object that can be argument in OpenCL kernels. Built-in implementations:
  [[CLBuffer]], java numbers, primitive arrays and `ByteBuffer`s."
  (set-arg [arg kernel n]
    "Specific implementation of setting the kernel arguments."))

(deftype CLBuffer [^cl_context ctx ^cl_mem cl ^Pointer cl* ^long s]
  Releaseable
  (release [_]
    (release cl))
  Mem
  (ptr [_]
    cl*)
  (size [_]
    s)
  CLMem
  (cl-mem [_]
    cl)
  (enq-copy* [this queue dst src-offset dst-offset size wait-events ev]
    (with-check
      (CL/clEnqueueCopyBuffer queue cl (cl-mem dst) src-offset dst-offset size
                              (if wait-events (alength ^objects wait-events) 0)
                              wait-events ev)
      queue))
  (enq-fill* [this queue pattern offset multiplier wait-events ev]
    (with-check
      (CL/clEnqueueFillBuffer queue cl (ptr pattern) (size pattern)
                              offset (* (long (size pattern)) (long multiplier))
                              (if wait-events (alength ^objects wait-events) 0)
                              wait-events ev)
      queue))
  Contextual
  (cl-context [_]
    ctx)
  Argument
  (set-arg [_ kernel n]
    (with-check (CL/clSetKernelArg kernel n Sizeof/cl_mem cl*) kernel)))

(defn cl-buffer?
  "Checks whether an object is a CL buffer."
  [x]
  (instance? CLBuffer x))

(deftype SVMBuffer [^cl_context ctx ^Pointer svm* ^long s]
  Releaseable
  (release [_]
    (CL/clSVMFree ctx svm*))
  Mem
  (ptr [_]
    svm*)
  (size [_]
    s)
  Contextual
  (cl-context [_]
    ctx)
  SVMMem
  (byte-buffer [this]
    (byte-buffer this 0 s))
  (byte-buffer [_ offset size]
    (.order (.getByteBuffer svm* offset size) (ByteOrder/nativeOrder)))
  Argument
  (set-arg [_ kernel n]
    (with-check (CL/clSetKernelArgSVMPointer kernel n svm*) kernel)))

(defn svm-buffer?
  "Checks whether an object is an SVM buffer."
  [x]
  (instance? SVMBuffer x))

(defn cl-buffer*
  "Creates a cl buffer object in `ctx`, given `size` in bytes and a bitfield
  `flags` describing memory allocation usage.

  Flags defined by the OpenCL standard are available as constants in the
  [org.jocl.CL](http://www.jocl.org/doc/org/jocl/CL.html) class.

  **Needs to be released after use.**

  This is a low-level alternative to [[cl-buffer]]
  If  `ctx` is nil or the buffer size is invalid, throws `ExceptionInfo`.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateBuffer-org.jocl.cl_context-long-long-org.jocl.Pointer-int:A-

  Example:

  (cl-buffer* ctx 24 CL/CL_MEM_READ_ONLY)
  "
  ([ctx ^long size ^long flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer ctx flags size nil err)]
     (with-check-arr err (->CLBuffer ctx res (Pointer/to ^cl_mem res) size)))))

(defn cl-buffer
  "Creates a cl buffer object ([[CLBuffer]]) in `ctx`, given `size` in bytes
  and one or more memory allocation usage keyword flags: `:read-write`,
  `:read-only`, `:write-only`, `:use-host-ptr`, `:alloc-host-ptr`,
  `:copy-host-ptr`, `:host-write-only`, `:host-read-only`, `:host-no-access`.

  If called with two arguments, uses the default `*context*`
  (see [[with-context]]).

  **Needs to be released after use.**

  If  `ctx` is nil or the buffer size is invalid, throws `ExceptionInfo`.
  If some of the flags is invalid, throws `IllegalArgumentexception`.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateBuffer-org.jocl.cl_context-long-long-org.jocl.Pointer-int:A-

  Examples:

  (cl-buffer 32 :read-only)
  (cl-buffer ctx 24 :write-only)
  "
  ([ctx size flag & flags]
   (cl-buffer* ctx size (mask cl-mem-flags flag flags)))
  ([^long size flag]
   (cl-buffer* *context* size (cl-mem-flags flag)))
  ([^long size]
   (cl-buffer* *context* size 0)))

(defn cl-sub-buffer*
  "Creates a cl buffer object ([[CLBuffer]]) that shares data with an existing
  buffer object.

  * `buffer` has to be a valid [[BLBuffer]] buffer object.
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
  (cl-sub-buffer* cl-buff CL/CL_MEM_READ_WRITE CL/CL_BUFFER_CREATE_TYPE_REGION region)
  (cl-sub-buffer* cl-buff CL/CL_MEM_READ_ONLY region)
  "
  ([buffer ^long flags ^long create-type ^cl_buffer_region region]
   (let [err (int-array 1)
         res (CL/clCreateSubBuffer ^cl_mem (cl-mem buffer) flags
                                   (int create-type)
                                   region err)]
     (with-check-arr err (->CLBuffer (cl-context buffer) res
                                     (Pointer/to ^cl_mem res) (.size region)))))
  ([buffer ^long flags region]
   (cl-sub-buffer* buffer flags CL/CL_BUFFER_CREATE_TYPE_REGION region)))

(defn cl-sub-buffer
  "Creates a cl buffer object ([[CLBuffer]]) that shares data with an existing
  buffer object.

  * `buffer` has to be a valid [[CLBuffer]] object.
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
   (cl-sub-buffer* buffer (mask cl-mem-flags flag flags)
                   (cl_buffer_region. origin size)))
  ([buffer origin size]
   (cl-sub-buffer* buffer 0 (cl_buffer_region. origin size))))

(defn svm-buffer*
  "Creates a svm buffer object in `ctx`, given `size` in bytes, bitfield
  `flags` describing memory allocation usage, and alignment size.

  Flags defined by the OpenCL standard are available as constants in the
  [org.jocl.CL](http://www.jocl.org/doc/org/jocl/CL.html) class.

  **Needs to be released after use.**

  This is a low-level alternative to [[svm-buffer!]]
  If  `ctx` is nil or the buffer size is invalid, throws `IllegalArgumentException`.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSVMAlloc.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clSVMAlloc-org.jocl.cl_context-long-long-int-

  Example:

      (svm-buffer* ctx 24 (bit-or CL/CL_MEM_SVM_FINE_GRAIN_BUFFER CL/CL_MEM_SVM_ATOMICS) 0)
  "
  [ctx ^long size ^long flags ^long alignment]
  (if (and ctx (< 0 size))
    (let [err (int-array 1)
          res (CL/clSVMAlloc ctx flags size alignment)]
      (with-check-arr err (->SVMBuffer ctx res size)))
    (throw (IllegalArgumentException.
            "To create a svm buffer, you must provide a context and a positive size."))))

(defn svm-buffer
  "Creates a svm buffer object ([[SVMBuffer]]) in `ctx`, given `size` and `alignment`
  in bytes and one or more memory allocation usage keyword flags: `:read-write`,
  `:read-only`, `:write-only`, :fine-grain-buffer, and/or :atomics

  If called with two arguments, uses the default alignment (platform dependent)
  and default `*context*` (see [[with-context]]). If called with one argument,
  use the default context, and alignment,and :read-write flag.

  **Needs to be released after use.** If you rely on the [[release]] method,
  be sure that all enqueued processing that uses this buffer finishes prior
  to that (watch out for non-blocking enqueues!).

  If  `ctx` is nil or the buffer size is invalid, throws `IllegalArgumentException`.
  If some of the flags is invalid, throws `NullPointerException`.

  See http://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clSVMAlloc.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clSVMAlloc-org.jocl.cl_context-long-long-int-

  Examples:

      (svm-buffer 32 :read-only)
      (svm-buffer ctx 24 0 :fine-grain-buffer :atomics)
  "
  ([ctx size alignment & flags]
   (svm-buffer* ctx size (mask cl-svm-mem-flags flags) alignment))
  ([^long size flag]
   (svm-buffer* *context* size (cl-svm-mem-flags flag) 0))
  ([^long size]
   (svm-buffer* *context* size 0 0)))

(extend-type Long
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n this nil)
      kernel)))

(extend-type Integer
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n this nil)
      kernel)))

(extend-type (Class/forName "[F")
  Mem
  (ptr [this]
    (Pointer/to ^floats this))
  (size [this]
    (* Float/BYTES (alength ^floats this)))
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
  (size [this]
    (* Double/BYTES (alength ^doubles this)))
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
  (size [this]
    (* Integer/BYTES (alength ^ints this)))
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
  (size [this]
    (* Long/BYTES (alength ^longs this)))
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
  (size [this]
    (alength ^bytes this))
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
  (size [this]
    (* Short/BYTES (alength ^shorts this)))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (* Short/BYTES (alength ^shorts this))
                                   (Pointer/to ^shorts this))
      kernel)))

(extend-type (Class/forName "[C")
  Mem
  (ptr [this]
    (Pointer/to ^chars this))
  (size [this]
    (* Character/BYTES (alength ^chars this)))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (* Character/BYTES (alength ^chars this))
                                   (Pointer/to ^chars this))
      kernel)))

(extend-type ByteBuffer
  Mem
  (ptr [this]
    (Pointer/toBuffer this))
  (size [this]
    (.capacity ^ByteBuffer this)))

;; ============== Events ==========================================

(defn event
  "creates new `cl_event`.

  see http://www.jocl.org/doc/org/jocl/cl_event.html.
  "
  []
  (cl_event.))

(defn host-event
  "Creates new `cl_event` on the host (in OpenCL terminology,
  known as \"user\" event.

  If called without `ctx` argument, uses [[*context*]].

  If `ctx` is `nil`, throws ExceptionInfo

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateUserEvent.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateUserEvent-org.jocl.cl_context-int:A-
  "
  ([]
   (host-event *context*))
  ([ctx]
   (let [err (int-array 1)
         res (CL/clCreateUserEvent ctx err)]
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
  ([ctx source]
   (let [err (int-array 1)
         n (count source)
         res (CL/clCreateProgramWithSource
              ctx n (into-array String source) nil err)]
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
  "Sets all provided arguments of `kernel`, starting from optional index `x`,
  and returns the changed `cl_kernel` object.
  Equivalent to calling [[set-arg!]] for each provided argument.

  Examples:

      (set-args! my-kernel cl-buffer-0)
      (set-args! my-kernel cl-buffer-0 cl-buffer-1 (int-array 8) 42)
      (set-args! my-kernel 2 cl-buffer-2 cl-buffer-3 (int-array 8) 42)
  "
  ([kernel x & values]
   (if (integer? x)
     (loop [i (long x) values values]
       (if-let [val (first values)]
         (do (set-arg! kernel i val)
             (recur (inc i) (next values)))
         kernel))
     (apply set-args! kernel 0 (cons x values)))))

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
      (work-size) ; same as (work-size [1])
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
  ([global]
   (let [global-array (long-array global)]
     (->WorkSize (alength global-array) global-array nil nil)))
  ([]
   (let [global-array (doto (long-array 0) (aset 0 1))]
     (->WorkSize 1 global-array nil nil))))

(defn work-size-1d
  "Creates a 1-dimensional [[WorkSize]] from the long numbers it receives.
  See also [[work-size]].

  Examples:
  (work-size-1d 1024)
  (work-size-1d 1024 256)
  (work-size-1d 1024 256 1)"
  ([^long global ^long local ^long offset]
   (->WorkSize 1
               (doto (long-array 1) (aset 0 global))
               (doto (long-array 1) (aset 0 local))
               (doto (long-array 1) (aset 0 offset))))
  ([^long global ^long local]
   (->WorkSize 1
               (doto (long-array 1) (aset 0 global))
               (doto (long-array 1) (aset 0 local))
               nil))
  ([^long global]
   (->WorkSize 1 (doto (long-array 1) (aset 0 global)) nil nil))
  ([]
   (->WorkSize 1 (doto (long-array 1) (aset 0 1)) nil nil)))

(defn work-size-2d
  "Creates a 2-dimensional [[WorkSize]] from the long numbers it receives.
  See also [[work-size]].

  Examples:
  (work-size-2d 1024 2048)
  (work-size-2d 1024 2048 16 16)
  (work-size-2d 1024 2048 16 16 8 0 2)
  "
  ([global0 global1 local0 local1 offset0 offset1]
   (->WorkSize 2
               (doto (long-array 2) (aset 0 (long global0)) (aset 1 (long global1)))
               (doto (long-array 2) (aset 0 (long local0)) (aset 1 (long local1)))
               (doto (long-array 2) (aset 0 (long offset0)) (aset 1 (long offset1)))))
  ([^long global0 ^long global1 ^long local0 ^long local1]
   (->WorkSize 2
               (doto (long-array 2) (aset 0 global0) (aset 1 global1))
               (doto (long-array 2) (aset 0 local0) (aset 1 local1))
               nil))
  ([^long global0 ^long global1]
   (->WorkSize 2 (doto (long-array 2) (aset 0 global0) (aset 1 global1)) nil nil))
  ([]
   (->WorkSize 2 (doto (long-array 2) (aset 0 1) (aset 1 1)) nil nil)))

(defn work-size-3d
  "Creates a 3-dimensional [[WorkSize]] from the long numbers it receives.
  See also [[work-size]].

  Examples:
  (work-size-3d 1024 2048 512)
  (work-size-3d 1024 2048 256 16 16)
  (work-size-3d 1024 2048 128 16 4 4 16 0 2 512)
  "
  ([global0 global1 global2 local0 local1 local2 offset0 offset1 offset2]
   (->WorkSize 3
               (doto (long-array 3) (aset 0 (long global0))
                     (aset 1 (long global1)) (aset 2 (long global2)))
               (doto (long-array 3) (aset 0 (long local0))
                     (aset 1 (long local1)) (aset 2 (long local2)))
               (doto (long-array 3) (aset 0 (long offset0))
                     (aset 1 (long offset1)) (aset 2 (long offset2)))))
  ([global0 global1 global2 local0 local1 local2]
   (->WorkSize 3
               (doto (long-array 3) (aset 0 (long global0))
                     (aset 1 (long global1)) (aset 2 (long global2)))
               (doto (long-array 3) (aset 0 (long local0))
                     (aset 1 (long local1)) (aset 2 (long local2)))
               nil))
  ([^long global0 ^long global1 ^long global2]
   (->WorkSize 3
               (doto (long-array 3) (aset 0 global0)
                     (aset 1 global1) (aset 2 global2))
               nil nil))
  ([]
   (->WorkSize 3 (doto (long-array 3) (aset 0 1) (aset 1 1) (aset 2 1)) nil nil)))

;; ============== Command Queue ===============================

(defn command-queue*
  "Creates a host or device command queue on a specific device.

  ** If you need to support OpenCL 1.2 platforms, you MUST use the alternative
  [command-queue-1*] function or or risk JVM crash. What is important is the
  version of the platform, not the devices. This function is for platforms
  (regardless of the devices) supporting OpenCL 2.0 and higher. **

  Arguments are:

  * `ctx` - the `cl_context` for the queue;
  * `device` - the `cl_device_id` for the queue;
  * `size` - the size of the (on device) queue;
  * `properties` - long bitmask containing properties, defined by the OpenCL
  standard are available as constants in the org.jocl.CL class.

  This is a low-level version of [[command-queue]].

  If called with invalid context or device, throws `ExceptionInfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateCommandQueueWithProperties.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueueWithProperties-org.jocl.cl_context-org.jocl.cl_device_id-org.jocl.cl_queue_properties-int:A-

  Examples:
      (command-queue* ctx dev 524288  (bit-or CL/CL_QUEUE_PROFILING_ENABLED
                                              CL/CL_QUEUE_ON_DEVICE))
      (command-queue* ctx dev CL/CL_QUEUE_PROFILING_ENABLED)
  "
  ([ctx device ^long properties]
   (command-queue* ctx device 0 properties))
  ([ctx device ^long size ^long properties]
   (let [err (int-array 1)
         props (let [clqp (cl_queue_properties.)]
                 (when (< 0 properties)
                   (.addProperty clqp CL/CL_QUEUE_PROPERTIES properties))
                 (when (< 0 size)
                   (.addProperty clqp CL/CL_QUEUE_SIZE size))
                 clqp)
         res (CL/clCreateCommandQueueWithProperties ctx device props err)]
     (with-check-arr err res))))

(defn command-queue
  "Creates a host or device command queue on a specific device.

  ** If you need to support OpenCL 1.2 platforms, you MUST use the alternative
  [command-queue-1] function or or risk JVM crash. What is important is the
  version of the platform, not the devices. This function is for platforms
  (regardless of the devices) supporting OpenCL 2.0 and higher. **

  Arguments are:

  * `ctx` - the `cl_context` for the queue;
  * `device` - the `cl_device_id` for the queue;
  * `x` - if integer, the size of the (on device) queue, otherwise treated
  as property;
  * `properties` - additional optional keyword properties: `:profiling`,
  `:queue-on-device`, `:out-of-order-exec-mode`, and `queue-on-device-default`;

  **Needs to be released after use.**

  See also [[command-queue*]].

  If called with invalid context or device, throws `ExceptionInfo`.
  If called with any invalid property, throws NullPointerexception.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clCreateCommandQueueWithProperties.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueueWithProperties-org.jocl.cl_context-org.jocl.cl_device_id-org.jocl.cl_queue_properties-int:A-

  Examples:

       (command-queue ctx)
       (command-queue ctx dev)
       (command-queue ctx dev :profiling :queue-on-device :out-of-order-execution-mode)
       (command-queue ctx dev 524288 :queue-on-device)
  "
  ([ctx device x & properties]
   (if (integer? x)
     (command-queue* ctx device x
                     (mask cl-command-queue-properties properties))
     (command-queue* ctx device 0
                     (mask cl-command-queue-properties x properties))))
  ([ctx device]
   (command-queue* ctx device 0 0))
  ([device]
   (command-queue* *context* device 0 0)))

(defn enq-nd!
  "Enqueues a command to asynchronously execute a kernel on a device.
  Returns the queue.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that executes the kernel.
  If omitted, [[*command-queue*]] will be used.
  * `kernel`: the `cl_kernel` that is going to be executed.
  * `work-size`: [[WorkSize]] containing the settings of execution
  (global work size, local work size, global work offset).
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this command can be executed.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this command.

  If an OpenCL error occurs during the call, throws `ExceptionInfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueNDRangeKernel.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueNDRangeKernel-org.jocl.cl_command_queue-org.jocl.cl_kernel-int-long:A-long:A-long:A-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-nd! my-kernel (work-size [8]))
      (enq-nd! my-queue my-kernel (work-size [8]))
      (enq-nd! my-queue my-kernel (work-size [8] (events event1 event2) my-event))
  "
  ([queue kernel ^WorkSize work-size ^objects wait-events event]
   (with-check
     (CL/clEnqueueNDRangeKernel queue kernel (.workdim work-size) (.offset work-size)
                                (.global work-size) (.local work-size)
                                (if wait-events (alength wait-events) 0)
                                wait-events event)
     queue))
  ([queue kernel work-size event]
   (enq-nd! queue kernel work-size nil event))
  ([queue kernel work-size]
   (enq-nd! queue kernel work-size nil nil))
  ([kernel work-size]
   (enq-nd! *command-queue* kernel work-size nil nil)))

(defn enq-read!
  "Enqueues a command to read from a cl object to host memory.
  Returns the queue.

  * `queue` (optional): the `cl_command_queue` that reads the object.
  If omitted, [[*command-queue*]] will be used.
  * `cl`: the [[CLMem]] that is going to be read from.
  * `host`: [[Mem]] object on the host that the data is to be transferred to.
  Must be a direct buffer is the reading is asynchronous.
  * `blocking`: boolean indicator of synchronization.
  * `offset`: the offset in bytes in the buffer object to read from.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  If event is specified, the operation is asynchronous, otherwise it blocks the
  current thread until the data transfer completes, unless explicitly specifiend
  with `blocking`. See also [[register]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueReadBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueReadBuffer-org.jocl.cl_command_queue-org.jocl.cl_mem-boolean-long-long-org.jocl.Pointer-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (let [host-data (direct-buffer 32)
            ev (event)
            notifications (chan)
            follow (register notifications)]
        (enq-read! my-queue cl-data host-data ev) ;; asynchronous
        (follow ev)
        (<!! notifications))

      (enq-read! my-queu cl-data host-data) ;; blocking
  "
  ([queue cl host blocking offset ^objects wait-events event]
   (with-check
     (CL/clEnqueueReadBuffer queue (cl-mem cl) blocking offset
                             (min (long (size cl)) (long (size host))) (ptr host)
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
  "Enqueues a command to write to a cl object from host memory.
  Returns the queue.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that writes the object.
  If omitted, [[*command-queue*]] will be used.
  * `cl`: the [[CLMem]] that is going to be written to.
  * `host`: [[Mem]] object on the host that the data is to be transferred from.
  Must be a direct buffer is the writing is asynchronous.
  * `blocking`: boolean indicator of synchronization.
  * `offset`: the offset in bytes in the buffer object to write to.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  If event is specified, the operation is asynchronous, otherwise it blocks the
  current thread until the data transfer completes, unless explicitly specifiend
  with `blocking`. See also [[register]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueWriteBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueWriteBuffer-org.jocl.cl_command_queue-org.jocl.cl_mem-boolean-long-long-org.jocl.Pointer-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (let [host-data (direct-buffer 32)
            ev (event)
            notifications (chan)
            follow (register notifications)]
        (enq-write! my-queue cl-data host-data ev) ;; asynchronous
        (follow ev)
        (<!! notifications))

      (enq-write! my-queu cl-data host-data) ;; blocking
  "
  ([queue cl host blocking offset ^objects wait-events event]
   (with-check
     (CL/clEnqueueWriteBuffer queue (cl-mem cl) blocking offset
                              (min (long (size cl)) (long (size host))) (ptr host)
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

(defn enq-copy!
  "Enqueues a command to copy from one [[CLMem]] memory object to another.

  In case of OpenCL errors, throws an `ExceptionInfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueCopyBuffer.html
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueCopyBuffer-org.jocl.cl_command_queue-org.jocl.cl_mem-org.jocl.cl_mem-long-long-long-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-copy! my-queue cl-src cl-dst 4 8 32 (events) ev)
  "
  ([queue cl-src cl-dst src-offset dst-offset size wait-events ev]
   (enq-copy* cl-src queue cl-dst src-offset dst-offset size wait-events ev))
  ([queue cl-src cl-dst size wait-events ev]
   (enq-copy* cl-src queue cl-dst 0 0 size wait-events ev))
  ([queue cl-src cl-dst wait-events ev]
   (enq-copy* cl-src queue cl-dst 0 0
              (min (long (size cl-src)) (long (size cl-dst)))
              wait-events ev))
  ([queue cl-src cl-dst size]
   (enq-copy* cl-src queue cl-dst 0 0 size nil nil))
  ([queue cl-src cl-dst]
   (enq-copy! queue cl-src cl-dst nil nil))
  ([cl-src cl-dst]
   (enq-copy! *command-queue* cl-src cl-dst nil nil)))

(defn enq-fill!
  "Enqueues a command to fill a buffer object with a [[Mem]] pattern.

  In case of OpenCL errors, throws an `ExceptionInfo`.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueFillBuffer.html
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueFillBuffer-org.jocl.cl_command_queue-org.jocl.cl_mem-org.jocl.Pointer-long-long-long-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-fill! my-queue cl-buf (float-array [1 2 3 4]) 2 (events) ev)
  "
  ([queue this pattern offset multiplier wait-events ev]
   (enq-fill* this queue pattern offset multiplier wait-events ev))
  ([queue this pattern wait-events ev]
   (enq-fill* this queue pattern
              0 (quot (long (size this)) (long (size pattern)))
              wait-events ev))
  ([queue this pattern]
   (enq-fill! queue this pattern nil nil))
  ([this pattern]
   (enq-fill! *command-queue* this pattern nil nil)))

(defn enq-map-buffer*
  "Enqueues a command to map a region of the cl buffer into the host
  address space. Returns the mapped `java.nio.ByteBuffer`. The result
  must be unmapped by calling [[enq-unmap!]] for the effects of working
  with the mapping byte buffer to be transfered back to the device memory.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that maps the object.
  If omitted, [[*command-queue*]] will be used.
  * `cl`: the [[CLMem]] that is going to be mapped to.
  * `blocking`: whether the operation is blocking (CL/CL_TRUE) or non-blocking
  (CL/CL_FALSE).
  *  `offset`: integer value of the memory offset in bytes.
  * `req-size`: integer value of the requested size in bytes (if larger than
    the available data, it will be shrinked.).
  * `flags`: a bitfield that indicates whether the memory is mapped for reading
  (`CL/CL_MAP_READ`), writing (`CL/CL_MAP_WRITE`) or both
  `(bit-or CL/CL_MAP_READ CL/CL_MAP_WRITE)`.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  This is a low-level version of [[enq-map-buffer!]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueMapBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueMapBuffer-org.jocl.cl_command_queue-org.jocl.cl_mem-boolean-long-long-long-int-org.jocl.cl_event:A-org.jocl.cl_event-int:A-

  Examples:

      (enq-map-buffer* queue cl-data true 0 CL/CL_WRITE (events ev-nd) ev-map)
  "
  [queue cl blocking offset req-size flags ^objects wait-events event]
  (let [err (int-array 1)
        res (CL/clEnqueueMapBuffer queue (cl-mem cl) blocking flags offset
                                   (min (long req-size) (- (long (size cl)) (long offset)))
                                   (if wait-events (alength wait-events) 0)
                                   wait-events event err)]
    (with-check-arr err (.order res (ByteOrder/nativeOrder)))))

(defn enq-map-buffer!
  "Enqueues a command to map a region of the cl buffer into the host
  address space. Returns the mapped `java.nio.ByteBuffer`. The result
  must be unmapped by calling [[enq-unmap!]] for the effects of working
  with the mapping byte buffer to be transfered back to the device memory.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that maps the object.
  If omitted, [[*command-queue*]] will be used.
  * `cl`: the [[CLMem]] that is going to be mapped to.
  * `blocking`: whether the operation is blocking or non-blocking.
  * `offset`: integer value of the memory offset in bytes.
  * `req-size`: integer value of the requested size in bytes (if larger than
     the available data, it will be shrinked).
  * flags: one keyword or a sequence of keywords that indicates memory mapping
  settings: `:read`, `:write`, and/or `:write-invalidate-settings`.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  If event is specified, the operation is asynchronous, otherwise it blocks the
  current thread until the data transfer completes. See also [[register]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueMapBuffer.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueMapBuffer-org.jocl.cl_command_queue-org.jocl.cl_mem-boolean-long-long-long-int-org.jocl.cl_event:A-org.jocl.cl_event-int:A-

  Examples:

      (enq-map-buffer! queue cl-data :write (events ev-nd) ev-map)
      (enq-map-buffer! queue cl-data [:write :read])
      (enq-map-buffer! cl-data :write)
  "
  ([queue cl blocking offset req-size flags wait-events event]
   (enq-map-buffer* queue cl blocking offset req-size
                    (if (keyword? flags)
                      (cl-map-flags flags)
                      (mask cl-map-flags flags))
                    wait-events event))
  ([queue cl offset req-size flags wait-events event]
   (enq-map-buffer! queue cl false offset req-size flags wait-events event))
  ([queue cl flags wait-events event]
   (enq-map-buffer! queue cl 0 (size cl) flags wait-events event))
  ([queue cl flags event]
   (enq-map-buffer! queue cl flags nil event))
  ([queue cl flags]
   (enq-map-buffer* queue cl true 0 (size cl)
                    (if (keyword? flags)
                      (cl-map-flags flags)
                      (mask cl-map-flags flags))
                    nil nil))
  ([cl flags]
   (enq-map-buffer! *command-queue* cl flags)))

(defn enq-unmap!
  "Enqueues a command to unmap a previously mapped memory region.
  Returns the queue.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that unmaps the object.
  If omitted, [[*command-queue*]] will be used.
  *  `cl`: the [[CLMem]] that is going to be unmapped.
  *  `host`: the host byte buffer that is going to be unmapped.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  If event is specified, the operation is asynchronous, otherwise it blocks the
  current thread until the data transfer completes. See also [[register]].

  See also [[enq-map-buffer!]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueUnmapMemObject,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueUnmapMemObject-org.jocl.cl_command_queue-org.jocl.cl_mem-java.nio.ByteBuffer-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-unmap! queue cl-data byte-buff (events ev-nd) ev-map)
      (enq-unmap! queue cl-data byte-buff ev-map)
      (enq-unmap! queue cl-data byte-buff)
      (enq-unmap! cl-data byte-buff)
  "
  ([queue cl host ^objects wait-events event]
   (let [err (CL/clEnqueueUnmapMemObject queue (cl-mem cl) host
                                         (if wait-events (alength wait-events) 0)
                                         wait-events event)]
     (with-check err queue)))
  ([queue cl host event]
   (enq-unmap! queue cl host nil event))
  ([queue cl host]
   (enq-unmap! queue cl host nil nil))
  ([cl host]
   (enq-unmap! *command-queue* cl host nil nil)))

(defn enq-svm-map*
  "Enqueues a command that will allow the host to update a region of a SVM buffer.
. Returns the mapped `java.nio.ByteBuffer` (which is the same byte buffer that is
  already accessible through `(byte-buffer svm)`). Together with [[enq-svm-unmap!]],
  works as a synchronization point.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that maps the object.
  If omitted, [[*command-queue*]] will be used.
  * `svm`: the [[SVMMem]] that is going to be mapped to.
  * `blocking`: whether the operation is blocking (CL/CL_TRUE) or non-blocking
  (CL/CL_FALSE).
  * `flags`: a bitfield that indicates whether the memory is mapped for reading
  (`CL/CL_MAP_READ`), writing (`CL/CL_MAP_WRITE`), both
  `(bit-or CL/CL_MAP_READ CL/CL_MAP_WRITE)` or `CL_MAP_WRITE_INVALIDATE_REGION`.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  This is a low-level version of [[enq-svm-map!]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueSVMMap.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueSVMMap-org.jocl.cl_command_queue-boolean-long-org.jocl.Pointer-long-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-svm-map* queue svm-data false 0 CL/CL_WRITE (events ev-nd) ev-map)
  "
  [queue svm blocking flags ^objects wait-events event]
  (with-check
    (CL/clEnqueueSVMMap queue blocking flags (ptr svm) (size svm)
                        (if wait-events (alength wait-events) 0)
                        wait-events event)
    (byte-buffer svm)))

(defn enq-svm-map!
  "Enqueues a command that will allow the host to update a region of a SVM buffer.
. Returns the mapped `java.nio.ByteBuffer` (which is the same byte buffer that is
  already accessible through `(byte-buffer svm)`). Together with [[enq-svm-unmap!]],
  works as a synchronization point.

  Arguments:

  * `queue` (optional): the `cl_command_queue` that maps the object.
  If omitted, [[*command-queue*]] will be used.
  * `svm`: the [[SVMMem]] that is going to be mapped to.
  *  `offset` (optional): integer value of the memory offset in bytes.
  * `flags` (optional): a bitfield that indicates whether the memory is mapped for reading
  :read, :write, and/or :write-invalidate-region.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  If event is specified, the operation is asynchronous, otherwise it blocks the
  current thread until the data transfer completes. See also [[register]].

  See also [[enq-svm-map*]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueSVMMap.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueSVMMap-org.jocl.cl_command_queue-boolean-long-org.jocl.Pointer-long-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-svm-map queue svm-data 0 [:write :read] (events ev-nd) ev-map)
      (enq-svm-map queue svm-data [:write :read] (events ev-nd) ev-map)
      (enq-svm-map queue svm-data :write ev-map)
      (enq-svm-map queue svm-data :read)
      (enq-svm-map svm-data :write-invalidate-region)
  "
  ([queue svm flags wait-events event]
   (enq-svm-map* queue svm false
                 (if (keyword? flags)
                   (cl-map-flags flags)
                   (mask cl-map-flags flags))
                 wait-events event))
  ([queue svm flags event]
   (enq-svm-map! queue svm flags nil event))
  ([queue svm flags]
   (enq-svm-map* queue svm true
                    (if (keyword? flags)
                      (cl-map-flags flags)
                      (mask cl-map-flags flags))
                    nil nil))
  ([svm flags]
   (enq-svm-map! *command-queue* svm flags)))

(defn enq-svm-unmap!
  "Enqueues a command to indicate that the host has completed updating the region
  given by svm [[SVMMem]] and which was specified in a previous call to
  [[enq-svm-map!]].

  Arguments:

  * `queue` (optional): the `cl_command_queue` that unmaps the object.
  If omitted, [[*command-queue*]] will be used.
  *  `svm`: the [[SVMMem]] that is going to be unmapped.
  * `wait-events` (optional): [[events]] array specifying the events (if any)
  that need to complete before this operation.
  * `event` (optional): if specified, the `cl_event` object tied to
  the execution of this operation.

  If event is specified, the operation is asynchronous, otherwise it blocks the
  current thread until the data transfer completes. See also [[register]].

  See also [[enq-svm-map!]].

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueSVMUnmap,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueSVMUnmap-org.jocl.cl_command_queue-org.jocl.Pointer-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

      (enq-svm-unmap! queue svm-data byte-buff (events ev-nd) ev-map)
      (enq-svm-unmap! queue svm-data byte-buff ev-map)
      (enq-svm-unmap! queue svm-data byte-buff)
      (enq-svm-unmap! svm-data byte-buff)
"
  ([queue svm ^objects wait-events event]
   (let [err (CL/clEnqueueSVMUnmap queue (ptr svm)
                                   (if wait-events (alength wait-events) 0)
                                   wait-events event)]
     (with-check err queue)))
  ([queue svm event]
   (enq-svm-unmap! queue svm nil event))
  ([queue svm]
   (enq-svm-unmap! queue svm nil nil))
  ([svm]
   (enq-svm-unmap! *command-queue* svm nil nil)))

(defn enq-marker!
  "Enqueues a marker command which waits for either a list of events to complete,
  or all previously enqueued commands to complete. Returns the queue.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueMarkerWithWaitList,

  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueMarkerWithWaitList-org.jocl.cl_command_queue-int-org.jocl.cl_event:A-org.jocl.cl_event-
  Examples:

  (enq-marker! queue (events ev-nd) ev-map)
  (enq-marker! queue)
  (enq-marker! queue ev-map)
  "
  ([queue]
   (enq-marker! queue nil nil))
  ([queue ev]
   (enq-marker! queue nil ev))
  ([queue ^objects wait-events ev]
   (with-check
     (CL/clEnqueueMarkerWithWaitList queue
                                     (if wait-events(alength wait-events) 0)
                                     wait-events ev)
     queue)))

(defn enq-barrier!
  "A synchronization point that enqueues a barrier operation. Returns the queue.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clEnqueueBarrierWithWaitList,
  http://www.jocl.org/doc/org/jocl/CL.html#clEnqueueBarrierWithWaitList-org.jocl.cl_command_queue-int-org.jocl.cl_event:A-org.jocl.cl_event-

  Examples:

  (enq-barrier! queue (events ev-nd) ev-map)
  (enq-barrier! queue)
  (enq-barrier! queue ev-map)
  "
  ([queue]
   (enq-barrier! queue nil nil))
  ([queue ev]
   (enq-barrier! queue nil ev))
  ([queue ^objects wait-events ev]
   (with-check
     (CL/clEnqueueBarrierWithWaitList queue
                                      (if wait-events(alength wait-events) 0)
                                      wait-events ev)
     queue)))

(defn finish!
  "Blocks until all previously queued OpenCL commands in a command-queue
  are issued to the associated device and have completed. Returns the queue.
  If called with no arguments, works on the default [*command-queue*]

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clFinish.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clFinish-org.jocl.cl_command_queue-

  Example:

      (finish! my-queue)
"
  ([queue]
   (with-check (CL/clFinish queue) queue))
  ([]
   (finish! *command-queue*)))

(defn flush!
  "Issues all previously queued OpenCL commands in a command-queue to the device
  associated with the command-queue.

  See https://www.khronos.org/registry/cl/sdk/2.0/docs/man/xhtml/clFlush.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clFinish-org.jocl.cl_command_queue-

  Example:

      (flush! my-queue)
"
 [queue]
 (with-check (CL/clFlush queue) queue ))

(defmacro with-queue
  "Dynamically binds `queue` to the default queue [[*command-queue*]].
  and evaluates the body with that binding. Releases the queue
  in the `finally` block. Take care *not* to release that queue in
  some other place; JVM might crash.

  Example:

      (with-queue (command-queue dev)
        (enq-read cl-data data))
  "
  [queue & body]
  `(binding [*command-queue* ~queue]
     (try ~@body
          (finally (release *command-queue*)))))

(defmacro with-default
  "Dynamically binds [[*platform*]], [[*context*]] and [[*command-queue]]
  to the first of the available platforms, the context containing the first
  device of that platform, and the queue on the device in that context.
  Requires OpenCL 2.0 support in the platform."
  [& body]
  `(with-platform (first (platforms))
     (let [dev# (first (devices))]
       (with-context (context [dev#])
         (with-queue (command-queue dev#)
           ~@body)))))
