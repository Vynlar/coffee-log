(ns dev.aleixandre.espresso.schema
  (:require
   [com.biffweb :as biff]))

(def schema
  {:user/id :uuid
   :user/email :string
   :user/joined-at inst?
   :user [:map {:closed true}
          [:xt/id :user/id]
          :user/email
          :user/joined-at]

   :brew/id :uuid
   :brew/brewed-at inst?
   :brew/yield :double
   :brew/dose :double
   :brew/duration :double
   :brew/user :user/id
   :brew [:map {:closed true}
          [:xt/id :brew/id]
          :brew/brewed-at
          :brew/yield
          :brew/dose
          :brew/duration
          :brew/user]})

(def features
  {:schema schema})
