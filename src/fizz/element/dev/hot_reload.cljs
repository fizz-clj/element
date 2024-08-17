(ns fizz.element.dev.hot-reload
  "The strategy is as follows:
  
  1. Custom Element Instances are proxied to the most
     recent version, stored in an atom.
  2. Override customElements.define so that on the first
     call, it registers the element in the customElement
     registry and our own registry. On the second call,
     we:

     a. Update the Proxy registry
     b. Check for new static properties, and add an event handler for them
     c. Hot reload existing instances
     "
  (:require [clojure.set :as set]
            [cljs.core :as c]))

(defonce original-define
  (.-define js/window.customElements))

(defonce class-proxies
  ;"A map of the class name to a proxy for that name."
  (atom {}))

(defn get-current-class
  ([class-name] (get-current-class class-name class-proxies))
  ([class-name !registry]
   (get-in !registry [class-name :current-class])))

(defn get-observed-attributes
  ([class-name]
   (get-observed-attributes class-name @class-proxies))
  ([class-name registry]
   (get-in registry [class-name :observed-attributes])))

(defn get-untracked-attributes
  "Attributes added after initialization aren't tracked
  by the browser."
  ([class-name]
   (get-untracked-attributes class-name @class-proxies))
  ([class-name registry]
   (let [observed-attributes
         (get-observed-attributes class-name registry)
         original-attributes
         (get-in registry [class-name :original-observed-attributes])]
     (set/difference (into #{} observed-attributes)
                     (into #{} original-attributes)))))

(defn- register-instance!
  "register an instance live in the DOM"
  [class-name instance !registry]
  (swap! !registry update-in [class-name :instances]
         #(into #{instance} %)))

(defn- deregister-instance!
  "register an instance live in the DOM"
  [class-name instance !registry]
  (swap! !registry update-in [class-name :instances]
         disj instance))

(defn init-class-proxies!
  ([class-name m]
   (init-class-proxies! class-name m class-proxies))
  ([class-name {:keys [observed-attributes] :as m} !registry]
   (assert (= #{:class-name
                :current-class
                :current-proxy
                :original-class
                :original-proxy
                :observed-attributes}
              (into #{} (keys m))))
   (let [proxies (assoc m
                        :original-observed-attributes
                        observed-attributes)]
     (swap! !registry
            (fn [class-proxies']
              (when (get class-proxies' class-name)
                (throw (ex-info "Class already registered"
                                {:class-name class-name})))
              (assoc class-proxies' class-name proxies))))))

(defn register-new-proxies!
  ([class-name m] (register-new-proxies! class-name m class-proxies))
  ([class-name m !registry]
   (assert (= #{:current-class :current-proxy :observed-attributes}
              (into #{} (keys m))))
   (swap! !registry
          (fn [class-proxies']
            (when-not (get class-proxies' class-name)
              (throw (ex-info "Class not registered!"
                              {:class-name class-name})))
            (update class-proxies' class-name merge m)))))

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
    "set"
    "connectedCallback"
    "disconnectedCallback"})

(defn- make-proxy-handler
  "`target` refers to an instance or a prototype"
  [class-name get-current-target !registry]
  (let [f (fn [method this & args]
            (cond
              (and (= method "get")
                   (= (first args) "prototype"))
              (apply (.-get js/Reflect) this (to-array args))

              ;; Capture the instances when they connect
              (and (= method "get")
                   (= (first args) "connectedCallback"))
              (this-as this
                       (fn []
                         (register-instance!
                          class-name
                          this
                          !registry)
                         (when-let [orig-cb (.-connectedCallback (get-current-target))]
                           (.apply orig-cb this (to-array args)))))

              ;; Remove the instances when they disconnect
              (and (= method "get")
                   (= (first args) "disconnectedCallback"))
              (this-as this
                       (fn []
                         (deregister-instance!
                          class-name
                          this
                          !registry)
                         (when-let [orig-cb (.-disconnectedCallback (get-current-target))]
                           (.apply orig-cb this (to-array args)))))

              :else
              (apply (.-get js/Reflect)
                     (get-current-target)
                     (to-array args))))]
    (reduce
     (fn [handler method]
       (assoc handler method (partial f method)))
     {}
     proxy-methods)))

(defn create-proxy
  "`original-target` should be an instance or a prototype."
  ([class-name original-target get-current-target]
   (create-proxy class-name original-target get-current-target class-proxies))
  ([class-name original-target get-current-target !registry]
   (js/Proxy. original-target
              (clj->js (make-proxy-handler class-name get-current-target !registry)))))

(defn replace-proto-with-proxy!
  "Point the prototype of a Class/constructor fn to
  the proxy for the original constructor fn"
  ([instance proxy]
   (js/Object.setPrototypeOf instance proxy)))

(defn initialize-class!
  "Create proxies and register the class"
  ([class-name class]
   (initialize-class! class-name class class-proxies))
  ([class-name class !registry]
   (let [class-proxy (create-proxy class-name class get-current-class)
         m {:class-name class-name
            :original-class class
            :current-class class
            :original-proxy class-proxy
            :observed-attributes (.-observedAttributes class)
            :current-proxy class-proxy}]
     (init-class-proxies! class-name m !registry))))

(defn update-class!
  "Create a proxy update the proxy and the class"
  ([class-name class]
   (update-class! class-name class class-proxies))
  ([class-name class !registry]
   (let [new-proxy (create-proxy class-name class get-current-class)]
     (register-new-proxies! class-name
                            {:current-proxy new-proxy
                             :current-class class}
                            !registry))))

(defn define-custom-element!
  "the function to replace customElements.define in development"
  [class-name class o]
  (if (js/customElements.get class-name)
    (update-class! class-name class)
    (do (original-define class-name class o)
        (initialize-class! class-name class))))
        ;; TODO hot reload here!

(defn hot-reload-on!
  []
  (set! (.-define js/CustomElements)
        define-custom-element!))

