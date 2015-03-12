(ns uncomplicate.clojurecl.core-test
  (:require [midje.sweet :refer :all]
            [vertigo.bytes :refer :all])
  (:import [org.jocl CL cl_platform_id cl_context_properties cl_device_id
            cl_context cl_command_queue cl_mem cl_program cl_kernel Sizeof Pointer]
           [java.nio ByteBuffer ByteOrder]))
