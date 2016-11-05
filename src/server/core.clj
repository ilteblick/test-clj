(ns server.core
  (:require [compojure.route :as route]
            [clojure.java.io :as io])
  (:use compojure.core
        compojure.handler
        ring.middleware.edn
        carica.core
        korma.db
        korma.core
        ring.util.json-response
        ))

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


(defroutes compojure-handler
           (GET "/" [] (slurp (io/resource "public/html/index.html")))
           (GET "/req" request (json-response {:foo "bar"}))
           (GET "/getall" [] ( article-list))
           (GET "/getbyid/:id" [id] (articleOne id) )
           (GET "/update/:id/:data" [id data]
             (updateArticle id data)
              (articleOne id))
           (GET "/delete/:id" [id]
              (deleteArticle id)
              (str 200))
           (GET "/insert/:data" [data]
             (insertArticle data)
             (str 200))
           (route/resources "/")
           (route/files "/" {:root (config :external-resources)})
           (route/not-found "Not found!"))


(def app
  (-> compojure-handler
      site
      ))
