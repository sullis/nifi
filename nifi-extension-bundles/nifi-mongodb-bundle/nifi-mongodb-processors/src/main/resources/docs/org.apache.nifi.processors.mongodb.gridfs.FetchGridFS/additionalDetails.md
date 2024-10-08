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

# FetchGridFS

## Description:

This processor retrieves one or more files from GridFS. The query can be provided in one of three ways:

* Query configuration parameter.
* Built for you by configuring the filename parameter. (Note: this is just a filename, Mongo queries cannot be embedded
  in the field).
* Retrieving the query from the flowfile contents.

The processor can also be configured to either commit only once at the end of a fetch operation or after each file that
is retrieved. Multiple commits is generally only necessary when retrieving a lot of data from GridFS as measured in
total data size, not file count, to ensure that the disks NiFi is using are not overloaded.