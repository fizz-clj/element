(ns fizz.element.dev.hmr-redux
  (:require [clojure.set :as set]))

(defonce original-define
  (.-define js/window.customElements))

(defn- html-set-attr
  "Call set attribute using the html prototype"
  [this attr-name attr-value]
  (.call (.-setAttribute (.-prototype js/HTMLElement))
         this attr-name attr-value))

(defn- html-remove-attr
  [this attr-name attr-value]
  (.call (.-removeAttribute (.-prototype js/HTMLElement))
         this attr-name attr-value))

(defn- html-get-attr
  [this attr-name attr-value]
  (.call (.getAttribute (.prototype js/HTMLElement))))

(defn- -patch-attributes!
  "The browser will only track the original observed
  attributes. We simulate attribute changed callbacks
  for newly added attributes."
  [!registry instance original-class untracked-attributes]
  (when (seq untracked-attributes)
    (let [set-attribute
          (fn set-attribute [attr-name attr-value]
            (if (untracked-attributes attr-name)))])

    (->> clj->js
         (js/Object.defineProperty instance))))


