(ns fizz.element.dev.hmr.constructor
  "There are two pieces here:
  
  What happens when we REGISTER an element with its
  constructor, and
  
  What happens when we INSTANTIATE that element.
  
  The constructor call trick involves ensuring that,
  when the the constructor runs, the CURRENT instance
  is returned."
  (:require [fizz.element.dev.hmr.patch :as patch]))

(defn make
  "Make a constructor."
  [parent-class constructor-cb proto-methods]
  (let [constructor
        (fn constructor [& constructor-args]
          (let [ret (js/Reflect.construct parent-class
                                          #js []
                                          constructor)]
            (this-as this (constructor-cb this
                                          constructor-args))
            ret))

        prototype
        (js/Object.create (.-prototype parent-class) proto-methods)

        _ (set! (.-prototype constructor) prototype)]

    constructor))

(defn set-prototype-to-latest!
  [tag-name instance {:keys [tag->latest-constructor]}]
  (->> (tag->latest-constructor tag-name)
       (.-prototype)
       (js/Reflect.setPrototypeOf instance)))

(defn internal-upgrade!
  "Called by the pivot constructor in order to:

    1. Set the protoype to the latest constructor's prototype
    2. Register the element's definition. (skip? we're looking up by tag)
    3. Patch the instance's attributes
    4. Register the instance as being in the upgrade process
       using the constructor call trick
    5. Calling any attributeChanged for any new attributes
       not tracked by the browser
    6. Maybe calling the disconnected call?"
  [tag-name instance {:keys [tag->latest-constructor
                             get-untracked-attributes
                             set-constructor-trick-slot!]
                      :as m}]
  (let [latest-constructor (tag->latest-constructor tag-name)]
    (assert latest-constructor)
    (set-prototype-to-latest! tag-name instance m)
    ;; but has the class diff been set yet?
    ;; how does it know what the current attributes are?
    ;; call register-instance?
    #_(patch/attributes! instance (get-untracked-attributes tag-name))
    (set-constructor-trick-slot! tag-name instance)
    (js/Reflect.construct latest-constructor #js [])))

(defn make-pivot-constructor-cb
  "Make a constructor callback to run in the
  pivot constructor.
  
  enqueue-instance handles the case where we have an
  instance but no definition for that tag yet. This
  is distict from the constructor-trick instances"
  [original-definition tag-name]
  (fn [instance ;; this
       {:keys [tag->definition
               upgrade-instance!
               enqueue-instance!
               patch-prototype!]}]
    (if-let [tag-definition
             (tag->definition tag-name)]
      (upgrade-instance! instance
                         {:tag-name tag-name
                          :original-definition
                          original-definition
                          :current-definition
                          tag-definition})
      (do (enqueue-instance! instance
                             {:tag-name tag-name
                              :original-definition
                              original-definition})
          (patch-prototype! instance)))))

(defn- invoke-method-on
  [class method instance & [args]]
  (when class
    (when-let [method (-> class
                          (.-prototype)
                          (js/Reflect.get method))]
      (->> (into [instance] args)
           (clj->js)
           (js/Reflect.apply method instance)))))

(comment
  (let [a #js [1 2 3]]
    (invoke-method-on js/Array "push" a [4])
    a))

(defn make-methods
  [tag-name
   {:keys [tag->current-class
           enqueue-awaiting-upgrade
           dequeue-awaiting-upgrade]}]
  (let [connected-callback
        (fn []
          (this-as this
                   (if-let [current-class
                            (tag->current-class tag-name)]
                     (invoke-method-on current-class "connectedCallback" this)
                     (enqueue-awaiting-upgrade tag-name this))))

        disconnected-callback
        (fn []
          (this-as this
                   (if-let [current-class
                            (tag->current-class tag-name)]
                     (invoke-method-on current-class "disconnectedCallback" this)
                     (dequeue-awaiting-upgrade tag-name this))))

        adopted-callback
        (fn [& args]
          (this-as this
                   (-> tag-name
                       (tag->current-class)
                       (invoke-method-on "adoptedCallback" this args))))

        form-associated-callback
        (fn [& args]
          (this-as this
                   (-> tag-name
                       (tag->current-class)
                       (invoke-method-on "formAssociatedCallback" this args))))

        form-disabled-callback
        (fn [& args]
          (this-as this
                   (-> tag-name
                       (tag->current-class)
                       (invoke-method-on "formDisabledCallback" this args))))
        form-reset-callback
        (fn [& args]
          (this-as this
                   (-> tag-name
                       (tag->current-class)
                       (invoke-method-on "formResetCallback" this args))))

        form-state-restore-callback
        (fn [& args]
          (this-as this
                   (-> tag-name
                       (tag->current-class)
                       (invoke-method-on "formStateRestoreCallback" this args))))]

    ;; need attribute changed callback!
    {:connectedCallback connected-callback
     :disconnectedCallback disconnected-callback
     :adoptedCallback adopted-callback
     :formAssociatedCallback form-associated-callback
     :formDisabledCallback form-disabled-callback
     :formResetCallback form-reset-callback
     :formStateRestoreCallback form-state-restore-callback}))

(defn make-patched-html-constructor
  "Make a matched version of HTMLElement"
  [{:keys [HTMLElement]
    :or {HTMLElement js/HTMLElement}}]
  (fn HTMLElement
    [this]
    (when-not (instance? HTMLElement this)
      (throw (ex-info "Please use the new operator"
                      {:this this :HTMLElement HTMLElement})))
    (when (identical? HTMLElement this)
      (throw (ex-info "Illegal Constructor"
                      {:this this :HTMLElement HTMLElement})))))

(defn create-pivot-class
  [m tag-name])
  ;; TODO need to 
  ;;  1. Use the callback proto methods above
  ;;  2. create the patched html prototype.
  ;;  3. Use the patched html protype with the constructor at step 2
