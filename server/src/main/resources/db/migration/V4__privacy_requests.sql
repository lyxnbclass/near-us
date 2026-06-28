create table privacy_requests (
  id bigint primary key auto_increment,
  user_id bigint not null,
  couple_id bigint null,
  request_type varchar(64) not null,
  status varchar(32) not null default 'pending',
  requested_at timestamp not null default current_timestamp,
  scheduled_delete_at timestamp null,
  completed_at timestamp null,
  note varchar(500),
  index idx_privacy_user_time (user_id, requested_at),
  constraint fk_privacy_request_user foreign key (user_id) references users(id)
);
