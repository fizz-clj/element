(ns fizz.element.dev.hmr
  "Handle hot module reload")

(defonce original-define
  (.-define js/window.customElements))

(comment
  ;; Call once, we're good. Twice it should throw.
  (js/customElements.define "my-element"
                            (fn [] (js/HTMLElement.))))

(defn redefine-custom-elements
  "Apply the original define iff it hasn't already been defined"
  []
  (set! (.-define js/customElements)
        (fn [name & rest]
          (if-not (.get js/customElements name)
            (do (js/console.log (str "Defining for the first time,"
                                     " using original-define: "
                                     name))
                (.apply original-define js/customElements
                        (clj->js (cons name rest))))
            (js/console.log (str "not defining using original define: "
                                 name))))))

(comment
  (redefine-custom-elements)

  ;; Call to your heart's content
  (js/customElements.define "my-element"
                            (fn [] (js/HTMLElement.))))

(defn revert-to-original-define
  []
  (set! (.-define js/customElements) original-define))

(comment
  (revert-to-original-define)
  ;; Should throw
  (js/customElements.define "my-element"
                            (fn [] (js/HTMLElement.)))
  (redefine-custom-elements))

(defonce proxies-for-keys (atom {}))
(defonce class->key (atom {}))

(comment
  (swap! class->key assoc
         js/Number :number)
  (get @class->key js/Number))

