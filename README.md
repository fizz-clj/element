# 🫧 Element

Simple, declarative custom web elements in Clojure(Script).

Element is narrowly tailored to render custom components from their attributes.

#### Status

Experimental. For a more mature Clojure custom elements library, see [subzero](https://github.com/raystubbs/subzero).

## Example

Define a component in a cljc file:

```clojure
(require '[fizz.element :as f])

(defn my-custom-element
  [{:keys [number]}]
  [:div [:p (str "Your number is " number)]])

(f/define! "my-custom-component" 
  {:observed-attributes #{:number}]
   :shadow-mode :open          ;; default
   :form-associated false      ;; default
   :render my-custom-component}
```

On the client, this defines a custom component that can be used as follows:

```html
<my-custom-element number="0"></my-custom-element>
```

When the `number` attribute is updated on the client, the component will re-render.

Server side rendering is achieved with:

```clojure
(f/html "my-custom-element" {:number 1})
```

Returning:

```html
<my-custom-element number="1">
  <template shadow-root-mode="open">
    <div><p>Your number is 1<p></div>
  <template>
</my-custom-element>
```

This can be streamed to the client, loaded to the DOM as the bytes are received, and rendered -- all before the JavaScript mounts. And when it does -- no hydration is required.

## Features

*Hot module reload*.

*Small*.

*Reactive re-rendering*.

*SSR Streaming*.

## Design Goals and Non-Goals

**Leverage the browser**. Avoid the React cruft. Just use the built-in, optimized custom elements API.

**Functional and Declarative**. The state of each component is a function of its attributes.

**Backend and Frontend**. Render components using the declarative shadow DOM on the backend, in Clojurescript on the frontend.

**Fast**. Fast to render.

**State management**. Focus on rendering custom web element from its attributes. Leave more sophisticated state management to other libraries.

**Open for extension**. Leave the question of how events and changes are handled open.

## Prior Work

- [Subzero](https://github.com/raystubbs/subzero).
- [Facet](https://github.com/kgscialdone/facet)
