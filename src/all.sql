-- :name create-run-table
-- :command :execute
-- :result :raw
-- :doc Create run table
create table if not exists run (
  worker_id  text not null,
  task_name  text not null default 'card',
  run_number varchar(2) not null default '1',
  -- tp prev varchar(2). now text to use as mturk's "assignmentId"
  -- ALTER TABLE run ALTER COLUMN timepoint TYPE text;
  timepoint  text not null default '1',
  version    text not null,
  json       text,
  system_info text,
  created_at timestamp not null default current_timestamp,
  finished_at timestamp,
  -- currently not useful
  --FOREIGN KEY(worker_id) REFERENCES worker(worker_id),
  PRIMARY KEY (worker_id, task_name, version, run_number, timepoint)
)

-- :name create-worker-table
-- :command :execute
-- :result :raw
-- :doc Create worker table
create table worker (
  worker_id  text primary key,
  create_at timestamp not null default current_timestamp
)


-- :name recent-runs :n
-- :doc summary info for each run. everytyhing but info and json
select worker_id, task_name, version, timepoint, run_number, created_at, finished_at
from run
order by created_at desc limit 10

--  ":1" result = a single record (hashmap)
-- :name run-by-id :? :1
-- :doc Get all run info by run info (id,task,timepoint,run,version)
select * from run where worker_id = :id and task_name like :task and timepoint = :timepoint and run_number = :run and version = :version

-- :name get-run-json :? :1
-- :doc get task data for a single run
select json from run
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint


-- :name most-recent :? :n
-- :doc find the most recently finished row. :id can be '%'
-- "with" doesn't play with hugsql?
select *
 from run
 join (select worker_id, max(finished_at) as finished_at from run where worker_id like :id group by worker_id) as mx
 on
  run.worker_id = mx.worker_id and
  run.finished_at = mx.finished_at

--  :n returns affected row count
-- :name create-run-sqll :! :n
-- :doc create or update response. TODO: add run
replace into run (worker_id, task_name, version, run_number, timepoint, system_info)
values (:id, :task, :version, :run, :timepoint, :info)

--  :n returns affected row count
-- :name create-run-psql :! :n
-- :doc create or update response. TODO: add run
insert into run (worker_id, task_name, version, run_number, timepoint, system_info)
values (:id, :task, :version, :run, :timepoint, :info)
on conflict (worker_id,task_name,version, run_number, timepoint) do update set system_info = :info


-- :name upload-json :! :n
-- :doc create or update response. TODO: add run
update run set json = :json
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint

-- :name upload-system-info :! :n
-- :doc create or update response. TODO: add run
update run set system_info = :info
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint

-- :name finish-run :! :n
-- :doc set finished_at to current time
update run set finished_at = current_timestamp
  where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint


-- :name md5-finish :? :1
-- :doc get 5 digit code from md5sum of fininsh
select left(md5(finished_at||json),5)
from run
where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint

-- :name string-for-md5-finish :? :1
-- :doc get 5 digit code from md5sum of fininsh
select finished_at||json as runinfostr
from run
where worker_id = :id and
        task_name = :task and
	version = :version and
        run_number = :run and
        timepoint = :timepoint
