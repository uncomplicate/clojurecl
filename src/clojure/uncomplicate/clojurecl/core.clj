(ns uncomplicate.clojurecl.core
  (:require [uncomplicate.clojurecl
             [constants :refer :all]
             [utils :refer [with-check with-check-arr]]
             [info :refer [info]]]
            [clojure.string :as str])
  (:import [org.jocl CL cl_platform_id cl_context_properties cl_device_id
            cl_context cl_command_queue cl_mem cl_program cl_kernel cl_sampler
            cl_event
            Sizeof Pointer CreateContextFunction]
           [java.nio ByteBuffer ByteOrder]))

(def ^:dynamic *platform*)
(def ^:dynamic *context*)
(def ^:dynamic *command-queue*)

;; =============== Release CL Resources ==================================

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

(defn ^:private close-seq [cl]
  (cond
   (instance? uncomplicate.clojurecl.core.Releaseable cl) (close cl)
   (sequential? cl) (map close-seq cl)) )

(defmacro with-release [bindings & body]
  (assert (vector? bindings) "a vector for its binding")
  (assert (even? (count bindings)) "an even number of forms in binding vector")
  (cond
   (= (count bindings) 0) `(do ~@body)
   (symbol? (bindings 0)) `(let ~(subvec bindings 0 2)
                             (try
                               (with-release ~(subvec bindings 2) ~@body)
                               (finally
                                 (close-seq ~(bindings 0)))))
   :else (throw (IllegalArgumentException.
                 "with-release only allows Symbols in bindings"))))

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

(defn platform-info []
  (info *platform*))

(defmacro with-platform [platform & body]
  `(binding [*platform* ~platform]
    ~@body))

;; =============== Device ==========================================

(defn num-devices* ^long [platform ^long device-type]
  (let [res (int-array 1)
        err (CL/clGetDeviceIDs platform device-type 0 nil res)]
    (with-check err (aget res 0))))

(defn num-devices
  ([platform device-type]
   (num-devices* platform (cl-device-type device-type)))
  ([x]
   (if (keyword? x)
     (num-devices *platform* x)
     (num-devices* x CL/CL_DEVICE_TYPE_ALL)))
  ([]
   (num-devices* *platform* CL/CL_DEVICE_TYPE_ALL)))

(defn devices* [platform ^long device-type]
  (let [num-devices (num-devices* platform device-type)
        res (make-array cl_device_id num-devices)
        err (CL/clGetDeviceIDs platform device-type num-devices res nil)]
    (with-check err res)))

(defn devices
  ([platform device-type]
   (vec (devices* platform (cl-device-type device-type))))
  ([x]
   (if (keyword? x)
     (devices *platform* x)
     (vec (devices* x CL/CL_DEVICE_TYPE_ALL))))
  ([]
   (devices* *platform* CL/CL_DEVICE_TYPE_ALL)))

;; ========================= Context ===========================================

;; TODO Check for memory leaks! (devices) are used in creating the context, but
;; then forgotten to roam around freely!!!

(defn context-properties [props]
  (reduce (fn [^cl_context_properties cp [p v]]
            (.addProperty cp (cl-context-properties p) v))
          (cl_context_properties.)
          props))

;;TODO Callback function
(defn context* [^"[Lorg.jocl.cl_device_id;" devices notify user-data properties]
  (let [err (int-array 1)
        res (CL/clCreateContext properties
                                (alength devices) devices
                                notity user-data err)]
    (with-check-arr err res)))

(defn context
  ([devices properties]
   (context* (into-array ^cl_device_id devices) nil nil (context-properties properties)))
  ([devices]
   (context devices nil nil nil))
  ([]
   (with-release [devs (devices)]
     (context devs)))) ;; TODO Test whether this solution for memory leaks works as expected

(defn context-info []
  (info *context*))

;;TODO assert
(defmacro with-context [context & body]
  `(binding [*context* ~context]
     (try ~@body
          (finally (close *context*)))))

;; =========================== Memory  =========================================
(defprotocol CLMem ;;TODO add context to memory?
  (cl-mem [this])
  (cl-mem* [this])
  (size [this]))

(defprotocol Argument
  (set-arg [arg kernel n]))

(defprotocol HostMem
  (ptr [this]))

(deftype CLBuffer [^cl_mem cl ^Pointer cl* s]
  Releaseable
  (close [_]
    (close cl))
  CLMem
  (cl-mem [_]
    cl)
  (cl-mem* [_]
    cl*)
  (size [_]
    s)
  Argument
  (set-arg [_ kernel n]
    (with-check (CL/clSetKernelArg kernel n Sizeof/cl_mem cl*) kernel)))

