#!/usr/bin/env bats
setup() {
   PSICLJ="$(pwd)/psiclj"
   [ ! -x $PSICLJ ] && echo "MISSING BINARY '$PSICLJ'" >&2 && return 127
   MYTEMP=$(mktemp -d /tmp/psiclj-bats-XXXXX)
   PID=""
   cd "$MYTEMP"
   mkdir out/
   echo "index" > out/index.html
   echo "not-found" > out/not-found.html
}
teardown() {
    cd
    [ -n "$PID" ] && jobs -p |grep $PID && kill $PID;
    [[ -r "$MYTEMP" &&
       -n "$MYTEMP" &&
       "$MYTEMP" =~ /tmp ]] && rm -r "$MYTEMP"
    return 0
}
start_server(){
    $PSICLJ "$@" &
    PID=$!
    maxtry=20
    for i in $(seq 1 $maxtry); do
        curl -sq http://localhost:3001/ && break
        sleep 1
    done
    # make it to max? something when wrong
    [ $i -ge $maxtry ] &&
        echo "ERROR: could not start server with opts $*" >&2 &&
        return 1
    echo $PID
}
start_and_finish(){
    run curl http://localhost:3001/id/task/1/1/
    [[ $output =~ index ]] # contents of index.html from setup
    sleep 1
    curl -X POST http://localhost:3001/id/task/1/1/finish
}

@test "sqllite packaged correctly" { 
    start_server -r out -p 3001
}

@test "create json file and update db" { 
    start_server -r out -p 3001 -v version
    start_and_finish
    sleep 1

    # debugging
    pwd >&2; ls >&2

    # actual checks
    [ -r psiclj.sqlite3 ]
    run sqlite3 psiclj.sqlite3 'select worker_id from run where finished_at is not null'
    [[ $output =~ id ]]
    [ -r id_task_1_1_version.json ]
}
