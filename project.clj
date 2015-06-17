(defproject uncomplicate/clojurecl "0.1.2-SNAPSHOT"
  :description "ClojureCL is a Clojure library for parallel computations with OpenCL."
  :url "https://github.com/uncomplicate/clojurecl"
  :scm {:name "git"
        :url "https://github.com/uncomplicate/clojurecl"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.jocl/jocl "0.2.0-RC00"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [vertigo "0.1.3"]
                 [com.outpace/config "0.9.0"]]

  :codox {:defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/uncomplicate/clojurecl/blob/master/"
          :src-linenum-anchor-prefix "L"
          :output-dir "docs/codox"}

  :jvm-opts ^:replace ["-XX:MaxDirectMemorySize=16g" "-XX:+UseLargePages"];;also replaces lein's default JVM argument TieredStopAtLevel=1

  :profiles {:dev {:plugins [[lein-midje "3.1.3"]
                             [bilus/lein-marginalia "0.8.8"]
                             [codox "0.8.12"]]
                   :global-vars {*warn-on-reflection* true
                                 *assert* true
                                 *unchecked-math* :warn-on-boxed
                                 *print-length* 128}
                   :dependencies [[midje "1.7.0-beta1"]
                                  [criterium "0.4.3"]]}}

  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :source-paths ["src/clojure"]
  :test-paths ["test/clojure" "test/opencl"]
  :java-source-paths ["src/java"])