(defn cl-buffer
  ([context ^long size ^long flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer context flags size nil err)]
     (with-check-arr err (->CLBuffer res (Pointer/to ^cl_mem res) size))))
  ([^long size ^long flags]
   (cl-buffer *context* size flags)))

(extend-type (Class/forName "[F")
  HostMem
  (ptr [this]
    (Pointer/to ^floats this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Float/BYTES (alength ^floats this))
                                   (Pointer/to ^floats this))
      kernel)))

(extend-type (Class/forName "[D")
  HostMem
  (ptr [this]
    (Pointer/to ^doubles this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Double/BYTES (alength ^doubles this))
                                   (Pointer/to ^doubles this))
      kernel)))

(extend-type (Class/forName "[I")
  HostMem
  (ptr [this]
    (Pointer/to ^ints this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Integer/BYTES (alength ^ints this))
                                   (Pointer/to ^ints this))
      kernel)))

(extend-type (Class/forName "[J")
  HostMem
  (ptr [this]
    (Pointer/to ^longs this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (* Long/BYTES (alength ^longs this))
                                   (Pointer/to ^longs this))
      kernel)))

(extend-type (Class/forName "[B")
  HostMem
  (ptr [this]
    (Pointer/to ^bytes this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n
                                   (alength ^bytes this)
                                   (Pointer/to ^bytes this))
      kernel)))

(extend-type (Class/forName "[S")
  HostMem
  (ptr [this]
    (Pointer/to ^shorts this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (* Short/BYTES (alength ^shorts this))
                                   (Pointer/to ^shorts this))
      kernel)))

(extend-type ByteBuffer
  HostMem
  (ptr [this]
    (Pointer/toBuffer this)))

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

(defn set-arg! [kernel ^long n memory]
  (set-arg memory kernel n))

(defn set-args! [kernel & memories]
  (do (reduce (fn [^long i mem]
               (do
                 (set-arg! kernel i mem)
                 (inc i)))
             0
             memories)
      kernel))

;; ============== Events ======================================
(defn event []
  (cl_event.))

;; ============== Command Queue ===============================

;; TODO Opencl 2.0  clCreateCommandQueue is deprecated in JOCL 0.2.0
;; use ccqWithProperties
(defn command-queue
  ([context device properties]
   (let [err (int-array 1)
         res (CL/clCreateCommandQueue context device properties err)]
     (with-check-arr err res)))
  ([device properties]
   (command-queue *context* device properties)))

;; TODO use *command-queue* in enqueueXXX functions
(defn enqueue-nd-range
  [queue kernel work-dim
     global-work-offset global-work-size local-work-size
     num-events-in-wait-list event-wait-list event]
  (with-check
    (CL/clEnqueueNDRangeKernel queue kernel work-dim
                               global-work-offset
                               global-work-size
                               local-work-size
                               num-events-in-wait-list
                               event-wait-list
                               event)
    queue))

(defn enqueue-read
  ([queue cl host blocking-read num-events-in-wait-list
    event-wait-list event]
   (with-check
     (CL/clEnqueueReadBuffer queue (cl-mem cl) blocking-read 0
                             (size cl) (ptr host)
                             num-events-in-wait-list event-wait-list event)
     queue))
  ([queue cl host]
   (enqueue-read queue cl host CL/CL_TRUE 0 nil nil)))

(defn enqueue-write
  ([queue cl host blocking-write num-events-in-wait-list
    event-wait-list event]
   (with-check
     (CL/clEnqueueWriteBuffer queue (cl-mem cl) blocking-write 0
                             (size cl) (ptr host)
                             num-events-in-wait-list event-wait-list event)
     queue))
  ([queue cl host]
   (enqueue-write queue cl host CL/CL_TRUE 0 nil nil)))

(defn enqueue-map-buffer
  ([queue cl blocking flags num-events
    wait-list event]
   (let [err (int-array 1)
         res (CL/clEnqueueMapBuffer queue (cl-mem cl) blocking flags 0
                                    (size cl) num-events
                                    wait-list event err)]
     (with-check-arr err (.order res (ByteOrder/nativeOrder)))))
  ([queue cl flags]
   (enqueue-map-buffer queue cl CL/CL_TRUE flags 0 nil nil)))

;; TODO with-mapping

(defn enqueue-unmap-mem-object
  ([queue cl host num-events-in-event-list wait-list event]
   (let [err (CL/clEnqueueUnmapMemObject queue (cl-mem cl) host
                                         num-events-in-event-list wait-list event)]
     (with-check err queue)))
  ([queue cl host]
   (enqueue-unmap-mem-object queue cl host 0 nil nil)))

(defmacro with-queue [queue & body]
  `(binding [*command-queue* ~queue]
     (try ~@body
          (finally (close *command-queue*)))))
