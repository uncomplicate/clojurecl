(ns uncomplicate.clojurecl.core
  (:require [uncomplicate.clojurecl
             [constants :refer :all]
             [utils :refer [with-check with-check-arr mask error]]
             [info :refer [info build-info program-devices]]]
            [clojure.string :as str]
            [clojure.core.async :refer [go >!]])
  (:import [org.jocl CL cl_platform_id cl_context_properties cl_device_id
            cl_context cl_command_queue cl_mem cl_program cl_kernel cl_sampler
            cl_event cl_buffer_region Sizeof Pointer CreateContextFunction EventCallbackFunction
            BuildProgramFunction]
           [java.nio ByteBuffer ByteOrder]))

(def ^:dynamic *platform*)
(def ^:dynamic *context*)
(def ^:dynamic *command-queue*)

;; =============== Release CL Resources ==================================

(defprotocol Releaseable
  (release [this]))

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

(defn release-seq [cl]
  (if (sequential? cl)
    (map release-seq cl)
    (release cl)))

(defmacro with-release [bindings & body]
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
;; Perhaps write all errors to a sliding async channel that is going to be read
;; by an error reporting go block?
(defn context* [^objects devices notify user-data properties]
  (let [err (int-array 1)
        res (CL/clCreateContext properties
                                (alength devices) devices
                                notify user-data err)]
    (with-check-arr err res)))

(defn context
  ([devices properties]
   (context* (into-array ^cl_device_id devices) nil nil
             (context-properties properties)))
  ([devices]
   (context devices nil))
  ([]
   (with-release [devs (devices)]
     (context devs)))) ;; TODO Test whether this solution for memory leaks works as expected

(defn context-info []
  (info *context*))

(defmacro with-context [context & body]
  `(binding [*context* ~context]
     (try ~@body
          (finally (release *context*)))))

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
  (release [_]
    (release cl))
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

(defn cl-buffer*
  ([context ^long size ^long flags]
   (let [err (int-array 1)
         res (CL/clCreateBuffer context flags size nil err)]
     (with-check-arr err (->CLBuffer res (Pointer/to ^cl_mem res) size))))
  ([^long size ^long flags]
   (cl-buffer* *context* size flags)))

(defn cl-buffer
  ([context size flag & flags]
   (cl-buffer* context size (mask cl-mem-flags flag flags)))
  ([^long size flag]
   (cl-buffer* *context* size (cl-mem-flags flag))))

(defn cl-sub-buffer*
  ([^cl_mem cl-mem ^long flags ^long create-type ^cl_buffer_region info]
   (let [err (int-array 1)
         res (CL/clCreateSubBuffer cl-mem flags
                                   (int create-type)
                                   info err)]
     (with-check-arr err (->CLBuffer res (Pointer/to ^cl_mem res) size))))
  ([cl-mem ^long flags info]
   (cl-sub-buffer* cl-mem flags CL/CL_BUFFER_CREATE_TYPE_REGION info)))

(defn cl-sub-buffer
  ([buffer origin size flag & flags]
   (cl-sub-buffer* (cl-mem buffer) (mask cl-mem-flags flag flags)
                   (cl_buffer_region. origin size))))

(extend-type Number
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (long this) nil)
      kernel)))

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

(extend-type (Class/forName "[C")
  HostMem
  (ptr [this]
    (Pointer/to ^chars this))
  Argument
  (set-arg [this kernel n]
    (with-check (CL/clSetKernelArg kernel n (* Character/BYTES (alength ^chars this))
                                   (Pointer/to ^chars this))
      kernel)))

(extend-type ByteBuffer
  HostMem
  (ptr [this]
    (Pointer/toBuffer this)))

;; ============== Events ==========================================

(defn event []
  (cl_event.))

(defn host-event
  ([]
   (host-event *context*))
  ([context]
   (let [err (int-array 1)
         res (CL/clCreateUserEvent context err)]
     (with-check-arr err res))))

(defn events
  ([]
   (make-array cl_event 0))
  ([e]
   (doto ^objects (make-array cl_event 1)
         (aset 0 e)))
  ([e0 e1]
   (doto ^objects (make-array cl_event 2)
         (aset 0 e0)
         (aset 1 e1)))
  ([e0 e1 e2]
   (doto ^objects (make-array cl_event 3)
         (aset 0 e0)
         (aset 1 e1)
         (aset 2 e2)))
  ([e0 e1 e2 e3]
   (doto ^objects (make-array cl_event 4)
         (aset 0 e0)
         (aset 1 e1)
         (aset 2 e2)
         (aset 3 e3)))
  ([e0 e1 e2 e3 e4]
   (doto ^objects (make-array cl_event 5)
         (aset 0 e0)
         (aset 1 e1)
         (aset 2 e2)
         (aset 3 e3)
         (aset 4 e4)))
  ([e0 e1 e2 e3 e4 & es]
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

(defn event-callback [ch]
  (->EventCallback ch))

(defn set-event-callback!
  ([e ^EventCallback callback ^long callback-type data]
   (with-check
     (CL/clSetEventCallback e callback-type callback data)
     (.ch callback)))
  ([e ^EventCallback callback]
   (set-event-callback! e callback CL/CL_COMPLETE nil)))

(defn follow
  ([channel callback-type]
   (let [callback (->EventCallback channel)
         callb-type (if callback-type
                      (cl-command-execution-status callback-type)
                      CL/CL_COMPLETE)]
     (fn
       ([e callback-type data]
        (set-event-callback! e callback
                            (cl-command-execution-status callback-type) data))
       ([e data]
        (set-event-callback! e callback callb-type data))
       ([e]
        (set-event-callback! e callback callb-type nil)))))
  ([channel]
   (follow channel nil)))

(defn set-status* [ev ^long status]
  (let [err (CL/clSetUserEventStatus ev status)]
    (with-check err ev)))

(defn set-status! [ev status]
  (set-status* ev (cl-command-execution-status status)))

;; ============= Program ==========================================

(defrecord BuildCallbackInfo [program data])

(deftype BuildCallback [ch]
  BuildProgramFunction
  (function [this program data]
    (go (>! ch (->BuildCallbackInfo program data)))))

(defn program-with-source
  ([context source]
   (let [err (int-array 1)
         n (count source)
         res (CL/clCreateProgramWithSource
              context n (into-array String source) nil err)]
     (with-check-arr err res)))
  ([source]
   (program-with-source *context* source)))

(defn build-program!
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

(defn num-kernels [program]
  (let [res (int-array 1)
        err (CL/clCreateKernelsInProgram program 0 nil res)]
    (with-check err (aget res 0))))

(defn kernel
  ([program name]
   (let [err (int-array 1)
         res (CL/clCreateKernel program name err)]
     (with-check-arr err res)))
  ([program]
   (let [nk (num-kernels program)
         res (make-array cl_kernel nk)
         err (CL/clCreateKernelsInProgram program nk res nil)]
     (vec (with-check err res)))))

(defn set-arg! [kernel n cl-mem]
  (set-arg cl-mem kernel n))

(defn set-args!
  ([kernel cl-mem]
   (set-arg! kernel 0 cl-mem))
  ([kernel cl-mem-0 cl-mem-1]
   (-> (set-arg! kernel 0 cl-mem-0)
       (set-arg! 1 cl-mem-1)))
  ([kernel cl-mem-0 cl-mem-1 cl-mem-2]
   (-> (set-arg! kernel 0 cl-mem-0)
       (set-arg! 1 cl-mem-1)
       (set-arg! 2 cl-mem-2)))
  ([kernel cl-mem-0 cl-mem-1 cl-mem-2 cl-mem-3 & cl-mems]
   (let [ker (-> (set-arg! kernel 0 cl-mem-0)
                 (set-arg! 1 cl-mem-1)
                 (set-arg! 2 cl-mem-2)
                 (set-arg! 3 cl-mem-3))]
     (loop [i 4 cl-mems cl-mems]
       (if-let [mem (first cl-mems)]
         (do (set-arg! ker i mem)
             (recur (inc i) (next cl-mems)))
         ker)))))

;; ============== Work Size ==================================

(defrecord WorkSize [^long workdim ^longs global ^longs local ^longs offset])

(defn work-size
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

;; TODO Opencl 2.0  clCreateCommandQueue is deprecated in JOCL 0.2.0
;; use ccqWithProperties
(defn command-queue* [context device ^long properties]
  (let [err (int-array 1)
        res (CL/clCreateCommandQueue context device properties err)]
    (with-check-arr err res)))

(defn command-queue
  ([context device prop1 prop2 & properties]
   (command-queue* context device
                   (apply mask cl-command-queue-properties
                          prop1 prop2 properties)))
  ([context device prop]
   (command-queue* context device (get cl-command-queue-properties prop 0)))
  ([device prop]
   (command-queue* *context* device (get cl-command-queue-properties prop 0)))
  ([device]
   (command-queue* *context* device 0)))

;; TODO use *command-queue* in enqXXX functions
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
   (enq-read! *command-queue* cl host false 0 nil nil)))

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
   (enq-write! *command-queue* cl host false 0 nil nil)))

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
