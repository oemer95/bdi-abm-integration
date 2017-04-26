#!/bin/bash
set -e # abort if any command fails

DIR=`dirname "$0"`
SERVER=gams-macbook # assumes this is configured in .ssh/config
DESTDIR=./testing # directory on server (will be created if needed, and contents deleted if existing)


ssh $SERVER "mkdir -p $DESTDIR/target/classes/gams/"
rsync --delete -avz $DIR/../target/conservation-ethics-2.0.2-SNAPSHOT-jar-with-dependencies.jar $SERVER:$DESTDIR/target/
rsync --delete -avz $DIR/../target/classes/gams/bid_selection_model.gms $SERVER:$DESTDIR/target/classes/gams/
rsync --delete -avz  $DIR/*{.sh,.py} $SERVER:$DESTDIR/test/ --exclude=`basename $0`
rsync --delete -avz $DIR/output/ $SERVER:$DESTDIR/test/output/ --exclude="/log" --delete-excluded


ssh $SERVER "cd $DESTDIR/test; ./qsub-run-samples.sh --local ./output > log.out 2>&1 < /dev/null &"

