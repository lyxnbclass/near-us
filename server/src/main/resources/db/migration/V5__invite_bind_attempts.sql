create table invite_bind_attempts (
  id bigint primary key auto_increment,
  user_id bigint not null,
  ip_hash varchar(128),
  invite_code_hash varchar(128) not null,
  success boolean not null default false,
  failure_code varchar(64),
  attempted_at timestamp not null default current_timestamp,
  index idx_invite_attempt_user_time (user_id, attempted_at),
  index idx_invite_attempt_ip_time (ip_hash, attempted_at),
  index idx_invite_attempt_hash_time (invite_code_hash, attempted_at),
  constraint fk_invite_attempt_user foreign key (user_id) references users(id)
);
