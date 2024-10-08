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

# SiteToSiteProvenanceReportingTask

The Site-to-Site Provenance Reporting Task allows the user to publish all the Provenance Events from a NiFi instance
back to the same NiFi instance or another NiFi instance. This provides a great deal of power because it allows the user
to make use of all the different Processors that are available in NiFi in order to process or distribute that data. When
possible, it is advisable to send the Provenance data to a different NiFi instance than the one that this Reporting Task
is running on, because when the data is received over Site-to-Site and processed, that in and of itself will generate
Provenance events. As a result, there is a cycle that is created. However, the data is sent in batches (1,000 by
default). This means that for each batch of Provenance events that are sent back to NiFi, the receiving NiFi will have
to generate only a single event per component.

By default, when published to a NiFi instance, the Provenance data is sent as a JSON array. However, the user can define
a Record Writer and directly specify the output format and data with the assumption that the input schema is defined as
follows:

```json
{
  "type": "record",
  "name": "provenance",
  "namespace": "provenance",
  "fields": [
    {
      "name": "eventId",
      "type": "string"
    },
    {
      "name": "eventOrdinal",
      "type": "long"
    },
    {
      "name": "eventType",
      "type": "string"
    },
    {
      "name": "timestampMillis",
      "type": "long"
    },
    {
      "name": "durationMillis",
      "type": "long"
    },
    {
      "name": "lineageStart",
      "type": {
        "type": "long",
        "logicalType": "timestamp-millis"
      }
    },
    {
      "name": "details",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "componentId",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "componentType",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "componentName",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "processGroupId",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "processGroupName",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "entityId",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "entityType",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "entitySize",
      "type": [
        "null",
        "long"
      ]
    },
    {
      "name": "previousEntitySize",
      "type": [
        "null",
        "long"
      ]
    },
    {
      "name": "updatedAttributes",
      "type": {
        "type": "map",
        "values": "string"
      }
    },
    {
      "name": "previousAttributes",
      "type": {
        "type": "map",
        "values": "string"
      }
    },
    {
      "name": "actorHostname",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "contentURI",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "previousContentURI",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "parentIds",
      "type": {
        "type": "array",
        "items": "string"
      }
    },
    {
      "name": "childIds",
      "type": {
        "type": "array",
        "items": "string"
      }
    },
    {
      "name": "platform",
      "type": "string"
    },
    {
      "name": "application",
      "type": "string"
    },
    {
      "name": "remoteIdentifier",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "alternateIdentifier",
      "type": [
        "null",
        "string"
      ]
    },
    {
      "name": "transitUri",
      "type": [
        "null",
        "string"
      ]
    }
  ]
}
```