(ns basilica.components
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [basilica.conf :as conf]
   [basilica.autosize :refer [autosize]]
   [clojure.string :as string]
   [clojure.set :refer [select]]
   [cljs.core.async :as async :refer [chan close! put!]]))

(defn link-button [on-click text]
  (dom/a #js {:href "#"
              :className "button"
              :onClick #(do (on-click) false)}
         text))

(defn format [timestamp-string]
  (.fromNow (js/moment timestamp-string)))

(defn with-classes [keys & all]
  (clj->js (into keys {:className (string/join " " all)})))

(defn classes [& all]
  (apply with-classes {} all))

(defn key-down [on-submit]
  (fn [e]
    (let [textarea (. e -target)
          key (. e -which)
          command (or (. e -metaKey) (. e -ctrlKey))]
      (when command
        (when (= key 13)
          (let [value (. textarea -value)]
            (when-not (= value "")
              (on-submit value)
              (set! (. textarea -value) "")
              (autosize textarea)))
            )))))

(defn comment-component [on-submit owner]
  (reify
    om/IDidMount
    (did-mount [_] (.focus (om/get-node owner)))
    om/IRender
    (render
     [_]
     (dom/textarea (with-classes {:placeholder "⌘↵ to submit"
                                  :onChange #(autosize (. % -target))
                                  :onKeyDown (key-down on-submit)}
                     "comment")))))

(defn render-post-header [post]
  (dom/div (classes "header")
           (post :by)
           " "
           (format (post :at))
           " "
           (dom/a #js {:href (str conf/site-base "/" (post :id))} "link")))

(. js/marked setOptions #js {:sanitize true})

(defn markdown [text]
  (dom/div #js {:className "markdown"
                :dangerouslySetInnerHTML #js {:__html (js/marked text)}}))

(defn render-post-body [on-click post]
  (dom/div (classes "content")
           (markdown (post :content))
           (let [child-count (post :count)
                 text (if (= child-count 0) "comment" (str child-count " comments" ))]
             (link-button on-click text))))

(declare post-component)

(defn render-post-children [on-comment children all-posts]
  (let [build-child (fn [{id-child :id}]
                      (om/build post-component
                                [id-child all-posts]
                                {:react-key id-child}))]
    (apply dom/div (classes "children")
           (om/build comment-component on-comment)
           (map build-child children))))

(defn root-post-component [posts owner]
  (om/component
   (let [children (->> posts
                       (select (comp nil? :idParent))
                       (sort-by :id >))]
     (dom/div nil
              (dom/div #js {:id "header"}
                       (dom/a #js {:href conf/site-base}
                              (dom/h1 nil "Basilica")))

              (render-post-children #(put! (om/get-shared owner :comment-ch) {:post nil, :text %})
                                    children
                                    posts))
     )))

(defn post-component [[id-post posts] owner]
  (reify
    om/IInitState
    (init-state [_] {:expanded false})
    om/IRenderState
    (render-state
     [_ {:keys [expanded]}]
     (let [post (->> posts
                     (select #(= (% :id) id-post))
                     first)
           children (->> posts
                         (select #(= (% :idParent) id-post))
                         (sort-by :id >))]
       (dom/div (classes "post" (if expanded "expanded" "collapsed"))
                (render-post-header post)
                (render-post-body #(om/update-state! owner :expanded not) post)
                (if expanded
                  (render-post-children #(put! (om/get-shared owner :comment-ch) {:post post, :text %})
                                        children
                                        posts)))
       ))))
