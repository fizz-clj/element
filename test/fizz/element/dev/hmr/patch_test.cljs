(ns fizz.element.dev.hmr.patch-test
  (:require [cljs.test :refer [deftest is]]
            [fizz.element.dev.hmr.patch :as patch]))

(deftest test-set-attr
  (let [instance (js/document.createElement "div")

        _ (patch/html-set-attr instance "foo" "bar")

        attr-after-set (patch/html-get-attr instance "foo")

        _ (patch/html-remove-attr instance "foo")

        attr-after-remove (patch/html-remove-attr instance "foo")]

    (is (= "bar" attr-after-set))
    (is (nil? attr-after-remove))))

(deftest test-make-set-attr
  (let [!a (atom [])

        set-attr-callback
        (fn [& args] (swap! !a conj args))

        proto
        (->> {:attributeChangedCallback
              {:value set-attr-callback}}
             clj->js
             (js/Object.create js/HTMLElement))

        instance (js/document.createElement "div")

        _ (js/Object.setPrototypeOf instance proto)

        set-attr-fn (patch/make-set-attr instance #{"x"})

        _ (set-attr-fn "x" "y")

        _ (set-attr-fn "z" "!")]
    (is (= [["x" nil "y"]]
           @!a)
        "Should only call attribute changed callback on attribute `x`")))

(deftest test-make-remove-attr
  (let [!a (atom [])

        remove-attr-callback
        (fn [& args] (swap! !a conj args))

        proto
        (->> {:attributeChangedCallback
              {:value remove-attr-callback}}
             clj->js
             (js/Object.create js/HTMLElement))

        instance (js/document.createElement "div")

        _ (js/Object.setPrototypeOf instance proto)

        remove-attr-fn (patch/make-remove-attr instance #{"x"})

        _ (remove-attr-fn "x")
        _ (remove-attr-fn "y")]

    (is (= [["x" nil nil]]
           @!a))))

(deftest test-patch-attributes
  (let [!a (atom [])

        attr-changed-callback
        (fn [& args] (swap! !a conj args))

        proto
        (->> {:attributeChangedCallback
              {:value attr-changed-callback}}
             clj->js
             (js/Object.create js/HTMLElement))

        instance (js/document.createElement "div")

        _ (js/Object.setPrototypeOf instance proto)

        _ (patch/attributes! instance #{"x"})
        _ (.setAttribute instance "x" "1")
        _ (.setAttribute instance "x" "2")
        _ (.setAttribute instance "y" "z")
        _ (.removeAttribute instance "x")]
    (is (= [["x" nil "1"] ["x" "1" "2"] ["x" "2" nil]]
           @!a))))




