# Component Definitions

status: proposed

## Problem

How should users define their components?

## Constraints

- Performance
- Familiarity of users

## Options

### Macro

```clojure
(fizz/el my-component
  [{:keys [valid]}]
  {:attributes [:valid]}
  [:div.outer
   (if valid
      [:p "Valid!"]
      [:p "Not Valid!"])])
```

### Map

Provide that declares its attributes and its render function.

```clojure
(defn my-component
  [{:keys [valid]}]
  [:div.outer
   (if valid 
      [:p "Valid!"]
      [:p "Not Valid!"])]) 
{:attributes #{:valid}        ;; declared attributes cause re-render 
 :render  my-component
 :name "my-component" }
```

## Solution

Use a map for now, we can always add macros for further features later.

For rendering, use borkdude's `html` library.
