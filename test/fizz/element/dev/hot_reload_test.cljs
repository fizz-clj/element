(ns fizz.element.dev.hot-reload-test
  (:require [cljs.test :refer [is testing deftest]]
            [fizz.element.dev.hot-reload :as hr]))

(defn make-component
  [prototype methods]
  (let [constructor
        (fn constructor []
          (js/Reflect.construct prototype #js [] constructor))
        _ (set! (.-prototype constructor)
                (js/Object.create (.-prototype prototype)
                                  methods))]
    constructor))

(comment
  (instance? js/HTMLElement (.-prototype (make-component js/HTMLElement #js {}))))

(deftest test-init-class-proxies!
  (testing "Registering and updated class proxies"
    (let [class-name "my-custom-class"
          class ::class-stub
          proxy ::proxy-stub
          test-registry (atom {})

          _ (hr/init-class-proxies!
             class-name
             {:class-name class-name
              :current-class class
              :current-proxy proxy
              :original-class class
              :original-proxy proxy
              :observed-attributes #{:a :b}}
             test-registry)

          after-init @test-registry

          _ (hr/register-new-proxies!
             class-name
             {:current-class ::new-class-stub
              :current-proxy ::new-proxy-stub
              :observed-attributes #{:a :b :c}}
             test-registry)

          after-update @test-registry]

      (is (= #{:a :b}
             (get-in after-init [class-name
                                 :original-observed-attributes])
             (get-in after-init [class-name
                                 :observed-attributes])))
      (is (= 1 (count after-init)))
      (is (get after-init class-name))

      (is (= #{:a :b}
             (get-in after-update
                     [class-name :original-observed-attributes])))

      (is (= #{:a :b :c}
             (get-in after-update
                     [class-name :observed-attributes]))))))

(comment (test-init-class-proxies!))

(deftest test-create-proxy
  (let [original-target
        (js/Object.create
         js/HTMLElement
         (clj->js {"name" {:value "original"
                           :writable true}
                   "getName"
                   {:value (fn [] (this-as this (.-name this))),
                    :writable true}}))

        updated-target
        (js-obj "name" "updated"
                "getName" (fn [] (this-as this (.-name this))))

        !handler (atom original-target)

        proxy (hr/create-proxy "my-custom-el"
                               original-target
                               (fn [] @!handler))

        name-before-update (.getName proxy)

        proto-before-update (.-prototype proxy)

        _ (reset! !handler updated-target)

        name-after-update (.getName proxy)

        proto-after-update (.-prototype proxy)]

    (is (= js/HTMLElement (type proto-before-update)))
    (is (= js/HTMLElement (type proto-after-update)))
    (is (= "original" name-before-update))
    (is (= "updated" name-after-update))))

(comment
  (test-create-proxy))

(deftest test-create-proxy-registers-component!
  (testing "The component should be registered and unregistered
    as it is added to or removed from the dom."
    (let [!a (atom [])

          fake-registry (atom {})

          original-connected-cb
          (fn [] (swap! !a conj :original-connected))

          original-disconnected-cb
          (fn [] (swap! !a conj :original-disconnected))

          original-target
          (js/Object.create
           js/HTMLElement
           #js {:connectedCallback
                #js {:configurable true
                     :value original-connected-cb
                     :writable true}
                :disconnectedCallback
                #js {:configurable true
                     :value original-disconnected-cb
                     :writable true}})

          proxy (hr/create-proxy
                 "my-custom-el"
                 original-target
                 (fn [& _] original-target)
                 fake-registry)

          _ (.connectedCallback proxy)

          registry-after-connect @fake-registry

          _ (.disconnectedCallback proxy)

          registry-after-disconnect @fake-registry]

      (is (= [:original-connected :original-disconnected]
             @!a))

      (is (= js/Window
             (-> registry-after-connect
                 (get-in ["my-custom-el" :instances])
                 first
                 type)))

      (is (= #{} (get-in registry-after-disconnect
                         ["my-custom-el" :instances]))))))

(deftest test-injection-of-proxy

  (let [class-name "my-custom-array"

        CustomArray
        (fn CustomArray []
          (js/Reflect.construct js/Array #js [] CustomArray))

        prototype1
        (->> (clj->js {:foo {:writeable true
                             :configurable true
                             :enumerable true
                             :value "bar"}})
             (js/Object.create (.-prototype js/Array)))

        _ (set! (.-prototype CustomArray)
                prototype1)

        custom-array (js/Reflect.construct
                      CustomArray #js [])

        _ (assert (instance? CustomArray custom-array))
        _ (assert (instance? js/Array custom-array))
        _ (assert (= prototype1
                     (js/Object.getPrototypeOf custom-array)))

        CustomArrayTwo
        (fn CustomArrayTwo []
          (js/Reflect.construct js/Array #js [] CustomArrayTwo))

        prototype2
        (->> (clj->js {:foo {:writeable true
                             :configurable true
                             :enumerable true
                             :value "baz"}})
             (js/Object.create (.-prototype js/Array)))

        _ (set! (.-prototype CustomArrayTwo)
                prototype2)

        foo-ret-before-injection (.-foo custom-array)

        proxy
        (js/Proxy.
         (js/Object.getPrototypeOf CustomArray)
         #js {:get (fn [_target property receiver]
                     (js/Reflect.get
                      (.-prototype CustomArrayTwo)
                      property receiver))})

        _ (hr/replace-proto-with-proxy! custom-array proxy)
        foo-ret-after-injection (.-foo custom-array)]

    (is (= "bar" foo-ret-before-injection))
    (is (= "baz" foo-ret-after-injection))))

(deftest test-proxy-patches-new-observed-attributes
  (testing "Given that a new observed attribute is added
    When that element is updated
    Then the attribute update callback should be called"
    (let [el-name "my-custom-el"

          !a (atom [])
          !registry (atom {})

          attr-changed-callback
          (fn [name old-value new-value]
            (swap! !a conj [name old-value new-value]))

          original-target
          (js/Object.create
           js/HTMLElement
           #js {:observedAttributes
                #js {:configurable true
                     :writable true
                     :value #js ["a"]}
                :attributeChangedCallback
                #js {:configurable true
                     :writable true
                     :value attr-changed-callback}})

          proxy (hr/create-proxy "my-custom-el"
                                 original-target
                                 (fn [& _] original-target)
                                 !registry)

          _ (hr/initialize-class!
             el-name original-target !registry)

          _ (assert (= #{} (hr/get-untracked-attributes
                            el-name @!registry)))

          instance (js/Reflect.construct proxy #js [])

          _ (assert (instance? js/HTMLElement instance))

          ;_ (.setAttribute proxy "b" 1)

          after-initial-call @!a]

      (= []))))

(deftest test-proxy-protype
  (testing "Given that I have a web component class
    When I proxy its protoype
    And I update the class
    Then it uses its proxies"))

(deftest ^:integration test-creating-and-registering-custom-element
  (testing "Given that I have proxied an element
    When I register it on the DOM
    Then no error is thrown"
    (let [class-name (str "custom-el-" (random-uuid))

          !registry (atom {})

          original-target
          (js/Object.create js/HTMLElement #js {})

          proxy
          (hr/create-proxy class-name
                           original-target
                           (fn [& _] original-target)
                           !registry)
          _ (js/window.customElements.define
             class-name original-target)])))





