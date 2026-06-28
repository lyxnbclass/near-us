create table future_letters (
  id bigint primary key auto_increment,
  couple_id bigint not null,
  sender_user_id bigint not null,
  recipient_user_id bigint null,
  title varchar(120) not null,
  content_cipher text not null,
  content_nonce varchar(64) not null,
  open_at timestamp not null,
  delivered_at timestamp null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  deleted_at timestamp null,
  index idx_future_letters_couple_time (couple_id, open_at),
  constraint fk_future_letter_couple foreign key (couple_id) references couples(id),
  constraint fk_future_letter_sender foreign key (sender_user_id) references users(id)
);
