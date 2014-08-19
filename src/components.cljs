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

(defn clear [textarea]
  (set! (. textarea -value) "")
  (autosize textarea))

(defn key-down [on-submit]
  (fn [e]
    (let [textarea (. e -target)
          key (. e -which)
          command (or (. e -metaKey) (. e -ctrlKey))]
      (when command
        (when (= key 13)
          (on-submit (. textarea -value))
          (clear textarea)))
      )))

(defn mouse-down [on-submit get-textarea]
  (fn [e]
    (let [textarea (get-textarea)]
      (on-submit (. textarea -value))
      (clear textarea)
      (.focus textarea))))

(defn comment-component [on-submit owner]
  (reify
    om/IDidMount
    (did-mount [_] (.focus (om/get-node owner "input")))
    om/IRender
    (render
     [_]
     (dom/div (classes "add-post")
              (dom/textarea #js {:placeholder "⌘↵ to submit"
                                 :ref "input"
                                 :onChange #(autosize (. % -target))
                                 :onKeyDown (key-down on-submit)})
              (dom/button #js {:onClick (mouse-down on-submit #(om/get-node owner "input"))}))
     )))

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

(defn make-submit-handler [owner post]
  (fn [text]
    (when-not (= text "")
      (put! (om/get-shared owner :comment-ch) {:post post, :text text}))))

(defn root-post-component [posts owner]
  (om/component
   (let [children (->> posts
                       (select (comp nil? :idParent))
                       (sort-by :id >))]
     (render-post-children (make-submit-handler owner nil)
                           children
                           posts)
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
                  (render-post-children (make-submit-handler owner post)
                                        children
                                        posts)))
       ))))

(def status-titles {:disconnected "A little weather. Waiting for the clouds to pass..."
                    :connected "It's a sunny day in websocket land!"
                    :error "We cannot see Basilica through this storm. Trying again soon..."})

(defn header-component [socket-state owner]
  (om/component
   (dom/div #js {:id "header"}
            (dom/h1 nil (dom/a #js {:href conf/site-base} "Basilica"))
            (dom/div (with-classes {:id "socket-status"
                                    :onTouchStart (fn [e]) ; allows the hover state to work on mobile safari
                                    :title (status-titles socket-state)}
                       (name socket-state))
                     (dom/div (classes "tooltip")
                              (status-titles socket-state))))))
