(ns clabango.test.parser
  (:use [clojure.test]
        [clabango.parser]
        [clabango.tags]))

(deftest lex-tests
  (is (= (lex "a b c d")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a b c d"}]))
  (is (= (lex "a {{ b c d")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a "}
          {:offset 3 :line 1 :file "UNKNOWN" :token :open-filter}
          {:offset 5 :line 1 :file "UNKNOWN" :token " b c d"}]))
  (is (= (lex "a {{ b c }}d")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a "}
          {:offset 3 :line 1 :file "UNKNOWN" :token :open-filter}
          {:offset 5 :line 1 :file "UNKNOWN" :token " b c "}
          {:offset 10 :line 1 :file "UNKNOWN" :token :close-filter}
          {:offset 12 :line 1 :file "UNKNOWN" :token "d"}]))
  (is (= (lex "a {{ b c d}")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a "}
          {:offset 3 :line 1 :file "UNKNOWN" :token :open-filter}
          {:offset 5 :line 1 :file "UNKNOWN" :token " b c d}"}]))
  (is (= (lex "a {{ b c d%}")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a "}
          {:offset 3 :line 1 :file "UNKNOWN" :token :open-filter}
          {:offset 5 :line 1 :file "UNKNOWN" :token " b c d"}
          {:offset 11 :line 1 :file "UNKNOWN" :token :close-tag}]))
  (is (= (lex "a {%foo%}")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a "}
          {:offset 3 :line 1 :file "UNKNOWN" :token :open-tag}
          {:offset 5 :line 1 :file "UNKNOWN" :token "foo"}
          {:offset 8 :line 1 :file "UNKNOWN" :token :close-tag}]))
  (is (= (lex "a {%foo%}\nline 2!")
         [{:offset 1 :line 1 :file "UNKNOWN" :token "a "}
          {:offset 3 :line 1 :file "UNKNOWN" :token :open-tag}
          {:offset 5 :line 1 :file "UNKNOWN" :token "foo"}
          {:offset 8 :line 1 :file "UNKNOWN" :token :close-tag}
          {:offset 10 :line 1 :file "UNKNOWN" :token "\n"}
          {:offset 1 :line 2 :file "UNKNOWN" :token "line 2!"}])))

(deftest passthrough
  (let [s "a b c d"]
    (is (= s (render s {}))))
  (let [s "{a b c } d"]
    (is (= s (render s {}))))
  (let [s "{a b c }\n d"]
    (is (= s (render s {})))))

(deftest test-include
  (let [s (str "{{foo}}\n dogs live in the park."
               " {% include \"clabango/templates/foo.html\" %}")]
    (is (= (parse s {:foo 42 :name "Dan"})
           [{:type :string
             :body {:token "42"
                    :offset 3
                    :line 1
                    :file "UNKNOWN"}}
            {:type :string
             :body {:token "\n"
                    :offset 8
                    :line 1
                    :file "UNKNOWN"}}
            {:type :string
             :body {:token " dogs live in the park. "
                    :offset 1
                    :line 2
                    :file "UNKNOWN"}}
            {:type :string
             :body {:token "Hello, "
                    :offset 1
                    :line 1
                    :file (load-template "clabango/templates/foo.html")}}
            {:type :string
             :body {:token "Dan"
                    :offset 10
                    :line 1
                    :file (load-template "clabango/templates/foo.html")}}
            {:type :string
             :body {:token "!"
                    :offset 16
                    :line 1
                    :file (load-template "clabango/templates/foo.html")}}
            {:type :string
             :body {:token "\n"
                    :offset 17
                    :line 1
                    :file (load-template "clabango/templates/foo.html")}}]))
    (is (= (render s {:foo 42 :name "Dan"})
           "42\n dogs live in the park. Hello, Dan!\n"))))

(deftest test-block-overriding
  (is (= (render (str "{% block foo%}default {{bar}}{%endblock%}"
                      "1234{%block foo%}override {{bar}} {%endblock%}")
                 {:bar "BAR"})
         "override BAR 1234"))
  (testing "empty overrides should work"
    (is (= (render (str "{% block foo%}default {{bar}}{%endblock%}"
                        "{% block bar%}default bar{%endblock%}"
                        "{%block foo%}{%endblock%}{%block bar%}{%endblock%}")
                   {:bar "BAR"})
           "")))
  (testing "works when extending"
    (testing "override first block"
      (is (= (render (str "{% extends \"clabango/templates/blocks.html\" %}"
                          "{% block foo %}overriding foo now{% endblock %}")
                     {:foo 12
                      :bar 47})
             (str "overriding foo now\n"
                  "Here's the default text of bar 47\n"))))
    (testing "override second block"
      (is (= (render (str "{% extends \"clabango/templates/blocks.html\" %}"
                          "{% block bar %}override {{foo}}{% endblock %}")
                     {:foo 12
                      :bar 47})
             (str "Here's the default text of foo 12\n"
                  "override 12\n"))))
    (testing "override both blocks"
      (is (= (render (str "{% extends \"clabango/templates/blocks.html\" %}"
                          "{% block foo %}new foo!{% endblock %}"
                          "{% block bar %}new bar {{foo}} {{bar}}{% endblock %}")
                     {:foo 12
                      :bar 47})
             (str "new foo!\n"
                  "new bar 12 47\n"))))))

(deftest test-if
  (= (render "{% if foo %}foo is true{% endif %}" {:foo true})
     "foo is true")
  (= (render "{% if foo %}foo is true{% endif %}" {:foo false})
     ""))

(deftest filter-upper
  (is (= "FOO" (render "{{f|upper}}" {:f "foo"}))))
