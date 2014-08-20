(ns basilica.utils
  (:require [basilica.conf :as conf]
            [clojure.string :as string]))

(defn url [host path & components]
  (->> (concat [host] path components)
       (remove nil?)
       (string/join "/")))

(def api-url (partial url conf/api-host conf/api-path))
(def site-url (partial url conf/site-host conf/site-path))
(def ws-url (partial url conf/ws-host conf/ws-path))

(def site-hist-prefix
  (if (= conf/site-base [])
    "/"
    (string/join "/" (concat [""] conf/site-base [""]))))

(enable-console-print!)

(defn logger [area & msg]
  (apply print (str area) msg))

(defn with-classes [keys & all]
  (clj->js (into keys {:className (string/join " " all)})))

(defn classes [& all]
  (apply with-classes {} all))
