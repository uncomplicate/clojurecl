(defproject uncomplicate/clojurecl "0.1.0-SNAPSHOT"
  :description "ClojureCL is a Clojure library for OpenCL computations."
  :url "https://github.com/uncomplicate/clojurecl"
  :scm {:name "git"
        :url "https://github.com/uncomplicate/clojurecl"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.jocl/jocl "0.1.9"]
                 [vertigo "0.1.3"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]

  :codox {:src-dir-uri "http://github.com/uncomplicate/clojurecl/blob/master/"
          :src-linenum-anchor-prefix "L"
    ;;      :exclude [uncomplicate.clojrecl.protocols]
          :output-dir "docs/codox"}

  ;;:aot [uncomplicate.clojurecl.protocols]
  :jvm-opts ^:replace ["-XX:MaxDirectMemorySize=16g" "-XX:+UseLargePages"];;also replaces lein's default JVM argument TieredStopAtLevel=1

  :profiles {:dev {:plugins [[lein-midje "3.1.3"]
                             [lein-marginalia "0.8.0"]
                             [codox "0.8.10"]]
                   :global-vars {*warn-on-reflection* true
                                 *assert* true
                                 *unchecked-math* :warn-on-boxed
                                 *print-length* 128}
                   :dependencies [[midje "1.6.3"]
                                  [criterium "0.4.3"]]}}

  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :source-paths ["src/clojure"]
  :test-paths ["test/clojure" "test/opencl"]
  :java-source-paths ["src/java"])
