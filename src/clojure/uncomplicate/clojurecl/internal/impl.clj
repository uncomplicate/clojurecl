;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    uncomplicate.clojurecl.internal.impl
  (:require [uncomplicate.commons
             [core :refer [Releaseable release Info info]]
             [utils :refer [dragan-says-ex]]]
            [uncomplicate.clojurecl.internal
             [protocols :refer :all]
             [constants :refer :all]
             [utils :refer [with-check with-check-arr]]]
            [clojure.core.async :refer [go >!]])
  (:import [java.nio ByteBuffer ByteOrder]
           clojure.lang.IDeref
           [org.jocl CL NativePointerObject cl_device_id cl_mem
            cl_context cl_command_queue cl_mem cl_program cl_kernel cl_sampler
            cl_event cl_buffer_region cl_queue_properties cl_platform_id
            Sizeof Pointer CreateContextFunction EventCallbackFunction
            BuildProgramFunction JOCLAccessor]))

(extend-type nil
  Wrapper
  (extract [_]
    nil)
  Wrappable
  (wrap [this]
    nil))

;; =============== Release CL Resources ==================================

(defn native-pointer ^long [npo]
  (if npo (JOCLAccessor/getNativePointer npo) 0))

(extend-type NativePointerObject
  Releaseable
  (release [this]
    (dragan-says-ex "It is not allowed to use and release raw JOCL objects. Use safe wrappers."
                    {:this this})))

