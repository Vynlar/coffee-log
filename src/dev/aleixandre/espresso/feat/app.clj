(ns dev.aleixandre.espresso.feat.app
  (:require [com.biffweb :as biff :refer [q]]
            [dev.aleixandre.espresso.middleware :as mid]
            [dev.aleixandre.espresso.ui :as ui]
            [dev.aleixandre.espresso.icons :as icons]
            [rum.core :as rum]
            [xtdb.api :as xt]
            [ring.adapter.jetty9 :as jetty]
            [cheshire.core :as cheshire]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]))

#_(defn set-foo [{:keys [session params] :as req}]
    (biff/submit-tx req
                    [{:db/op :update
                      :db/doc-type :user
                      :xt/id (:uid session)
                      :user/foo (:foo params)}])
    {:status 303
     :headers {"location" "/app"}})

#_(defn bar-form [{:keys [value]}]
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

#_(defn set-bar [{:keys [session params] :as req}]
    (biff/submit-tx req
                    [{:db/op :update
                      :db/doc-type :user
                      :xt/id (:uid session)
                      :user/bar (:bar params)}])
    (biff/render (bar-form {:value (:bar params)})))

#_(defn message [{:msg/keys [text sent-at]}]
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

#_(defn send-message [{:keys [session] :as req} {:keys [text]}]
    (let [{:keys [text]} (cheshire/parse-string text true)]
      (biff/submit-tx req
                      [{:db/doc-type :msg
                        :msg/user (:uid session)
                        :msg/text text
                        :msg/sent-at :db/now}])))

#_(defn chat [{:keys [biff/db]}]
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

(defn input-field [form {:keys [id name label right-element type]}]
  [:div.flex.flex-col.gap-2
   [:label.font-bold {:for id} label]
   [:div.flex.gap-4.items-center
    [:input.w-full {:id id
                    :name name :type (or type "text")
                    :step "any"
                    :default-value (get-in form [:values (keyword name)])}]
    (when right-element
      [:span right-element])]
   (ui-error (get-in form [:errors (keyword name)]))])

(defn select-field [form {:keys [id name label right-element options]}]
  [:div.flex.flex-col.gap-2
   [:label.font-bold {:for id} label]
   [:div.flex.gap-4.items-center
    [:select {:id id
              :class "w-full"
              :name name :type "number"
              :step "any"
              :default-value (get-in form [:values (keyword name)])}
     (for [{:keys [value label]} options] [:option {:value value} label])]
    (when right-element
      [:span right-element])]
   (ui-error (get-in form [:errors (keyword name)]))])

(def default-brew-values {:values {:grind "" :dose "17" :yield "" :duration ""}})

(defn- form-save-button []
  [:div.flex.gap-3.items-center.justify-end
   [:a.link {:href "/app"} "Cancel"]
   [:button.btn
    {:type "submit"} "Save"]])

(defn- new-brew-form [{:keys [biff/db session]} form]
  (let [all-beans (biff/q db '{:find [(pull ?beans [*])]
                               :where [[?user :xt/id ?uid]
                                       [?beans :beans/user ?user]]
                               :in [?uid]}
                          (:uid session))]
    (biff/form
     {:class "space-y-4 max-w-sm"
      :hx-post "/brew"
      :hx-swap "outerHTML"}

     (input-field form {:id "grind"
                        :type "number"
                        :name "grind"
                        :label "Grind"})

     (input-field form {:id "dose"
                        :type "number"
                        :name "dose"
                        :label "Dose"
                        :right-element "grams"})

     (input-field form {:id "yield"
                        :type "number"
                        :name "yield"
                        :label "Yield"
                        :right-element "grams"})

     (input-field form {:id "duration"
                        :type "number"
                        :name "duration"
                        :label "Duration"
                        :right-element "grams"})

     (select-field form {:id "beans"
                         :name "beans"
                         :label "Beans"
                         :options (mapv (fn [[beans]]
                                          {:value (:xt/id beans) :label (str (:beans/name beans) " (" (:beans/roaster beans) ")")})
                                        all-beans)})

     (form-save-button))))

