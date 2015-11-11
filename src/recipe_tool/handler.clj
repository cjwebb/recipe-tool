(ns recipe-tool.handler
  (:require [compojure.core :refer :all]
            [ring.util.response :as response]
            [compojure.route :as route]
            [cheshire.core :refer :all]
            [taoensso.carmine :as redis :refer (wcar)]
            [hiccup.core :refer :all]
            [hiccup.form :refer [hidden-field]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

; ------------- data -----------
(defmacro wcar* [& body] `(redis/wcar nil ~@body))

(def recipes
  (parsed-seq (clojure.java.io/reader "/kickstand/munchcal/recipe-tool/nice.json")))

(defn init! 
  "Read all the recipes and put into a 'todo' set"
  []
  (doseq [x recipes]
    (wcar* (redis/sadd "todo" x))))

(defn get-a-recipe []
  (wcar* (redis/spop "todo")))

; ---------- handlers -----------
(defn show-recipe []
  (let [recipe (clojure.walk/keywordize-keys (get-a-recipe))
        remaining (wcar* (redis/scard "todo"))
        btn-style "width:100px;height:100px"]
    (html
      [:div
       [:p remaining]
       [:h1 [:a {:href (:url recipe)} (:name recipe)]]
       [:p (:id recipe)]
       [:p (:description recipe)]
       [:img {:src (:image recipe)}]
       [:p (clojure.string/join ", " (:parsed_ingredients recipe))]
       [:p (clojure.string/join ", " (:ingredients recipe))]]
      [:form {:method "post"}
      (hidden-field :recipe-id (:id recipe))
      [:button {:type "submit" :name "tag" :value "starter" :style btn-style} "Starter"]
      [:button {:type "submit" :name "tag" :value "main" :style btn-style} "Main Meal"]
      [:button {:type "submit" :name "tag" :value "side" :style btn-style} "Side"]
      [:button {:type "submit" :name "tag" :value "dessert" :style btn-style} "Dessert"]
      [:button {:type "submit" :name "tag" :value "discard" :style btn-style} "Discard"]])))

(defn handle-recipe [req]
  (let [recipe-id (get-in req [:params :recipe-id])
        tag (get-in req [:params :tag])]
    (do 
      (wcar* (redis/sadd tag recipe-id))
      (println req)
      (response/redirect "/"))))

; --------- app --------------
(defroutes app-routes
  (GET "/" [] (show-recipe))
  (POST "/" req (handle-recipe req))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes (assoc-in site-defaults [:security :anti-forgery] false)))
