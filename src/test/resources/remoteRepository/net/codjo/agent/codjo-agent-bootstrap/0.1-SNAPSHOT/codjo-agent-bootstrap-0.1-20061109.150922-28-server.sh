#!/bin/sh
#
# Script de demarrage/arret d'un serveur ${artifactId}
#

SERVER_NAME=${artifactId}
LOG_DIR=${logDir}
MAIN_CLASS=${serverMainClass}



LOG_FILE=${LOG_DIR}/server-java.log

DEFAULT_ARG="-configuration ./server-config.properties"

PIDS=`/usr/ucb/ps auxwww | grep ${SERVER_NAME} | grep -v grep | awk '{print $2}'`

# Pour demarrer un process
callJava() {
        java -Djava.io.tmpdir=/var/tmp/ -Dfile.encoding=ISO-8859-1 -Dlog.dir=$LOG_DIR -cp $MY_CLASSPATH $1 $2 >> ${LOG_FILE} 2>&1 &
}

# Procedure pour tuer des process
killServer() {
        [ "$PIDS" != "" ] && kill $1 $PIDS >/dev/null 2>&1
}

case "$1" in
  'start')
        echo "Starting server..."
        touch ${LOG_FILE}
        mv ${LOG_FILE} ${LOG_FILE}.old

        set MY_CLASSPATH=""
        for i in `ls *.jar`
        do
            MY_CLASSPATH=$i:$MY_CLASSPATH
        done

        callJava $MAIN_CLASS "${DEFAULT_ARG}"
        ;;

  'stop')
        echo "Stopping server..."
        killServer -15
        ;;

  'fstop')
        echo "Killing server"
        killServer -9
        ;;
  'status')
        if [ -z "${PIDS}" ]
        then
                echo "Status server : OFF"
                exit 0
        else
                echo "Status server : ON"
                exit 1
        fi
        ;;
*)
        echo "Usage: $0 { start | stop | fstop | status}"
        ;;
esac
exit 0