(defn app [{:keys [user] :as req}]
  (ui/app-page
   req

   [:section.space-y-4
    [:div.flex.justify-between
     [:h2.text-lg.font-bold "My Brews"]
     [:a.inline-block.btn {:href "/brew/new"} "New brew"]]
    [:ul.space-y-2.m-0.p-0
     (for [{:brew/keys [brewed-at dose yield duration beans] :xt/keys [id]} (reverse (sort-by :brew/brewed-at (:brew/_user user)))]
       [:li.list-none.m-0.border.border-gray-300.rounded.shadow.p-4
        [:div.flex.justify-between
         [:div

          [:div.flex.gap-3
           [:div.flex.flex-col.items-center
            [:span.uppercase.text-xs.text-gray-500 "Recipe"]
            [:div dose " in"]
            (icons/arrow-down)
            [:div yield " out"]]

           [:div.flex.flex-col.items-center
            [:span.uppercase.text-xs.text-gray-500 "Time"]
            duration "s"]

           [:div.flex.flex-col.items-center
            [:span.uppercase.text-xs.text-gray-500 "Ratio"]
            "1:" (/ yield dose)]]]

         [:div.flex.gap-2.items-center
          [:div.flex.flex-col
           [:span.text-sm (:beans/name beans) " (" (:beans/roaster beans) ")"]
           [:span.text-sm.text-gray-500 (biff/format-date brewed-at "MMM d yyyy h:mm a")]]
          [:button {:hx-delete (str "/brew/" id)
                    :hx-confirm "Are you sure?"} (icons/trash)]]]])]]))

#_(defn ws-handler [{:keys [dev.alreixandre.espresso/chat-clients] :as req}]
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
                         [:grind pos-required-double]
                         [:dose pos-required-double]
                         [:yield pos-required-double]
                         [:duration pos-required-double]
                         [:beans :uuid]])

(defn create-brew [{:keys [params session] :as req}]
  (let [parsed (m/decode create-brew-params params mt/string-transformer)]
    (if-not (m/validate create-brew-params parsed)
      (biff/render (new-brew-form
                    req
                    {:values parsed
                     :errors (me/humanize (m/explain create-brew-params parsed))}))
      (do
        (biff/submit-tx req
                        [{:db/op :create
                          :db/doc-type :brew
                          :brew/brewed-at :db/now
                          :brew/grind (:grind parsed)
                          :brew/yield (:yield parsed)
                          :brew/duration (:duration parsed)
                          :brew/dose (:dose parsed)
                          :brew/beans (:beans parsed)
                          :brew/user (:uid session)}])

        {:status 201
         :headers {"HX-Redirect" "/app"}}))))

(defn delete-brew [{:keys [biff/db session path-params] :as req}]
  (let [user-id (:uid session)
        brew-id (parse-uuid (:id path-params))
        brews (biff/q db '{:find [(pull ?brew [*])]
                           :where [[?brew :xt/id ?bid]
                                   [?brew :brew/user ?uid]]
                           :in [?uid ?bid]}
                      user-id brew-id)]
    (if (seq brews)
      (do
        (biff/submit-tx req
                        [{:db/op :delete
                          :db/doc-type :brew
                          :xt/id brew-id}])

        {:status 204
         :headers {"HX-Refresh" "true"}})

      {:status 404})))

(defn beans-page [{:keys [biff/db session] :as req}]
  (let [all-beans (biff/q db '{:find [(pull ?beans [*])]
                               :where [[?user :xt/id ?uid]
                                       [?beans :beans/user ?user]]
                               :in [?uid]}
                          (:uid session))]
    (ui/app-page
     req
     [:div.flex.justify-between
      [:h2.text-lg.font-bold "My Beans"]
      [:a.inline-block.btn {:href "/beans/new"} "Add beans"]]
     [:div#delete-response.text-red-500]
     [:ul.p-0.m-0
      (for [[{:keys [xt/id beans/name beans/roaster]}] all-beans]
        [:li.flex.items-center name " (" roaster ")"
         [:button {:hx-delete (str "/beans/" id)
                   :hx-target "#delete-response"
                   :hx-confirm "Are you sure?"} (icons/trash)]])])))

