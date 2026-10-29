#!/bin/sh
# Run the full Maven reactor with the project's required Java version.
set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$project_root"

if [ -n "${JAVA_HOME:-}" ]; then
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi
java_version=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/{print $3}')
if [ "$java_version" != 21 ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
  java_version=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version =/{print $3}')
fi
if [ "$java_version" != 21 ]; then
  printf '%s\n' 'Quiz Arena requires JDK 21. Set JAVA_HOME to a JDK 21 installation.' >&2
  exit 1
fi

# Extra Maven arguments are passed through, e.g. -Pmysql-it or -Dquiz.uiEvidence=/absolute/path.
exec mvn verify "$@"
