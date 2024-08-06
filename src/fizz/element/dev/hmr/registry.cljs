(ns fizz.element.dev.hmr.registry
  (:require [clojure.set :as set]))

(defonce !registry
  "if passed a class name, returns:
  
  the original and current classes, and the observed attributes
  
  If passed the constructor, returns the class name"
  (atom {}))

(defn get-current-class
  [!registry class-name]
  (get-in @!registry [class-name :current-class]))

(defn get-original-class
  [!registry class-name]
  (get-in @!registry [class-name :original-class]))

(defn get-observed-attributes
  [!registry class-name]
  (get-in @!registry [class-name :observed-attributes]))

(defn get-untracked-attributes
  "Attributes added after initialization aren't tracked
  by the browser."
  [class-name !registry]
  (let [observed-attributes
        (get-observed-attributes class-name !registry)
        original-attributes
        (get-in @!registry [class-name :original-observed-attributes])]
    (set/difference (into #{} observed-attributes)
                    (into #{} original-attributes))))

(defn register-instance!
  "register an instance live in the DOM"
  ([class-name instance !registry]
   (swap! !registry update-in [class-name :instances]
          #(into #{instance} %))))

(defn deregister-instance!
  "register an instance live in the DOM"
  [class-name instance !registry]
  (swap! !registry update-in [class-name :instances]
         disj instance))

(defonce !pending-definition-queue
  #_"These are the instances whose constructors were
  run before the custom element was defined.

  Map of instance -> {:tag-name, :original-definition}"
  (atom {}))

(defn enqueue-undefined-instance!
  "An instance needs to be scheduled to be upgraded
  when its tag is defined."
  ([instance opts]
   (enqueue-undefined-instance!
    instance opts !pending-definition-queue))
  ([instance
    {tag-name :tag-name
     original-definition :original-definition
     :as opts}
    !pending-definition-queue]
   (assert instance)
   (assert tag-name)
   (assert original-definition)
   (swap! !pending-definition-queue assoc instance opts)))

(def !constructor-call-trick-slot
  (atom nil))

(defn set-constructor-call-trick-slot
  ([tag-name instance]
   (set-constructor-call-trick-slot
    !constructor-call-trick-slot tag-name instance))
  ([!slot tag-name instance]
   (swap! !slot
          (fn [slot]
            (when slot (throw (ex-info "Slot full!"
                                       {:slot slot})))
            [tag-name instance]))))

(defn pop-constructor-call-trick-slot
  ([]
   (pop-constructor-call-trick-slot
    !constructor-call-trick-slot))
  ([!slot]
   (let [v @!slot]
     (reset! !slot nil)
     v)))
