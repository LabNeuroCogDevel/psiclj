(defproject psiclj "0.2.2"
  :dependencies [
        [org.clojure/tools.cli "1.0.206"]  ; parse-opts
        [org.postgresql/postgresql "42.2.2"]
        [org.clojure/java.jdbc  "0.7.5"]
        [org.clojure/data.json "2.4.0"]
        [org.xerial/sqlite-jdbc "3.7.2"]
        [com.layerware/hugsql   "0.5.1"]
        [compojure/compojure    "1.6.2"]
        [http-kit/http-kit      "2.5.3"]
        [ring/ring-core         "1.9.4"]
        [ring/ring-json         "0.5.1"]
        [ring-basic-authentication/ring-basic-authentication "1.1.1"]
        ;[ring/ring-defaults "0.3.3"]
        [org.clj-commons/digest "1.4.100"] ; md5
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
                            "-H:IncludeResources=.*js$"
                            "-H:IncludeResources=.*css$"
                            "-J-Xmx3g"
                            "--allow-incomplete-classpath"
                            ;;avoid spawning build server
                            "--no-server"]})
; after thml
; '-H:ResourceConfigurationResources@jar:file:///home/foranw/.m2/repository/org/xerial/sqlite-jdbc/3.36.0.3/sqlite-jdbc-3.36.0.3.jar!/META-INF/native-image/org.xerial/sqlite-jdbc/native-image.properties=META-INF/native-image/org.xerial/sqlite-jdbc/resource-config.json' \
;'-H:JNIConfigurationResources@jar:file:///home/foranw/.m2/repository/org/xerial/sqlite-jdbc/3.36.0.3/sqlite-jdbc-3.36.0.3.jar!/META-INF/native-image/org.xerial/sqlite-jdbc/jni-config.json=META-INF/native-image/org.xerial/sqlite-jdbc/jni-config.json' \
;'-H:ReflectionConfigurationResources@jar:file:///home/foranw/.m2/repository/org/xerial/sqlite-jdbc/3.36.0.3/sqlite-jdbc-3.36.0.3.jar!/META-INF/native-image/org.xerial/sqlite-jdbc/reflect-config.json=META-INF/native-image/org.xerial/sqlite-jdbc/reflect-config.json' \
;'-H:ResourceConfigurationResources@jar:file:///home/foranw/.m2/repository/org/xerial/sqlite-jdbc/3.36.0.3/sqlite-jdbc-3.36.0.3.jar!/META-INF/native-image/org.xerial/sqlite-jdbc/resource-config.json=META-INF/native-image/org.xerial/sqlite-jdbc/resource-config.json' \
;
