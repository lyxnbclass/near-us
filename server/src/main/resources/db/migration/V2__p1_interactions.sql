create table affection_cards (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  created_by bigint not null,
  title varchar(120) not null,
  amount decimal(10, 2) null,
  message varchar(1000),
  file_id bigint null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_affection_couple_time (couple_id, created_at),
  constraint fk_affection_couple foreign key (couple_id) references couples(id)
);

create table wishes (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  created_by bigint not null,
  title varchar(160) not null,
  note varchar(800),
  completed boolean not null default false,
  completed_by bigint null,
  completed_at timestamp null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_wishes_couple_done (couple_id, completed, created_at),
  constraint fk_wish_couple foreign key (couple_id) references couples(id)
);

create table daily_topics (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  topic_date date not null,
  question varchar(300) not null,
  created_at timestamp not null default current_timestamp,
  unique key uk_daily_topic (couple_id, topic_date),
  constraint fk_topic_couple foreign key (couple_id) references couples(id)
);

create table daily_topic_answers (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  topic_id bigint not null,
  user_id bigint not null,
  answer varchar(1000) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_topic_answer_user (topic_id, user_id),
  constraint fk_answer_topic foreign key (topic_id) references daily_topics(id)
);
