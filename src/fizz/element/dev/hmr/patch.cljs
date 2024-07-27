(ns fizz.element.dev.hmr.patch
  "Patch attributes on HTML elements.")

(defn html-set-attr
  "Call set attribute using the html prototype"
  [this attr-name attr-value]
  (.call (.-setAttribute (.-prototype js/HTMLElement))
         this attr-name attr-value))

(defn html-remove-attr
  [this attr-name]
  (.call (.-removeAttribute (.-prototype js/HTMLElement))
         this attr-name))

(defn html-get-attr
  [this attr-name]
  (.call (.-getAttribute (.-prototype js/HTMLElement))
         this attr-name))

(defn make-set-attr
  [instance untracked-attributes]
  (fn set-attr [attr-name attr-value]
    (when (get untracked-attributes attr-name)
      (let [old-value (html-get-attr instance attr-name)]
        (.call (.-attributeChangedCallback instance)
               instance attr-name old-value attr-value)))
    (html-set-attr instance attr-name attr-value)))

(defn make-remove-attr
  [instance untracked-attributes]
  (fn remove-attr [attr-name]
    (when (get untracked-attributes attr-name)
      (let [old-value (html-get-attr instance attr-name)]
        (.call (.-attributeChangedCallback instance)
               instance attr-name old-value nil)))
    (html-remove-attr instance attr-name)))

(defn attributes!
  "The browser will only track the original observed
 attributes. We simulate attribute changed callbacks
 for newly added attributes."
  [instance untracked-attributes]
  (when (and (seq untracked-attributes)
             (.-attributeChangedCallback instance))
    (->> {:setAttribute {:writable true
                         :configurable true
                         :enumerable true
                         :value
                         (make-set-attr instance
                                        untracked-attributes)}
          :removeAttribute {:writable true
                            :configurable true
                            :enumerable true
                            :value
                            (make-remove-attr instance
                                              untracked-attributes)}}
         clj->js
         (js/Object.defineProperties instance))))