(defmacro ^:private deftype-wrapper [name release-method]
  (let [name-str (str name)]
    `(deftype ~name [ref#]
       Object
       (hashCode [this#]
         (hash (deref ref#)))
       (equals [this# other#]
         (= (deref ref#) (extract other#)))
       (toString [this#]
         (format "#%s[0x%s]" ~name-str (Long/toHexString (native-pointer (deref ref#)))))
       Wrapper
       (extract [this#]
         (deref ref#))
       Releaseable
       (release [this#]
         (locking ref#
           (when-let [d# (deref ref#)]
             (locking d#
               (with-check (~release-method d#) (vreset! ref# nil)))))
         true))))

(deftype-wrapper CLDevice CL/clReleaseDevice)
(deftype-wrapper CLContext CL/clReleaseContext)
(deftype-wrapper CLCommandQueue CL/clReleaseCommandQueue)
(deftype-wrapper CLEvent CL/clReleaseEvent)
(deftype-wrapper CLKernel CL/clReleaseKernel)
(deftype-wrapper CLProgram CL/clReleaseProgram)
(deftype-wrapper CLSampler CL/clReleaseSampler)

(extend-type cl_platform_id
  Releaseable
  (release [_]
    true)
  Wrappable
  (wrap [this]
    this)
  Wrapper
  (extract [this]
    this))

(extend-type cl_command_queue
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [queue]
    (->CLCommandQueue (volatile! queue))))

(extend-type cl_context
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [ctx]
    (->CLContext (volatile! ctx))))

(extend-type cl_device_id
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [dev]
    (->CLDevice (volatile! dev))))

(extend-type cl_event
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [event]
    (->CLEvent (volatile! event))))

(extend-type cl_kernel
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [kernel]
    (->CLKernel (volatile! kernel))))

(extend-type cl_program
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [program]
    (->CLProgram (volatile! program))))

(extend-type cl_sampler
  Info
  (info [this]
    (info (wrap this)))
  Wrappable
  (wrap [sampler]
    (->CLSampler (volatile! sampler))))

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
      (with-check err
        {:platform (info platform) :device-type device-type}
        (aget res 0)))))

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
        (with-check err
          {:platform (info platform) :device-type device-type}
          res))
      res)))

;; ========================= Context ===========================================

(defrecord CreateContextInfo [errinfo private-info data])

(deftype CreateContextCallback [ch]
  CreateContextFunction
  (function [this errinfo private-info cb data]
    (go (>! ch (->CreateContextInfo errinfo private-info data)))))

(defn context*
  "Creates `CLContext` for an array of `device`s, with optional
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
    (with-check-arr err
      {:devices (map info devices)}
      res)))

;; =========================== Memory  =========================================

(deftype CLBuffer [cl ^Pointer cl* ^long s]
  Object
  (hashCode [this]
    (hash @cl))
  (equals [this other]
    (= @cl (extract other)))
  (toString [this]
    (format "#CLBuffer[0x%s]" (Long/toHexString (native-pointer @cl))))
  Wrapper
  (extract [_]
    @cl)
  Releaseable
  (release [this]
    (locking cl
      (when-let [c @cl]
        (locking c
          (with-check (CL/clReleaseMemObject c)
            (do
              (vreset! cl nil)
              (vreset! cl* nil))))))
    true)
  Mem
  (ptr [_]
    @cl*)
  (size [_]
    s)
  CLMem
  (enq-copy* [this queue dst src-offset dst-offset size wait-events ev]
    (with-check
      (CL/clEnqueueCopyBuffer queue @cl (extract dst) src-offset dst-offset size
                              (if wait-events (alength ^objects wait-events) 0)
                              wait-events ev)
      queue))
  (enq-fill* [this queue pattern offset multiplier wait-events ev]
    (with-check
      (CL/clEnqueueFillBuffer queue @cl (ptr pattern) (size pattern)
                              offset (* ^long (size pattern) ^long multiplier)
                              (if wait-events (alength ^objects wait-events) 0)
                              wait-events ev)
      queue))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n Sizeof/cl_mem @cl*)
      {:kernel (info kernel) :n n :arg (info this)}
      kernel)))

(deftype SVMBuffer [ctx svm* ^long s]
  Object
  (hashCode [this]
    (hash @svm*))
  (equals [this other]
    (= @svm* (extract other)))
  (toString [this]
    (str @svm*))
  Wrapper
  (extract [_]
    @svm*)
  Releaseable
  (release [this]
    (locking svm*
      (when-let [ss @svm*]
        (locking ss
          (CL/clSVMFree ctx ss)
          (vreset! svm* nil))))
    true)
  Mem
  (ptr [_]
    @svm*)
  (size [_]
    s)
  SVMMem
  (byte-buffer [this]
    (byte-buffer this 0 s))
  (byte-buffer [_ offset size]
    (.order (.getByteBuffer ^Pointer @svm* offset size) (ByteOrder/nativeOrder)))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArgSVMPointer kernel n @svm*)
      {:kernel (info kernel) :n n :arg (info this)}
      kernel)))

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
  ([^cl_context ctx ^long size ^long flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer ctx flags size nil err)]
     (with-check-arr err
       {:ctx (info ctx) :size size}
       (->CLBuffer (volatile! res) (volatile! (Pointer/to ^cl_mem res)) size)))))

(defn cl-sub-buffer*
  "Creates a cl buffer object ([[CLBuffer]]) that shares data with an existing
  buffer object.

  * `buffer` has to be a valid `cl_mem` buffer object.
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
  ([^cl_mem buffer ^long flags ^long create-type ^cl_buffer_region region]
   (let [err (int-array 1)
         res (CL/clCreateSubBuffer buffer flags (int create-type) region err)]
     (with-check-arr err (->CLBuffer (volatile! res) (volatile! (Pointer/to ^cl_mem res))
                                     (.size region)))))
  ([^cl_mem buffer ^long flags region]
   (cl-sub-buffer* buffer flags CL/CL_BUFFER_CREATE_TYPE_REGION region)))

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
  [^cl_context ctx ^long size ^long flags ^long alignment]
  (if (and ctx (< 0 size))
    (let [err (int-array 1)
          res (CL/clSVMAlloc ctx flags size alignment)]
      (with-check-arr err (->SVMBuffer ctx (volatile! res) size)))
    (dragan-says-ex "To create a svm buffer, you must provide a context and a positive size.")))

(extend-type Long
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n this nil)
      {:kernel (info kernel) :n n :arg (info this)}
      kernel)))

(extend-type Integer
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n this nil)
      {:kernel (info kernel) :n n :arg (info this)}
      kernel)))

(defmacro ^:private extend-mem-array [type atype bytes]
  `(extend-type ~type
    Mem
    (ptr [this#]
      (Pointer/to (~atype this#)))
    (size [this#]
      (* ~bytes (alength (~atype this#))))
    Argument
    (set-arg [this# kernel# n#]
      (with-check
        (CL/clSetKernelArg kernel# n# (* ~bytes (alength (~atype this#))) (Pointer/to (~atype this#)))
        {:kernel (info kernel#) :n n# :arg (info this#)}
        kernel#))))

(extend-mem-array (Class/forName "[F") floats Float/BYTES)
(extend-mem-array (Class/forName "[D") doubles Double/BYTES)
(extend-mem-array (Class/forName "[I") ints Integer/BYTES)
(extend-mem-array (Class/forName "[J") longs Long/BYTES)
(extend-mem-array (Class/forName "[B") bytes 1)
(extend-mem-array (Class/forName "[S") shorts Short/BYTES)
(extend-mem-array (Class/forName "[C") chars Character/BYTES)

(extend-type ByteBuffer
  Mem
  (ptr [this]
    (Pointer/toBuffer this))
  (size [this]
    (.capacity ^ByteBuffer this)))

;; ============== Events ==========================================

(defrecord EventCallbackInfo [status data])

(deftype EventCallback [ch]
  EventCallbackFunction
  (function [this ev status data]
    (go (>! ch (->EventCallbackInfo (dec-command-execution-status status) data)))))

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
  ([^cl_event e ^EventCallback callback ^long callback-type data]
   (with-check (CL/clSetEventCallback e callback-type callback data) (.ch callback)))
  ([^cl_event e ^EventCallback callback]
   (set-event-callback* e callback CL/CL_COMPLETE nil)))

;; ============= Program ==========================================

(deftype BuildCallback [ch]
  BuildProgramFunction
  (function [this program data]
    (go (>! ch (if data data :no-user-data)))))

;; ============== Command Queue ===============================

(defn command-queue*
  "Creates a host or device command queue on a specific device.

  ** If you need to support OpenCL 1.2 platforms, you MUST use the alternative
  [[command-queue-1*]] function. Otherwise, you will get an
  UnsupportedOperationException erorr. What is important is the version of the
  platform, not the devices. This function is for platforms (regardless of the
  devices) supporting OpenCL 2.0 and higher. **

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
  ([^cl_context ctx ^cl_device_id device ^long properties]
   (command-queue* ctx device 0 properties))
  ([^cl_context ctx ^cl_device_id device ^long size ^long properties]
   (let [err (int-array 1)
         props (let [clqp (cl_queue_properties.)]
                 (when (< 0 properties) (.addProperty clqp CL/CL_QUEUE_PROPERTIES properties))
                 (when (< 0 size) (.addProperty clqp CL/CL_QUEUE_SIZE size))
                 clqp)
         res (CL/clCreateCommandQueueWithProperties ctx device props err)]
     (with-check-arr err {:device (info device)} res))))

(defn command-queue-1*
  "Creates a host or device command queue on a specific device.

  ** If you need to support legacy OpenCL 1.2 or earlier platforms,
  you MUST use this  function instead of [command-queue*], which is for
  OpenCL 2.0 and higher. What is important is the version of the platform,
  not the devices.**

  Arguments are:

  * `ctx` - the `cl_context` for the queue;
  * `device` - the `cl_device_id` for the queue;
  * `size` - the size of the (on device) queue;
  * `properties` - long bitmask containing properties, defined by the OpenCL
  standard are available as constants in the org.jocl.CL class.

  This is a low-level version of [[command-queue-1]].

  If called with invalid context or device, throws `ExceptionInfo`.

  See https://www.khronos.org/registry/cl/sdk/1.2/docs/man/xhtml/clCreateCommandQueue.html,
  http://www.jocl.org/doc/org/jocl/CL.html#clCreateCommandQueue-org.jocl.cl_context-org.jocl.cl_device_id-long-int:A-

  Examples:
      (command-queue-1* ctx dev 524288  (bit-or CL/CL_QUEUE_PROFILING_ENABLED
                                                CL/CL_QUEUE_ON_DEVICE))
      (command-queue-1* ctx dev CL/CL_QUEUE_PROFILING_ENABLED)
  "
  ([ctx device ^long properties]
   (command-queue-1* ctx device 0 properties))
  ([ctx device ^long size ^long properties]
   (let [err (int-array 1)
         res (CL/clCreateCommandQueue ctx device properties err)]
     (with-check-arr err res))))

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
  ^ByteBuffer [queue cl blocking offset req-size flags ^objects wait-events event]
  (if (< 0 ^long req-size)
    (let [err (int-array 1)
          res (CL/clEnqueueMapBuffer queue (extract cl) blocking flags offset
                                     (min ^long req-size (- ^long (size cl) ^long offset))
                                     (if wait-events (alength wait-events) 0)
                                     wait-events event err)]
      (with-check-arr err (.order res (ByteOrder/nativeOrder))))
    (ByteBuffer/allocateDirect 0)))

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
