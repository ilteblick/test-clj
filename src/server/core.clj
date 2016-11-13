(ns core
  (:require [compojure.route :as route]
            [clojure.java.io :as io]
            [org.httpkit.server :as kit]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [clojure.set :refer [rename-keys]

             ]
            [ring.middleware.reload :as reload]
            )

  (:use compojure.core
        compojure.handler
        ring.middleware.params
        carica.core
        korma.db
        korma.core
        ring.util.json-response
        org.httpkit.server
        ))

(def users (atom {"friend" {:username "friend"
                            :password (creds/hash-bcrypt "clojure")
                            :pin "1234" ;; only used by multi-factor
                            :roles #{::user}}
                  "friend-admin" {:username "friend-admin"
                                  :password (creds/hash-bcrypt "clojure")
                                  :pin "1234" ;; only used by multi-factor
                                  :roles #{::admin}}}))

(defn response [data & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str data)})

(defdb db {:classname   "com.mysql.jdbc.Driver"
           :subprotocol "mysql"
           :user        (config :db :user)
           :password    (config :db :password)
           :subname     (str "//127.0.0.1:3306/" (config :db :name) "?useUnicode=true&characterEncoding=utf8")
           :delimiters  "`"
           })

(defentity article)
(defentity user)

(defn findUser [username] (select user (where {:username username})))
(defn addUser [username password]
  (insert user (values {:username username :roles "user" :password (creds/hash-bcrypt password)})))


(defn article-list []
  (json-response (select article)))

(defn articleOne [id]
  (json-response (select article (where {:id id}))))

(defn updateArticle [id data]
  (update article (set-fields {:title data}) (where {:id id}) ))

(defn deleteArticle [id]
  (delete article (where {:id id})))

(defn insertArticle [data]
  (insert article (values {:title data})))



(defprotocol IRepository
  ;;find all entities
  (FindAll [this])
  ;;find entity by id
  (FindOne [this id])
  ;;insert entity
  (Insert [this entity])
  ;;update entity
  (Update [this id entity])
  ;;delete entity
  (Delete [this id])
  )




(defentity article)

(defrecord UserRepository [] IRepository
  (FindOne [this username] (select user (where {:username username})))
  (Insert [this entity] (println entity)
    (insert user (values {:username (:username entity) :roles "user" :password (creds/hash-bcrypt (:password entity))}))
  ))

(defrecord ArticleRepository [] IRepository
  (FindAll [this] (select article))
  )



(def page-bodies {"/login" "Login page here."
                  "/" "Homepage."
                  "/admin" "Admin page."
                  "/user/account" "User account page."
                  "/user/private-page" "Other ::user-private page."
                  "/hook-admin" "Should be admin only."})


(defroutes user-routes
           (GET "/account" request "DAVAI DAVAI TI USER AI KRASAVCHEK NA STRANICE ACCOUNT")
           (GET "/req" request (str request))
           (GET "/private-page" request (page-bodies (:uri request))))


(defn echo-handler [request]
  (with-channel request channel
                (on-close channel (fn [status] (println "chanel closed " status)))
                (on-receive channel (fn [data] (send! channel data)))
                ))

(defroutes compojure-handler
           ;; requires user role

           (context "/user" request
                              (friend/wrap-authorize user-routes #{::user}))

           ;; requires admin role
           (GET "/admin" request (friend/authorize #{::admin}
                                                   #_any-code-requiring-admin-authorization
                                                   "Admin page."))

           ;; anonymous
           (GET "/echo" [] echo-handler)
           (GET "/" request  (-> request (str)))
           (GET "/lol" request (json-response (.FindAll(ArticleRepository.))))
           (GET "/login" [login_failed username]
             (if (= login_failed "Y")  (-> "NEVERNII " (str))
                                (-> "public/html/index.html"
                                    (ring.util.response/file-response {:root "resources"})
                                    (ring.util.response/content-type "text/html")))
             )
           (GET "/register" [] (-> "public/html/register.html"
                                (ring.util.response/file-response {:root "resources"})
                                (ring.util.response/content-type "text/html")))
           (friend/logout (ANY "/logout" request (ring.util.response/redirect "/"))))


(defn register [{:keys [username password]}]
  (try
    (.Insert (UserRepository. ) {:username username :password password})
    (workflows/make-auth {:identity username :roles #{::user} :username username})
    (catch Exception e
      (.println System/out e)
      (json-response (str "Username address already in use"))))
  )

(defn reg-workflow []
  (fn [{:keys [uri request-method params]}]
    (when (and (= uri "/register")
               (= request-method :post))
      (register params))))


(defn credential-fn []
  (fn [auth-map]
    (let [user (get (.FindOne (UserRepository.) (:username auth-map))0)]
      (if (not (= user nil))
        (if (creds/bcrypt-verify (:password auth-map) (:password user))
          {:identity (:id user) :roles #{::user} :user user})
        )
      )))

  (def app
  (-> #'compojure-handler
      (friend/authenticate {
                            :login-uri "/login"
                            :credential-fn (credential-fn)
                            :workflows [(workflows/interactive-form)
                                        (reg-workflow)]
                            })
      site

      ))

(defn -main [& args]
  (kit/run-server (reload/wrap-reload app) {:port 3000}))


