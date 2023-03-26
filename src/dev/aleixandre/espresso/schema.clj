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
   :brew/grind :double
   :brew/yield :double
   :brew/dose :double
   :brew/duration :double
   :brew/user :user/id
   :brew/beans :beans/id
   :brew [:map {:closed true}
          [:xt/id :brew/id]
          :brew/brewed-at
          :brew/grind
          :brew/yield
          :brew/dose
          :brew/duration
          :brew/user
          :brew/beans]

   :beans/id :uuid
   :beans/name :string
   :beans/roaster :string
   :beans/roasted-on inst?
   :beans/user :user/id
   :beans [:map {:closed true}
           [:xt/id :beans/id]
           :beans/name
           :beans/roaster
           :beans/roasted-on
           :beans/user]})

(def features
  {:schema schema})
