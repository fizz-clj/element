# Granular Re-Rendering on the Client

status: hammock

We want to be able to provide granular reactivity. For example, if I have a component like this:

```html
<my-custom-element state="active" level="10"></my-custom-component>
```

And it is implemented like so:

```
(fizz/el my-custom-element           ;; hypothetical macro
  [{:keys [state level]}]            ;; element attributes
  [:div.outermost 
    (if active 
      [:div.inner-active
        [:h2 "Active"]
        [:p (str "Level " level)]]  ;; <- When "level updates"
      [:div.inner-nonactive         ;;    this is the only thing that
        [:p "Not active"]])])       ;;    should re-render
        
```

When "level" is modified, we ultimately don't want to render the whole thing.

We never want to render the outermost div (even on hot reload)
