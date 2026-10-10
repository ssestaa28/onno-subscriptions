#!/bin/sh
set -eu
: "${APP_ADMIN_PASSWORD:?Set APP_ADMIN_PASSWORD in Render environment variables}"
exec java -Xms64m -Xmx256m -Xss512k -XX:ActiveProcessorCount=2 -jar /app/app.jar
