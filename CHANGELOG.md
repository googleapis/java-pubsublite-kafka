# Changelog

## [0.3.0](https://www.github.com/googleapis/java-pubsublite-kafka/compare/v0.2.3...v0.3.0) (2021-05-19)


### Features

* Implement Consumer.endOffsets ([#102](https://www.github.com/googleapis/java-pubsublite-kafka/issues/102)) ([58e2e60](https://www.github.com/googleapis/java-pubsublite-kafka/commit/58e2e609ed9d9c73ed817e7ddf2c5be930e66339))
* Implement Consumer.offsetsForTimes ([#123](https://www.github.com/googleapis/java-pubsublite-kafka/issues/123)) ([a785e53](https://www.github.com/googleapis/java-pubsublite-kafka/commit/a785e53d03f73fa4e0af410ccc8388e0ff109155))


### Bug Fixes

* release scripts from issuing overlapping phases ([#115](https://www.github.com/googleapis/java-pubsublite-kafka/issues/115)) ([7f92099](https://www.github.com/googleapis/java-pubsublite-kafka/commit/7f9209936a45b6250aeaeeef6e57194c01be9898))
* typo ([#114](https://www.github.com/googleapis/java-pubsublite-kafka/issues/114)) ([f9bbaff](https://www.github.com/googleapis/java-pubsublite-kafka/commit/f9bbaffe3341455fd9e73b99ff944a370434f329))


### Dependencies

* update dependency org.apache.kafka:kafka-clients to v2.8.0 ([#116](https://www.github.com/googleapis/java-pubsublite-kafka/issues/116)) ([7115868](https://www.github.com/googleapis/java-pubsublite-kafka/commit/7115868f06d16bc0d2b64c9bd44137a0f0fcb8ee))
* update to google-cloud-pubsublite v0.14.1 ([#121](https://www.github.com/googleapis/java-pubsublite-kafka/issues/121)) ([5135c6b](https://www.github.com/googleapis/java-pubsublite-kafka/commit/5135c6bf05e136729c092cb6183534b802ac6e93))
* update to google-cloud-pubsublite v0.14.2 ([#129](https://www.github.com/googleapis/java-pubsublite-kafka/issues/129)) ([3ff2cdf](https://www.github.com/googleapis/java-pubsublite-kafka/commit/3ff2cdfc296ff4ba2f9a37c845d9da112689bda5))

### [0.2.3](https://www.github.com/googleapis/java-pubsublite-kafka/compare/v0.2.2...v0.2.3) (2021-03-03)


### Bug Fixes

* Add default batching in producer settings ([#96](https://www.github.com/googleapis/java-pubsublite-kafka/issues/96)) ([a79b2a3](https://www.github.com/googleapis/java-pubsublite-kafka/commit/a79b2a330b055aea93d6f702c2fbff28757aca79))

### [0.2.2](https://www.github.com/googleapis/java-pubsublite-kafka/compare/v0.2.1...v0.2.2) (2021-03-02)


### Dependencies

* update dependency com.google.api.grpc:proto-google-cloud-pubsublite-v1 to v0.11.1 ([#88](https://www.github.com/googleapis/java-pubsublite-kafka/issues/88)) ([7a576a1](https://www.github.com/googleapis/java-pubsublite-kafka/commit/7a576a19be2b0671c319a75de75645449ec545f9))
* update dependency com.google.cloud:google-cloud-pubsublite to v0.11.1 ([#89](https://www.github.com/googleapis/java-pubsublite-kafka/issues/89)) ([0975722](https://www.github.com/googleapis/java-pubsublite-kafka/commit/0975722736dc12f5bfd1590294ec854e7fde93f3))
* update dependency com.google.cloud:google-cloud-pubsublite-parent to v0.11.1 ([#90](https://www.github.com/googleapis/java-pubsublite-kafka/issues/90)) ([cba8a24](https://www.github.com/googleapis/java-pubsublite-kafka/commit/cba8a24e693b559063b3b4ecbb8a27c26ff00c2f))

### [0.2.1](https://www.github.com/googleapis/java-pubsublite-kafka/compare/v0.2.0...v0.2.1) (2021-03-01)


### Bug Fixes

* Add admin client in producer settings ([#82](https://www.github.com/googleapis/java-pubsublite-kafka/issues/82)) ([c1cf1d1](https://www.github.com/googleapis/java-pubsublite-kafka/commit/c1cf1d1ff5d44d67219da61bb85120774bb6a724))

## [0.2.0](https://www.github.com/googleapis/java-pubsublite-kafka/compare/v0.1.1...v0.2.0) (2021-02-26)


### Features

* Add support for increasing partitions to the kafka shim ([#37](https://www.github.com/googleapis/java-pubsublite-kafka/issues/37)) ([13f2138](https://www.github.com/googleapis/java-pubsublite-kafka/commit/13f2138c3274c52ea19d4fcac1fb0be3576a7acc))


### Bug Fixes

* **readme:** update readme snippets ([#31](https://www.github.com/googleapis/java-pubsublite-kafka/issues/31)) ([cb262aa](https://www.github.com/googleapis/java-pubsublite-kafka/commit/cb262aaa170d3088d517457d445feeda612bc0f2))
* update exception handling exposed by underlying API translating changes ([#53](https://www.github.com/googleapis/java-pubsublite-kafka/issues/53)) ([85d3119](https://www.github.com/googleapis/java-pubsublite-kafka/commit/85d3119d476b47f3ec28396d0107f15092c9b4f9))
* update repo name ([#67](https://www.github.com/googleapis/java-pubsublite-kafka/issues/67)) ([a43c890](https://www.github.com/googleapis/java-pubsublite-kafka/commit/a43c890af1e94fcdc38fa807937736368551035c))


### Documentation

* rename .readme_partials to .readme-partials ([#48](https://www.github.com/googleapis/java-pubsublite-kafka/issues/48)) ([263ed6a](https://www.github.com/googleapis/java-pubsublite-kafka/commit/263ed6ad642454d82f0d8954461826a4e3af81ed))
* update client library documentation link ([#77](https://www.github.com/googleapis/java-pubsublite-kafka/issues/77)) ([75a8fc2](https://www.github.com/googleapis/java-pubsublite-kafka/commit/75a8fc26ef2f1570eced6c41dbdd7c81068c838b))
* update readme source files ([#54](https://www.github.com/googleapis/java-pubsublite-kafka/issues/54)) ([c75c9c1](https://www.github.com/googleapis/java-pubsublite-kafka/commit/c75c9c1f8339543cc34514e1be75fb8427975366))


### Dependencies

* Bump underlying pub/sub lite version ([#79](https://www.github.com/googleapis/java-pubsublite-kafka/issues/79)) ([d901201](https://www.github.com/googleapis/java-pubsublite-kafka/commit/d9012016c0642f544ec6c821e1f2fa49a6c77cb6))
* update dependency com.google.api.grpc:proto-google-cloud-pubsublite-v1 to v0.10.0 ([#58](https://www.github.com/googleapis/java-pubsublite-kafka/issues/58)) ([28c821a](https://www.github.com/googleapis/java-pubsublite-kafka/commit/28c821a42a0d301a4499a13ae89cf0f6a07dedb7))
* update dependency com.google.cloud:google-cloud-pubsublite-parent to v0.10.0 ([#68](https://www.github.com/googleapis/java-pubsublite-kafka/issues/68)) ([3e0315f](https://www.github.com/googleapis/java-pubsublite-kafka/commit/3e0315f85525e78e345953524f1b4a1d6d92d99f))
* update dependency com.google.cloud:google-cloud-pubsublite-parent to v0.11.0 ([#76](https://www.github.com/googleapis/java-pubsublite-kafka/issues/76)) ([e1026c6](https://www.github.com/googleapis/java-pubsublite-kafka/commit/e1026c6cdadff7513a697fff4ae40db9944ff895))
* update dependency com.google.cloud:google-cloud-pubsublite-parent to v0.6.5 ([#28](https://www.github.com/googleapis/java-pubsublite-kafka/issues/28)) ([3245f61](https://www.github.com/googleapis/java-pubsublite-kafka/commit/3245f61ff0800d0938ab171d4d2755289cd09b79))
* update dependency com.google.cloud:google-cloud-pubsublite-parent to v0.8.0 ([#45](https://www.github.com/googleapis/java-pubsublite-kafka/issues/45)) ([2416797](https://www.github.com/googleapis/java-pubsublite-kafka/commit/24167976e9bc4a14fb0754dfd174ea75516ada1e))
* update dependency com.google.cloud:google-cloud-pubsublite-parent to v0.9.0 ([#60](https://www.github.com/googleapis/java-pubsublite-kafka/issues/60)) ([6f3a731](https://www.github.com/googleapis/java-pubsublite-kafka/commit/6f3a731eaaca4fd19f4b522b8e0ed0488d93e2a6))
* update dependency org.apache.kafka:kafka-clients to v2.7.0 ([#41](https://www.github.com/googleapis/java-pubsublite-kafka/issues/41)) ([df3802c](https://www.github.com/googleapis/java-pubsublite-kafka/commit/df3802c5a405f8bd35ba4f81cb3b8e62e3eaa539))

### [0.1.1](https://www.github.com/googleapis/java-pubsublite-kafka/compare/v0.1.0...v0.1.1) (2020-11-18)


### Bug Fixes

* Start assigner when subscribe() is called ([#17](https://www.github.com/googleapis/java-pubsublite-kafka/issues/17)) ([f143f9d](https://www.github.com/googleapis/java-pubsublite-kafka/commit/f143f9dc23a760e7c0e8204ac7c6ba0ca2feb98a))


### Documentation

* Update version after a release happened ([26bc438](https://www.github.com/googleapis/java-pubsublite-kafka/commit/26bc438051c789221834b1801280bdbee54f0f64))

## 0.1.0 (2020-10-30)


### Features

* Init repo ([584584f](https://www.github.com/googleapis/java-pubsublite-kafka/commit/584584f3ca9d0d193caf58fedc41509187d3d706))


### Dependencies

* update dependency com.google.api.grpc:proto-google-cloud-pubsublite-v1 to v0.6.1 ([#4](https://www.github.com/googleapis/java-pubsublite-kafka/issues/4)) ([f7837f3](https://www.github.com/googleapis/java-pubsublite-kafka/commit/f7837f3a36f16c6fe7d52f7fe2088c863f2c12d2))
