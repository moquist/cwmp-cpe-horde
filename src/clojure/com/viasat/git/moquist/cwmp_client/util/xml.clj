(ns com.viasat.git.moquist.cwmp-client.util.xml
  (:require [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.xml :as c-xml]
            [com.viasat.git.moquist.cwmp-client.util.value-finders :as value-finders]))

(set! *warn-on-reflection* true)

(defn str->inputstream
  "https://stackoverflow.com/a/38284236"
  ([^java.lang.String s] (str->inputstream s "UTF-8"))
  ([^java.lang.String s ^java.lang.String encoding]
   (java.io.ByteArrayInputStream. (.getBytes s encoding))))

(defn xml-node?
  "Is x an xml-node like:
  {:tag ..., :attrs ..., :content ...}"
  [x]
  (and (map? x)
       (contains? x :tag)))

(defn xml-node->tag [x]
  (when (xml-node? x)
    (:tag x)))

(defn xml-tag->lowercased-namespace
  "Take a keyword like :a:b where a is an XML namespace and b is a tag, and return :a:b where the XML namespace is lowercase.
  Example:
  (xml-tag->lowercase :CWMP:Meep)
  ;;=> :cwmp:Meep"
  [xmlns-tag]
  (let [[maybe-xml-ns tag] (str/split (name xmlns-tag) #":" 2)]
    (if-not tag
      ;; there's no XML namespace, just return the tag as-is
      xmlns-tag
      ;; we have an XML namespace, so lower-case it.
      (keyword (str (str/lower-case maybe-xml-ns) ":" tag)))))

(defn xml-tags=
  "Check two XML tags for equality, with special-case treatment of an XML namespace expressed as colon-delimited prefix on a Clojure keyword. E.g., the following is truthy:
  (xml-tags= :SOAP-ENV:Body :soap-env:Body)

  XML tags are case-sensitive in general, though XML namespaces are declared in each XML document and casing may vary.

  This fn is a compromise between strict case-sensitivity for XML namespaces (deemed unnecessary) vs not checking XML namespaces at all (which could lead to bad surprises).

  XXX: Will hyphen-vs-underscore be a problem?
  "
  [& tags]
  (and (every? keyword? tags)
       (->> tags
            (map xml-tag->lowercased-namespace)
            (into #{})
            count
            (= 1))))

(defn xml->map-xml
  [data]
  (cond
    ;; assume {:tag "", :attrs {}, :content [...]}
    (map? data) data

    ;; assume [tag attrs content ...]
    ;; This seems overly elaborate... round-tripping surely shouldn't be this ridiculous.
    ;; If it ever matters, maybe refactor this to be less elaborate.
    (vector? data) (-> data
                       xml/sexp-as-element
                       xml/emit-str
                       str->inputstream
                       c-xml/parse)

    (string? data) (c-xml/parse (str->inputstream data))
    :else (throw (ex-info (format "This data doesn't seem to be XML: %s, %s" data (type data))
                          {:cause :xml->map-xml-unknown-type
                           :data data
                           :type-of-data (type data)}))))

(defn xml-tag-finder-fn [tag]
  (fn [x]
    (xml-tags= tag (xml-node->tag x))))

(defn xml->tag-content
  "Given an acs-message and a single tag, return the first content value from that tag."
  [data tag]
  (-> (xml-tag-finder-fn tag)
      (value-finders/find-value (xml->map-xml data))
      :content
      first))


