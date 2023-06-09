(ns dev.aleixandre.espresso.ui
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [dev.aleixandre.espresso.settings :as settings]
            [com.biffweb :as biff]
            [ring.middleware.anti-forgery :as csrf]))

(defn css-path []
  (if-some [f (io/file (io/resource "public/css/main.css"))]
    (str "/css/main.css?t=" (.lastModified f))
    "/css/main.css"))

(defn base [{:keys [::recaptcha] :as opts} & body]
  (apply
   biff/base-html
   (-> opts
       (merge #:base{:title settings/app-name
                     :lang "en-US"
                     :icon "/img/glider.png"
                     :description (str settings/app-name " Description")
                     :image "https://clojure.org/images/clojure-logo-120b.png"})
       (update :base/head (fn [head]
                            (concat [[:link {:rel "stylesheet" :href (css-path)}]
                                     [:script {:src "https://unpkg.com/htmx.org@1.8.4"}]
                                     [:script {:src "https://unpkg.com/hyperscript.org@0.9.3"}]
                                     (when recaptcha
                                       [:script {:src "https://www.google.com/recaptcha/api.js"
                                                 :async "async" :defer "defer"}])]
                                    head))))
   body))

(defn page [opts & body]
  (base
   opts
   [:.px-3.py-8.mx-auto.max-w-screen-sm.w-full
    (when (bound? #'csrf/*anti-forgery-token*)
      {:hx-headers (cheshire/generate-string
                    {:x-csrf-token csrf/*anti-forgery-token*})})
    body]))

(defn app-page [req & body]
  (let [{:keys [user]} req
        current-nav-item (get-in req [:reitit.core/match :data :nav-item])
        {:user/keys [email]} user]
    (page
     {}
     [:div.flex.items-baseline.justify-between.pb-6
      [:h1.font-bold
       settings/app-name]
      [:div.text-sm "Signed in as " email ". "
       (biff/form
        {:action "/auth/signout"
         :class "inline"}
        [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
         "Sign out"])
       "."]]

     [:nav.flex.mb-6.border-b.border-gray-300.px-2
      (for [{:keys [label route nav-item]}
            [{:label "Brews" :route "/app" :nav-item :brews-page}
             {:label "Beans" :route "/beans" :nav-item :beans-page}]]
        [:a {:href route
             :class ["block border border-b-0 font-bold py-2 px-4 -mb-[1px] text-blue-500"
                     (if (= nav-item current-nav-item)
                       "font-bold border-gray-300 bg-white rounded-t-lg"
                       "border-white")]}
         label])]

     body)))
