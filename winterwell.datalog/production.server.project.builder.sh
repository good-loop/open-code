#!/bin/bash
/home/winterwell/config/build-scripts/builder.sh \
BUILD_TYPE="production" \
PROJECT_NAME="datalogger" \
NAME_OF_SERVICE="lg" \
GIT_REPO_URL="github.com:good-loop/open-code" \
PROJECT_ROOT_ON_SERVER="/home/winterwell/open-code/winterwell.datalog" \
PROJECT_USES_BOB="yes" \
PROJECT_USES_NPM="no" \
PROJECT_USES_WEBPACK="no" \
PROJECT_USES_JERBIL="no" \
PROJECT_USES_WWAPPBASE_SYMLINK="no"