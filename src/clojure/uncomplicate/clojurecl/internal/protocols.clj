;;   Copyright (c) Dragan Djuric. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) or later
;;   which can be found in the file LICENSE at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Dragan Djuric"}
    uncomplicate.clojurecl.internal.protocols)

(defprotocol Wrappable
  (wrap [this]))

(defprotocol Mem
  "An object that represents memory that participates in OpenCL operations.
  It can be on the device ([[CLMem]]), or on the host.  Built-in implementations:
  cl buffer, Java primitive arrays and `ByteBuffer`s."
  (ptr [this]
    "JOCL `Pointer` to this object.")
  (size [this]
    "Memory size of this cl or host object in bytes."))

(defprotocol CLMem
  "A wrapper for `cl_mem` objects, that also holds a `Pointer` to the cl mem
  object, context that created it, and size in bytes. It is useful in many
  functions that need that (redundant in Java) data because of the C background
  of OpenCL functions."
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
