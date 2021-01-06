(defproject whitelabeling "0.1.0-SNAPSHOT"
    :description "FIXME: write description"
    :url "http://example.com/FIXME"
    :license {:name "Eclipse Public License"
              :url "http://www.eclipse.org/legal/epl-v10.html"}
    :dependencies [[org.clojure/clojure "1.10.1"]
                   [org.clojure/java.jdbc "0.6.1"]
                   [org.clojure/data.csv "0.1.4"]
                   [org.clojure/data.json "0.2.6"]
                   [org.clojure/data.codec "0.1.1"]
                   [org.clojure/data.xml "0.2.0-alpha6"]
                   [mysql/mysql-connector-java "5.1.41"]
                   [clj-time "0.14.3"]
                   [clj-http "3.7.0"]
                   [com.dinstone/beanstalkc "2.2.0"]
                   [digest "1.4.8"]
                   [crypto-password "0.2.0"]
                   [com.fasterxml.jackson.core/jackson-core "2.8.7"] ; required for java-jwt
                   [com.auth0/java-jwt "3.8.0"]
                   [com.auth0/jwks-rsa "0.8.1"] ; https://github.com/auth0/jwks-rsa-java
                   [org.clojure/core.async "1.0.567"]
                   ]

    :source-paths      ["src/clojure"]
    :java-source-paths ["src/java"]
    :repl-options {:init-ns com.jkbff.budabot.core}
    :main beanstalk-worker.core
    :target-path "target/%s"
    :profiles {:dev {}})
