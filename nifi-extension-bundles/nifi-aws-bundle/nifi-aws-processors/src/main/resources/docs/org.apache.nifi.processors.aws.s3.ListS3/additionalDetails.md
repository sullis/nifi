<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# ListS3

## Streaming Versus Batch Processing

ListS3 performs a listing of all S3 Objects that it encounters in the configured S3 bucket. There are two common,
broadly defined use cases.

### Streaming Use Case

By default, the Processor will create a separate FlowFile for each object in the bucket and add attributes for filename,
bucket, etc. A common use case is to connect ListS3 to the FetchS3 processor. These two processors used in conjunction
with one another provide the ability to easily monitor a bucket and fetch the contents of any new object as it lands in
S3 in an efficient streaming fashion.

### Batch Use Case

Another common use case is the desire to process all newly arriving objects in a given bucket, and to then perform some
action only when all objects have completed their processing. The above approach of streaming the data makes this
difficult, because NiFi is inherently a streaming platform in that there is no "job" that has a beginning and an end.
Data is simply picked up as it becomes available.

To solve this, the ListS3 Processor can optionally be configured with a Record Writer. When a Record Writer is
configured, a single FlowFile will be created that will contain a Record for each object in the bucket, instead of a
separate FlowFile per object. See the documentation for ListFile for an example of how to build a dataflow that allows
for processing all the objects before proceeding with any other step.

One important difference between the data produced by ListFile and ListS3, though, is the structure of the Records that
are emitted. The Records emitted by ListFile have a different schema than those emitted by ListS3. ListS3 emits records
that follow the following schema (in Avro format):

```json
{
  "type": "record",
  "name": "nifiRecord",
  "namespace": "org.apache.nifi",
  "fields": [
    {
      "name": "key",
      "type": "string"
    },
    {
      "name": "bucket",
      "type": "string"
    },
    {
      "name": "owner",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "etag",
      "type": "string"
    },
    {
      "name": "lastModified",
      "type": {
        "type": "long",
        "logicalType": "timestamp-millis"
      }
    },
    {
      "name": "size",
      "type": "long"
    },
    {
      "name": "storageClass",
      "type": "string"
    },
    {
      "name": "latest",
      "type": "boolean"
    },
    {
      "name": "versionId",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "tags",
      "type": [
        "null",
        {
          "type": "map",
          "values": "string"
        }
      ]
    },
    {
      "name": "userMetadata",
      "type": [
        "null",
        {
          "type": "map",
          "values": "string"
        }
      ]
    }
  ]
}
```