(defn track-connected-elements

  ([hmr-class]
   (track-connected-elements hmr-class (atom #{})))

  ([hmr-class connected-elements]
   (let [original-cb (.-connectedCallback (.-prototype hmr-class))
         original-dcb (.-disconnectedCallback (.-prototype hmr-class))]
     (js/console.log "defined original-cb and original-dcb respectively"
                     original-cb original-dcb)

     (js/console.log "Old connectedCallback:"
                     (js/Object.getOwnPropertyDescriptor (.-prototype hmr-class) "connectedCallback"))
     (js/console.log "Old disconnectedCallback:"
                     (js/Object.getOwnPropertyDescriptor (.-prototype hmr-class) "disconnectedCallback"))

     (set! (.-connectedCallback (.-prototype hmr-class))
           (fn [& args]
             (js/console.log "In new connected callback")
             (this-as this
                      (when original-cb
                        (js/console.log "Applying original callback")
                        (.apply original-cb this (clj->js args)))
                      (js/console.log "Adding this to connected elements")
                      (swap! connected-elements conj this)
                      (js/console.log "Connected elements added"
                                      (clj->js @connected-elements)))))

     (js/console.log (str "Set the connected callback."))

     (set! (.-disconnectedCallback (.-prototype hmr-class))
           (fn [& args]
             (js/console.log "In disconnected callback")
             (this-as this
                      (when original-dcb
                        (js/console.log "Applying original disconnected callback")
                        (.apply original-dcb this (clj->js args)))
                      (js/console.log "removing this from connected-elements")
                      (swap! connected-elements disj this))))

     (js/console.log "Set the disconnected callback")
     (js/console.log "New connectedCallback:"
                     (js/Object.getOwnPropertyDescriptor (.-prototype hmr-class) "connectedCallback"))
     (js/console.log "New disconnectedCallback:"
                     (js/Object.getOwnPropertyDescriptor (.-prototype hmr-class) "disconnectedCallback"))

     connected-elements)))

(comment
  (defn make-component
    []
    (let [component
          (fn component []
            (js/Reflect.construct js/HTMLElement #js [] component))]

      (set! (.-prototype component)
            (js/Object.create (.-prototype js/HTMLElement)
                              #js {:connectedCallback
                                   #js {:configurable true
                                        :writable true
                                        :value
                                        (fn []
                                          (this-as this
                                                   (js/console.log "connected" this)))}

                                   :disconnectedCallback
                                   #js {:configurable true
                                        :writable true
                                        :value
                                        (fn []
                                          (this-as this
                                                   (js/console.log "disconnectedCallback" this)))}}))
      component))

  (def MyElement (make-component))

  (def connected-elements* (atom #{}))
  (def connected-elements
    (track-connected-elements MyElement connected-elements*))

  @connected-elements*

  (.connectedCallback (.-prototype MyElement))

  ;; Should see it's being tracked.
  @connected-elements*)

(def proxy-methods
  #{"construct"
    "defineProperty"
    "deleteProperty"
    "getOwnPropertyDescriptor"
    "getPrototypeOf"
    "setPrototypeOf"
    "isExtensible"
    "ownKeys"
    "preventExtensions"
    "has"
    "get"
    "set"})

(defn make-proxy-handler
  [get-current-target]
  (reduce
   (fn [handler method]
     (assoc handler method
            (fn [this & args]
              (js/console.log "In proxy handler with method and args"
                              method (clj->js args))
              (if (and (= method "get") (= (first args) "prototype"))
                ;; prototype must always return original target value
                (apply (.-get js/Reflect) this (to-array args))
                (apply (.-get js/Reflect) (get-current-target) (to-array args))))))
   {}
   proxy-methods))

(comment

  (defn create-original-target []
    (js/Object.create js/HTMLElement
                      (clj->js {"name" {:value "original", :writable true},
                                "getName" {:value (fn [] (this-as this (.-name this))), :writable true}})))

  (defn create-updated-target []
    (js-obj "name" "updated"
            "getName" (fn [] (this-as this (.-name this)))))

  (.getName (create-original-target)) ;; -> "original"
  (.-name (create-original-target))   ;; -> "original"
  (.-prototype (create-original-target)) ;; js/HTMLElement
  (.-prototype (create-updated-target)) ;; -> nil

  (def updated-target (create-updated-target))

  (.getName updated-target) ;; -> "updated"

  (def current-target (atom (create-original-target)))

  (defn get-current-target []
    @current-target)

  (.-name (get-current-target)) ;; -> "original"

  (def handler (make-proxy-handler get-current-target))

   ;; Create the proxy manually for testing the handler
  (def original-target (create-original-target))

  (def proxy (js/Proxy. original-target (clj->js handler)))

  ;; Test accessing properties through the proxy
  (.getName proxy) ;; -> Should log "original"
  (.-prototype proxy) ;; Should return the original target's prototype

  ;; Update
  (reset! current-target updated-target)
  (.getName proxy) ;; -> updated 

  ;; Test accessing the prototype through the proxy
  (.-prototype proxy)) ;; Should return the original target's prototype

(defn create-proxy [original-target get-current-target]
  (js/Proxy. original-target (clj->js (make-proxy-handler get-current-target))))

(defn replace-prototypes-with-proxies
  "Replace all the protypes in the inheritance chain with proxies"
  [instance & [{:keys [on-set-prototype]
                :or {on-set-prototype identity}}]]
  (js/console.log "Starting in replace protype")
  (letfn [(create-proxy-handler [proto key]
            (js/console.log "hit create proxy handler")
            (let [get-current-proto
                  (fn [] (.. (get @proxies-for-keys key) -currentClass -prototype))]
              (create-proxy proto get-current-proto)))]
    (loop [previous instance
           proto (js/Object.getPrototypeOf instance)]
      (js/console.log "entered loop with :instance :previous :prototype"
                      instance previous proto)
      (when (and proto (not= (.-constructor proto) js/HTMLElement))
        (let [key (get @class->key (.-constructor proto))]
          (js/console.log "Looked up constructor for :proto using found:"
                          proto (.-constructor proto) key)
          (when key
            (on-set-prototype {:key key
                               :proto proto
                               :instance instance
                               :previous previous})
            (js/Object.setPrototypeOf previous (create-proxy-handler proto key))))
        (recur proto (js/Object.getPrototypeOf proto))))))

(comment
  (defn create-custom-el []
    (let [component (fn component []
                      (js/Reflect.construct js/HTMLElement #js [] component))]
      (set! (.-prototype component)
            (js/Object.create (.-prototype js/HTMLElement)
                              #js {}))
      component))

  (def Base (create-custom-el))
  (def Derived (js/Object.create js/Object #js {}))
  (js/Object.setPrototypeOf Derived)

  (def el-name (str "c-" (rand-int 10000)))

  (js/customElements.define el-name Base)
  (def el (js/document.createElement el-name))

  (js/document.body.appendChild el)

  (def a* (atom nil))

  ;; TODO - this part doesn't really work without the injection of HMR
  ;; in the prototype chain.
  (replace-prototypes-with-proxies el {:on-set-prototype
                                       (fn [m]
                                         (js/console.log "set prototype!!!"
                                                         (clj->js m))
                                         (reset! a* m))}))

(comment
  ;; A bit of explanation on class creation
  ;; example with js/Map
  (defn make-extended-map [constructor-callback]
    (let [constructor (fn constructor []
                        ;; Call the parent class constructor
                        (let [ret (js/Reflect.construct js/Map #js [] constructor)]
                          (this-as this
                                   (constructor-callback this))
                          ret))]
      ;; Set the prototype chain correctly
      (set! (.-prototype constructor)
            (js/Object.create (.-prototype js/Map)
                              #js {:constructor (clj->js {:value constructor :writable true :configurable true})}))
      constructor))

  (def ExtendedMap (make-extended-map #(js/console.log "Constructor running" %)))

  (def map-instance (js/Reflect.construct ExtendedMap #js [] ExtendedMap))

  (instance? js/Map map-instance)

  (.-constructor map-instance)

  (js/Object.getPrototypeOf map-instance))

(defn web-component-hmr-constructor-callback [this]
  (let [key (get @class->key (.-constructor this))]
    ;; Check if the constructor is registered
    (js/console.log "In web-component-hmr-constructor-callback")
    (when key
      (js/console.log "found key for class:" key)
      (let [p (get @proxies-for-keys key)]
        ;; Replace the constructor with a proxy that references the latest implementation of this class
        (js/console.log "p is" p)
        (set! (.-constructor this) (.-currentProxy p))))
    ;; Replace prototype chain with a proxy to the latest prototype implementation
    (replace-prototypes-with-proxies this)))

(defn make-web-component-hmr [& [constructor-callback]]
  (let [constructor-callback' (or constructor-callback web-component-hmr-constructor-callback)
        constructor (fn constructor []
                      ;; Call the parent class constructor
                      (js/console.log "In make-web-component-hmr constructor")
                      (let [ret (js/Reflect.construct js/HTMLElement #js [] constructor)]
                        (js/console.log "reflect call made")
                        (this-as this (constructor-callback' this))
                        (js/console.log "Constructor callback complete")
                        ret))]

    ;; Set the prototype chain correctly
    (set! (.-prototype constructor)
          (js/Object.create (.-prototype js/HTMLElement) #js {}))
    constructor))

;; does this work? Maybe this is the solution: https://stackoverflow.com/questions/47779762/proxy-a-webcomponents-constructor-that-extends-htmlelement

(def WebComponentHmr (make-web-component-hmr))

(comment
  (def wc-name "web-component-hmr6")

  (js/customElements.define wc-name WebComponentHmr)

  (def hmr-instance (js/document.createElement wc-name))

  (instance? js/HTMLElement hmr-instance)
  (instance? WebComponentHmr hmr-instance))

(defn inject-inherits-hmr-class
  "Define the function to inject WebComponentHmr into the inheritance chain"
  [class]

  ;; Walk prototypes until we reach HTMLElement
  (loop [parent class
         proto (js/Object.getPrototypeOf class)]
    (if (and proto (not= proto js/HTMLElement))
      (recur proto (js/Object.getPrototypeOf proto))
      ;; Check if we've reached HTMLElement and it's not already inheriting from WebComponentHmr
      (when (and (= proto js/HTMLElement)
                 (not= parent WebComponentHmr))
        ;; Inject WebComponentHmr into the inheritance chain
        (js/Object.setPrototypeOf parent WebComponentHmr)))))

(defn register-new-class!
  [class-key class proxy]
  (let [_ (inject-inherits-hmr-class class)
        ;; Track connected elements for this class
        connected-elements (track-connected-elements class)]
          ;; Store registration info in proxies-for-keys atom
    (swap! proxies-for-keys assoc class-key
           {:original-proxy proxy
            :current-proxy proxy
            :original-class class
            :current-class class
            :connected-elements connected-elements})
    (swap! class->key assoc class class-key)
    proxy))

(defn re-register-proxies!
  [class-key class current-proxy]
  (swap! proxies-for-keys update class-key merge
         {:current-class class
          :current-proxy current-proxy}))

(defn hmr-callback
  [class-key class new-proxy {:keys [connected-elements
                                     current-proxy]}]
  (.then (js/Promise.resolve)
         #(do (if-let [element-callback (.-hotReplacedCallback class)]
                (try
                  (js/console.log (str "Found a callback for " class-key ". Calling..."))
                  (element-callback)
                  (js/console.log (str "Callback called for " class-key))
                  (catch js/Error e
                    (js/console.error e)))
                (js/console.log (str "No callback for " class-key)))
              (doseq [el @connected-elements] ;; not seqqable
                (if true ;; (= current-proxy (.-constructor el)) does it matter?
                  (do (js/console.log "Replacing the connected element's construtor with the new proxy" class-key el)
                      (set! (.-constructor el) new-proxy))
                  (js/console.log "Skipping replacing constructor, because the element's constructor is not the current proxy"
                                  class-key el current-proxy
                                  (.-constructor el)))
                (if (.-hotReplacedCallback el)
                  (do (js/console.log "Calling the hot replaced callback for " class-key el)
                      (.hotReplacedCallback el))
                  (js/console.log "no hot repalced callback found" class-key el))))))

(comment
  (.then (js/Promise.resolve)
         #(println "hi")))

(defn register! [name class]
  (let [key (keyword name)
        ;; Check if this class was already registered
        existing-proxies (get @proxies-for-keys key)
        new-proxy (create-proxy class #(-> (get @proxies-for-keys key) :current-class))]
    (if-not existing-proxies
      (register-new-class! key class new-proxy) ;; No HMR Required

      ;; Subsequent registrations (HMR updates)
      ;; Update existing registration to use the new class and proxy
      (do (re-register-proxies! key class new-proxy)

          ;; Run the HMR callback
          (hmr-callback key class new-proxy existing-proxies)))

         ;; Return the new proxy
    new-proxy))

(comment
  (def el-name "custom-component-9")

  (defn create-custom-el []
    (let [component (fn component []
                      (js/Reflect.construct js/HTMLElement #js [] component))]
      (set! (.-prototype component)
            (js/Object.create (.-prototype js/HTMLElement)
                              #js {:connectedCallback
                                   #js {:configurable true
                                        :writable true
                                        :value
                                        (fn []
                                          (this-as this
                                                   (js/console.log "original connected" this)))}

                                   :disconnectedCallback
                                   #js {:configurable true
                                        :writable true
                                        :value
                                        (fn []
                                          (this-as this
                                                   (js/console.log "original disconnectedCallback" this)))}}))
      component))

  (def MyCustomWebComponent (create-custom-el))

  (def el-proxy (register! el-name MyCustomWebComponent))

  (js/customElements.define el-name MyCustomWebComponent)

  (def el-instance (js/document.createElement el-name))
  (.connectedCallback el-instance)

  (instance? js/HTMLElement el-instance)
  (instance? MyCustomWebComponent el-instance)
  (js/document.body.appendChild el-instance))

