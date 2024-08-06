(ns fizz.element.dev.hmr.constructor-test
  (:require [cljs.test :refer [testing is deftest]]
            [fizz.element.dev.hmr.constructor :as ctor]))

(deftest test-make-constructor
  (let [!a (atom [])

        constructor-cb (fn [this] (swap! !a conj this))

        proto-methods (clj->js
                       {:foo {:value "bar"
                              :configurable true
                              :enumerable true
                              :writable true}})

        ctor (ctor/make js/Array constructor-cb proto-methods)
        instance (js/Reflect.construct ctor #js [])]

    (is (= "bar" (.-foo instance)))
    (is (= 1 (count @!a)))
    (is (instance? js/Array (first @!a)))
    (is (instance? js/Array instance))))

(deftest test-make-proxy-constructor-defined-component
  (testing "Given that the tag has already been defined
    When the constructor runs
    Then the internal-upgrade function is called
    And the instance is upgraded"
    (let [tag->definition
          {"my-custom-element" :current-definition}

          !a (atom [])

          upgrade-instance
          (fn [& args] (swap! !a conj args))

          cb (ctor/make-pivot-constructor-cb
              :original-definition
              "my-custom-element")

          _ (cb :this
                {:upgrade-instance! upgrade-instance
                 :tag->definition tag->definition})]
      (is (= [[:this {:tag-name "my-custom-element"
                      :original-definition
                      :original-definition
                      :current-definition
                      :current-definition}]]
             @!a)))))

(deftest test-make-proxy-constructor-undefined-component
  (testing "Given that the tag has not already been defined
    When the constructor runs
    Then the instance is enqueued so that it will be upgrade when the element is defined
    And the instance is patched so that its prototype points to our modified HTMLElement prototype."
    (let [tag->definition {}

          !upgrade-args (atom [])

          upgrade-instance
          (fn [& args] (swap! !upgrade-args conj args))

          !queue (atom {})

          enqueue-instance!
          (fn [instance opts]
            (swap! !queue assoc instance opts))

          !patch-prototype-args (atom [])

          patch-prototype!
          (fn [instance] (swap! !patch-prototype-args
                                conj instance))

          cb (ctor/make-pivot-constructor-cb
              :original-definition
              "my-custom-element")

          _ (cb :this
                {:upgrade-instance! upgrade-instance
                 :tag->definition tag->definition
                 :patch-prototype! patch-prototype!
                 :enqueue-instance! enqueue-instance!})]

      (is (= [] @!upgrade-args)
          "The upgrade instance callback should not be called")
      (is (= {:this {:tag-name "my-custom-element"
                     :original-definition
                     :original-definition}}
             @!queue))
      (is (= [:this]
             @!patch-prototype-args)))))

(deftest test-make-proto-methods-connected-callback-when-defined
  (testing "Given that a custom element has been defined
    When we create its methods
    And we call its connected callback
    Then the connected callback from the current class is called"
    (let [!a (atom [])

          proto-methods
          (clj->js
           {:connectedCallback
            {:value (fn [& _args]
                      (swap! !a conj :called!))
             :configurable true
             :enumerable true
             :writable true}})

          ctor (ctor/make js/Array
                          (constantly nil)
                          proto-methods)

          methods
          (ctor/make-methods
           "c-el" {:tag->current-class {"c-el" ctor}})
          _ ((:connectedCallback methods))]
      (is (= [:called!] @!a)))))

(deftest test-make-proto-methods-connected-callback-when-notdefined
  (testing "Given that a custom element has not yet been defined
    When we create its methods
    And we call its connected callback
    Then it is put in the awaiting upgrade queue"
    (let [!a (atom [])

          enqueue-awaiting-upgrade
          (fn [& args]
            (swap! !a conj args))

          methods
          (ctor/make-methods
           "c-el" {:tag->current-class {}
                   :enqueue-awaiting-upgrade
                   enqueue-awaiting-upgrade})
          _ ((:connectedCallback methods))]

      (is (= 1 (count @!a)))
      (is (= 2 (count (first @!a))))
      (is (= "c-el" (ffirst @!a))))))

(deftest test-make-proto-methods-disconnected-callback-when-defined
  (testing "Given that a custom element has been defined
    When we create its methods
    And we call its disconnected callback
    Then the disconnected callback from the current class is called"
    (let [!a (atom [])

          proto-methods
          (clj->js
           {:disconnectedCallback
            {:value (fn [& _args]
                      (swap! !a conj :called!))
             :configurable true
             :enumerable true
             :writable true}})

          ctor (ctor/make js/Array
                          (constantly nil)
                          proto-methods)

          methods
          (ctor/make-methods
           "c-el" {:tag->current-class {"c-el" ctor}})
          _ ((:disconnectedCallback methods))]
      (is (= [:called!] @!a)))))

(deftest test-make-proto-methods-disconnected-callback-when-notdefined
  (testing "Given that a custom element has not yet been defined
    When we create its methods
    And we call its disconnected callback
    Then it is removed from the awaiting upgrade queue"
    (let [!a (atom [])

          dequeue-awaiting-upgrade
          (fn [& args]
            (swap! !a conj args))

          methods
          (ctor/make-methods
           "c-el" {:tag->current-class {}
                   :dequeue-awaiting-upgrade
                   dequeue-awaiting-upgrade})
          _ ((:disconnectedCallback methods))]

      (is (= 1 (count @!a)))
      (is (= 2 (count (first @!a))))
      (is (= "c-el" (ffirst @!a))))))

(deftest test-make-proto-methods-form-associated-callback
  (testing "Given that a custom element has been defined
    When we create its methods
    And we call its form associated callback
    Then the form associated callback from the current class is called
    And arguments are passed to that callback"
    (let [!a (atom [])

          proto-methods
          (clj->js
           {:formAssociatedCallback
            {:value (fn [& args]
                      (swap! !a conj args))
             :configurable true
             :enumerable true
             :writable true}})

          ctor (ctor/make js/Array
                          (constantly nil)
                          proto-methods)

          methods
          (ctor/make-methods
           "c-el" {:tag->current-class {"c-el" ctor}})
          _ ((:formAssociatedCallback methods)
             :a :b :c)]
      (is (= 1 (count @!a)))
      (is (= 4 (count (first @!a))))
      (is (= ["a" "b" "c"] (rest (first @!a)))))))

(deftest test-other-proto-methods
  (let [simple-methods
        [:adoptedCallback
         :formAssociatedCallback
         :formDisabledCallback
         :formResetCallback
         :formStateRestoreCallback]
        !a (atom [])

        make-proto-method
        (fn [method]
          {:value
           (fn [& args]
             (swap! !a conj [method args]))
           :configurable true
           :writable true
           :enumerable true})

        proto-methods
        (reduce
         (fn [acc method]
           (assoc acc method
                  (make-proto-method method)))
         {}
         simple-methods)

        ctor (ctor/make js/Array
                        (constantly nil)
                        (clj->js proto-methods))

        tag->current-class
        {"c-el" ctor}

        methods (ctor/make-methods
                 "c-el"
                 {:tag->current-class
                  tag->current-class})]

    (doseq [m simple-methods]
      ((get methods m) m :a :b :c))

    (is (= (count simple-methods)
           (count @!a)))
    (is (= simple-methods
           (mapv first @!a)))
    (is (= (mapv name simple-methods)
           (->> @!a
                (map second)
                (map second))))))

(deftest test-internal-upgrade!
  (let [!constructor-slot (atom nil)
        tag-name "my-custom-element"
        instance #js []
        !constructed (atom false)
        ctor (ctor/make js/Array
                        (fn [& _] (reset! !constructed true))
                        #js {})

        latest-constructor (fn [tag]
                             (assert (= tag-name tag))
                             ctor)

        _ (ctor/internal-upgrade!
           tag-name instance
           {:tag->latest-constructor latest-constructor
            :set-constructor-trick-slot!
            (fn [& args] (reset! !constructor-slot
                                 args))})]

    (is @!constructed
        "the constructor should be called")

    (is (= (.-prototype ctor)
           (js/Reflect.getPrototypeOf instance))
        "protype of the instance should be set to the latest constructor")

    (is (= 1))))