(def default-beans-values {:values {:name "" :roaster ""}})

(defn- new-beans-form [req form]
  (biff/form {:class "space-y-4 max-w-sm"
              :hx-post "/beans"
              :hx-swap "outerHTML"}
             (input-field form {:id "name"
                                :name "name"
                                :label "Name"})
             (input-field form {:id "roaster"
                                :name "roaster"
                                :label "Roaster"})

             (form-save-button)))

(defn new-beans-page [{:keys [biff/db session] :as req}]
  (ui/app-page
   req
   [:h2.text-lg.font-bold "New Beans"]

   (new-beans-form req default-beans-values)))

(defn new-brew-page [{:keys [biff/db session] :as req}]
  (let [last-brew (first (mapv second (biff/q db '{:find [?brew-time (pull ?beans [*])]
                                                   :where [[?beans :brew/user ?uid]
                                                           [?beans :brew/brewed-at ?brew-time]]
                                                   :order-by [[?brew-time :desc]]
                                                   :limit 1
                                                   :in [?uid]}
                                              (:uid session))))
        last-grind (when last-brew (:brew/grind last-brew))
        last-beans (when last-brew (:brew/beans last-brew))]
    (ui/app-page
     req

     [:h2.text-lg.font-bold "New Brew"]
     (new-brew-form req (-> default-brew-values
                            (assoc-in [:values :grind] (or last-grind ""))
                            (assoc-in [:values :beans] (or last-beans "")))))))

(def create-beans-params [:map
                          [:name :string]
                          [:roaster :string]])

(defn create-beans [{:keys [params session] :as req}]
  (let [parsed (m/decode create-beans-params params mt/string-transformer)]
    (if-not (m/validate create-beans-params parsed)
      (biff/render (new-beans-form
                    req
                    {:values parsed
                     :errors (me/humanize (m/explain create-beans-params parsed))}))
      (do
        (biff/submit-tx req
                        [{:db/op :create
                          :db/doc-type :beans
                          :beans/name (:name parsed)
                          :beans/roaster (:roaster parsed)
                          :beans/roasted-on :db/now
                          :beans/user (:uid session)}])

        {:status 201
         :headers {"HX-Redirect" "/beans"}}))))

(defn delete-beans [{:keys [biff/db session path-params] :as req}]
  (let [user-id (:uid session)
        beans-id (parse-uuid (:id path-params))
        beans (biff/q db '{:find [(pull ?beans [* {:brew/_beans [*]}])]
                           :where [[?beans :xt/id ?beansid]
                                   [?beans :beans/user ?uid]
                                   (not-join [?beansid]
                                             [?brew :brew/beans ?beansid])]
                           :in [?uid ?bid]}
                      user-id beans-id)]
    (if (seq beans)
      (do
        (biff/submit-tx req
                        [{:db/op :delete
                          :db/doc-type :beans
                          :xt/id beans-id}])

        {:status 204
         :headers {"HX-Refresh" "true"}})

      [:span "Beans that are used in brews cannot be deleted"])))

(def features
  {:routes ["" {:middleware [mid/wrap-signed-in]}
            ["/app" {:nav-item :brews-page}
             ["" {:get app}]]
            ["/brew"
             ["" {:post create-brew}]
             ["/new" {:get new-brew-page :conflicting true :nav-item :brews-page}]
             ["/:id" {:delete delete-brew :conflicting true}]]
            ["/beans" {:get beans-page :nav-item :beans-page :post create-beans}]
            ["/beans/new" {:get new-beans-page :nav-item :beans-page :conflicting true}]
            ["/beans/:id" {:delete delete-beans :conflicting true}]]})
