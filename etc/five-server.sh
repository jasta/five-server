#!/bin/sh
base=$(dirname $0)

libs="$base/main/five-server.jar"

# Figure out which architecture Java needs so we can load the correct swt.jar
# for this platform.
arch=$(java -cp "$libs" com.android.archquery.Main)
libs="$libs:$base/libs/$arch/swt.jar"

libs="$libs:$base/libs/commons-logging.jar"
libs="$libs:$base/libs/entagged-audioformats-0.15.jar"
libs="$libs:$base/libs/hsqldb-1.8.0.10.jar"
libs="$libs:$base/libs/httpclient-4.0.jar"
libs="$libs:$base/libs/httpcore-4.0.1.jar"
libs="$libs:$base/libs/jcip-annotations-1.0.jar"
libs="$libs:$base/libs/protobuf-2.0.3.jar"


java -cp "$libs" org.devtcg.five.Main "$@"
