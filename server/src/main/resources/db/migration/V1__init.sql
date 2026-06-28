create table users (
  id bigint primary key auto_increment,
  phone varchar(32) unique,
  openid varchar(128) unique,
  nickname varchar(64) not null,
  avatar_url varchar(512),
  status varchar(32) not null default 'active',
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null
);

create table couples (
  id bigint primary key auto_increment,
  invite_code_hash varchar(128),
  invite_expires_at timestamp null,
  paired_at timestamp null,
  anniversary_start_date date null,
  status varchar(32) not null default 'inviting',
  unbind_requested_by bigint null,
  unbind_requested_at timestamp null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_couples_invite_hash (invite_code_hash),
  index idx_couples_status (status)
);

create table couple_members (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  user_id bigint not null,
  role varchar(32) not null default 'member',
  joined_at timestamp not null default current_timestamp,
  left_at timestamp null,
  status varchar(32) not null default 'active',
  unique key uk_active_user (user_id, status),
  index idx_member_couple (couple_id),
  constraint fk_member_couple foreign key (couple_id) references couples(id),
  constraint fk_member_user foreign key (user_id) references users(id)
);

create table couple_modules (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  module_key varchar(64) not null,
  enabled boolean not null default false,
  enabled_at timestamp null,
  disabled_at timestamp null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_couple_module (couple_id, module_key),
  constraint fk_module_couple foreign key (couple_id) references couples(id)
);

create table files (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  owner_user_id bigint not null,
  object_key varchar(512) not null,
  original_name varchar(255),
  mime_type varchar(128) not null,
  size_bytes bigint not null default 0,
  width int null,
  height int null,
  checksum varchar(128),
  created_at timestamp not null default current_timestamp,
  deleted_at timestamp null,
  index idx_files_couple (couple_id),
  constraint fk_files_couple foreign key (couple_id) references couples(id),
  constraint fk_files_owner foreign key (owner_user_id) references users(id)
);

create table album_items (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  file_id bigint not null,
  caption varchar(512),
  scene_type varchar(64) not null default 'couple_album',
  scene_ref_id bigint null,
  taken_at timestamp null,
  created_by bigint not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_album_timeline (couple_id, scene_type, created_at),
  constraint fk_album_couple foreign key (couple_id) references couples(id),
  constraint fk_album_file foreign key (file_id) references files(id),
  constraint fk_album_user foreign key (created_by) references users(id)
);

create table statuses (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  created_by bigint not null,
  content varchar(1000),
  mood_tag varchar(64),
  file_id bigint null,
  created_at timestamp not null default current_timestamp,
  deleted_at timestamp null,
  index idx_statuses_couple_time (couple_id, created_at),
  constraint fk_status_couple foreign key (couple_id) references couples(id),
  constraint fk_status_user foreign key (created_by) references users(id)
);

create table status_reactions (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  status_id bigint not null,
  created_by bigint not null,
  reaction_key varchar(64) not null,
  created_at timestamp not null default current_timestamp,
  constraint fk_reaction_status foreign key (status_id) references statuses(id)
);

create table diaries (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  owner_user_id bigint not null,
  title varchar(120) not null,
  visibility varchar(32) not null,
  content_cipher text not null,
  content_nonce varchar(64) not null,
  cover_file_id bigint null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_diaries_couple_time (couple_id, created_at),
  constraint fk_diary_couple foreign key (couple_id) references couples(id),
  constraint fk_diary_owner foreign key (owner_user_id) references users(id)
);

create table diary_comments (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  diary_id bigint not null,
  created_by bigint not null,
  content_cipher text not null,
  content_nonce varchar(64) not null,
  created_at timestamp not null default current_timestamp,
  deleted_at timestamp null,
  constraint fk_comment_diary foreign key (diary_id) references diaries(id)
);

create table anniversaries (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  created_by bigint not null,
  title varchar(120) not null,
  event_date date not null,
  event_type varchar(64) not null default 'custom',
  remind_time time null,
  card_theme varchar(64) not null default 'warm',
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_anniversary_couple_date (couple_id, event_date),
  constraint fk_anniversary_couple foreign key (couple_id) references couples(id)
);

create table notifications (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  recipient_user_id bigint not null,
  actor_user_id bigint null,
  type varchar(64) not null,
  title varchar(120) not null,
  body varchar(500),
  read_at timestamp null,
  created_at timestamp not null default current_timestamp,
  index idx_notification_user_time (recipient_user_id, created_at)
);

create table pet_profiles (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  name varchar(80) not null,
  breed varchar(120),
  birthday date null,
  avatar_file_id bigint null,
  created_by bigint not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_pet_couple (couple_id)
);

create table pet_events (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  pet_id bigint not null,
  event_type varchar(64) not null,
  content varchar(1000),
  file_id bigint null,
  created_by bigint not null,
  created_at timestamp not null default current_timestamp,
  deleted_at timestamp null,
  index idx_pet_event_time (couple_id, pet_id, created_at),
  constraint fk_pet_event_pet foreign key (pet_id) references pet_profiles(id)
);
