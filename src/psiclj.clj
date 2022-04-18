;; QUICKSTART
;;  open in emacs. M-x package-install cider; run cider-jack-in-clj (c-c M-j). run:
;; (in-ns psiclj) (->"." io/file .getCanonicalPath) (DB)
;; (run-server 4444) (print server-stop-fn)
;; NB. many changes wont be seen until (def app...) is re-sourced
;; elisp:
;;   (local-set-key (kbd "M-C-S-x")  (lambda () (interactive) (save-excursion (beginning-of-buffer) (re-search-forward "^(def app") (cider-eval-defun-at-point))))
(ns psiclj
  (:require
   ;; DB
   [hugsql.core :as hugsql]
   ;; HTTP - server
   [org.httpkit.server :as srv]
   ;; HTTP - routes
   [clojure.java.io :as io]
   [compojure.core :refer [routes wrap-routes context defroutes GET POST] :as comp]
   [compojure.route :as route]
   ;;[ring.util.resposne :refer [resource-response]]
   [ring.middleware.json :as json]
   [ring.middleware.params :refer [assoc-query-params params-request]]
   [ring.util.response :as resp]
   [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
   ; for params in request query
   ;[ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   ;; [compojure.handler :as handler]
   
   ;; str split DATABASE_URL
   [clojure.string :as str]
   ;; command line args
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.java.io :as io]
   ;; saving to file
   [clojure.data.json :as datajson]
   ;; completion code
   [clj-commons.digest :refer [md5]]
   )
  (:gen-class))


;; global settings
;; this can be modified with command arguments
(def OPTIONS (atom {:version "nover"
                    :taskname "task"
                    :path-root "out/"
                    :allow-repeat false}))


;; DB
(defn dburl-map
  "extract db info from postgres://user:pass@host:port/db"
  [dburl]
  (zipmap [:dbtype :user :password :host :port :db]
          (str/split dburl #"[:/@]+")))
(defn jdbc-url
  "make from map: //host:port/db?user=X&password=Y"
  [{:keys [host port db user password]}]
      (str ;; "jdbc:postgresql:"
           "//" host ":" port
           "/" db
           "?user="  user
           "&password=" password
           ;; 'allow' and 'prefer' are not supported by JDBC driver
           ;; choice is 'disable' or 'require' and ssl is the better default
           ;; but it's annoying on devel, so have the option to disable it by setting
           ;; export PSQLSSLQUERY="sslmode=disable"
           (or (System/getenv "PSQLSSLQUERY") "&sslmode=require")
           ))
(defn db-to-jdbc
  "parse DATABASE_URL to JDBC_DATABASE_URL
  -> postgres://user:pass@host:port/db
  <- //host:port/db?user=X&password=Y  ; NB no jdbc:postgresql:
  "
  [dburl] (-> dburl dburl-map jdbc-url))

; here for native-image build
; otherwise: Exception in thread "main" java.sql.SQLException: No suitable driver found for jdbc:sqlite:psiclj.sqlite3
(Class/forName "org.sqlite.JDBC") 

(defn get-db-params
  "setup database default to hardcoded psiclj.sqlite3. otherwise use postgres"
  []
  (let [db (System/getenv "DATABASE_URL")]
    (if db
        {:subprotocol "postgresql"
         :subname (db-to-jdbc db)}
        (do (Class/forName "org.sqlite.JDBC") 
            {:subprotocol "sqlite"
             :classname "org.sqlite.JDBC"
             :subname "psiclj.sqlite3"}))))

(defn DB [] (get-db-params))


;; middleware. if we are on local host we dont need passwords and
;; we can save json files per run when finished (safer than everything in one sqlite file?)
(defn trusted-host? "trust localhost" [remote-addr]
  (print (str "remote-addr:" remote-addr "\n"))
  ;(= (str remote-addr) "127.0.0.1")
  (contains? #{"0:0:0:0:0:0:0:1" "0.0.0.0" "127.0.0.1" "localhost"} (str remote-addr))
  )
(defn auth?
  "authentiate for db access. Basic auth. password only. must be in env HTTPDBPASS"
  [name pass]
  (if-let [want-pass (System/getenv "HTTPDBPASS")]
    (= pass want-pass)
    false))
(defn localhost-bypass-auth
  "wrap the wrapper. skip basic-authentication when host is good"
  [app]
  (fn [{:keys [remote-addr] :as req}]
    (if (trusted-host? remote-addr)
        (app req)
        ((wrap-basic-authentication app auth?) req))))


;; 20211012 - not needed for working postgres, and doesn't help sqlite
;; need to tell native-image we use both postgres and sqlite so it's in the binary
;(defn force-db-lib-load [con] (try
;    (java.sql.DriverManager/getConnection con)
;  (catch Exception e (str "junk db load for lib pull: " (.getMessage e)))))
;(force-db-lib-load "jdbc:sqlite::memory")
;(force-db-lib-load "jdbc:postgresql://localhost/postgres?user=postgres")

(hugsql/def-db-fns "all.sql")
(hugsql/def-sqlvec-fns "all.sql")
(defn create-run [db info]
  (if (= "sqlite" (:subprotocol db))
    (create-run-sqll db info)
    (create-run-psql db info)))

(defn already-done?
  "did we already finish this run?"
  [run-data]
  (-> (DB) (run-by-id run-data) :finished_at nil? not))

(defn worker-already-seen
  "is this person anywhere in the DB already?"
  [id]
  (-> (DB) (n_sessions {:id id}) :n (> 0)))

(defn write-run-json
  "write out run info into a json file. TODO: save to results folder"
  [{:keys [id task version run timepoint] :as run-info}]
  (let [fname (apply str (interpose "_" (vals run-info)))
        fname (.replaceAll (re-matcher #"[^A-Z0-9a-z:,._-]+" fname) "_")
        fname (str fname ".json")
        data (datajson/write-str (get-run-json (DB)  run-info))]
    (with-open [out (io/writer fname )] (.write out data))
    fname))

(defn md5-data
  "generate code for compeltion. also see psql specific md5-finish"
  [run-info]
  (let [finished_at_str (string-for-md5-finish (DB) run-info)]
    (md5 (:runinfostr finished_at_str))))

;; HTTP
;; https://github.com/taylorwood/lein-native-image/tree/master/examples/http-api
;; https://github.com/weavejester/compojure/wiki/Nesting-routes

;; 20211008 removed io/resource from read-file, getting files outside of jar/exe
(defn read-file [file] (slurp file))

(defn task-run [req]
  (routes
   (POST "/response" []
         (let [ task-resp (-> req :body slurp)
               run-info (assoc (:params req) :json task-resp)
               DB (DB)]
           (resp/response {:ok (upload-json DB run-info)})))
           ;(let [run-info (:params req)]
           ;  (println run-info)
           ;  (resp/response {:ok (-> req :body slurp)})))

   (POST "/info" []
         (let [status
               (upload-system-info
                (DB) (assoc (:params req)
                          :info (-> req :body slurp)))]
           (resp/response {:ok status})))

   (POST "/finish" []
         (let [status (finish-run (DB) (:params req))]
           ;; if we are running locally (trusted), then also save a json file
           (if (trusted-host? (:remote-addr req)) (write-run-json (:params req)))
           (resp/response {:ok status :code (subs ( md5-data (:params req)) 0 5)})))))

(defroutes task-run-context
  (context "/:id/:task/:timepoint/:run"
           req
           (task-run (assoc-in req [:params :version] (:version  @OPTIONS))))

  ;; return json with all tasks (currently just one)
  ;; TODO: report more than one task. need major overhall
  (GET "/tasks" [] (resp/response {:tasks [(:taskname @OPTIONS)]}))

  ;; get-anchor returns {:anchor "option1=value&option2=value"} from permutations table
  ;; but make null empty string so json parses correctly ""
  (GET "/anchor/:task" [task]
       (let [task-anchor (get (get-anchor (DB) {:task task}) :anchor "")]
         (resp/response {:anchor task-anchor}))))

(defn quick-info "where are we. used to debug"
  [req]
  (str  "  \npwd=" (-> (java.io.File. ".") .getAbsolutePath)
        "  \nroot=" (:path-root @OPTIONS)
        "  \nreq=" (:uri req)))

;; 20211008 these simple functions were source of great headache
;; https://stackoverflow.com/questions/69501727/updated-atom-value-not-used-in-compojure-route/69502052#69502052
;; need to pass them as var to route so we can use the update atom value
;; which we need because the default 'out/' can be change by command parameters
;; still TODO: confirm the same doesn't need to happen with @VERSION
;; -- that is: does defroutes evaluated each request and update @VERSION?
;;             will see in the DB after setting -v
;; 20211029 add io/resource back if file doesn't exist. namely for built in not-found
(defn slurp-root
  "return file contents of either what's provided at run time via -r root
  or what's stored in the resources of compiled applicaiton
  used for not-found page and /ad"
  [fname]
  (let [path (io/file (:path-root @OPTIONS) fname)
        ;; if no file, use resource (for not-found.html)
        path (if (-> path .exists)
               path
               (io/resource fname))]
    (if path
      (slurp (str path))
      (str "cannot find " fname "!"))))
(defn not-found-fn [req]
  {:status 404
   :body (slurp-root "not-found.html")})

(defn send-built-in
  "respond with built in file. optionally set status"
  [file & {:keys [status] :or {status 200}}]
  (fn [req]
          {:status status
           :body (slurp-root file)}))

(defn find-root
  "root from global atom.
  prefix with extra / if we are an absolute path
  route/resources strips of the first leading / as we want it.
  so give it ^//"
  [& args]
  {:root (:path-root @OPTIONS) :allow-symlinks? true})


;; db access is behind basic auth
(defn db-run-json [who]
  (resp/response (most-recent (DB) {:id who})))

(defn tag "html tag" [t v] (str "<" t ">" v "</" t ">"))
(defn str-map [f col] (apply str (map f col)))
(defn tag-list [t l] (str-map (fn[i] (tag t i)) l))
(defn db-runs-html
  "quick html table of recent complete runs"
  [req]
  (resp/response
   (str "<html><body><table>"
        ;; hard coded headers from recent-runs. todo: use let above and keys here
        (tag  "tr" (tag-list "th"
                             '(worker_id, task_name , version , timepoint , run_number , started_at , finished_at)))
        ;; tr with nested td
        (str-map (fn [row] (tag "tr" (tag-list "td" (vals row))))
                 (recent-runs (DB)))
       "</table></body></html>" )))
(defroutes db-routes
  (context "/" req
           (GET "/db" {:keys [headers params body server-port] :as req}  (db-runs-html req))
           (GET "/db/:who" [who]  (db-run-json who))))


(defroutes pages
  (context "/:id/:task/:timepoint/:run" req
           (GET "/" []
                ;; TODO: try to get agent and maybe viewport from browser
                ;; for :info
                (let [run-info (assoc (:params req) :info "" :version (:version @OPTIONS))
                      index (str (io/file (:path-root @OPTIONS) "index.html"))
                      DB (DB)]
                  (if (already-done?  run-info)
                    (resp/response "already done!")
                    (do
                      (println "loading" index)
                      (println "sending runinfo to db" DB)
                      (create-run DB run-info)
                      (resp/response (read-file index))))))

           ;; main.js, style.css, img/*
           ;; route/resources depends on io/resource
           ;; only for inside jar/exe/src path
           ;; use files! (this took a long time to understand!)
           (route/files "/" (find-root))) )

;; default routes + root/extra for additional html pages
(def app
  (routes
   pages
   (wrap-routes #'task-run-context json/wrap-json-response)
   (route/files "/" {:root (str (:path-root @OPTIONS) "/extra") :allow-symlinks? true})
   (GET "/ad" req
        (let [req (params-request req)
              id (get-in req [:params "workerId"])
              seen (worker-already-seen id)]
          (if (and (not (:allow-repeat @OPTIONS)) seen)
            (resp/response "<html><body>Sorry, you've already done this!</body></html>")
            ;; (resp/response (str "<html><body>req:" id "<br>seen:" seen "</body></html>"))
            (send-built-in "ad.html"))))
   (GET "/consent.html" []  (send-built-in "consent.html"))
   (GET "/mturk.js" []  (send-built-in "mturk.js"))
   (localhost-bypass-auth db-routes)
   ;; (handler/api)
   (route/not-found not-found-fn)
   ;(site-defaults)
))


;; exercise
(defn excercise-db []
  (def run-data {:id "will" :task "test" :version "x" :run 1 :timepoint 1 :json "[]" :info "[]"})
  (let [DB (DB)]
  (create-run-table DB)
  (create-permutation-lookup-table DB)
  (already-done? run-data)
  (create-run DB (assoc run-data :info "[{system: \"none\"}]"))
  (upload-json DB (assoc run-data :json "[{data: [1,3]}]"))
  (finish-run DB run-data)
  (md5-data run-data)))


;; Main
(defn check-file [fname]
  (when (-> (:path-root @OPTIONS) (io/file fname) .exists not)
      (println (str "cannot find '" (:path-root @OPTIONS) "/" fname "'. consier using -r"))
      (System/exit 1)))

(defonce server-stop-fn (atom nil))
(defn run-server
  "run web service. restarts if running. state in @server-stop-fn"
  [port]
  (println (str "Running webserver on " port))
  (when @server-stop-fn (@server-stop-fn))
  (reset! server-stop-fn (srv/run-server #'app {:port port})))

(defn -main [& args]
  (let [opts [["-p" "--port PORT"
               "port. tries env PORT first. default 3001"
               :default "3001"]
              ["-r" "--root-path PATH/out. TODO: DOESNT WORK. always looks for out relative to binary"
               "path to index.html, not-found.html, resources, extra/ root"
               :default (:path-root @OPTIONS)]
              ["-v" "--version VERSION"
               "set code version inserted in DB"
               :default (:version  @OPTIONS)]
              ["-t" "--taskname TASKNAME"
               "set the task name in id/TASKNAME/timepont/run not-found.html"
               :default (:taskname @OPTIONS)]
              ;; ["-d" "--database DB"
              ;;  (str "psql url. looks to DATABASE_URL first. "
              ;;       "like postgres://user:pass@host:port/db. "
              ;;       "TODO: IMPLEMENT. also see PSQLSSLQUERY='sslmode=disable'")
              ;;  :default nil]
              [ "-a" "--allow-repeat" "Allow workers to repeat task in /ad"
               :default (:allow-repeat @OPTIONS)]
              ["-h" "--help" "This message" :default false]]
        {:keys [options arguments summary errors]} (parse-opts args opts)
        port (Integer/parseInt (or (System/getenv "PORT")
                                   (:port options)))]

    ;; checking if native-image included the html resources
    ;; (print (str "have not found?" (not (nil? (io/resource "not-found.html"))) "\n"))

    ;; update settings from command parsing
    (swap! OPTIONS assoc :version (:version options))
    (swap! OPTIONS assoc :path-root  (:root-path options))
    (swap! OPTIONS assoc :taskname  (:taskname options))
    (swap! OPTIONS assoc :allow-repeat  (:allow-repeat options))
    (println "settings:" @OPTIONS)

    ;; shouldn't continue if there isn't anything to serve
    (check-file "index.html")
    ;;(check-file "not-found.html") ; will use built in if not available

    ;; test out the DB (or die w/error)
    (let [DB (DB)]
      (println (str "creating run table on " DB))
      (create-run-table DB)
      (create-permutation-lookup-table DB))

    ;; serve it up
    ;; kill if already running (repl)
    (run-server port)))
