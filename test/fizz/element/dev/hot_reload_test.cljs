(ns fizz.element.dev.hot-reload-test
  (:require [cljs.test :refer [is testing deftest]]
            [fizz.element.dev.hot-reload :as hr]))

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
                :foo
                #js {:configurable true
                     :value #(js/console.log "hi")
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

(deftest ^:integration test-creating-and-registering-custom-element
  ())
