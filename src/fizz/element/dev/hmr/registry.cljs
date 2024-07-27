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

