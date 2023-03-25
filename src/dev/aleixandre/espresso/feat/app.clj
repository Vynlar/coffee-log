(ns dev.aleixandre.espresso.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [dev.aleixandre.espresso.middleware :as mid]
            [dev.aleixandre.espresso.ui :as ui]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]))

(defn set-foo [{:keys [session params] :as req}]
  (biff/submit-tx req
                  [{:db/op :update
                    :db/doc-type :user
                    :xt/id (:uid session)
                    :user/foo (:foo params)}])
  {:status 303
   :headers {"location" "/app"}})

(defn bar-form [{:keys [value]}]
  (biff/form
   {:hx-post "/app/set-bar"
    :hx-swap "outerHTML"}
   [:label.block {:for "bar"} "Bar: "
    [:span.font-mono (pr-str value)]]
   [:.h-1]
   [:.flex
    [:input.w-full#bar {:type "text" :name "bar" :value value}]
    [:.w-3]
    [:button.btn {:type "submit"} "Update"]]
   [:.h-1]
   [:.text-sm.text-gray-600
    "This demonstrates updating a value with HTMX."]))

(defn set-bar [{:keys [session params] :as req}]
  (biff/submit-tx req
                  [{:db/op :update
                    :db/doc-type :user
                    :xt/id (:uid session)
                    :user/bar (:bar params)}])
  (biff/render (bar-form {:value (:bar params)})))

(defn message [{:msg/keys [text sent-at]}]
  [:.mt-3 {:_ "init send newMessage to #message-header"}
   [:.text-gray-600 (biff/format-date sent-at "dd MMM yyyy HH:mm:ss")]
   [:div text]])

#_(defn notify-clients [{:keys [dev.aleixandre.espresso/chat-clients]} tx]
    (doseq [[op & args] (::xt/tx-ops tx)
            :when (= op ::xt/put)
            :let [[doc] args]
            :when (contains? doc :msg/text)
            :let [html (rum/render-static-markup
                        [:div#messages {:hx-swap-oob "afterbegin"}
                         (message doc)])]
            ws @chat-clients]
      (jetty/send! ws html)))

(defn send-message [{:keys [session] :as req} {:keys [text]}]
  (let [{:keys [text]} (cheshire/parse-string text true)]
    (biff/submit-tx req
                    [{:db/doc-type :msg
                      :msg/user (:uid session)
                      :msg/text text
                      :msg/sent-at :db/now}])))

(defn chat [{:keys [biff/db]}]
  (let [messages (q db
                    '{:find (pull msg [*])
                      :in [t0]
                      :where [[msg :msg/sent-at t]
                              [(<= t0 t)]]}
                    (biff/add-seconds (java.util.Date.) (* -60 10)))]
    [:div {:hx-ws "connect:/app/chat"}
     [:form.mb0 {:hx-ws "send"
                 :_ "on submit set value of #message to ''"}
      [:label.block {:for "message"} "Write a message"]
      [:.h-1]
      [:textarea.w-full#message {:name "text"}]
      [:.h-1]
      [:.text-sm.text-gray-600
       "Sign in with an incognito window to have a conversation with yourself."]
      [:.h-2]
      [:div [:button.btn {:type "submit"} "Send message"]]]
     [:.h-6]
     [:div#message-header
      {:_ "on newMessage put 'Messages sent in the past 10 minutes:' into me"}
      (if (empty? messages)
        "No messages yet."
        "Messages sent in the past 10 minutes:")]
     [:div#messages
      (map message (sort-by :msg/sent-at #(compare %2 %1) messages))]]))

(defn ui-error [error]
  (when error
    [:div.text-red-600.first-letter:capitalize (first error)]))

(defn number-field [form {:keys [id name label right-element]}]
  [:div.flex.flex-col.gap-2
   [:label.font-bold {:for id} label]
   [:div.flex.gap-4.items-center
    [:input {:id id :placeholder "e.g. 17"
             :name name :type "number"
             :step "any"
             :default-value (get-in form [:values (keyword name)])}]
    (when right-element
      [:span right-element])]
   (ui-error (get-in form [:errors (keyword name)]))])

(def default-brew-values {:values {:dose "17" :yield "" :duration ""}})

(defn- new-brew-form [form]
  (biff/form
   {:class "space-y-4"
    :hx-post "/brew"
    :hx-swap "outerHTML"}

   (number-field form {:id "dose"
                       :name "dose"
                       :label "Dose"
                       :right-element "grams"})

   (number-field form {:id "yield"
                       :name "yield"
                       :label "Yield"
                       :right-element "grams"})

   (number-field form {:id "duration"
                       :name "duration"
                       :label "Duration"
                       :right-element "grams"})

   [:button.btn
    {:type "submit"} "Save"]))

(defn app [{:keys [session biff/db] :as req}]
  (let [{:user/keys [email] :as user} (xt/pull db '[* {:brew/_user [*]}] (:uid session))]
    (ui/page
     {}
     nil
     [:div "Signed in as " email ". "
      (biff/form
       {:action "/auth/signout"
        :class "inline"}
       [:button.text-blue-500.hover:text-blue-800 {:type "submit"}
        "Sign out"])
      "."]

     (new-brew-form default-brew-values)

     [:section
      [:h2 "Brews"]
      [:ul.space-y-2
       (for [{:brew/keys [brewed-at dose yield duration] :xt/keys [id]} (:brew/_user user)]
         [:li
          [:div "Date: " brewed-at]
          [:div "In: " dose]
          [:div "Out: " yield]
          [:div "Time: " duration]
          [:button.btn {:hx-delete (str "/brew/" id)} "Delete"]])]])))

(defn ws-handler [{:keys [dev.alreixandre.espresso/chat-clients] :as req}]
  {:status 101
   :headers {"upgrade" "websocket"
             "connection" "upgrade"}
   :ws {:on-connect (fn [ws]
                      (swap! chat-clients conj ws))
        :on-text (fn [ws text-message]
                   (send-message req {:ws ws :text text-message}))
        :on-close (fn [ws status-code reason]
                    (swap! chat-clients disj ws))}})

(def pos-required-double [:and [:double {:error/message "Required"}] [:> 0]])
(def create-brew-params [:map
                         [:dose pos-required-double]
                         [:yield pos-required-double]
                         [:duration pos-required-double]])

(defn create-brew [{:keys [params session] :as req}]
  (let [parsed (m/decode create-brew-params params mt/string-transformer)]
    (if-not (m/validate create-brew-params parsed)
      (biff/render (new-brew-form
                    {:values parsed
                     :errors (me/humanize (m/explain create-brew-params parsed))}))
      (do
        (biff/submit-tx req
                        [{:db/op :create
                          :db/doc-type :brew
                          :brew/brewed-at :db/now
                          :brew/yield (:yield parsed)
                          :brew/duration (:duration parsed)
                          :brew/dose (:dose parsed)
                          :brew/user (:uid session)}])

        {:status 201
         :headers {"HX-Refresh" "true"}}
        #_(biff/render (new-brew-form default-brew-values))))))

(defn delete-brew [{:keys [biff/db path-params] :as req}]
  (biff/pprint path-params)

  (let [brew-id (parse-uuid (:id path-params))]
    (if-let [brew (biff/lookup db :xt/id brew-id)]
      (do
        (biff/submit-tx req
                        [{:db/op :delete
                          :db/doc-type :brew
                          :xt/id brew-id}])

        {:status 204
         :headers {"HX-Refresh" "true"}})

      {:status 404})))

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app" {:get app}]
            ["/brew"
             ["" {:post create-brew}]
             ["/:id" {:delete delete-brew}]]]})