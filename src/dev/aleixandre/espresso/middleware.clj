(ns dev.aleixandre.espresso.middleware
  (:require
   [xtdb.api :as xt]))

(defn wrap-redirect-signed-in [handler]
  (fn [{:keys [session] :as req}]
    (if (some? (:uid session))
      {:status 303
       :headers {"location" "/app"}}
      (handler req))))

(defn wrap-signed-in [handler]
  (fn [{:keys [biff/db session] :as req}]
    (if (some? (:uid session))
      (let
       [user (xt/pull
              db
              '[* {:brew/_user [* {:brew/beans [*]}]}]
              (:uid session))]
        (handler (assoc req :user user)))
      {:status 303
       :headers {"location" "/signin?error=not-signed-in"}})))
