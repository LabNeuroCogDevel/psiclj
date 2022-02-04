(defproject psiclj "0.1.0"
  :dependencies [
        [org.postgresql/postgresql "42.2.2"]
        [org.clojure/java.jdbc  "0.7.5"]
        [org.xerial/sqlite-jdbc "3.7.2"]
        [com.layerware/hugsql   "0.5.1"]
        [compojure/compojure    "1.6.2"]
        [http-kit/http-kit      "2.5.3"]
        [ring/ring-core         "1.9.4"]
        [ring/ring-json         "0.5.1"]
        [org.clojure/tools.cli "0.3.5"]  ; lein complains but not actually used by project?
        ]
  :main psiclj
  :plugins [[io.taylorwood/lein-native-image "0.3.0"]]
  :aot :all
  :native-image {:name     "psiclj"
                 :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
                 :opts     ["--report-unsupported-elements-at-runtime"
                            "--initialize-at-build-time"
                            "--verbose"
                            "-H:+ReportExceptionStackTraces"
                            "-H:IncludeResources=.*html$"
                            "-J-Xmx3g"
                            "--allow-incomplete-classpath"
                            ;;avoid spawning build server
                            "--no-server"]})
