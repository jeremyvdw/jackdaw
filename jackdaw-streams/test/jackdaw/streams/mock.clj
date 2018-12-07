(ns jackdaw.streams.mock
  "Mocks for testing kafka streams."
  (:refer-clojure :exclude [send])
  (:require [jackdaw.streams :as k]
            [jackdaw.streams.configurable :refer [config configure]]
            [jackdaw.streams.configured :as configured]
            [jackdaw.streams.interop :as interop])
  (:import [org.apache.kafka.test KStreamTestDriver MockProcessorSupplier]
           java.nio.file.Files
           java.nio.file.attribute.FileAttribute
           org.apache.kafka.common.serialization.Serdes))

(defn topology-builder
  "Creates a mock topology-builder."
  ([]
   (topology-builder (interop/topology-builder)))
  ([topology-builder]
   (configured/topology-builder
    {::topology-builder (k/topology-builder* topology-builder)}
    topology-builder)))

(defn build
  "Builds the topology."
  [topology]
  (let [processor-supplier (MockProcessorSupplier.)]
    (.process (k/kstream* topology)
              processor-supplier
              (into-array String []))
    (let [test-driver (KStreamTestDriver. (-> topology config ::topology-builder)
                                          (.toFile
                                           (Files/createTempDirectory
                                            "kstream-test-driver"
                                            (into-array FileAttribute []))))]
      (-> topology
          (configure ::test-driver test-driver)
          (configure ::processor-supplier processor-supplier)))))

(defn send
  "Publishes message to a topic."
  [topology topic-config key message]
  (let [test-driver (-> topology config ::test-driver)
        time (or (-> topology config ::test-driver-time) 0)]
    (.setTime test-driver time)
    (.process test-driver
              (:topic.metadata/name topic-config)
              key
              message)
    (.flushState test-driver)
    (-> topology
        (configure ::test-driver-time (inc time)))))

(defn collect
  "Collects the test results. The test driver returns a list of messages with
  each message formatted like \"key:value\""
  [topology-builder]
  (let [processor-supplier (-> topology-builder config ::processor-supplier)
        processed (into [] (.processed processor-supplier))]
    (.clear (.processed processor-supplier))
    processed))

(defn topic
  "Helper to create a topic."
  [topic-name]
  {:topic.metadata/name topic-name
   :jackdaw.serdes/key-serde (Serdes/Long)
   :jackdaw.serdes/value-serde (Serdes/Long)})