#!/bin/bash

scaladoc -version >/dev/null 2>&1 || { echo >&2 "Please install the scala command line tools before generating documentation.  Aborting."; exit 1; }

scaladoc "$@" -d ./scaladoc $(find src/main -name '*.scala') $(find src/main -name '*.java')
